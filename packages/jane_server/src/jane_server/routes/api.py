"""REST JSON endpoints (replace the old SOAP API). See MODERNIZATION_PLAN.md section 3.

Operations: suggest_journals, suggest_authors, find_similar_papers, find_author_emails.
Metrics-only structured logging (no raw query text) replaces the old LogFile.
"""

from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, Request
from jane_core.filters import Filters
from jane_core.models import Author, EMail, Journal, Paper
from pydantic import BaseModel, Field

from ..logging_metrics import log_metrics

router = APIRouter(prefix="/api", tags=["jane"])


class FilterModel(BaseModel):
    language: list[str] = Field(default_factory=list)
    types: list[str] = Field(default_factory=list)
    openaccess: bool | None = None
    medlineindexed: bool | None = None
    nihpa: bool | None = None
    pmcmonths: str | None = None
    recent: bool = False

    def to_filters(self) -> Filters:
        return Filters(
            language=list(self.language),
            types=list(self.types),
            openaccess=self.openaccess,
            medlineindexed=self.medlineindexed,
            nihpa=self.nihpa,
            pmcmonths=self.pmcmonths,
            recent=self.recent,
        )


class SuggestRequest(BaseModel):
    text: str
    filter: FilterModel = Field(default_factory=FilterModel)
    structured: bool = False


class SimilarRequest(BaseModel):
    text: str
    filter: FilterModel = Field(default_factory=FilterModel)
    count: int = 20
    offset: int = 0
    structured: bool = False


class EmailRequest(BaseModel):
    author: str
    coauthors: list[str] = Field(default_factory=list)


def get_service(request: Request):
    manager = request.app.state.index_manager
    if not manager.is_open:
        raise HTTPException(status_code=503, detail="Index not available")
    return manager.service


@router.post("/suggest_journals", response_model=list[Journal])
def suggest_journals(req: SuggestRequest, service=Depends(get_service)) -> list[Journal]:
    with log_metrics("suggest_journals") as m:
        results = service.suggest_journals(
            req.text, req.filter.to_filters(), structured=req.structured
        )
        m["results"] = len(results)
        return results


@router.post("/suggest_authors", response_model=list[Author])
def suggest_authors(req: SuggestRequest, service=Depends(get_service)) -> list[Author]:
    with log_metrics("suggest_authors") as m:
        results = service.suggest_authors(
            req.text, req.filter.to_filters(), structured=req.structured
        )
        m["results"] = len(results)
        return results


@router.post("/similar_papers", response_model=list[Paper])
def similar_papers(req: SimilarRequest, service=Depends(get_service)) -> list[Paper]:
    with log_metrics("similar_papers") as m:
        results = service.find_similar_papers(
            req.text,
            req.filter.to_filters(),
            count=req.count,
            offset=req.offset,
            structured=req.structured,
        )
        m["results"] = len(results)
        return results


@router.post(
    "/author_emails",
    response_model=list[EMail],
)
def author_emails(req: EmailRequest, service=Depends(get_service)) -> list[EMail]:
    with log_metrics("author_emails") as m:
        results = service.find_author_emails(req.author, req.coauthors)
        m["results"] = len(results)
        return results
