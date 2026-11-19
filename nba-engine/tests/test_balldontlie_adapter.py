import copy
import pytest

from nba_engine.balldontlie_adapter import parse_stats


def sample(*, minutes="28:30", points=19, date="2026-11-16", status="Final"):
    return {"data": [{
        "game": {"id": 123, "date": date, "season": 2026, "status": status,
                 "home_team_id": 1, "visitor_team_id": 2},
        "team": {"id": 1},
        "player": {"id": 44, "first_name": "Test", "last_name": "Player"},
        "min": minutes, "pts": points,
    }]}


def test_valid_player_game():
    row = parse_stats(sample()).iloc[0]
    assert row.minutes == pytest.approx(28.5)
    assert row.points == 19
    assert row.season == "2026-27"


@pytest.mark.parametrize("points", [1.5, -1, True, "19", None])
def test_reject_non_integer_points(points):
    with pytest.raises(ValueError, match="Points must"):
        parse_stats(sample(points=points))


@pytest.mark.parametrize("date", ["2025-11-16", "2027-09-01", "not-a-date"])
def test_reject_invalid_season_dates(date):
    with pytest.raises(ValueError, match="date"):
        parse_stats(sample(date=date))


def test_missing_minutes_are_not_zero_games():
    assert parse_stats(sample(minutes=None, points=0)).empty
    assert parse_stats(sample(minutes="", points=None)).empty
    with pytest.raises(ValueError, match="without minutes"):
        parse_stats(sample(minutes=None, points=12))


def test_reject_duplicate_player_game():
    payload = sample()
    payload["data"].append(copy.deepcopy(payload["data"][0]))
    with pytest.raises(ValueError, match="Duplicate"):
        parse_stats(payload)


def test_reject_incomplete_game():
    with pytest.raises(ValueError, match="completed"):
        parse_stats(sample(status="In Progress"))


def test_reject_wrong_team():
    payload = sample()
    payload["data"][0]["team"]["id"] = 999
    with pytest.raises(ValueError, match="Team not"):
        parse_stats(payload)


def test_documented_status_state_takes_priority():
    payload = sample(status="Final")
    payload["data"][0]["game"]["status_state"] = "pre"
    with pytest.raises(ValueError, match="completed"):
        parse_stats(payload)
    payload["data"][0]["game"]["status_state"] = "final"
    assert len(parse_stats(payload)) == 1
