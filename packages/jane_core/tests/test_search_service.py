"""End-to-end core search test over a tiny in-memory Tantivy index."""

import tantivy
from jane_core.filters import Filters
from jane_core.mlt import extract_terms
from jane_core.schema import (
    FIELD_AUTHORS,
    FIELD_AUTHORS_STORED,
    FIELD_EMAIL,
    FIELD_EMAIL_AUTHOR,
    FIELD_JOURNAL,
    FIELD_LANGUAGE,
    FIELD_OPEN_ACCESS,
    FIELD_PMID,
    FIELD_TEXT,
    FIELD_TITLE,
    FIELD_YEAR,
    build_schema,
)
from jane_core.search_service import SearchService
from jane_core.text import normalize_to_text

DOCS = [
    # (pmid, title, body, journal, authors, lang, oa, email, emailauthor)
    (
        1,
        "Tumor growth in cancer",
        "cancer tumor cell proliferation growth oncogene",
        "Journal of Oncology",
        ["Smith, J", "Doe, A"],
        "eng",
        True,
        "smith@onc.org",
        "Smith, J",
    ),
    (
        2,
        "Cancer therapy",
        "cancer therapy drug treatment tumor response",
        "Journal of Oncology",
        ["Smith, J", "Roe, B"],
        "eng",
        True,
        "",
        "",
    ),
    (
        3,
        "Cancer immunology",
        "cancer immune tumor cell antigen response",
        "Cancer Immunology",
        ["Lee, K"],
        "eng",
        False,
        "lee@imm.org",
        "Lee, K",
    ),
    (
        4,
        "Cardiac function",
        "heart cardiac muscle contraction function",
        "Cardiology Review",
        ["Brown, C"],
        "eng",
        False,
        "",
        "",
    ),
    (
        5,
        "Heart failure",
        "heart failure cardiac output ventricle",
        "Cardiology Review",
        ["Brown, C", "Smith, J"],
        "fre",
        False,
        "",
        "",
    ),
]


def _build_index():
    schema = build_schema()
    index = tantivy.Index(schema)
    writer = index.writer(50_000_000, 1)
    for pmid, title, body, journal, authors, lang, oa, email, emailauthor in DOCS:
        doc = tantivy.Document()
        doc.add_text(FIELD_TEXT, normalize_to_text(title + "\n" + body))
        doc.add_integer(FIELD_PMID, pmid)
        doc.add_text(FIELD_TITLE, title)
        doc.add_text(FIELD_JOURNAL, journal)
        for a in authors:
            doc.add_text(FIELD_AUTHORS, a)
        doc.add_text(FIELD_AUTHORS_STORED, "\n".join(authors))
        doc.add_text(FIELD_LANGUAGE, lang)
        doc.add_integer(FIELD_YEAR, 2024)
        doc.add_boolean(FIELD_OPEN_ACCESS, oa)
        if email:
            doc.add_text(FIELD_EMAIL, email)
            doc.add_text(FIELD_EMAIL_AUTHOR, emailauthor)
        # mark everything recent so suggest_journals (forces recent) returns results
        doc.add_boolean("recent", True)
        doc.add_boolean("medlineindexed", True)
        doc.add_boolean("nihpa", False)
        doc.add_integer("pmcmonths", -1)
        writer.add_document(doc)
    writer.commit()
    index.reload()
    return index


def test_extract_terms_reads_idf_from_index():
    index = _build_index()
    terms = extract_terms("cancer tumor therapy", index.searcher(), min_doc_freq=1)
    assert terms, "should extract at least one term"
    names = [t for t, _ in terms]
    assert "cancer" in names


def test_suggest_journals_ranks_oncology_first_and_normalizes():
    svc = SearchService(_build_index(), min_doc_freq=1)
    # Patch min_doc_freq via monkeypatch-free call: use a query matching the corpus.
    journals = svc.suggest_journals("cancer tumor cell oncogene therapy response")
    assert journals
    assert journals[0].name == "Journal of Oncology"
    # scores normalized to probabilities
    assert abs(sum(j.score for j in journals) - 1.0) < 1e-6


def test_suggest_authors_normalizes():
    svc = SearchService(_build_index(), min_doc_freq=1)
    authors = svc.suggest_authors("cancer tumor cell oncogene therapy response")
    assert authors
    assert abs(sum(a.score for a in authors) - 1.0) < 1e-6


def test_filters_language_restricts_results():
    svc = SearchService(_build_index(), min_doc_freq=1)
    papers = svc.find_similar_papers("heart cardiac function", Filters(language=["fre"]), count=10)
    assert all(p.pmid == "5" for p in papers)


def test_find_author_emails_requires_and_uses_disambiguation():
    svc = SearchService(_build_index(), min_doc_freq=1)
    emails = svc.find_author_emails("Smith, J", coauthors=["Doe, A"])
    assert [e.email for e in emails] == ["smith@onc.org"]
