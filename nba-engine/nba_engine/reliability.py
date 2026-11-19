"""Descriptive out-of-sample reliability by season and confidence bucket.

Does not fit or alter probabilities. Small groups are explicitly flagged.
"""
import argparse
import json
from pathlib import Path

import numpy as np
import pandas as pd
from sklearn.metrics import brier_score_loss, log_loss


def reliability(predictions, out="outputs/reliability", min_bin_games=100):
    pred = pd.read_csv(predictions, dtype={"game_id": str})
    required = {"game_id", "season", "home_win", "prob_home_win"}
    if required - set(pred):
        raise ValueError(f"Missing columns: {sorted(required-set(pred))}")
    if pred.empty or pred.game_id.isna().any() or pred.game_id.duplicated().any():
        raise ValueError("Predictions must have unique non-null game IDs")
    if pred[["season", "home_win", "prob_home_win"]].isna().any().any():
        raise ValueError("Missing values in predictions")
    if not pred.home_win.isin([0, 1]).all() or not pred.prob_home_win.between(0, 1).all():
        raise ValueError("Invalid outcomes or probabilities")
    pred["bucket"] = np.minimum((pred.prob_home_win * 10).astype(int), 9)
    rows = []
    for season, part in pred.groupby("season", sort=True):
        for bucket in range(10):
            sub = part[part.bucket == bucket]
            n = len(sub)
            mean_p = float(sub.prob_home_win.mean()) if n else None
            observed = float(sub.home_win.mean()) if n else None
            rows.append({
                "season": int(season), "bucket": bucket,
                "lower": bucket / 10, "upper": (bucket + 1) / 10,
                "games": n, "mean_probability": mean_p,
                "observed_win_rate": observed,
                "absolute_gap": abs(mean_p - observed) if n else None,
                "small_sample": n < min_bin_games,
            })
    table = pd.DataFrame(rows)
    seasons = []
    for season, part in pred.groupby("season", sort=True):
        subset = table[(table.season == season) & (table.games > 0)]
        ece = float((subset.games * subset.absolute_gap).sum() / len(part))
        seasons.append({
            "season": int(season), "games": int(len(part)),
            "ece_10_bins": ece,
            "brier": float(brier_score_loss(part.home_win, part.prob_home_win)),
            "log_loss": float(log_loss(part.home_win, np.clip(part.prob_home_win, 1e-6, 1-1e-6), labels=[0, 1])),
            "sparse_nonempty_bins": int(((subset.games < min_bin_games)).sum()),
            "nonempty_bins": int(len(subset)),
        })
    summary = {
        "seasons": seasons,
        "min_bin_games": min_bin_games,
        "warnings": [
            "Season buckets are descriptive; no probability recalibration was fitted",
            "Small samples and extreme probabilities can produce unstable gaps",
            "Season labels inherit the October–September approximation",
        ],
    }
    out = Path(out)
    out.mkdir(parents=True, exist_ok=True)
    table.to_csv(out / "season_bins.csv", index=False)
    pd.DataFrame(seasons).to_csv(out / "season_calibration.csv", index=False)
    (out / "reliability.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
    return summary


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--predictions", required=True)
    parser.add_argument("--out", default="outputs/reliability")
    args = parser.parse_args()
    result = reliability(args.predictions, args.out)
    print(json.dumps({"seasons": len(result["seasons"]),
                      "highest_ece_seasons": sorted(result["seasons"], key=lambda x: x["ece_10_bins"], reverse=True)[:5]}, indent=2))
