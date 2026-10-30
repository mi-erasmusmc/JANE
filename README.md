Journal / Author Name Estimator (JANE)
======================================

This repository contains the source code for [JANE](http://jane.biosemantics.org/).
Paste the text of an abstract (or any biomedical text) and JANE finds the journals
most likely to publish it, the authors who write about it, and similar articles.

This is the **modernized** stack: Python 3.12 + [FastAPI](https://fastapi.tiangolo.com/) +
[Tantivy](https://github.com/quickwit-oss/tantivy-py) (an embeddable, Lucene-like search
engine). It replaces the legacy PHP + Java/SOAP + Lucene 3 implementation. Two functional
changes versus the legacy app: **Article Influence has been removed**, and an **MCP endpoint**
(for AI assistants) has been added. The legacy sources are kept under `legacy/` for reference.

See `MODERNIZATION_PLAN.md` for the design rationale and `IMPLEMENTATION_NOTES.md` for the
exact constants and algorithms.


Architecture
============

JANE runs on two kinds of machine, backed by one shared library:

- **`jane_core`** (shared) — the analyzer, Tantivy schema, MoreLikeThis term extractor,
  `SearchService`, and response models. Used by both the indexer and the server.
- **`jane_indexer`** (heavy box) — the `jane-index` command. Reads `PubMed.sqlite` plus
  freshly downloaded journal metadata, applies inclusion rules, and builds a **portable
  Tantivy index directory**. Optionally packages it (`.tar.zst`) and uploads it to S3.
- **`jane_server`** (lightweight box) — a FastAPI app that opens a local copy of the index
  and serves three faces over the same port: a **web UI**, a **REST API**, and an **MCP
  endpoint**. It has an admin reload hook so a new index can be swapped in with no downtime.

The index is a plain directory, so building (expensive, monthly) and serving (cheap, always
on) are fully decoupled: the heavy box builds and promotes; the lightweight box pulls and reloads.


Repository layout
==================

```
packages/
  jane_core/      # shared search library
  jane_indexer/   # `jane-index` CLI (build the index)
  jane_server/    # FastAPI web UI + REST + MCP
deploy/
  indexer/        # systemd unit + timer + env example for the heavy box
  server/         # systemd units, Caddyfile, pull-index.sh, env example
tests/parity/     # legacy-vs-modern parity harness (cutover gate)
legacy/           # original PHP/Java sources (reference only)
MODERNIZATION_PLAN.md
IMPLEMENTATION_NOTES.md
```

This is a [`uv`](https://docs.astral.sh/uv/) workspace: the three packages live under
`packages/*` and each declares its own dependencies, so a lightweight server can install only
what it needs (`uv sync --package jane-server`) without pulling in the heavy indexer stack.


Prerequisites
=============

- **Python 3.12+**
- **[uv](https://docs.astral.sh/uv/)** — install with `curl -LsSf https://astral.sh/uv/install.sh | sh`
- For building a full index: a **`PubMed.sqlite`** database (see Step 1) and outbound internet
  access to download journal metadata.
- For production deploy: an **S3 bucket** (or S3-compatible store) and the `aws` CLI on both boxes.


Quick start (local, end to end)
===============================

This builds a small index from a `PubMed.sqlite` in the repo root and runs the server locally.

```bash
# 1. Install the toolchain and all workspace dependencies.
uv sync

# 2. Build a small index (first 50k rows is plenty to try it out).
#    --offline skips the metadata downloads (journals are marked not-OA / not-indexed).
uv run jane-index --sqlite PubMed.sqlite --out ./index --limit 50000 --offline

# 3. Run the server against that index.
JANE_INDEX_PATH=./index \
  uv run uvicorn jane_server.app:create_app --factory --port 8000

# 4. In another terminal, try the REST API:
curl -s localhost:8000/api/suggest_journals \
  -H 'content-type: application/json' \
  -d '{"text":"We measured left ventricular ejection fraction after myocardial infarction."}' | head

# ...and open the web UI at http://localhost:8000/
```

For a real deployment, use the full dataset (drop `--limit`) and download real metadata (drop
`--offline`).


Detailed instructions
=====================

Step 1 — Obtain the PubMed data (`PubMed.sqlite`)
-------------------------------------------------

The indexer reads a single SQLite database with one table, `pubmed_articles`:

```sql
CREATE TABLE pubmed_articles (
    pmid INTEGER PRIMARY KEY,
    title TEXT, abstract TEXT,
    publication_date INTEGER,          -- Python date.toordinal() value
    mesh_terms TEXT, keywords TEXT, chemicals TEXT,
    authors TEXT, affiliations TEXT,   -- newline-separated, aligned by position
    journal_name TEXT, journal_abbr TEXT, journal_medline_abbr TEXT,
    issn TEXT, issn_linking TEXT,
    year INTEGER, volume TEXT, issue TEXT, pagination TEXT,
    publication_types TEXT,            -- separated list
    language TEXT, file_number INTEGER
);
```

To produce it:

1. Download the annual [MEDLINE / PubMed](https://www.nlm.nih.gov/databases/download/pubmed_medline.html)
   baseline + daily update XML from the NLM.
2. Load the XML into the `pubmed_articles` table above. The companion project
   [MedlineXmlToDatabase](https://github.com/OHDSI/MedlineXmlToDatabase) performs this parse/load.
3. Place the resulting file at the repo root as `PubMed.sqlite` for local testing, or on the
   indexing box at the path given by `JANE_SQLITE` (see Step 3).

> The file is large (tens of GB) and is git-ignored. Full aggregation over it is slow — use
> `--limit` when experimenting.


Step 2 — Install the toolchain
------------------------------

```bash
uv sync                          # everything (local dev)
uv sync --package jane-indexer   # heavy box only
uv sync --package jane-server    # lightweight box only
```

`uv` creates `.venv/` and installs the workspace packages in editable mode. All commands below
are run via `uv run` (which uses that venv) — no manual activation needed.


Step 3 — Build the index (`jane-index`)
---------------------------------------

The `jane-index` command runs the full build pipeline:

1. Compute journal sets from the SQLite file. A journal is **included** only if it has
   **>= 20 papers** in the last **10 years**; a journal is flagged **recent** if it published
   at least one paper in the last year. Only citations with a non-empty abstract, a journal
   name, and an allowed publication type are indexed.
2. Download journal metadata (unless `--offline`). Every source is a single bulk-file
   download — no API keys, no paged API calls:
   - **Open access** ISSNs from [DOAJ](https://doaj.org/csv)
   - **PMC embargo months** from the [PMC journal list](https://cdn.ncbi.nlm.nih.gov/pmc/home/jlist.csv)
   - **MEDLINE-indexed** ISSNs from NLM's [`J_Medline.txt`](https://ftp.ncbi.nlm.nih.gov/pubmed/J_Medline.txt) journal list
3. Build the Tantivy index directory.
4. Optionally package (`.tar.zst`) and upload to S3, and optionally promote it.

```bash
# Full build with live metadata downloads:
uv run jane-index --sqlite PubMed.sqlite --out index-$(date +%Y-%m)

# Build, package, upload to S3, and promote (write current.txt) in one shot:
uv run jane-index --sqlite /var/jane/PubMed.sqlite --out /var/jane/index-$(date +%Y-%m) \
  --bucket jane-index --promote
```

Flags (all have `JANE_*` env-var equivalents):

| Flag | Env var | Default | Purpose |
|------|---------|---------|---------|
| `--sqlite` | `JANE_SQLITE` | `PubMed.sqlite` | Input database path |
| `--out` | `JANE_INDEX_OUT` | `index` | Output index directory |
| `--bucket` | `JANE_S3_BUCKET` | *(none)* | If set, package + upload to this S3 bucket |
| `--version` | `JANE_INDEX_VERSION` | `YYYY-MM` | Version tag used in the S3 key |
| `--promote` | — | off | Write `current.txt` pointer so servers pull this build |
| `--offline` | — | off | Skip metadata downloads (everything not-OA / not-indexed) |
| `--limit` | — | *(none)* | Cap rows read — for testing only |
| `--heap` | — | `1_000_000_000` | Tantivy writer heap (bytes) |
| `--threads` | — | `1` | Tantivy writer threads |

The metadata downloads need outbound HTTPS to `doaj.org`, `cdn.ncbi.nlm.nih.gov`, and
`ftp.ncbi.nlm.nih.gov`. No NCBI account or API key is required.


Step 4 — Run the server
-----------------------

The server opens the index at `JANE_INDEX_PATH` and serves the web UI, REST API, and MCP
endpoint on one port.

```bash
# Development (one worker, index at ./index):
JANE_INDEX_PATH=./index \
  uv run uvicorn jane_server.app:create_app --factory --port 8000

# Production (several workers, index behind the current symlink):
JANE_INDEX_PATH=/var/jane/current \
  uv run uvicorn jane_server.app:create_app --factory --host 127.0.0.1 --port 8000 --workers 4
```

Faces exposed:

- **Web UI** — `GET /` (plus `POST /suggestions`, `/email`, `/export`, `GET /faq`)
- **REST API** — under `/api` (see Step 5)
- **MCP** — Streamable HTTP at `/mcp` (see Step 6)
- **Admin** — `POST /admin/reload` to re-open the index after the symlink flips
- **Static assets** — `/static`

In production, static files are served by Caddy (see Step 9); `/static` here is the fallback.


Step 5 — Use the REST API
-------------------------

All endpoints accept JSON. A `filter` object is optional and may constrain `language`,
`types` (publication types), `openaccess`, `medlineindexed`, `nihpa`, `pmcmonths`, and `recent`.

```bash
# Suggest journals:
curl -s localhost:8000/api/suggest_journals -H 'content-type: application/json' -d '{
  "text": "We measured left ventricular ejection fraction after myocardial infarction.",
  "filter": {"openaccess": true}
}'

# Suggest authors:
curl -s localhost:8000/api/suggest_authors -H 'content-type: application/json' \
  -d '{"text": "CRISPR-Cas9 genome editing in zebrafish embryos"}'

# Find similar papers (MoreLikeThis), paginated:
curl -s localhost:8000/api/similar_papers -H 'content-type: application/json' \
  -d '{"text": "deep learning for retinal image segmentation", "count": 20, "offset": 0}'
```

`suggest_journals` returns a ranked list of `{name, score, openaccess, medlineindexed,
pmcmonths, issn, journalabbr, papers[]}`; `suggest_authors` returns `{name, score, papers[]}`;
`similar_papers` returns `{pmid, title, authors[], journal, year, score}`.

**Author emails** are gated behind an API key (set `JANE_EMAIL_API_KEY`), require
co-authors for disambiguation, and are sent via the `X-API-Key` header:

```bash
curl -s localhost:8000/api/author_emails -H 'content-type: application/json' \
  -H 'X-API-Key: your-email-key' \
  -d '{"author": "Smith J", "coauthors": ["Jones A", "Lee K"]}'
```


Step 6 — Use the MCP endpoint
-----------------------------

The MCP server (Streamable HTTP) is mounted at `/mcp` and exposes four tools:
`suggest_journals`, `suggest_authors`, `find_similar_papers`, and `find_author_emails`
(the email tool is API-key gated with stricter rate limits, via an `X-API-Key` header).

Point any MCP client at `http://<host>:8000/mcp`. Example with the Python MCP SDK:

```python
import asyncio
from mcp.client.session import ClientSession
from mcp.client.streamable_http import streamable_http_client

async def main():
    async with streamable_http_client("http://localhost:8000/mcp") as streams:
        async with ClientSession(streams[0], streams[1]) as session:
            await session.initialize()
            tools = await session.list_tools()
            print([t.name for t in tools.tools])
            result = await session.call_tool(
                "suggest_journals",
                {"text": "left ventricular ejection fraction after myocardial infarction"},
            )
            print(result.structured_content)

asyncio.run(main())
```


Step 7 — Run the tests and linters
----------------------------------

```bash
uv run pytest                      # full suite across all packages + the parity harness
uv run ruff check packages tests   # lint
uv run ruff format packages tests  # auto-format
```


Step 8 — Parity harness (cutover gate)
--------------------------------------

Before switching production from the legacy app to this one, the harness in `tests/parity/`
compares modern results against recorded legacy baselines (rank overlap and true-journal
recall). Baselines live in `tests/parity/baselines/*.json`.

```bash
uv run python tests/parity/harness.py --baselines tests/parity/baselines --index ./index
```

It exits non-zero if the rank-overlap median or recall falls below the configured gate.


Step 9 — Production deployment
------------------------------

Ready-to-adapt systemd units, a Caddyfile, and the pull script live under `deploy/`.

**Heavy box (indexing), monthly:**

1. `uv sync --package jane-indexer` into `/opt/jane`.
2. Copy `deploy/indexer/jane-indexer.env.example` to `/etc/jane/jane-indexer.env` and fill in
   `JANE_SQLITE_PATH` and `JANE_S3_BUCKET`.
3. Install `deploy/indexer/jane-indexer.{service,timer}`. The timer runs `jane-index` on the
   1st of each month; it builds, uploads to S3, and (when `--promote` is set) writes `current.txt`.

**Lightweight box (serving), always on:**

1. `uv sync --package jane-server` into `/opt/jane`.
2. Copy `deploy/server/jane.env.example` to `/etc/jane/jane.env` and fill in `JANE_INDEX_PATH`,
   `JANE_S3_BUCKET`, `JANE_ADMIN_API_KEY`, `JANE_EMAIL_API_KEY`.
3. Install `deploy/server/jane-server.service` (runs Uvicorn with several workers).
4. Install `deploy/server/jane-index-pull.{service,timer}`. The pull job
   (`deploy/server/pull-index.sh`) runs hourly: it reads `current.txt` from S3, and if a new
   version has been promoted, downloads it, extracts it, atomically flips the
   `/var/jane/current` symlink, calls `POST /admin/reload` to reload all workers, and prunes
   old generations. It is idempotent — a no-op when already on the promoted version.
5. Put `deploy/server/Caddyfile` in front for automatic HTTPS, static-file serving, and reverse
   proxy to Uvicorn. Set your domain in it.


Configuration reference
=======================

Server (`jane_server`, `JANE_` prefix, read from env or `/etc/jane/jane.env`):

| Variable | Default | Purpose |
|----------|---------|---------|
| `JANE_INDEX_PATH` | `/var/jane/current` | Path (usually a symlink) to the active index directory |
| `JANE_S3_BUCKET` | *(empty)* | Bucket the pull job reads from |
| `JANE_ADMIN_API_KEY` | *(empty)* | Required (`X-API-Key`) for `POST /admin/reload` |
| `JANE_EMAIL_API_KEY` | *(empty)* | Required (`X-API-Key`) for the author-email REST/MCP tools |

Indexer environment variables are listed in the Step 3 table.


License
=======
JANE is licensed under Apache License 2.0.
