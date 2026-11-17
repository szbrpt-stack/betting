import pytest
from nba_engine.current import TeamState, project

NOW="2026-11-17T16:00:00Z"

def team(name, **kwargs):
    data=dict(team=name,as_of="2026-11-17T15:00:00Z",games=12,
              offensive_rating=115,defensive_rating=110,pace=100)
    data.update(kwargs)
    return TeamState(**data)

def test_current_matchup_uses_current_league_reference_and_lineup():
    a=project(team("A"),team("B"),league_off_rating=112,league_pace=100,now=NOW)
    assert a["expected_home_points"]==pytest.approx(113)
    assert a["expected_away_points"]==pytest.approx(113)
    b=project(team("A",offense_lineup_delta=-5),team("B"),
              league_off_rating=112,league_pace=100,now=NOW)
    assert b["expected_home_points"]==pytest.approx(108)
    assert b["win_probability"] is None

def test_rejects_stale_and_future_snapshots():
    with pytest.raises(ValueError,match="Stale"):
        project(team("A",as_of="2026-11-01T15:00:00Z"),team("B"),
                league_off_rating=112,league_pace=100,now=NOW)
    with pytest.raises(ValueError,match="Stale"):
        project(team("A",as_of="2026-11-18T15:00:00Z"),team("B"),
                league_off_rating=112,league_pace=100,now=NOW)

def test_rejects_insufficient_current_games():
    with pytest.raises(ValueError,match="five"):
        project(team("A",games=3),team("B"),
                league_off_rating=112,league_pace=100,now=NOW)
