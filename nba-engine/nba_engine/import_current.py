"""Validated, licensed current-season CSV input for S2S Sigma v0.3.

This module performs no network requests. The operator must verify that the
data license explicitly allows the intended use (including betting analysis).
"""
import argparse
import json
from datetime import datetime, timezone
from pathlib import Path

import pandas as pd

from .current import TeamState, project

REQUIRED = {
    "team", "as_of", "season", "games", "offensive_rating",
    "defensive_rating", "pace", "source", "license_reference",
    "betting_use_permitted",
}


def load_current_states(csv, *, season="2026-27"):
    frame = pd.read_csv(csv, dtype={"team": str, "season": str})
    missing = REQUIRED - set(frame.columns)
    if missing:
        raise ValueError(f"Missing columns: {sorted(missing)}")
    frame = frame[frame.season == season].copy()
    if frame.empty:
        raise ValueError(f"No records for season {season}")
    if frame.team.isna().any() or frame.team.duplicated().any():
        raise ValueError("Team identifiers must be unique and non-null")
    if frame[["source", "license_reference"]].isna().any().any():
        raise ValueError("Source and license reference required")
    if (frame[["source", "license_reference"]].astype(str).apply(lambda col: col.str.strip().eq("")).any().any()):
        raise ValueError("Source and license reference cannot be empty")
    # Require an explicit attestation; never infer permission from 'free'.
    permission = frame.betting_use_permitted.astype(str).str.strip().str.lower()
    if not permission.eq("true").all():
        raise ValueError("Explicit betting-use authorization required for every record")
    numeric = ["games", "offensive_rating", "defensive_rating", "pace"]
    for name in numeric:
        frame[name] = pd.to_numeric(frame[name], errors="coerce")
    if frame[numeric].isna().any().any():
        raise ValueError("Invalid numeric team metrics")
    if (frame.games % 1 != 0).any():
        raise ValueError("games must be an integer")
    states = {}
    for record in frame.to_dict("records"):
        states[record["team"]] = TeamState(
            team=record["team"], as_of=str(record["as_of"]),
            games=int(record["games"]),
            offensive_rating=float(record["offensive_rating"]),
            defensive_rating=float(record["defensive_rating"]),
            pace=float(record["pace"]),
            lineup_confirmed=False,
        )
    return states


def run(csv, home, away, *, season, league_off_rating, league_pace,
        now=None, out="outputs/current"):
    states = load_current_states(csv, season=season)
    if home not in states or away not in states:
        raise ValueError("Requested team not in authorized current-season dataset")
    result = project(states[home], states[away],
                     league_off_rating=league_off_rating,
                     league_pace=league_pace, now=now)
    result["season"] = season
    result["source_mode"] = "operator_supplied_authorized_csv"
    result["injury_adjustments"] = "not_available"
    output = Path(out)
    output.mkdir(parents=True, exist_ok=True)
    (output / "projection.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--csv", required=True)
    parser.add_argument("--home", required=True)
    parser.add_argument("--away", required=True)
    parser.add_argument("--season", default="2026-27")
    parser.add_argument("--league-off-rating", type=float, required=True)
    parser.add_argument("--league-pace", type=float, required=True)
    parser.add_argument("--now", default=None)
    parser.add_argument("--out", default="outputs/current")
    args = parser.parse_args()
    print(json.dumps(run(args.csv, args.home, args.away, season=args.season,
                         league_off_rating=args.league_off_rating,
                         league_pace=args.league_pace, now=args.now, out=args.out), indent=2))
