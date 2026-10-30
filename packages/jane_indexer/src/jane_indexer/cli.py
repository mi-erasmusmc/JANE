"""Stage 2 orchestration entry point (`jane-index`). See MODERNIZATION_PLAN.md section 6.

Steps: read SQLite -> load journal metadata -> filter (inclusion) -> build Tantivy index
-> (optionally) package + upload to S3 -> (optionally) promote by writing current.txt.
Config via env / CLI flags (no hardcoded paths).
"""

from __future__ import annotations

import argparse
import datetime
import os
import sys

from .build_index import build_index
from .journal_metadata import load_journal_metadata
from .sqlite_source import compute_journal_sets, connect, window_ordinals


def _windowed_rows(conn, lower, upper, recent_journals, limit):
    from .sqlite_source import _row_to_citation

    sql = (
        "SELECT pmid, title, abstract, authors, affiliations, journal_name, "
        "journal_abbr, journal_medline_abbr, issn, issn_linking, year, "
        "publication_types, language FROM pubmed_articles "
        "WHERE publication_date BETWEEN ? AND ? "
        "AND abstract IS NOT NULL AND abstract != ''"
    )
    params = [lower, upper]
    if limit is not None:
        sql += " LIMIT ?"
        params.append(limit)
    for row in conn.execute(sql, params):
        yield _row_to_citation(row, recent_journals)


def build(args) -> int:
    reference = datetime.date.today()
    print(f"{datetime.datetime.now():%H:%M:%S}\tComputing journal sets from {args.sqlite}")
    conn = connect(args.sqlite)
    try:
        lower, recent, upper = window_ordinals(reference)
        valid_journals, recent_journals = compute_journal_sets(conn, lower, recent, upper)
        print(
            f"  valid journals (>=20 papers): {len(valid_journals)}; recent: {len(recent_journals)}"
        )

        print(
            f"{datetime.datetime.now():%H:%M:%S}\tLoading journal metadata (offline={args.offline})"
        )
        metadata = load_journal_metadata(
            api_key=args.ncbi_api_key, email=args.email, offline=args.offline
        )
        print(
            f"  open-access ISSNs: {len(metadata.open_access_issns)}; "
            f"medline-indexed ISSNs: {len(metadata.medline_indexed_issns)}; "
            f"pmc ISSNs: {len(metadata.issn2pmcmonths)}"
        )

        print(f"{datetime.datetime.now():%H:%M:%S}\tBuilding Tantivy index at {args.out}")
        citations = _windowed_rows(conn, lower, upper, recent_journals, args.limit)
        count = build_index(
            citations,
            metadata,
            args.out,
            valid_journals=valid_journals,
            heap_size=args.heap,
            threads=args.threads,
        )
    finally:
        conn.close()
    print(f"{datetime.datetime.now():%H:%M:%S}\tIndexed {count} documents")

    if args.bucket:
        from .package_upload import package_and_upload

        version = args.version or f"{reference:%Y-%m}"
        print(
            f"Packaging + uploading index-{version} to s3://{args.bucket} (promote={args.promote})"
        )
        key = package_and_upload(args.out, args.bucket, version, promote=args.promote)
        print(f"Uploaded {key}")
    return count


def main() -> None:
    parser = argparse.ArgumentParser(prog="jane-index", description="Build the JANE Tantivy index.")
    parser.add_argument("--sqlite", default=os.environ.get("JANE_SQLITE", "PubMed.sqlite"))
    parser.add_argument("--out", default=os.environ.get("JANE_INDEX_OUT", "index"))
    parser.add_argument("--bucket", default=os.environ.get("JANE_S3_BUCKET", ""))
    parser.add_argument("--version", default=os.environ.get("JANE_INDEX_VERSION", ""))
    parser.add_argument("--promote", action="store_true", help="Write current.txt pointer last.")
    parser.add_argument("--offline", action="store_true", help="Skip metadata downloads.")
    parser.add_argument("--ncbi-api-key", default=os.environ.get("JANE_NCBI_API_KEY", ""))
    parser.add_argument("--email", default=os.environ.get("JANE_NCBI_EMAIL", ""))
    parser.add_argument("--limit", type=int, default=None, help="Cap rows (for testing).")
    parser.add_argument("--heap", type=int, default=1_000_000_000)
    parser.add_argument("--threads", type=int, default=1)
    args = parser.parse_args()
    if not os.path.exists(args.sqlite):
        parser.error(f"SQLite not found: {args.sqlite}")
    build(args)


if __name__ == "__main__":
    sys.exit(main())
