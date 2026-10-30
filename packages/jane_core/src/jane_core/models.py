"""Data models shared across REST, web UI, and MCP. See MODERNIZATION_PLAN.md section 2.

Journal/Author/Paper/EMail. NOTE: Journal has NO ai/airank fields (Article Influence removed).
These pydantic models are used directly by FastAPI responses and MCP tool schemas.
"""

from pydantic import BaseModel, Field


class Paper(BaseModel):
    pmid: str
    title: str = ""
    authors: list[str] = Field(default_factory=list)
    journal: str = ""
    year: str = ""
    score: float = 0.0


class Journal(BaseModel):
    name: str
    score: float = 0.0
    openaccess: bool | None = None
    medlineindexed: bool | None = None
    pmcmonths: int | None = None
    issn: str = ""
    journalabbr: str = ""
    papers: list[Paper] = Field(default_factory=list)


class Author(BaseModel):
    name: str
    score: float = 0.0
    papers: list[Paper] = Field(default_factory=list)


class EMail(BaseModel):
    email: str
    year: str = ""
    pmid: str = ""
