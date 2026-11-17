# Current NBA data-source assessment — 2026-11-17

This is a research note, **not** an authorization to scrape or ingest third-party feeds.

| Source | Access and scope | Decision |
| --- | --- | --- |
| NBA.com Stats | Official statistics, but its terms restrict regularly updated comprehensive databases without prior express consent. FAQ says stats are not available for download. | Use for human-readable spot checks and attribution where allowed; do not automate ingestion without authorization. |
| ESPN site API | Community wrappers document scoreboard and summary endpoints; no verified official license/permission for automated ingestion. | Retain offline JSON adapter; do not enable unattended scraping. |
| BALLDONTLIE | Offline adapter and synthetic tests exist. Authentic 2026–27 responses and access entitlement not verified. | Keep disabled pending authorized sample and schema reconciliation. |
| NBA Stat (nbastat.io) | Documents token-based, personal noncommercial read-only access and 10 authenticated requests per rolling hour; Public schedule explicitly lists 2026-27 and completed Nov 2026 games; authenticated player game-log coverage/completeness unverified. | API schema and season presence confirmed; do not ingest without personal token and rights review. |

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

## NBA Stat verification — 2026-11-17

- Public schedule at https://nbastat.io/schedule labels the season **2026-27** and shows completed November 2026 games. This confirms **public schedule coverage only**, not complete authenticated player-level game logs.
- API documentation at https://nbastat.io/developers/api lists `GET /api/v1/players/{player_id}/game-log?season=2026-27`, `GET /api/v1/games?season=2026-27`, and `GET /api/v1/games/{game_id}/boxscore`.
- Personal bearer token is required. Default 10 requests per rolling 60 minutes; 429 with Retry-After. Never commit tokens.
- Terms/FAQ at https://nbastat.io/faq prohibit commercial use without prior written agreement and third-party permissions.
- No authenticated requests were performed, and no authentic player game-log response has been verified. **No live source is approved for unattended ingestion.**

**Next gate:** obtain operator-approved access and scope, inspect a real authenticated 2026-27 response, and independently reconcile selected player-game totals before enabling any feed.
