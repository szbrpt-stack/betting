"""NBA v0.2: season-based walk-forward evaluation, with strictly prior-game features."""
import argparse
import json
from pathlib import Path

import numpy as np
import pandas as pd
from sklearn.linear_model import LogisticRegression, Ridge
from sklearn.metrics import accuracy_score, brier_score_loss, log_loss, mean_absolute_error
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import StandardScaler

from .pipeline import load_games, features_from_history, FEATURES


def season_of(date):
    """NBA season label: October–September, named by its starting year."""
    date = pd.Timestamp(date)
    return int(date.year if date.month >= 10 else date.year - 1)


def evaluate(games, out="outputs/v02", min_train_seasons=3):
    features = features_from_history(games)
    if features.empty:
        raise ValueError("No eligible games")
    features["season"] = features["date"].map(season_of)
    seasons = sorted(features["season"].unique())
    if len(seasons) < min_train_seasons + 1:
        raise ValueError(f"Need at least {min_train_seasons + 1} NBA seasons")
    out = Path(out)
    out.mkdir(parents=True, exist_ok=True)
    folds, predictions = [], []
    for test_season in seasons[min_train_seasons:]:
        train = features[features.season < test_season]
        test = features[features.season == test_season]
        if train.home_win.nunique() < 2 or test.empty:
            continue
        classifier = make_pipeline(StandardScaler(), LogisticRegression(max_iter=2000))
        totals = make_pipeline(StandardScaler(), Ridge(alpha=10.0))
        classifier.fit(train[FEATURES], train.home_win)
        totals.fit(train[FEATURES], train.total)
        p = np.clip(classifier.predict_proba(test[FEATURES])[:, 1], 1e-6, 1 - 1e-6)
        t = totals.predict(test[FEATURES])
        reference_p = np.full(len(test), train.home_win.mean())
        reference_total = np.full(len(test), train.total.mean())
        metrics = {
            "test_season": int(test_season),
            "train_games": int(len(train)),
            "test_games": int(len(test)),
            "brier": float(brier_score_loss(test.home_win, p)),
            "baseline_brier": float(brier_score_loss(test.home_win, reference_p)),
            "log_loss": float(log_loss(test.home_win, p, labels=[0, 1])),
            "accuracy": float(accuracy_score(test.home_win, p >= 0.5)),
            "total_mae": float(mean_absolute_error(test.total, t)),
            "baseline_total_mae": float(mean_absolute_error(test.total, reference_total)),
        }
        folds.append(metrics)
        pred = test[["game_id", "date", "season", "home_win", "total"]].copy()
        pred["prob_home_win"] = p
        pred["predicted_total"] = t
        predictions.append(pred)
    if not folds:
        raise ValueError("No valid season folds")
    pred = pd.concat(predictions, ignore_index=True)
    all_y = pred.home_win.to_numpy()
    all_p = pred.prob_home_win.to_numpy()
    report = {
        "version": "0.2",
        "season_folds": folds,
        "overall": {
            "games": int(len(pred)),
            "brier": float(brier_score_loss(all_y, all_p)),
            "log_loss": float(log_loss(all_y, all_p, labels=[0, 1])),
            "accuracy": float(accuracy_score(all_y, all_p >= .5)),
            "total_mae": float(mean_absolute_error(pred.total, pred.predicted_total)),
            "brier_improvement_folds": int(sum(x["brier"] < x["baseline_brier"] for x in folds)),
            "total_mae_improvement_folds": int(sum(x["total_mae"] < x["baseline_total_mae"] for x in folds)),
        },
        "limitations": [
            "No sportsbook odds, ROI, closing-line value or profitability claims",
            "Features use points scored/allowed, not possession-based efficiency or true pace",
            "Historical team IDs, playoff inclusion and season coverage require source audit",
            "No hyperparameter tuning or market baseline in this version",
        ],
    }
    pred.to_csv(out / "predictions.csv", index=False)
    (out / "metrics.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    return report


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--csv", required=True)
    parser.add_argument("--out", default="outputs/v02")
    args = parser.parse_args()
    print(json.dumps(evaluate(load_games(args.csv), args.out), indent=2))
