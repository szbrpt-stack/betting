import pytest
from nba_engine.espn_adapter import parse_scoreboard, parse_summary_players

def scoreboard(completed=True):
    return {"events":[{"id":"g1","date":"2026-11-16T23:00Z",
        "status":{"type":{"completed":completed,"name":"STATUS_FINAL"}},
        "competitions":[{"competitors":[
            {"homeAway":"home","team":{"id":"1"},"score":"112"},
            {"homeAway":"away","team":{"id":"2"},"score":"109"}]}]}]}

def test_scoreboard_final_and_scheduled():
    final=parse_scoreboard(scoreboard())
    assert final.iloc[0].home_points==112
    scheduled=parse_scoreboard(scoreboard(False))
    assert scheduled.iloc[0].completed==False
    assert scheduled.iloc[0].home_points!=scheduled.iloc[0].home_points

def test_boxscore_minutes_and_dnp():
    data={"boxscore":{"players":[{"team":{"id":"1"},"statistics":[{
        "labels":["MIN","PTS"],"athletes":[
            {"athlete":{"id":"p1","displayName":"Player"},"stats":["30:30","24"]},
            {"athlete":{"id":"p2","displayName":"DNP"},"didNotPlay":True,"stats":["0","0"]}
        ]}]}]}}
    result=parse_summary_players(data,game_id="g1",game_date="2026-11-16T23:00Z")
    assert len(result)==1
    assert result.iloc[0].minutes==pytest.approx(30.5)
    assert result.iloc[0].points==24

def test_bad_structure_rejected():
    with pytest.raises(ValueError):
        parse_scoreboard({"events":[{"id":"x","competitions":[]}]})
