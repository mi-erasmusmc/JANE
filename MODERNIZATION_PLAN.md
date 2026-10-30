# JANE Modernization Plan

Porting JANE (Journal/Author Name Estimator) from its PHP + Java/SOAP + Lucene 3.0.1
stack to a modern, low-maintenance architecture. Two functional changes from today:
**remove Article Influence** and **add an MCP endpoint for AI assistants**.

---

## 1. What JANE does (preserved)

A user pastes a paper title/abstract; JANE finds similar recent PubMed papers and
aggregates them into **ranked journal suggestions** and **ranked author suggestions**.
This signature behavior is preserved.

### Current architecture (as researched)
> The old code now lives under `legacy/` (`legacy/JaneServer/` Java, `legacy/JaneClient/` PHP), kept for reference until cutover.

- **Frontend:** PHP server-rendered pages (`legacy/JaneClient/`) + jQuery/jtip, talking to the backend over SOAP.
- **Backend:** Java `JaneSOAPServer` (6 SOAP methods), Lucene 3.0.1 `MoreLikeThis` + filters,
  `IndexSearcherPool` (1 h refresh), `MedlineAnalyzer` (StandardAnalyzer + 4 stopwords).
- **Ranking:** top-k = 50 similar papers → group by journal/author → sum scores → normalize to probabilities.
- **Indexing:** `JaneMasterIndexingScript` + `JournalIndexer` build a Lucene index from a staging
  SQLite DB plus DOAJ / PMC / E-utilities / J_Medline metadata (and Eigenfactor — being removed).
- **Ops today:** monthly rebuild on a separate heavy box; the Lucene index is SFTP'd to a lightweight server.

---

## 2. Decisions (locked)

| # | Area | Decision |
|---|------|----------|
| 1 | Language / stack | **Python 3.12 + FastAPI** (single unified stack). `uv` for deps/venv. |
| 2 | Search engine | **Tantivy** (embeddable, Lucene-like, via `tantivy-py`). Produces a **portable index directory**. OpenSearch rejected (needs an always-on daemon on the lightweight server; no portable file). |
| 3 | Search approach | **Keyword / BM25** (Tantivy default). No embeddings, zero external cost. |
| 4 | MoreLikeThis | Engine-agnostic Python **term extractor**: Lucene MLT defaults, **`minTermFreq = 1`**, IDF sourced **directly from Tantivy's index statistics** (no separate stats artifact). |
| 5 | Analyzer | **Preserve exactly:** lowercase, strip non-alphanumeric except `.`, 4 stopwords (of/the/and/in), **no stemming**. |
| 6 | Scoring | Accept **Tantivy BM25** per-document; keep the **k = 50 sum-per-journal/author + normalize-to-probabilities** aggregation verbatim. |
| 7 | Scope — dropped | **Keyword/structured "find papers" mode** (`getPapers` / `QueryParser`); **subset multi-index feature**. |
| 8 | Scope — kept | Abstract → journals + authors; a **similar-papers list derived from the same MLT query**; the **email-finder**. |
| 9 | Email finder | Keep in **both web UI and MCP**. Keep disambiguation requirement + JS obfuscation. MCP email tool is **API-key gated** with stricter rate limits. |
| 10 | Article Influence | **Removed entirely** — Eigenfactor download/parse, `ai`/`airank` fields, the UI bar, `articleinfluence.html`, and FAQ references. |
| 11 | MCP | **FastMCP over Streamable HTTP**, mounted in the same FastAPI app on the light server, sharing the `SearchService` and Tantivy index. Core tools **open with per-IP rate limiting**; email tool API-key gated. |
| 12 | Frontend | **Jinja2 server-rendered** + minimal vanilla JS (drop jQuery/jtip). **Preserve / 301-redirect** legacy `*.php` URLs. |
| 13 | Index reload | **Admin reload endpoint** (API-key protected) called after promotion; a few **Uvicorn workers**, each reopens the index (Tantivy mmap makes the brief double-map cheap). |
| 14 | Distribution | **Amazon S3** (see §5). Versioned tarball + `current.txt` pointer for **atomic promotion**; light server pulls to **local disk**, then symlink-flips + reloads. |
| 15 | Pipeline split | **Stage 1 (incremental SQLite acquisition) is EXISTING and OUT OF SCOPE.** The new system reads the existing `PubMed.sqlite`. **Stage 2 is new** (Python). |
| 16 | Journal metadata | **Not in SQLite** — Stage 2 still fetches DOAJ / PMC / E-utilities / J_Medline and joins by ISSN (minus Eigenfactor). |
| 17 | Reindex cadence | **Monthly full Tantivy rebuild** from the current SQLite. |
| 18 | Deployment | **systemd + venv** for the app; **Caddy** reverse proxy + automatic TLS. *Tentative — to be confirmed with the server maintainer; may containerize.* |
| 19 | Logging | **Metrics only** (timestamp, endpoint, result counts, latency, status) across web / REST / MCP. **No raw query text.** journald + rotation. |
| 20 | Cutover bar | Parity harness gated on **(a)** top-10 journal rank-overlap vs. old JANE **and (b)** true-journal recall on recent papers. See §7. |

