import pytest

from jane_core import text


@pytest.mark.skip(reason="scaffold — implement jane_core.text.normalize first")
def test_normalize_matches_legacy_analyzer():
    # Guards the #1 risk: index/query tokenization parity with the old MedlineAnalyzer.
    assert text.normalize("IL-6 levels (2020).") == ["il", "6", "levels", "2020."]
