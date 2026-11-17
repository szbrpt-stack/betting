import pandas as pd
import pytest
from nba_engine.v02 import season_of, evaluate
from nba_engine.pipeline import load_games

def test_season_boundary():
    assert season_of("2023-09-30") == 2022
    assert season_of("2023-10-01") == 2023
    assert season_of("2024-04-01") == 2023

def test_walk_forward_no_future_training(tmp_path):
    rows=[]
    for year in range(2019,2024):
        for day in range(1,23):
            for home,away in (("A","B"),("C","D")):
                rows.append({"GAME_ID":f"{year}-{day}-{home}",
                    "GAME_DATE_EST":f"{year}-11-{day:02}",
                    "HOME_TEAM_ID":home,"VISITOR_TEAM_ID":away,
                    "PTS_home":100+day+(year%3),"PTS_away":90+(day%7)})
    path=tmp_path/"games.csv"
    pd.DataFrame(rows).to_csv(path,index=False)
    result=evaluate(load_games(path),tmp_path/"out")
    folds=result["season_folds"]
    assert [x["test_season"] for x in folds]==[2022,2023]
    assert folds[0]["train_games"] < folds[1]["train_games"]
    assert (tmp_path/"out"/"metrics.json").exists()

def test_insufficient_seasons(tmp_path):
    rows=[]
    for day in range(1,15):
        rows.append({"GAME_ID":str(day),"GAME_DATE_EST":f"2023-11-{day:02}",
            "HOME_TEAM_ID":"A","VISITOR_TEAM_ID":"B","PTS_home":100,"PTS_away":90})
    path=tmp_path/"one.csv"
    pd.DataFrame(rows).to_csv(path,index=False)
    with pytest.raises(ValueError,match="at least"):
        evaluate(load_games(path),tmp_path/"out")