**Filters kept:** `language`, `type` (publication type), `openaccess`, `medlineindexed`, `nihpa`,
`pmcmonths`, `recent`. (`subset` dropped.)

**No backward compatibility** with the old SOAP API (it is unused).

---

## 3. Target architecture

```
                         HEAVY INDEXING BOX                         LIGHTWEIGHT SERVER
  ┌───────────────────────────────────────────┐        ┌──────────────────────────────────┐
  │ Stage 1 (EXISTING, out of scope):          │        │ Caddy (TLS, static, reverse proxy)│
  │   incremental PubMed → PubMed.sqlite        │        │            │                      │
  │                                             │        │     Uvicorn workers (x N)         │
  │ Stage 2 (NEW, Python):                      │        │      ├─ FastAPI (Jinja2 UI + REST)│
  │   read SQLite + fetch DOAJ/PMC/Eutils/      │        │      └─ FastMCP (Streamable HTTP) │
  │   J_Medline (join by ISSN, no Eigenfactor)  │        │            │                      │
  │   → build Tantivy index + apply inclusion   │        │       SearchService (shared)      │
  │   rules → package tarball                   │        │            │                      │
  │            │                                │        │   Tantivy index (LOCAL disk,      │
  │            ▼                                │        │   mmap) via `current` symlink     │
  │   upload index-YYYY-MM.tar.zst to S3        │──┐     └──────────────▲───────────────────┘
  │   write current.txt pointer LAST            │  │                    │ pull + extract + flip + reload
  └───────────────────────────────────────────┘  │   ┌────────────────┴─────────┐
                                                   └──▶│  S3 (versioned, lifecycle)│
                                                       │  index artifacts +        │
                                                       │  current.txt pointer      │
                                                       └───────────────────────────┘
```

- **One Python process family** on the light server serves the web UI, REST, and MCP from the same
  `SearchService` over a single local Tantivy index. No search daemon.
- **Tantivy serves from local disk** (mmap). S3 is the distribution channel only.

---

## 4. Core search design

1. **Analyzer** (preserved exactly): lowercase → strip non-alphanumeric except `.` → remove stopwords
   {of, the, and, in} → no stemming.
