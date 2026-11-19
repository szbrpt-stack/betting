"""Opt-in one-request NBA Stat connectivity and season probe.

Personal non-commercial use only. Does not save or print response bodies or token.
Requires NBASTAT_API_TOKEN environment variable. Never run in CI.
"""
import argparse
import json
import os
import urllib.error
import urllib.parse
import urllib.request

BASE = "https://nbastat.io/api/v1"


def probe(token, season="2026-27", timeout=15):
    if not token or not token.strip():
        raise ValueError("Set NBASTAT_API_TOKEN locally; never commit or paste it")
    query = urllib.parse.urlencode({"season": season, "per_page": 1})
    request = urllib.request.Request(
        f"{BASE}/games?{query}",
        headers={"Authorization": f"Bearer {token}", "Accept": "application/json"},
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            payload = json.load(response)
            headers = response.headers
            result = {
                "http_status": response.status,
                "season_requested": season,
                "payload_type": type(payload).__name__,
                "top_level_keys": sorted(payload.keys()) if isinstance(payload, dict) else [],
                "rate_limit_remaining": headers.get("RateLimit-Remaining"),
                "rate_limit_reset": headers.get("RateLimit-Reset"),
            }
            if isinstance(payload, dict):
                for key in ("data", "results", "games"):
                    if isinstance(payload.get(key), list):
                        result["record_count_returned"] = len(payload[key])
                        result["record_keys"] = sorted(payload[key][0].keys()) if payload[key] and isinstance(payload[key][0], dict) else []
                        break
            return result
    except urllib.error.HTTPError as exc:
        return {"http_status": exc.code, "season_requested": season,
                "retry_after": exc.headers.get("Retry-After"),
                "error": "API request rejected; check token, access rights, or rate limit"}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--season", default="2026-27")
    args = parser.parse_args()
    print(json.dumps(probe(os.environ.get("NBASTAT_API_TOKEN"), args.season), indent=2))
