import sqlite3

import tantivy
from jane_core.search_service import SearchService
from jane_indexer.build_index import build_index
from jane_indexer.journal_metadata import JournalMetadata
from jane_indexer.sqlite_source import Citation, compute_journal_sets, window_ordinals


def _make_citation(pmid, journal, ptypes, abstract="cancer tumor cell growth", recent=True):
    c = Citation(
        pmid=pmid,
        title="T",
        abstract=abstract,
        authors=["Smith, J"],
        affiliations=["Lab a@b.edu"],
        journal_name=journal,
        journal_abbr=journal,
        journal_medline_abbr=journal,
        issn="1111-2222",
        issn_linking="1111-2222",
        year="2024",
        publication_types=ptypes,
        language="eng",
    )
    c.recent = recent
    return c


def test_build_index_applies_inclusion(tmp_path):
    meta = JournalMetadata(open_access_issns={"1111-2222"})
    cits = [
        _make_citation(1, "Jour A", ["Journal Article"]),
        _make_citation(2, "Jour A", ["Comment"]),  # excluded type
        _make_citation(3, "Jour A", ["Journal Article"], abstract=""),  # no abstract
        _make_citation(4, "Jour B", ["Journal Article"]),  # not in valid set
    ]
    n = build_index(cits, meta, str(tmp_path / "idx"), valid_journals={"Jour A"})
    assert n == 1  # only pmid 1 survives

    idx = tantivy.Index.open(str(tmp_path / "idx"))
    svc = SearchService(idx, min_doc_freq=1)
    papers = svc.find_similar_papers("cancer tumor cell growth", count=10)
    assert [p.pmid for p in papers] == ["1"]
    assert papers[0].journal == "Jour A"


def test_compute_journal_sets(tmp_path):
    db = str(tmp_path / "p.sqlite")
    conn = sqlite3.connect(db)
    conn.execute(
        "CREATE TABLE pubmed_articles (pmid INTEGER PRIMARY KEY, journal_medline_abbr TEXT, "
        "journal_name TEXT, journal_abbr TEXT, publication_date INTEGER)"
    )
    import datetime

    today = datetime.date.today().toordinal()
    old = datetime.date.today().replace(year=datetime.date.today().year - 5).toordinal()
    rows = [(i, "Active J", "Active J", "Active J", today) for i in range(25)]
    rows += [(100 + i, "Old J", "Old J", "Old J", old) for i in range(25)]
    rows += [(200 + i, "Tiny J", "Tiny J", "Tiny J", today) for i in range(3)]
    conn.executemany("INSERT INTO pubmed_articles VALUES (?,?,?,?,?)", rows)
    conn.commit()
    conn.row_factory = sqlite3.Row
    lower, recent, upper = window_ordinals()
    valid, recent_set = compute_journal_sets(conn, lower, recent, upper)
    assert "Active J" in valid and "Old J" in valid  # both >=20 in 10y window
    assert "Tiny J" not in valid  # <20
    assert "Active J" in recent_set and "Old J" not in recent_set


def test_tarball_roundtrip_preserves_index(tmp_path):
    from jane_indexer.package_upload import artifact_name, extract_tarball, make_tarball

    meta = JournalMetadata(open_access_issns={"1111-2222"})
    build_index(
        [_make_citation(1, "Jour A", ["Journal Article"])],
        meta,
        str(tmp_path / "idx"),
        valid_journals={"Jour A"},
    )
    archive = make_tarball(str(tmp_path / "idx"), str(tmp_path / artifact_name("2024-01")))
    extract_tarball(archive, str(tmp_path / "restored"))
    idx = tantivy.Index.open(str(tmp_path / "restored"))
    svc = SearchService(idx, min_doc_freq=1)
    assert [p.pmid for p in svc.find_similar_papers("cancer tumor cell growth")] == ["1"]
