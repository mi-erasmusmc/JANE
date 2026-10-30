"""Parity harness: compare NEW JANE vs captured OLD-JANE baselines.

Cutover gate (MODERNIZATION_PLAN.md section 7): (a) top-10 journal rank-overlap vs old JANE and
(b) true-journal recall on recent papers. IMPORTANT: capture old-JANE outputs into
baselines/ BEFORE decommissioning the old system (IMPLEMENTATION_NOTES.md #0.2).

Baseline format (tests/parity/baselines/*.json), one file per corpus:
    [
      {"id": "pmid-or-label",
       "text": "title and abstract ...",
       "true_journal": "The Lancet",          # optional; for recall (b)
       "old_journals": ["Journal A", ...]},    # optional; top-N old-JANE journals for overlap (a)
      ...
    ]

Usage:
    python -m tests.parity.harness --index /var/jane/current --baseline tests/parity/baselines/recent.json
"""

from __future__ import annotations

import argparse
import glob
import json
import os
import statistics

import tantivy
from jane_core.filters import Filters
from jane_core.search_service import SearchService

TOP_N = 10
RANK_OVERLAP_GATE = 0.7  # median top-10 journal overlap vs old JANE


def _journal_names(service: SearchService, text: str, top_n: int) -> list[str]:
    return [j.name for j in service.suggest_journals(text, Filters())[:top_n]]


def rank_overlap(old: list[str], new: list[str], top_n: int = TOP_N) -> float:
    """Fraction of old top-N journals present in new top-N (set overlap / top_n)."""
    if not old:
        return float("nan")
    old_set = set(old[:top_n])
    new_set = set(new[:top_n])
    return len(old_set & new_set) / min(top_n, len(old_set))


def evaluate(service: SearchService, cases: list[dict], top_n: int = TOP_N) -> dict:
    overlaps: list[float] = []
    recall_hits = 0
    recall_total = 0
    for case in cases:
        new = _journal_names(service, case["text"], top_n)
        if case.get("old_journals"):
            overlaps.append(rank_overlap(case["old_journals"], new, top_n))
        if case.get("true_journal"):
            recall_total += 1
            if case["true_journal"] in new:
                recall_hits += 1
    result = {
        "n_cases": len(cases),
        "n_overlap_cases": len(overlaps),
        "median_top_overlap": statistics.median(overlaps) if overlaps else None,
        "recall_total": recall_total,
        "recall_hits": recall_hits,
        "recall": (recall_hits / recall_total) if recall_total else None,
    }
    return result


def load_cases(baseline: str) -> list[dict]:
    paths = (
        [baseline]
        if os.path.isfile(baseline)
        else sorted(glob.glob(os.path.join(baseline, "*.json")))
    )
    cases: list[dict] = []
    for path in paths:
        with open(path) as fh:
            cases.extend(json.load(fh))
    return cases


def main() -> None:
    parser = argparse.ArgumentParser(description="JANE parity harness.")
    parser.add_argument("--index", required=True, help="Path to the Tantivy index dir.")
    parser.add_argument(
        "--baseline",
        default=os.path.join(os.path.dirname(__file__), "baselines"),
        help="A baseline JSON file or a directory of them.",
    )
    parser.add_argument("--top-n", type=int, default=TOP_N)
    parser.add_argument("--min-doc-freq", type=int, default=5)
    args = parser.parse_args()

    cases = load_cases(args.baseline)
    if not cases:
        raise SystemExit(f"No baseline cases found at {args.baseline}")
    index = tantivy.Index.open(args.index)
    service = SearchService(index, min_doc_freq=args.min_doc_freq)
    result = evaluate(service, cases, args.top_n)
    print(json.dumps(result, indent=2))

    overlap = result["median_top_overlap"]
    if overlap is not None and overlap < RANK_OVERLAP_GATE:
        raise SystemExit(
            f"FAIL: median top-{args.top_n} journal overlap {overlap:.2f} < {RANK_OVERLAP_GATE}"
        )
    print("PASS")


if __name__ == "__main__":
    main()
