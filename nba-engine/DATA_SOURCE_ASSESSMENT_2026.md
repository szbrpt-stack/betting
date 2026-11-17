# Current NBA data-source assessment — 2026-11-17

This is a research note, **not** an authorization to scrape or ingest third-party feeds.

| Source | Access and scope | Decision |
| --- | --- | --- |
| NBA.com Stats | Official statistics, but its terms restrict regularly updated comprehensive databases without prior express consent. FAQ says stats are not available for download. | Use for human-readable spot checks and attribution where allowed; do not automate ingestion without authorization. |
| ESPN site API | Community wrappers document scoreboard and summary endpoints; no verified official license/permission for automated ingestion. | Retain offline JSON adapter; do not enable unattended scraping. |
| BALLDONTLIE | Offline adapter and synthetic tests exist. Authentic 2026–27 responses and access entitlement not verified. | Keep disabled pending authorized sample and schema reconciliation. |
| NBA Stat (nbastat.io) | Documents token-based, personal noncommercial read-only access and 10 authenticated requests per rolling hour; current-season coverage unverified. | Candidate for controlled evaluation only after account/terms confirmation. |

References:
- https://www.nba.com/termsofuse
- https://www-nng.nba.com/stats/help/faq
- https://nbastat.io/developers/api
- https://sportsdataverse-py.sportsdataverse.org/docs/0.0.73/nba/reference/site

## Acceptance gates before current-season statistical projections
1. Explicitly document permitted source, attribution, retention and rate limits.
2. Verify authentic response structure; do not use synthetic fixtures as proof of ingestion.
3. Cross-check a sample of player minutes, points, game date, team and game ID against independently published box scores.
4. Detect partial pages, duplicate games, missing minutes and DNP before writing player logs.
5. Keep provenance, retrieval timestamp, source season and snapshot cutoff.
6. Run statistical viability only on validated current-season records; never silently substitute historical seasons.

**Current state:** no live ingestion enabled and no authentic 2026–27 payload validated.
