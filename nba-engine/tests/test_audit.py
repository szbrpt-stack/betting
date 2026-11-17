import pandas as pd
import pytest
from nba_engine.audit import audit
from nba_engine.pipeline import load_games

def test_audit_coverage_and_calibration(tmp_path):
    rows=[]
    for day in range(1,13):
        rows.append({"GAME_ID":str(day),"GAME_DATE_EST":f"2023-11-{day:02}",
                     "HOME_TEAM_ID":"A","VISITOR_TEAM_ID":"B",
                     "PTS_home":100+day,"PTS_away":90})
    source=tmp_path/"games.csv"
    pd.DataFrame(rows).to_csv(source,index=False)
    predictions=tmp_path/"pred.csv"
    pd.DataFrame({"game_id":["6","7"],"season":[2023,2023],
                  "home_win":[1,1],"prob_home_win":[0.6,0.8]}).to_csv(predictions,index=False)
    report=audit(load_games(source),predictions,tmp_path/"audit")
    assert report["seasons"][0]["dataset_games"]==12
    assert report["calibration"]["n_predictions"]==2
    assert report["calibration"]["ece_10_bins"]==pytest.approx(0.3)
    assert (tmp_path/"audit"/"coverage.csv").exists()

def test_audit_rejects_duplicate_predictions(tmp_path):
    games=pd.DataFrame({"game_id":["1"],"date":[pd.Timestamp("2023-11-01")],
                        "home":["A"],"away":["B"],"home_pts":[100],"away_pts":[90]})
    path=tmp_path/"pred.csv"
    pd.DataFrame({"game_id":["1","1"],"season":[2023,2023],
                  "home_win":[1,1],"prob_home_win":[0.5,0.5]}).to_csv(path,index=False)
    with pytest.raises(ValueError,match="Duplicate"):
        audit(games,path,tmp_path/"out")
