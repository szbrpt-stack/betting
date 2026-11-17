"""Causal probability recalibration using only earlier out-of-sample seasons."""
import argparse
import json
from pathlib import Path

import numpy as np
import pandas as pd
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import brier_score_loss, log_loss


def recalibrate(predictions, out="outputs/recalibration", min_history_games=1000):
    pred = pd.read_csv(predictions, dtype={"game_id": str})
    required = {"game_id", "season", "home_win", "prob_home_win"}
    if required - set(pred):
        raise ValueError(f"Missing columns: {sorted(required-set(pred))}")
    if pred.empty or pred.game_id.isna().any() or pred.game_id.duplicated().any():
        raise ValueError("Prediction IDs must be unique and non-null")
    if pred[["season", "home_win", "prob_home_win"]].isna().any().any():
        raise ValueError("Missing prediction values")
    if not pred.home_win.isin([0, 1]).all() or not pred.prob_home_win.between(0, 1).all():
        raise ValueError("Invalid prediction values")
    # Platt scaling on the logit of *past out-of-sample* probabilities.
    # Do not use a season's outcomes until that season is finished.
    def logit(values):
        p = np.clip(np.asarray(values, dtype=float), 1e-6, 1 - 1e-6)
        return np.log(p / (1 - p)).reshape(-1, 1)

    rows, folds = [], []
    for season in sorted(pred.season.unique()):
        past = pred[pred.season < season]
        current = pred[pred.season == season].copy()
        if len(past) < min_history_games or past.home_win.nunique() < 2:
            continue
        model = LogisticRegression(max_iter=1000)
        model.fit(logit(past.prob_home_win), past.home_win)
        calibrated = np.clip(model.predict_proba(logit(current.prob_home_win))[:, 1], 1e-6, 1 - 1e-6)
        raw = np.clip(current.prob_home_win.to_numpy(), 1e-6, 1 - 1e-6)
        y = current.home_win.to_numpy()
        folds.append({
            "season": int(season), "calibration_train_games": int(len(past)),
            "test_games": int(len(current)),
            "raw_brier": float(brier_score_loss(y, raw)),
            "calibrated_brier": float(brier_score_loss(y, calibrated)),
            "raw_log_loss": float(log_loss(y, raw, labels=[0, 1])),
            "calibrated_log_loss": float(log_loss(y, calibrated, labels=[0, 1])),
            "slope": float(model.coef_[0, 0]),
            "intercept": float(model.intercept_[0]),
        })
        current["calibrated_prob_home_win"] = calibrated
        rows.append(current)
    if not rows:
        raise ValueError("Insufficient earlier out-of-sample seasons to recalibrate")
    combined = pd.concat(rows, ignore_index=True)
    y = combined.home_win.to_numpy()
    raw = combined.prob_home_win.to_numpy()
    calibrated = combined.calibrated_prob_home_win.to_numpy()
    report = {
        "method": "causal expanding-window Platt scaling on out-of-sample prediction logits",
        "overall": {
            "games": int(len(combined)),
            "raw_brier": float(brier_score_loss(y, raw)),
            "calibrated_brier": float(brier_score_loss(y, calibrated)),
            "raw_log_loss": float(log_loss(y, np.clip(raw, 1e-6, 1-1e-6), labels=[0, 1])),
            "calibrated_log_loss": float(log_loss(y, calibrated, labels=[0, 1])),
            "improved_brier_seasons": int(sum(f["calibrated_brier"] < f["raw_brier"] for f in folds)),
            "seasons": len(folds),
        },
        "folds": folds,
        "warnings": [
            "First eligible season is excluded because no earlier out-of-sample calibration history exists",
            "Compare raw and calibrated on identical games; do not compare against all-season v0.2 aggregate",
            "No betting odds, market edge, ROI or profitability assessment",
        ],
    }
    out = Path(out)
    out.mkdir(parents=True, exist_ok=True)
    combined.to_csv(out / "predictions.csv", index=False)
    pd.DataFrame(folds).to_csv(out / "folds.csv", index=False)
    (out / "metrics.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    return report


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--predictions", required=True)
    parser.add_argument("--out", default="outputs/recalibration")
    args = parser.parse_args()
    print(json.dumps(recalibrate(args.predictions, args.out), indent=2))
