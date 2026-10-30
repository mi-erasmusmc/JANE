# JANE Implementation Notes (for future agents)

Companion to [MODERNIZATION_PLAN.md](MODERNIZATION_PLAN.md). The plan says *what* and *why*;
this file captures *gotchas, exact constants, and risks* gathered from the existing codebase so an
implementing agent doesn't rediscover them the hard way. **Verify against source before relying on a value.**

> The old code referenced below by bare filename (e.g. `MedlineAnalyzer.java`, `JaneSuggestions.php`) now
> lives under `legacy/` — Java under `legacy/JaneServer/`, PHP under `legacy/JaneClient/`.

---

## 0. Do this first (highest-risk unknowns to de-risk early)

1. **Tantivy custom-analyzer parity (biggest risk).** We must reproduce the old `MedlineAnalyzer`
   exactly. `tantivy-py`'s support for custom tokenizers/char-filters is limited compared to Tantivy's
   Rust API. **Recommended approach: do the text normalization in Python** (replicate the char filtering
   and stopword removal below), then feed the already-normalized, space-separated text to Tantivy using a
   simple whitespace/"raw" tokenizer. Apply the **same preprocessing at query time** before MLT term
   extraction so index and query tokens match. Prototype and confirm this before building anything else.
2. **Capture old-system outputs for the parity harness BEFORE decommissioning.** The acceptance test
   compares against *old JANE*. Record its journal/author rankings for the ~100–200 test abstracts while
   the old Lucene system is still running — you may not be able to reproduce it later.
3. **Confirm `tantivy-py` has no usable MoreLikeThis** and that a weighted boolean-OR query with per-term
   boosts is the path (it almost certainly is). Confirm IDF/doc-freq is readable from the index.
4. **Index size / memory budget on the lightweight server** — measure a real full build early so the
   "lightweight" assumption holds (mmap keeps RSS low, but temp space during S3 extract is ~2× index size).

---

## 1. Exact constants and behaviors to preserve

From `JaneSOAPServer.java`, `MedlineAnalyzer.java`, `JournalIndexer.java`:

- **Stopwords (only 4):** `of`, `the`, `and`, `in`.
- **Analyzer char filter:** replace every char **except** `[a-zA-Z0-9.]` with a space **before** tokenizing
  (`ReaderAdapter` in `MedlineAnalyzer`). **Dots are preserved** (decimals, abbreviations). Then lowercase.
  **No stemming.**
- **MLT:** `maxQueryTerms = 25`, **`minTermFreq = 1`** (JANE explicitly overrides this — critical for a
  single pasted abstract), Lucene default doc-freq thresholds otherwise.
- **MLT boost (`boost=false`):** legacy `initMLT` never calls `setBoost(true)`, so Lucene MoreLikeThis
  used `tf*idf` **only to select** the top 25 terms, **not** to weight the query. Document ranking came
  from DefaultSimilarity, which weights each selected term by **idf²** (query-weight idf × field-weight idf)
  and is independent of the input term frequency. We reproduce this in `mlt.extract_terms`: select terms on
  `tf*idf`, but return each term's **idf** (not `tf*idf`) as its boost, so BM25's own idf yields idf²-style
  discrimination and a term repeated many times in the pasted text is not over-weighted. (Using `tf*idf` as
  the boost — the original port — let frequently-repeated common words dominate and surfaced applied papers
  instead of methods papers.)
- **k = 50** similar papers feed the journal/author aggregation.
- **Two different normalizations — don't conflate them:**
  1. per-result score: divide by max score if > 1 to keep `[0,1]` (in the old search path), then
  2. aggregation: divide each journal/author sum by the total sum → probabilities.
- **`maxResultSize = 100`** cap on returned items.
- **Email:** `requireDisambiguationForEMail = true` — email lookup needs a co-author list to disambiguate.
- **Journal inclusion:** `minPMIDsPerJournal = 20` (in the last year); journal name length cap (~80 chars).
- **Windows:** full index = **last 10 years**; `recent` flag = **last 1 year**.
- **Excluded publication types:** ~30 types (comment, editorial, news, retraction, erratum, etc.).
  Port the **exact list from `JournalIndexer.java`** rather than retyping it.
- **`ai` / `airank`** were `Field.Index.NO` (stored, not indexed) — removing them is just "don't emit."

## 2. Index fields (target)

`text` (analyzed), `pmid`, `journal`, `authors[]` (newline-separated in old index), `ptyp`,
`language`, `emailauthor`, `year`, `recent`, `openaccess`, `medlineindexed`, `nihpa`, `pmcmonths`.
**Drop `ai`, `airank`.** Authors are joined per-paper then split for aggregation.

