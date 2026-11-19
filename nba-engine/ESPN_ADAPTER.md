# ESPN NBA offline JSON adapter

The module `nba_engine.espn_adapter` parses **previously obtained** ESPN-style scoreboard and game-summary JSON into `games.csv` and `player_games.csv`. It makes **no network requests**, bypasses no access restrictions and schedules no collection. ESPN response formats are undocumented and may change.

Example (use only JSON files obtained and retained under applicable terms):
```bash
python -m nba_engine.espn_adapter \
  --scoreboard saved_scoreboard.json --summary saved_game_summary.json \
  --source-reference "ESPN JSON, acquired YYYY-MM-DD" \
  --rights-reference "documented permission/reference" \
  --research-permitted --out outputs/espn
```

`--research-permitted` is an operator attestation, **not proof of a license**. Confirm that obtaining, storing and processing the source data is permitted before use.

The adapter expects a scoreboard containing one completed game and a matching box score. It rejects duplicate IDs, missing scores, inconsistent team IDs and malformed played-athlete records. It excludes explicitly marked DNP players rather than treating absence as zero production. `player_games.csv` can be fed to `nba_engine.players` for descriptive recent-game scenarios once multiple authorized games have been consolidated.

**Not implemented:** live ESPN fetching, automated collection, player availability or injury confirmation, roster reconciliation, season-wide ingestion, predictive intervals or calibrated probabilities. Next gate is validating a real permitted sample and reconciling its schema against the offline adapter.
