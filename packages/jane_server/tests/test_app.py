import pytest


@pytest.mark.skip(reason="scaffold — implement create_app first")
def test_app_builds():
    from jane_server.app import create_app

    assert create_app() is not None
