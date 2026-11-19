import pandas as pd
import pytest
from nba_engine.reliability import reliability

def test_season_buckets_and_ece(tmp_path):
    p=tmp_path/"pred.csv"
    pd.DataFrame({"game_id":["1","2","3","4"],"season":[2022,2022,2023,2023],
                  "home_win":[1,0,1,0],"prob_home_win":[0.6,0.6,0.8,0.2]}).to_csv(p,index=False)
    result=reliability(p,tmp_path/"out",min_bin_games=2)
    assert len(result["seasons"])==2
    assert result["seasons"][0]["ece_10_bins"]==pytest.approx(0.1)
    assert result["seasons"][1]["ece_10_bins"]==pytest.approx(0.2)
    assert result["seasons"][1]["sparse_nonempty_bins"]==2
    bins=pd.read_csv(tmp_path/"out"/"season_bins.csv")
    assert len(bins)==20

def test_reject_invalid_probabilities(tmp_path):
    p=tmp_path/"pred.csv"
    pd.DataFrame({"game_id":["1"],"season":[2022],"home_win":[1],
                  "prob_home_win":[1.2]}).to_csv(p,index=False)
    with pytest.raises(ValueError,match="Invalid"):
        reliability(p,tmp_path/"out")
