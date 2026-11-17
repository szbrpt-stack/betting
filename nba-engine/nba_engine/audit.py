"""Audit dataset coverage and out-of-sample probability calibration."""
import argparse
import json
from pathlib import Path

import numpy as np
import pandas as pd
from .pipeline import load_games, features_from_history
from .v02 import season_of


def audit(games, predictions, out="outputs/audit"):
    out = Path(out)
    out.mkdir(parents=True, exist_ok=True)
    games = games.copy()
    games["season"] = games.date.map(season_of)
    features = features_from_history(games)
    if features.empty:
        features = pd.DataFrame(columns=["date", "season"])
    else:
        features["season"] = features.date.map(season_of)
    pred = pd.read_csv(predictions, dtype={"game_id": str})
    required = {"game_id", "season", "home_win", "prob_home_win"}
    if required - set(pred.columns):
        raise ValueError(f"Missing prediction columns: {sorted(required - set(pred.columns))}")
    if pred.game_id.duplicated().any():
        raise ValueError("Duplicate prediction game IDs")
    if not pred.prob_home_win.between(0, 1).all():
        raise ValueError("Probabilities outside [0, 1]")
    if not pred.home_win.isin([0, 1]).all():
        raise ValueError("Invalid outcomes")
    if not set(pred.game_id).issubset(set(games.game_id)):
        raise ValueError("Predictions contain unknown games")
    # Explicitly report source coverage; do not infer missing NBA fixtures.
    coverage = []
    for season, batch in games.groupby("season"):
        eligible = features[features.season == season]
        tested = pred[pred.season == season]
        coverage.append({
            "season": int(season),
            "dataset_games": int(len(batch)),
            "eligible_games": int(len(eligible)),
            "evaluated_games": int(len(tested)),
            "dataset_dates": int(batch.date.nunique()),
            "dataset_teams": int(pd.concat([batch.home, batch.away]).nunique()),
            "eligible_fraction": round(len(eligible) / len(batch), 5),
            "evaluation_fraction": round(len(tested) / len(batch), 5),
        })
    # Equal-width reliability diagram data, without fitting calibration on test.
    pred["bin"] = np.minimum((pred.prob_home_win * 10).astype(int), 9)
    bins = []
    for index in range(10):
        subset = pred[pred["bin"] == index]
        bins.append({
            "bin": index,
            "range": [index / 10, (index + 1) / 10],
            "count": int(len(subset)),
            "mean_predicted": float(subset.prob_home_win.mean()) if len(subset) else None,
            "observed_home_win_rate": float(subset.home_win.mean()) if len(subset) else None,
        })
    n = len(pred)
    ece = sum(x["count"] * abs(x["mean_predicted"] - x["observed_home_win_rate"])
              for x in bins if x["count"]) / n if n else None
    result = {
        "scope": "Coverage within downloaded dataset only; not verified against official NBA schedule",
        "seasons": coverage,
        "calibration": {"n_predictions": n, "bins": bins, "ece_10_bins": ece},
        "warnings": [
            "Season is approximated as October–September",
            "Preseason, playoffs and cancelled games are not distinguished",
            "Calibration bins are descriptive and do not recalibrate the model",
            "Verify dataset rights, game types and team identity continuity",
        ],
    }
    (out / "audit.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
    pd.DataFrame(coverage).to_csv(out / "coverage.csv", index=False)
    pd.DataFrame(bins).to_csv(out / "calibration.csv", index=False)
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--csv", required=True)
    parser.add_argument("--predictions", required=True)
    parser.add_argument("--out", default="outputs/audit")
    args = parser.parse_args()
    result = audit(load_games(args.csv), args.predictions, args.out)
    print(json.dumps({"seasons": len(result["seasons"]),
                      "predictions": result["calibration"]["n_predictions"],
                      "ece_10_bins": result["calibration"]["ece_10_bins"]}, indent=2))
