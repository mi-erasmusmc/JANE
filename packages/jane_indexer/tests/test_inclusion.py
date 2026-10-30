from jane_indexer import inclusion
from jane_indexer.build_index import extract_email_from_affiliation


def test_excluded_publication_types_populated():
    assert len(inclusion.EXCLUDED_PUBLICATION_TYPES) > 0
    assert "Comment" in inclusion.EXCLUDED_PUBLICATION_TYPES
    assert "Published Erratum" in inclusion.EXCLUDED_PUBLICATION_TYPES


def test_has_legal_pub_type():
    assert inclusion.has_legal_pub_type(["Journal Article"])
    assert not inclusion.has_legal_pub_type(["Journal Article", "Comment"])
    assert inclusion.has_legal_pub_type([])


def test_display_journal_name_caps_long_titles():
    short = "Nature"
    assert inclusion.display_journal_name(short, "Nat") == short
    long = "X" * 90
    assert inclusion.display_journal_name(long, "Abbr") == "Abbr"


def test_email_extraction():
    assert extract_email_from_affiliation("Lab. a.b@c.edu.") == "a.b@c.edu"
    assert extract_email_from_affiliation("no address") is None
