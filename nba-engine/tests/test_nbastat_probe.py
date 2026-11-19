import io
import json
import urllib.error
from unittest.mock import patch

import pytest

from nba_engine.nbastat_probe import probe


class FakeResponse:
    status = 200
    headers = {"RateLimit-Remaining": "9", "RateLimit-Reset": "3600"}

    def __enter__(self):
        return self

    def __exit__(self, *args):
        return False

    def read(self, *args):
        return json.dumps({"data": [{"id": 42, "season": "2026-27"}]}).encode()


def test_requires_token_without_network():
    with patch("nba_engine.nbastat_probe.urllib.request.urlopen") as call:
        with pytest.raises(ValueError, match="NBASTAT_API_TOKEN"):
            probe("")
        call.assert_not_called()


def test_single_authenticated_request_and_redacted_output():
    with patch("nba_engine.nbastat_probe.urllib.request.urlopen", return_value=FakeResponse()) as call:
        result = probe("secret-example-token")
    assert call.call_count == 1
    request = call.call_args.args[0]
    assert request.get_header("Authorization") == "Bearer secret-example-token"
    assert "season=2026-27" in request.full_url
    assert result["record_count_returned"] == 1
    assert result["record_keys"] == ["id", "season"]
    assert "secret-example-token" not in json.dumps(result)


def test_rate_limit_is_reported_without_leaking_body():
    error = urllib.error.HTTPError("https://nbastat.io", 429, "Too Many Requests",
                                   {"Retry-After": "120"}, io.BytesIO(b"private"))
    with patch("nba_engine.nbastat_probe.urllib.request.urlopen", side_effect=error):
        result = probe("secret-example-token")
    assert result["http_status"] == 429
    assert result["retry_after"] == "120"
    assert "private" not in json.dumps(result)
