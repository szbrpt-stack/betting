import pandas as pd
import pytest
from nba_engine.recalibration import recalibrate

def test_recalibration_never_trains_on_current_or_future_season(tmp_path):
    rows=[]
    for season in [2020,2021,2022]:
        for i in range(60):
            rows.append({"game_id":f"{season}-{i}","season":season,
                         "home_win":int(i%2==0),
                         "prob_home_win":0.65 if i%2==0 else 0.35})
    p=tmp_path/"pred.csv"
    pd.DataFrame(rows).to_csv(p,index=False)
    result=recalibrate(p,tmp_path/"out",min_history_games=60)
    assert [f["season"] for f in result["folds"]]==[2021,2022]
    assert [f["calibration_train_games"] for f in result["folds"]]==[60,120]
    assert result["overall"]["games"]==120
    assert (tmp_path/"out"/"metrics.json").exists()

def test_recalibration_rejects_duplicate_ids(tmp_path):
    p=tmp_path/"pred.csv"
    pd.DataFrame({"game_id":["a","a"],"season":[2020,2021],
                  "home_win":[1,0],"prob_home_win":[0.6,0.4]}).to_csv(p,index=False)
    with pytest.raises(ValueError,match="unique"):
        recalibrate(p,tmp_path/"out")
