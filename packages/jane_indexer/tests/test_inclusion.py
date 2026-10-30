import pytest

from jane_indexer import inclusion


@pytest.mark.skip(reason="scaffold — port the excluded-types list and inclusion rules first")
def test_excluded_publication_types_populated():
    assert len(inclusion.EXCLUDED_PUBLICATION_TYPES) > 0
