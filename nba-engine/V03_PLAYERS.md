# NBA v0.3 — player scenario observations

`python -m nba_engine.players --csv player_game_logs.csv --cutoff 2026-11-17T00:00:00Z --season 2026-27 --window 10 --threshold 25`

Required CSV columns: `game_id,game_date,season,player_id,player_name,team_id,minutes,points,source,license_reference,statistical_research_permitted`.

Input must consist of actual game-level records dated strictly **before** the specified cutoff. The importer refuses future-dated observations, duplicate player-game keys, missing provenance and non-authorized data. Authorization is an operator attestation, not independent legal verification.

Output: player-level recent mean/median points, mean minutes, empirical 10th and 90th percentiles, observed fraction meeting a chosen points threshold, last observed team and game date. These are **descriptive statistics only**. They are not calibrated forecasts, predictive confidence intervals, or claims about confirmed roster availability. A trade, injury or role change may invalidate the recent sample.

No provider is connected and no 2026–27 observations are bundled. Next: verify an accessible source and its terms, ingest dated box scores, reconcile roster changes, then evaluate pregame forecasts against subsequently observed games.
