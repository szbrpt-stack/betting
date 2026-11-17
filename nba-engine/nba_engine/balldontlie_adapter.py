"""Offline adapter for saved BALLDONTLIE NBA /v1/stats JSON.

No HTTP requests or credential handling. Operator must establish usage rights.
"""
import argparse
import json
import math
from pathlib import Path
import pandas as pd


def _minutes(value):
    if not isinstance(value, str) or not value.strip():
        raise ValueError("Missing player minutes")
    if ":" in value:
        parts = value.split(":")
        if len(parts) != 2:
            raise ValueError("Invalid minute format")
        m, s = map(int, parts)
        if not (0 <= s < 60):
            raise ValueError("Invalid seconds")
        minutes = m + s / 60
    else:
        minutes = float(value)
    if not math.isfinite(minutes) or not 0 <= minutes <= 70:
        raise ValueError("Invalid minutes")
    return minutes


def parse_stats(payload, *, season=2026):
    if not isinstance(payload, dict) or not isinstance(payload.get("data"), list):
        raise ValueError("Expected BALLDONTLIE data array")
    rows = []
    for stat in payload["data"]:
        game = stat["game"]
        if game.get("season") != season:
            raise ValueError("Unexpected season")
        if not str(game.get("status", "")).lower().startswith("final"):
            raise ValueError("Only completed games accepted")
        player, team = stat["player"], stat["team"]
        if team["id"] not in (game["home_team_id"], game["visitor_team_id"]):
            raise ValueError("Team not in game")
        rows.append({
            "game_id": str(game["id"]), "game_date": str(game["date"]),
            "season": f"{season}-{str((season + 1) % 100).zfill(2)}",
            "player_id": str(player["id"]),
            "player_name": f'{player["first_name"]} {player["last_name"]}',
            "team_id": str(team["id"]), "minutes": _minutes(stat["min"]),
            "points": int(stat["pts"]),
        })
        if rows[-1]["points"] < 0:
            raise ValueError("Negative points")
    frame = pd.DataFrame(rows, columns=["game_id","game_date","season","player_id",
                                        "player_name","team_id","minutes","points"])
    if frame.duplicated(["game_id","player_id"]).any():
        raise ValueError("Duplicate player-game; do not merge overlapping pages")
    return frame


def convert(input_path, *, source_reference, rights_reference, research_permitted, out):
    if not research_permitted or not source_reference.strip() or not rights_reference.strip():
        raise ValueError("Explicit source and rights attestation required")
    frame = parse_stats(json.loads(Path(input_path).read_text(encoding="utf-8")))
    if frame.empty:
        raise ValueError("No completed player records")
    frame["source"] = source_reference
    frame["license_reference"] = rights_reference
    frame["statistical_research_permitted"] = True
    destination = Path(out)
    destination.parent.mkdir(parents=True, exist_ok=True)
    frame.to_csv(destination, index=False)
    return {"player_rows":len(frame),"source_mode":"offline_saved_balldontlie_json"}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--source-reference", required=True)
    parser.add_argument("--rights-reference", required=True)
    parser.add_argument("--research-permitted", action="store_true")
    parser.add_argument("--out", default="outputs/balldontlie/player_games.csv")
    args = parser.parse_args()
    print(json.dumps(convert(args.input, source_reference=args.source_reference,
                             rights_reference=args.rights_reference,
                             research_permitted=args.research_permitted, out=args.out), indent=2))
