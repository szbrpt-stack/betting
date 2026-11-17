"""S2S Sigma v0.3: transparent, current-state NBA matchup calculator.

No inferred player impacts, injury status, odds, or live data.
All team ratings must be supplied as recent, timestamped observations.
"""
from dataclasses import dataclass
from datetime import datetime, timezone


@dataclass(frozen=True)
class TeamState:
    team: str
    as_of: str
    games: int
    offensive_rating: float
    defensive_rating: float
    pace: float
    # Measured, lineup-specific change in team points per 100 possessions.
    # Caller must supply independently estimated on/off adjustments; zero means
    # no measured adjustment, NOT evidence that absences have no effect.
    offense_lineup_delta: float = 0.0
    defense_lineup_delta: float = 0.0
    lineup_confirmed: bool = False


def _timestamp(value):
    dt = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if dt.tzinfo is None:
        raise ValueError("as_of must include a timezone")
    return dt.astimezone(timezone.utc)


def project(home: TeamState, away: TeamState, *, league_off_rating: float,
            league_pace: float, home_court_points: float = 0.0,
            max_age_hours: float = 48, now: str | None = None):
    """Project expected points, not a probability or betting recommendation.

    Ratings are points per 100 possessions. Home-court adjustment is points
    per GAME and must be empirically estimated outside this module.
    """
    current = _timestamp(now) if now else datetime.now(timezone.utc)
    if home.team == away.team:
        raise ValueError("Teams must differ")
    if home.games < 5 or away.games < 5:
        raise ValueError("At least five current-season games required per team")
    if max_age_hours <= 0:
        raise ValueError("max_age_hours must be positive")
    for team in (home, away):
        age = (current - _timestamp(team.as_of)).total_seconds() / 3600
        if age < 0 or age > max_age_hours:
            raise ValueError(f"Stale or future team snapshot: {team.team}")
        if not (70 <= team.pace <= 125):
            raise ValueError(f"Implausible pace: {team.team}")
        if not (70 <= team.offensive_rating <= 150 and 70 <= team.defensive_rating <= 150):
            raise ValueError(f"Implausible rating: {team.team}")
    if not (70 <= league_off_rating <= 150 and 70 <= league_pace <= 125):
        raise ValueError("Invalid current league reference")
    if not (-15 <= home_court_points <= 15):
        raise ValueError("Invalid home-court adjustment")
    # Pace is a symmetric matchup estimate centered on the current league.
    possessions = (home.pace + away.pace) / 2
    # Ratings are adjusted around a supplied current league offensive average.
    home_pp100 = (home.offensive_rating + home.offense_lineup_delta
                  + away.defensive_rating + away.defense_lineup_delta - league_off_rating)
    away_pp100 = (away.offensive_rating + away.offense_lineup_delta
                  + home.defensive_rating + home.defense_lineup_delta - league_off_rating)
    # Additive deviations from league offense are a heuristic, not a
    # validated league-wide estimator. Do not label as predictive accuracy.
    home_points = possessions * home_pp100 / 100 + home_court_points / 2
    away_points = possessions * away_pp100 / 100 - home_court_points / 2
    return {
        "home": home.team, "away": away.team,
        "estimated_possessions": round(possessions, 2),
        "expected_home_points": round(home_points, 2),
        "expected_away_points": round(away_points, 2),
        "expected_total": round(home_points + away_points, 2),
        "expected_home_margin": round(home_points - away_points, 2),
        "lineups_confirmed": home.lineup_confirmed and away.lineup_confirmed,
        "win_probability": None,
        "betting_viability": "unavailable_without_verified_odds_and_validated_uncertainty",
        "method": "league-centered offense/defense deviations, pace, measured lineup adjustments",
        "cautions": [
            "No automatic injury or player-status inference",
            "Missing lineup effects must not be treated as zero impact",
            "No probability until residual uncertainty is validated on recent out-of-sample games",
        ],
    }
