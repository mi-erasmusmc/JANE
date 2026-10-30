import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from harness import evaluate, rank_overlap


def test_rank_overlap():
    assert rank_overlap(["A", "B", "C", "D"], ["A", "B", "X", "Y"], top_n=4) == 0.5
    assert rank_overlap(["A", "B"], ["B", "A"], top_n=10) == 1.0
    assert rank_overlap([], ["A"]) != rank_overlap([], ["A"])  # nan != nan


class _FakeJournal:
    def __init__(self, name):
        self.name = name


class _FakeService:
    def __init__(self, ranking):
        self._ranking = ranking

    def suggest_journals(self, text, filters):
        return [_FakeJournal(n) for n in self._ranking[text]]


def test_evaluate_overlap_and_recall():
    service = _FakeService({"q1": ["A", "B", "C"], "q2": ["X", "Y", "Z"]})
    cases = [
        {"text": "q1", "old_journals": ["A", "B", "D"], "true_journal": "A"},
        {"text": "q2", "old_journals": ["X", "W", "V"], "true_journal": "Q"},
    ]
    r = evaluate(service, cases, top_n=3)
    assert r["recall_total"] == 2 and r["recall_hits"] == 1
    assert r["recall"] == 0.5
    assert r["median_top_overlap"] == (2 / 3 + 1 / 3) / 2