## 3. Legacy filter-string syntax (to convert to structured filters)

Pipe-separated `key=value`, e.g. `language=eng|type=...|openaccess|recent`. Keys seen:
`language=`, `type=`, `openaccess=`, `medlineindexed=`, `nihpa=`, `pmcmonths=`, `recent`,
and `subset=` (**subset is being dropped**).

## 4. Data sources for Stage 2 journal metadata (not in SQLite)

- **DOAJ (open access):** `https://doaj.org/csv`
- **PMC jlist (`pmcmonths` / deposit delay):** `https://cdn.ncbi.nlm.nih.gov/pmc/home/jlist.csv`
- **Medline-indexed ISSNs:** NLM's `J_Medline.txt` (`https://ftp.ncbi.nlm.nih.gov/pubmed/J_Medline.txt`)
  — a single bulk-file download listing every MEDLINE journal's ISSNs. (The legacy code also hit
  the NCBI E-utilities `nlmcatalog` API for this; the modern indexer uses only the file.)
- **NIHPA:** no upstream source; defaults False.
- Journal name/abbrev/ISSN come straight from the SQLite, so a separate journal-database file is
  not needed beyond the ISSN list above.
- Join is **by ISSN**, with a fallback to the linking ISSN.
- **Eigenfactor file is removed** — delete `loadEigenFactorData` and its CSV dependency entirely.

### NCBI usage policy
Every metadata source is now a single bulk-file download (DOAJ, PMC jlist, `J_Medline.txt`), so
there are **no paged API calls and no NCBI API key** required — just outbound HTTPS.

## 5. Existing SQLite (read-only dependency; Stage 1 is out of scope)

- Main table `pubmed_articles`; nested data in normalized `medcit_*` tables (authors in
  `medcit_art_authorlist_author`, affiliations in `..._affinfo`). Confirm the live schema before coding.
- `DBConnector.java` lists many DB types but **only SQLite is used in practice**.
- The incremental builder handles PubMed **revisions and `DeleteCitation`** records; the new indexer just
  reads whatever is currently in the DB and does a **full Tantivy rebuild** each month.

## 6. Old hardcoded paths (make all configurable via env/.env)

`E:/Medline/PubMed.sqlite`, `c:/temp/indexNew/`, `E:/Jane/EF_2015.txt`, `/var/jane/settings.txt`,
`/var/log/janeLog.txt`. None should survive into the new code.

## 7. Frontend specifics

- SOAP WSDL the PHP client hit: `http://localhost:8080/JaneServer/services/JaneSOAPServer?wsdl`.
- **Email obfuscation:** `Utilities.safePrintEMail()` emits JS to deter scrapers — preserve this behavior.
- `AccessControl.php` IP blacklist is currently **disabled** (`blackListed()` returns false).
- JS libs: jQuery + `jtip` tooltip — both dropped in the Jinja2 port.
- Static info pages to keep: `medlineindexed.html`, `openaccess.html`, `pubmedcentral.html`, `nihpa.html`.
  **Remove `articleinfluence.html`** and the Article-Influence bar in `JaneSuggestions.php` (~lines 250, 288–323).

## 8. MCP notes

- Mount FastMCP in the **same** FastAPI app so it shares `SearchService` + the Tantivy handle (no second
  index copy). Transport: **Streamable HTTP**.
- Tools: `suggest_journals`, `suggest_authors`, `find_similar_papers` (open, per-IP rate limited);
  `find_author_emails` (**API-key gated** + stricter limits; keep the disambiguation requirement).
- Give each tool a precise description + input schema so agents call them correctly.

## 9. Scoring fidelity caveat

Old Lucene 3.0.1 used TF-IDF `DefaultSimilarity`; Tantivy uses **BM25**. We accept this. Because the
journal/author step **normalizes summed scores into probabilities**, per-doc scoring differences largely
wash out at the aggregate level — but expect individual paper orderings to differ from old JANE. The
parity bar is at the **journal-ranking** level, not per-paper.

## 10. Reload / concurrency

- Multiple Uvicorn workers each hold their own Tantivy handle; **all must reopen** on reload.
- During a swap, two indexes are briefly mmap'd — cheap, but account for it in the disk/mem budget.
- The admin reload endpoint must be **auth-protected** (reuse the API-key mechanism).

## 11. Project context

Repo owner is in the OHDSI ecosystem (note the `SqlRender.jar` OHDSI SQL-templating dependency in the old
build). The existing incremental SQLite builder may live in a separate OHDSI/R or Java project — treat it
as an external, stable upstream producing `PubMed.sqlite`.
