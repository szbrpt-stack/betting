import pandas as pd
import pytest
from nba_engine.pipeline import load_games, features_from_history

def test_prior_games_only(tmp_path):
    games=[]
    for day in range(1,18):
        for home,away in (("A","B"),("C","D")):
            games.append({"GAME_ID":f"{day}-{home}","GAME_DATE_EST":f"2025-01-{day:02}","HOME_TEAM_ID":home,"VISITOR_TEAM_ID":away,"PTS_home":100+day,"PTS_away":90})
    path=tmp_path/"games.csv"
    pd.DataFrame(games).to_csv(path,index=False)
    result=features_from_history(load_games(path))
    assert result.iloc[0].home_for==103.0
    assert result.iloc[0].home_rest==1.0

def test_required_columns(tmp_path):
    path=tmp_path/"invalid.csv"
    pd.DataFrame({"foo":[1]}).to_csv(path,index=False)
    with pytest.raises(ValueError,match="Missing columns"):
        load_games(path)
