"""Jinja2 server-rendered pages. Preserve / 301-redirect legacy *.php URLs. See NOTES section 7.

Minimal vanilla JS (no jQuery/jtip). Article Influence removed everywhere.
"""

from __future__ import annotations

import urllib.parse
import urllib.request

from fastapi import APIRouter, Form, HTTPException, Request
from fastapi.responses import HTMLResponse, RedirectResponse, StreamingResponse
from fastapi.templating import Jinja2Templates
from jane_core.filters import Filters

from ..logging_metrics import log_metrics
from ..webdata import LANGUAGES, PUBLICATION_TYPES

try:  # Python 3.9+: importlib.resources.files
    from importlib.resources import files as _files

    _TEMPLATE_DIR = str(_files("jane_server") / "templates")
except Exception:  # noqa: BLE001  # pragma: no cover
    import os

    _TEMPLATE_DIR = os.path.join(os.path.dirname(os.path.dirname(__file__)), "templates")

templates = Jinja2Templates(directory=_TEMPLATE_DIR)

router = APIRouter()


def _service(request: Request):
    manager = request.app.state.index_manager
    if not manager.is_open:
        raise HTTPException(status_code=503, detail="Index not available")
    return manager.service


def _build_filters(
    languages: list[str], types: list[str], openaccess: str, pubmedcentral: str
) -> tuple[Filters, dict[str, str]]:
    """Return (Filters, human-readable summary strings) from form values."""
    f = Filters(language=list(languages), types=list(types))
    summary: dict[str, str] = {}
    if languages:
        names = [n for n, v in LANGUAGES if v in languages]
        summary["languages"] = ", ".join(names or languages)
    if types:
        summary["types"] = ", ".join(types)
    if openaccess and openaccess != "no preference":
        f.openaccess = openaccess == "true"
        summary["openaccess"] = (
            "open access journals" if f.openaccess else "non-open access journals"
        )
    if pubmedcentral and pubmedcentral != "no preference":
        f.pmcmonths = pubmedcentral
        summary["pubmedcentral"] = {
            "immediate": "journals with immediate open access in PubMed Central",
            "inoneyear": "journals with open access in PubMed Central in 12 months or less",
            "any": "journals with open access in PubMed Central",
        }.get(pubmedcentral, "")
    return f, summary


@router.get("/", response_class=HTMLResponse)
def index(request: Request) -> HTMLResponse:
    return templates.TemplateResponse(
        request,
        "index.html",
        {"languages": LANGUAGES, "publication_types": PUBLICATION_TYPES},
    )


@router.post("/suggestions", response_class=HTMLResponse)
def suggestions(
    request: Request,
    text: str = Form(""),
    action: str = Form("journals"),
    language: list[str] = Form(default=[]),
    type: list[str] = Form(default=[]),
    openaccess: str = Form("no preference"),
    pubmedcentral: str = Form("no preference"),
) -> HTMLResponse:
    service = _service(request)
    filters, summary = _build_filters(language, type, openaccess, pubmedcentral)
    journals = authors = papers = None
    with log_metrics(f"web_{action}") as m:
        if action == "authors":
            authors = service.suggest_authors(text, filters)
            m["results"] = len(authors)
        elif action == "papers":
            papers = service.find_similar_papers(text, filters, count=50)
            m["results"] = len(papers)
        else:
            action = "journals"
            journals = service.suggest_journals(text, filters)
            m["results"] = len(journals)
    return templates.TemplateResponse(
        request,
        "suggestions.html",
        {
            "text": text,
            "action": action,
            "journals": journals,
            "authors": authors,
            "papers": papers,
            "summary": summary,
            "pubmed_url": "https://pubmed.ncbi.nlm.nih.gov/",
        },
    )


@router.post("/email", response_class=HTMLResponse)
def email(
    request: Request,
    author: str = Form(...),
    coauthors: str = Form(""),
) -> HTMLResponse:
    service = _service(request)
    coauthor_list = [c for c in coauthors.split(";") if c.strip()]
    with log_metrics("web_email") as m:
        emails = service.find_author_emails(author, coauthor_list)
        m["results"] = len(emails)
    return templates.TemplateResponse(
        request,
        "email.html",
        {"author": author, "emails": emails},
    )


EFETCH_URL = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/efetch.fcgi"


@router.post("/export")
async def export(request: Request) -> StreamingResponse:
    """Fetch selected PMIDs in MEDLINE (.nbib) format from NCBI efetch (ported from export.php)."""
    form = await request.form()
    pmids = [str(v) for k, v in form.multi_items() if k.startswith("PMID")]
    if not pmids:
        raise HTTPException(status_code=400, detail="No citations selected")
    query = urllib.parse.urlencode(
        {"db": "pubmed", "id": ",".join(pmids), "retmode": "text", "rettype": "medline"}
    )

    def _fetch():
        with urllib.request.urlopen(f"{EFETCH_URL}?{query}", timeout=30) as resp:
            yield resp.read()

    return StreamingResponse(
        _fetch(),
        media_type="text/plain",
        headers={"Content-Disposition": 'attachment; filename="MEDLINECitationsFromJane.nbib"'},
    )


@router.get("/faq", response_class=HTMLResponse)
def faq(request: Request) -> HTMLResponse:
    return templates.TemplateResponse(request, "faq.html", {})


# ---- legacy *.php URL redirects (decision #12) --------------------------------
_LEGACY_REDIRECTS = {
    "/index.php": "/",
    "/suggestions.php": "/",
    "/faq.php": "/faq",
    "/similar.php": "/",
    "/email.php": "/",
    "/export.php": "/",
}


@router.get("/{legacy:path}.php")
def legacy_php(legacy: str) -> RedirectResponse:
    target = _LEGACY_REDIRECTS.get(f"/{legacy}.php", "/")
    return RedirectResponse(target, status_code=301)
