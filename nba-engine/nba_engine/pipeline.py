"""Offline NBA modeling baseline. Requires a user-supplied, licensed games CSV."""
import argparse
import json
from pathlib import Path
import numpy as np
import pandas as pd
from sklearn.linear_model import LogisticRegression, Ridge
from sklearn.metrics import brier_score_loss, log_loss, accuracy_score, mean_absolute_error

REQUIRED = {"GAME_ID","GAME_DATE_EST","HOME_TEAM_ID","VISITOR_TEAM_ID","PTS_home","PTS_away"}
FEATURES = ["home_for","home_against","away_for","away_against","home_winrate","away_winrate","home_rest","away_rest"]

def load_games(csv):
    df = pd.read_csv(csv, dtype={"GAME_ID":str,"HOME_TEAM_ID":str,"VISITOR_TEAM_ID":str})
    if REQUIRED - set(df):
        raise ValueError(f"Missing columns: {sorted(REQUIRED-set(df))}")
    df = df.rename(columns={"GAME_ID":"game_id","GAME_DATE_EST":"date","HOME_TEAM_ID":"home","VISITOR_TEAM_ID":"away","PTS_home":"home_pts","PTS_away":"away_pts"})
    df["date"] = pd.to_datetime(df["date"], errors="coerce")
    for col in ("home_pts","away_pts"):
        df[col] = pd.to_numeric(df[col], errors="coerce")
    df = df.dropna(subset=["game_id","date","home","away","home_pts","away_pts"])
    df = df[(df.home != df.away) & (df.home_pts >= 0) & (df.away_pts >= 0)]
    df = df.sort_values(["date","game_id"]).drop_duplicates("game_id", keep="last")
    if df.empty:
        raise ValueError("No completed games")
    return df.reset_index(drop=True)

def features_from_history(games, window=10, min_games=5):
    history, rows = {}, []
    for date, batch in games.groupby("date", sort=True):
        for game in batch.itertuples(index=False):
            h, a = history.get(game.home,[]), history.get(game.away,[])
            if min(len(h),len(a)) < min_games:
                continue
            h_recent, a_recent = h[-window:], a[-window:]
            mean = lambda seq,key: float(np.mean([item[key] for item in seq]))
            rest = lambda seq: float(np.clip((date-seq[-1]["date"]).days,0,14))
            rows.append({"game_id":game.game_id,"date":date,
                "home_for":mean(h_recent,"for"),"home_against":mean(h_recent,"against"),
                "away_for":mean(a_recent,"for"),"away_against":mean(a_recent,"against"),
                "home_winrate":mean(h_recent,"win"),"away_winrate":mean(a_recent,"win"),
                "home_rest":rest(h),"away_rest":rest(a),
                "home_win":int(game.home_pts>game.away_pts),
                "total":float(game.home_pts+game.away_pts)})
        # Same-date results enter history only after all features for that date.
        for game in batch.itertuples(index=False):
            history.setdefault(game.home,[]).append({"date":date,"for":game.home_pts,"against":game.away_pts,"win":int(game.home_pts>game.away_pts)})
            history.setdefault(game.away,[]).append({"date":date,"for":game.away_pts,"against":game.home_pts,"win":int(game.away_pts>game.home_pts)})
    return pd.DataFrame(rows)

def split_chronologically(df):
    dates = np.array(sorted(df.date.unique()))
    if len(dates)<20:
        raise ValueError("At least 20 eligible game dates required")
    a,b = int(len(dates)*.7),int(len(dates)*.85)
    return [df[df.date.isin(group)] for group in (dates[:a],dates[a:b],dates[b:])]

def run(csv, out="outputs"):
    games=load_games(csv)
    feats=features_from_history(games)
    if feats.empty:
        raise ValueError("Not enough historical games")
    train,valid,test=split_chronologically(feats)
    if train.home_win.nunique()!=2:
        raise ValueError("Training data must contain both outcomes")
    clf=LogisticRegression(max_iter=2000)
    reg=Ridge(alpha=10.0)
    clf.fit(train[FEATURES],train.home_win)
    reg.fit(train[FEATURES],train.total)
    report={"games":len(games),"eligible":len(feats),"splits":{},"metrics":{}}
    predictions=[]
    for name,part in (("validation",valid),("test",test)):
        prob=np.clip(clf.predict_proba(part[FEATURES])[:,1],1e-6,1-1e-6)
        total=reg.predict(part[FEATURES])
        report["splits"][name]=len(part)
        report["metrics"][name]={
            "brier":float(brier_score_loss(part.home_win,prob)),
            "log_loss":float(log_loss(part.home_win,prob,labels=[0,1])),
            "accuracy":float(accuracy_score(part.home_win,prob>=.5)),
            "total_mae":float(mean_absolute_error(part.total,total)),
            "constant_home_rate_brier":float(brier_score_loss(part.home_win,np.full(len(part),train.home_win.mean()))),
            "constant_total_mae":float(mean_absolute_error(part.total,np.full(len(part),train.total.mean())))}
        pred=part[["game_id","date","home_win","total"]].copy()
        pred["split"]=name
        pred["prob_home_win"]=prob
        pred["predicted_total"]=total
        predictions.append(pred)
    out=Path(out)
    out.mkdir(parents=True,exist_ok=True)
    games.to_parquet(out/"games.parquet",index=False)
    pd.concat(predictions).to_csv(out/"predictions.csv",index=False)
    (out/"metrics.json").write_text(json.dumps(report,indent=2),encoding="utf-8")
    return report

if __name__=="__main__":
    parser=argparse.ArgumentParser()
    parser.add_argument("--csv",required=True)
    parser.add_argument("--out",default="outputs")
    args=parser.parse_args()
    print(json.dumps(run(args.csv,args.out),indent=2))
