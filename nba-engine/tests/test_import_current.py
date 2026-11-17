import pandas as pd
import pytest
from nba_engine.import_current import load_current_states, run

NOW="2026-11-17T16:00:00Z"

def make_csv(path, permitted="true"):
    pd.DataFrame([
        dict(team=t,as_of="2026-11-17T15:00:00Z",season="2026-27",
             games=12,offensive_rating=115,defensive_rating=110,pace=100,
             source="licensed provider",license_reference="license-contract-123",
             statistical_research_permitted=permitted)
        for t in ("A","B")
    ]).to_csv(path,index=False)

def test_authorized_import_and_projection(tmp_path):
    csv=tmp_path/"current.csv"
    make_csv(csv)
    result=run(csv,"A","B",season="2026-27",
               league_off_rating=112,league_pace=100,now=NOW,out=tmp_path/"out")
    assert result["expected_total"]==pytest.approx(226)
    assert result["win_probability"] is None
    assert result["lineups_confirmed"] is False

def test_reject_unlicensed_data(tmp_path):
    csv=tmp_path/"current.csv"
    make_csv(csv,permitted="false")
    with pytest.raises(ValueError,match="authorization"):
        load_current_states(csv)

def test_reject_wrong_season(tmp_path):
    csv=tmp_path/"current.csv"
    make_csv(csv)
    with pytest.raises(ValueError,match="No records"):
        load_current_states(csv,season="2025-26")