2. **MoreLikeThis term extractor** (engine-agnostic Python): tokenize the pasted abstract, compute term
   scores from input TF × IDF (IDF read from Tantivy's term statistics), select the top ~25 terms
   (`maxQueryTerms = 25`, `minTermFreq = 1`, Lucene default doc-freq thresholds), build a weighted
   boolean OR query with per-term boosts.
3. **Query + filters:** run the MLT query against Tantivy, applying filters (`language`, `type`,
   `openaccess`, `medlineindexed`, `nihpa`, `pmcmonths`, `recent`) as boolean sub-queries.
4. **Aggregation (verbatim):** take top **k = 50** scored papers → sum BM25 scores per journal and per
   author → normalize each to probabilities (mirror `computeJournalScores` / `computeAuthorScores` /
   `normalizeScores`).
5. **Similar-papers list:** reuse the same top-k results (replaces `getSimilarDocumentsInJournal/OfAuthor`).
6. **Email finder:** port affiliation email extraction with the existing disambiguation requirement.

### Index fields
`text` (analyzed), `pmid`, `journal`, `authors[]`, `ptyp`, `language`, `emailauthor`, `year`,
`recent` (bool), `openaccess` (bool), `medlineindexed` (bool), `nihpa` (bool), `pmcmonths` (int).
**No `ai` / `airank`.**

---

## 5. Index distribution & update (S3)

1. Stage 2 builds the Tantivy index and packages it as a **versioned artifact**:
   `s3://jane-index/index-YYYY-MM.tar.zst` (one object per monthly build).
2. After the upload completes, the heavy box writes the pointer object **last**:
   `s3://jane-index/current.txt` → `index-YYYY-MM`. This is the **atomic promotion** and the
   **manual promote gate** (update the pointer only after verifying the build).
3. Light server (systemd timer): read `current.txt` → download + extract the named version into a
   local staging dir → atomic **symlink flip** of `current` → call the **admin reload endpoint**.
4. Keep the **last 1–2 generations** locally for instant rollback (flip the symlink back).
5. **S3 versioning + lifecycle** rule for history and automatic retention.
6. **Scoped IAM:** heavy box write-only, light box read-only. No SSH trust between the two machines.
7. A fresh light server can **self-provision on boot** (read pointer, pull index, serve).

---

## 6. Phased delivery

### Phase 1 — Core search service
- Tantivy schema + preserved analyzer.
- Engine-agnostic MLT term extractor (IDF from Tantivy).
- `SearchService`: MLT query + filters → top-k = 50 → journal/author aggregation + normalization.
- Port the kept operations: `suggest_journals`, `suggest_authors`, similar-papers list, `find_emails`.
  Convert the legacy filter-string into structured filters.

### Phase 2 — Indexer (Stage 2, heavy box, Python)
- Read the existing `PubMed.sqlite` (schema: `pubmed_articles` + normalized `medcit_*` tables).
- Fetch + join journal metadata by ISSN: DOAJ (open access), PMC jlist (`pmcmonths`),
  E-utilities medline-indexed list, J_Medline (abbrev/ISSN), NIHPA. **Drop Eigenfactor entirely.**
- Enforce inclusion rules (last 10 years, has abstract, ~30 excluded publication types,
  min 20 papers/journal in last year, name-length cap, `recent` = last 1 year).
- Build the Tantivy index, package `index-YYYY-MM.tar.zst`, upload to S3, write `current.txt` last.
- Env/`.env` config (no hardcoded paths).

### Phase 3 — REST API
- FastAPI JSON endpoints for the kept operations (replace SOAP).
- Pydantic models for Journal / Author / Paper / EMail (**no `ai` / `airank`**).
- Metrics-only structured logging (replaces `LogFile`).

### Phase 4 — MCP endpoint (new)
- FastMCP over Streamable HTTP, mounted in the same FastAPI app, sharing `SearchService`.
- Tools: `suggest_journals`, `suggest_authors`, `find_similar_papers`, `find_author_emails`
  (email tool API-key gated + stricter rate limits). Clear tool descriptions + input schemas.
- Per-IP rate limiting on the whole endpoint.

### Phase 5 — Frontend + remove Article Influence
- Jinja2 templates porting index / suggestions / similar / email / export / faq; repoint to REST.
- Preserve / 301-redirect legacy `*.php` URLs.
- **Remove Article Influence** everywhere: UI bar, `articleinfluence.html`, model fields, FAQ text.
- Keep static info pages (medlineindexed / openaccess / pmc / nihpa) and email obfuscation.

### Phase 6 — Deployment & cutover
- Light server: systemd unit for the app (Uvicorn, N workers) + systemd timer for the S3 pull/promote/reload job; Caddy for TLS + static + reverse proxy. *(Confirm with server maintainer; containerize if preferred.)*
- Heavy box: monthly scheduled Stage 2 (build → upload → write pointer), with a manual promote gate for the first cycles.
- Cutover: run new stack in parallel, pass the acceptance harness (§7), then switch DNS.

---

## 7. Verification & cutover acceptance

1. **Parity harness** — ~100–200 real abstracts (historical queries and/or recent PubMed), run through
   **old JANE** and **new JANE**. Gate cutover on:
   - **(a)** top-10 journal rank-overlap vs. old JANE (e.g. RBO / median overlap ≥ ~0.7), and
   - **(b)** true-journal recall on recent papers (known publication venue) **≥** the old system.
2. **Filters** — each filter (language / type / openaccess / medlineindexed / nihpa / pmcmonths / recent)
   measurably changes results as expected.
3. **MCP** — connect an MCP client (Inspector / Claude); validate each tool's schema + results; confirm
   the email tool requires its API key and rate limits apply.
4. **Indexing** — rebuild a sample month; assert doc counts, inclusion rules, and **no `ai`/`airank`** fields.
5. **UI smoke test** — search → suggestions → similar → email → export; confirm **no Article Influence** anywhere.
6. **Swap/reload** — S3 promote + symlink flip + reload is atomic, zero-downtime, and rollback works.
7. **Footprint** — app memory/CPU on the lightweight server is acceptable at expected QPS.

---

## 8. Key files to port or remove

| Legacy source (reference) | Action → new home |
|---------------------------|-------------------|
| `legacy/JaneServer/.../JaneSOAPServer.java` | Port kept endpoints, filter parsing, score normalization → `jane_core/search_service.py` + `jane_server/routes/api.py`. Drop `getPapers`. |
| `legacy/JaneServer/.../MedlineAnalyzer.java` | Reproduce analyzer rules → `jane_core/text.py`; feed the MLT term extractor in `jane_core/mlt.py`. |
| `legacy/JaneServer/.../IndexSearcherPool.java` | Replace with Tantivy open/reopen → `jane_server/index_manager.py`. |
| `legacy/JaneServer/.../dataClasses/*.java` | Port data models → `jane_core/models.py`; **drop `ai`/`airank`** from `Journal`. |
| `legacy/JaneServer/.../indexConstruction/JournalIndexer.java` | Port fields + inclusion rules → `jane_indexer/{build_index,inclusion}.py`; **drop `loadEigenFactorData`**. |
| `legacy/JaneServer/.../indexConstruction/JaneMasterIndexingScript.java` | Port to Stage 2 → `jane_indexer/cli.py` (+ `package_upload.py`); keep SQLite source; drop Eigenfactor; add S3 packaging/upload. |
| `legacy/JaneServer/.../medline/*` | Read existing `PubMed.sqlite` schema → `jane_indexer/sqlite_source.py`. |
| `legacy/JaneClient/JaneSuggestions.php` (AI bar), `legacy/JaneClient/articleinfluence.html` | **Remove Article Influence** (nothing to port). |
| `legacy/JaneClient/*.php`, `legacy/JaneClient/faq.php` | Port to Jinja2 → `jane_server/routes/web.py` + `jane_server/templates/`; update FAQ (remove AI; keep monthly-update note). |

---

## 9. Out of scope / assumptions

- **Stage 1 incremental SQLite building** is already implemented and maintained separately.
- **Deployment specifics** (systemd vs. container, S3 bucket/IAM details) pending confirmation with the server maintainer.
- No embeddings / LLM inference in the core search (keyword/BM25 only).

---

## 10. Repository layout (scaffolding in place)

The repo is a **uv workspace** with three packages under `packages/`. Shared `jane_core` is a
path dependency of both the indexer and the server, so index-time and query-time tokenization
share one implementation (guards the #1 risk in IMPLEMENTATION_NOTES.md §0).

```
packages/
  jane_core/     # shared: text (analyzer), schema, mlt, search_service, filters, models
  jane_indexer/  # heavy box (Stage 2): sqlite_source, journal_metadata, inclusion,
                 #   build_index, package_upload, cli   (console script: `jane-index`)
  jane_server/   # light box: app, config, index_manager, routes/{web,api,admin},
                 #   mcp/server, templates/, static/
deploy/
  server/        # jane-server.service, Caddyfile, pull-index.sh, jane-index-pull.{service,timer}, jane.env.example
  indexer/       # jane-indexer.{service,timer}, jane-indexer.env.example
tests/parity/    # harness.py + baselines/ (capture old-JANE output BEFORE decommissioning)
legacy/          # old JaneServer (Java) + JaneClient (PHP), reference until cutover
```

All stubs currently `raise NotImplementedError` with TODOs pointing back to this plan and
IMPLEMENTATION_NOTES.md. Constants already captured in code: `STOPWORDS`, `MAX_QUERY_TERMS = 25`,
`MIN_TERM_FREQ = 1`, `TOP_K = 50`, `MAX_RESULT_SIZE = 100`, `MIN_PMIDS_PER_JOURNAL = 20`.

**Phase → where to implement**

| Phase | Files |
|-------|-------|
| 1 Core search | `jane_core/{text,schema,mlt,search_service,filters,models}.py` |
| 2 Indexer | `jane_indexer/{sqlite_source,journal_metadata,inclusion,build_index,package_upload,cli}.py` |
| 3 REST API | `jane_server/routes/api.py`, `jane_server/app.py`, `jane_core/models.py` |
| 4 MCP | `jane_server/mcp/server.py` |
| 5 Frontend | `jane_server/routes/web.py`, `jane_server/templates/`, `jane_server/static/` |
| 6 Deploy | `deploy/server/`, `deploy/indexer/` |
| 7 Parity | `tests/parity/harness.py` |

**Developer setup**

- `uv sync` — create/populate the venv (Python 3.12; Homebrew interpreter on the dev MacBook).
- `uv run pytest` — run tests (scaffold tests are skipped until implemented).
- Per-target installs: `uv sync --package jane-server` / `uv sync --package jane-indexer`.
- `PubMed.sqlite` sits at the repo root (gitignored) for indexer development.
- **First implementation target:** `jane_core.text.normalize` (analyzer parity), guarded by
  `packages/jane_core/tests/test_text.py`.
