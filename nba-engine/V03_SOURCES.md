# NBA 2026–27 — authorized data intake

The importer `python -m nba_engine.import_current` accepts an operator-supplied CSV. **It does not scrape NBA.com, retrieve injury reports or imply a provider has granted betting-use rights.**

Required columns:
`team,as_of,season,games,offensive_rating,defensive_rating,pace,source,license_reference,betting_use_permitted`

Example invocation (with an actually authorized, current-season CSV):
```bash
python -m nba_engine.import_current --csv authorized_2026_27.csv \
  --home TEAM_A --away TEAM_B --season 2026-27 \
  --league-off-rating 113.2 --league-pace 99.4
```
The league values above are **illustrative placeholders**, not verified 2026–27 measurements. Replace with current licensed values before running.

The source and license reference fields are audit metadata, **not legal verification**. The explicit permission flag is an operator attestation; an independent rights review is still necessary. No live player-level feed is connected. Lineup status defaults to unconfirmed, and no missing-player adjustments are invented.

Next integration milestone: find a data provider or dataset with documented authorization for this project's intended betting-analysis use, including player box scores, rosters, injuries and timestamps. Preserve source provenance and only then implement a read-only ingestion adapter.
