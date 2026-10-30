import pytest
from jane_indexer.build_index import build_index
from jane_indexer.journal_metadata import JournalMetadata
from jane_indexer.sqlite_source import Citation


def _cit(pmid, journal, text, authors, recent=True):
    c = Citation(
        pmid=pmid,
        title=f"Title {pmid}",
        abstract=text,
        authors=authors,
        affiliations=[f"Dept. {authors[0].split(',')[0].lower()}@{journal.split()[0].lower()}.edu"],
        journal_name=journal,
        journal_abbr=journal,
        journal_medline_abbr=journal,
        issn="1111-2222",
        issn_linking="1111-2222",
        year="2024",
        publication_types=["Journal Article"],
        language="eng",
    )
    c.recent = recent
    return c


def _build_index(path):
    cits = [
        _cit(
            "1",
            "Cardiology J",
            "heart failure cardiac myocardial infarction treatment",
            ["Smith, J", "Doe, A"],
        ),
        _cit(
            "2",
            "Cardiology J",
            "heart cardiac ventricular remodeling therapy outcomes",
            ["Smith, J"],
        ),
        _cit("3", "Oncology J", "tumor cancer oncology chemotherapy survival analysis", ["Lee, B"]),
    ]
    meta = JournalMetadata(open_access_issns={"1111-2222"}, medline_indexed_issns={"1111-2222"})
    build_index(cits, meta, path, valid_journals={"Cardiology J", "Oncology J"})


@pytest.fixture()
def client(tmp_path, monkeypatch):
    idx_dir = tmp_path / "idx"
    idx_dir.mkdir()
    _build_index(str(idx_dir))
    monkeypatch.setenv("JANE_INDEX_PATH", str(idx_dir))
    monkeypatch.setenv("JANE_ADMIN_API_KEY", "admin-secret")
    monkeypatch.setenv("JANE_EMAIL_API_KEY", "email-secret")
    import importlib

    from jane_server import config

    importlib.reload(config)
    import jane_server.routes.admin as admin_mod
    import jane_server.routes.api as api_mod

    importlib.reload(api_mod)
    importlib.reload(admin_mod)
    import jane_server.app as app_mod

    importlib.reload(app_mod)
    from starlette.testclient import TestClient

    app = app_mod.create_app()
    # Tiny test corpus: relax the MoreLikeThis doc-frequency floor so terms aren't all dropped.
    app.state.index_manager.min_doc_freq = 1
    with TestClient(app) as c:
        yield c


def test_index_page(client):
    r = client.get("/")
    assert r.status_code == 200
    assert "Journal / Author Name Estimator" in r.text
    assert "Article Influence" not in r.text


def test_suggest_journals_rest(client):
    r = client.post(
        "/api/suggest_journals", json={"text": "heart cardiac myocardial infarction therapy"}
    )
    assert r.status_code == 200
    data = r.json()
    assert data and data[0]["name"] == "Cardiology J"
    assert "ai" not in data[0] and "airank" not in data[0]


def test_web_suggestions(client):
    r = client.post(
        "/suggestions", data={"text": "heart cardiac myocardial therapy", "action": "journals"}
    )
    assert r.status_code == 200
    assert "Cardiology J" in r.text
    assert "Article Influence" not in r.text


def test_author_emails_requires_key(client):
    body = {"author": "Smith, J", "coauthors": ["Doe, A"]}
    assert client.post("/api/author_emails", json=body).status_code == 401
    r = client.post("/api/author_emails", json=body, headers={"X-API-Key": "email-secret"})
    assert r.status_code == 200
    assert r.json()[0]["email"] == "smith@cardiology.edu"


def test_admin_reload(client):
    assert client.post("/admin/reload").status_code == 401
    r = client.post("/admin/reload", headers={"X-API-Key": "admin-secret"})
    assert r.status_code == 200
    assert r.json()["status"] == "reloaded"


def test_legacy_php_redirect(client):
    r = client.get("/faq.php", follow_redirects=False)
    assert r.status_code == 301
    assert r.headers["location"] == "/faq"
