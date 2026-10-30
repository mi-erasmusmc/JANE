from jane_core import text


def test_normalize_matches_legacy_analyzer():
    # Guards the #1 risk: index/query tokenization parity with the old MedlineAnalyzer.
    # Non-alnum (except '.') -> space; lowercase; drop the 4 stopwords; dots preserved.
    assert text.normalize("IL-6 levels (2020).") == ["il", "6", "levels", "2020"]
    assert text.normalize("the role of TNF-alpha in sepsis") == ["role", "tnf", "alpha", "sepsis"]
    assert text.normalize("e.g. 3.5 mg/dL") == ["e.g.", "3.5", "mg", "dl"]
    assert text.normalize("") == []
    assert text.normalize(None) == []


def test_normalize_to_text_roundtrips_to_space_joined():
    assert text.normalize_to_text("IL-6 levels (2020).") == "il 6 levels 2020"


def test_stopwords_are_exactly_four():
    assert text.STOPWORDS == frozenset({"of", "the", "and", "in"})
