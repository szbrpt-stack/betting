"""Current-season player game-log scenarios; no invented injuries or live claims.

Empirical rates describe recent observations, not calibrated future probabilities.
"""
import argparse
import json
from pathlib import Path
import pandas as pd

REQUIRED = {"game_id", "game_date", "season", "player_id", "player_name",
            "team_id", "minutes", "points", "source", "license_reference",
            "statistical_research_permitted"}


def player_scenarios(csv, *, season="2026-27", cutoff, window=10,
                     min_games=5, threshold=25, out="outputs/players"):
    if not 5 <= window <= 30 or min_games < 3 or min_games > window:
        raise ValueError("Invalid window or min_games")
    frame = pd.read_csv(csv, dtype={"game_id": str, "player_id": str,
                                    "team_id": str, "season": str})
    missing = REQUIRED - set(frame)
    if missing:
        raise ValueError(f"Missing columns: {sorted(missing)}")
    frame = frame[frame.season == season].copy()
    if frame.empty:
        raise ValueError("No current-season player games")
    if frame[list(REQUIRED)].isna().any().any():
        raise ValueError("Missing required values")
    if not frame.statistical_research_permitted.astype(str).str.lower().eq("true").all():
        raise ValueError("Statistical research permission attestation required")
    if (frame[["source", "license_reference"]].astype(str).apply(lambda x: x.str.strip().eq("")).any().any()):
        raise ValueError("Missing source provenance")
    frame["game_date"] = pd.to_datetime(frame.game_date, utc=True, errors="coerce")
    end = pd.to_datetime(cutoff, utc=True)
    if pd.isna(end) or frame.game_date.isna().any():
        raise ValueError("Invalid game dates")
    if (frame.game_date >= end).any():
        # Never silently discard future observations: fail closed.
        raise ValueError("Game log contains records on or after cutoff")
    if frame.duplicated(["game_id", "player_id"]).any():
        raise ValueError("Duplicate player-game observations")
    for field in ("minutes", "points"):
        frame[field] = pd.to_numeric(frame[field], errors="coerce")
    if frame[["minutes", "points"]].isna().any().any() or (frame.minutes < 0).any() or (frame.minutes > 60).any() or (frame.points < 0).any():
        raise ValueError("Invalid player box-score values")
    rows = []
    for player_id, group in frame.sort_values(["game_date", "game_id"]).groupby("player_id"):
        recent = group.tail(window)
        if len(recent) < min_games:
            continue
        # Player traded teams? Last observed team only; availability remains unknown.
        values = recent.points
        rows.append({
            "player_id": str(player_id), "player_name": str(recent.player_name.iloc[-1]),
            "team_id_last_seen": str(recent.team_id.iloc[-1]),
            "games": int(len(recent)), "last_game_date": recent.game_date.iloc[-1].isoformat(),
            "mean_minutes": float(recent.minutes.mean()),
            "mean_points": float(values.mean()),
            "median_points": float(values.median()),
            "observed_p10": float(values.quantile(0.1)),
            "observed_p90": float(values.quantile(0.9)),
            "threshold_points": float(threshold),
            "observed_fraction_at_least_threshold": float((values >= threshold).mean()),
            "availability": "unknown",
            "future_probability": None,
            "note": "Descriptive recent-game distribution; not a predictive confidence interval",
        })
    output = Path(out)
    output.mkdir(parents=True, exist_ok=True)
    pd.DataFrame(rows).to_csv(output / "player_scenarios.csv", index=False)
    report = {"season": season, "cutoff": end.isoformat(), "players": len(rows),
              "window": window, "min_games": min_games, "threshold": threshold,
              "probabilities_calibrated": False,
              "limitations": ["No confirmed availability or projected minutes",
                              "Empirical quantiles are not predictive intervals",
                              "Season aggregates cannot replace game-level observations"]}
    (output / "summary.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    return report


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--csv", required=True)
    parser.add_argument("--cutoff", required=True)
    parser.add_argument("--season", default="2026-27")
    parser.add_argument("--window", type=int, default=10)
    parser.add_argument("--threshold", type=float, default=25)
    parser.add_argument("--out", default="outputs/players")
    args = parser.parse_args()
    print(json.dumps(player_scenarios(args.csv, cutoff=args.cutoff, season=args.season,
                                      window=args.window, threshold=args.threshold,
                                      out=args.out), indent=2))
