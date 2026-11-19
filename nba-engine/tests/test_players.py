import pandas as pd
import pytest
from nba_engine.players import player_scenarios

def sample(path, permission="true"):
    pd.DataFrame([
        {"game_id":str(i),"game_date":f"2026-11-{i+1:02d}T00:00:00Z",
         "season":"2026-27","player_id":"p1","player_name":"Sample",
         "team_id":"T1","minutes":30,"points":10+i,
         "source":"authorized","license_reference":"research-terms",
         "statistical_research_permitted":permission}
        for i in range(6)
    ]).to_csv(path,index=False)

def test_player_recent_scenarios(tmp_path):
    path=tmp_path/"logs.csv"
    sample(path)
    result=player_scenarios(path,cutoff="2026-11-17T00:00:00Z",
                            threshold=13,out=tmp_path/"out")
    assert result["players"]==1
    row=pd.read_csv(tmp_path/"out"/"player_scenarios.csv").iloc[0]
    assert row["mean_points"]==pytest.approx(12.5)
    assert row["observed_fraction_at_least_threshold"]==pytest.approx(.5)
    assert pd.isna(row["future_probability"])

def test_rejects_future_observations(tmp_path):
    path=tmp_path/"logs.csv"
    sample(path)
    with pytest.raises(ValueError,match="cutoff"):
        player_scenarios(path,cutoff="2026-11-05T00:00:00Z")

def test_rejects_missing_permission(tmp_path):
    path=tmp_path/"logs.csv"
    sample(path,permission="false")
    with pytest.raises(ValueError,match="permission"):
        player_scenarios(path,cutoff="2026-11-17T00:00:00Z")
