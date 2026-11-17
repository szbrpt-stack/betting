"""Offline ESPN NBA JSON adapter.

Consumes saved scoreboard and game-summary responses. Never makes HTTP calls.
Endpoint schemas are undocumented: fail closed when required fields differ.
"""
import argparse
import json
from pathlib import Path

import pandas as pd


def parse_scoreboard(payload):
    if not isinstance(payload, dict) or not isinstance(payload.get("events"), list):
        raise ValueError("Expected ESPN scoreboard events array")
    rows = []
    for event in payload["events"]:
        competitions = event.get("competitions") or []
        if len(competitions) != 1:
            raise ValueError("Expected exactly one competition per event")
        competition = competitions[0]
        competitors = competition.get("competitors") or []
        if len(competitors) != 2:
            raise ValueError("Expected exactly two competitors")
        sides = {c.get("homeAway"): c for c in competitors}
        if set(sides) != {"home", "away"}:
            raise ValueError("Missing home/away designation")
        status = (event.get("status") or {}).get("type") or {}
        row = {
            "game_id": str(event["id"]), "date": str(event["date"]),
            "home_team_id": str(sides["home"]["team"]["id"]),
            "away_team_id": str(sides["away"]["team"]["id"]),
            "completed": status.get("completed") is True,
            "status": str(status.get("name", "unknown")),
        }
        if row["completed"]:
            try:
                row["home_points"] = int(sides["home"]["score"])
                row["away_points"] = int(sides["away"]["score"])
            except (KeyError, ValueError, TypeError) as exc:
                raise ValueError("Completed game missing valid scores") from exc
        else:
            row["home_points"] = None
            row["away_points"] = None
        rows.append(row)
    frame = pd.DataFrame(rows, columns=["game_id", "date", "home_team_id", "away_team_id",
                                        "completed", "status", "home_points", "away_points"])
    if frame.game_id.duplicated().any():
        raise ValueError("Duplicate event IDs")
    return frame


def parse_summary_players(payload, *, game_id, game_date, season="2026-27"):
    """Extract completed-game box scores; DNP entries are excluded, not zeroed."""
    teams = ((payload.get("boxscore") or {}).get("players") or [])
    if not isinstance(teams, list) or not teams:
        raise ValueError("Expected boxscore.players array")
    rows = []
    for team in teams:
        team_id = str((team.get("team") or {})["id"])
        for block in team.get("statistics") or []:
            labels = [str(x).upper() for x in (block.get("names") or block.get("labels") or [])]
            if "MIN" not in labels or "PTS" not in labels:
                continue
            for entry in block.get("athletes") or []:
                if entry.get("didNotPlay") is True:
                    continue
                stats = entry.get("stats") or []
                if len(stats) != len(labels):
                    raise ValueError("Player stats/labels length mismatch")
                athlete = entry.get("athlete") or {}
                try:
                    mins = str(stats[labels.index("MIN")])
                    if ":" in mins:
                        m, s = mins.split(":")
                        minutes = int(m) + int(s) / 60
                    else:
                        minutes = float(mins)
                    if not minutes == minutes:
                        raise ValueError("Non-finite minutes")
                    points = int(stats[labels.index("PTS")])
                    if not 0 <= minutes <= 60 or points < 0:
                        raise ValueError("Invalid minutes or points")
                    rows.append({
                        "game_id": str(game_id), "game_date": str(game_date),
                        "season": season, "player_id": str(athlete["id"]),
                        "player_name": str(athlete["displayName"]),
                        "team_id": team_id, "minutes": minutes, "points": points,
                    })
                except (KeyError, ValueError, TypeError) as exc:
                    raise ValueError("Invalid ESPN athlete box score") from exc
    result = pd.DataFrame(rows, columns=["game_id", "game_date", "season", "player_id",
                                         "player_name", "team_id", "minutes", "points"])
    if result.empty:
        raise ValueError("No played athletes in summary")
    if result.duplicated(["game_id", "player_id"]).any():
        raise ValueError("Duplicate athlete in game")
    return result


def convert(scoreboard_path, summary_path, *, source_reference, rights_reference,
            research_permitted, out):
    if not research_permitted or not rights_reference.strip() or not source_reference.strip():
        raise ValueError("Source and affirmative statistical-research permission required")
    scoreboard = parse_scoreboard(json.loads(Path(scoreboard_path).read_text(encoding="utf-8")))
    if len(scoreboard) != 1 or not bool(scoreboard.iloc[0].completed):
        raise ValueError("Provide a single completed scoreboard game")
    game = scoreboard.iloc[0]
    summary = json.loads(Path(summary_path).read_text(encoding="utf-8"))
    players = parse_summary_players(summary, game_id=game.game_id, game_date=game.date)
    if not set(players.team_id).issubset({game.home_team_id, game.away_team_id}):
        raise ValueError("Summary team IDs differ from scoreboard")
    players["source"] = source_reference
    players["license_reference"] = rights_reference
    players["statistical_research_permitted"] = True
    dest = Path(out)
    dest.mkdir(parents=True, exist_ok=True)
    scoreboard.to_csv(dest / "games.csv", index=False)
    players.to_csv(dest / "player_games.csv", index=False)
    return {"games": 1, "player_rows": len(players), "source_mode": "offline_saved_espn_json"}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--scoreboard", required=True)
    parser.add_argument("--summary", required=True)
    parser.add_argument("--source-reference", required=True)
    parser.add_argument("--rights-reference", required=True)
    parser.add_argument("--research-permitted", action="store_true")
    parser.add_argument("--out", default="outputs/espn")
    args = parser.parse_args()
    print(json.dumps(convert(args.scoreboard, args.summary,
                             source_reference=args.source_reference,
                             rights_reference=args.rights_reference,
                             research_permitted=args.research_permitted, out=args.out), indent=2))
