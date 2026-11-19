# S2S Sigma NBA v0.3 — current-state research prototype

The v0.3 module `nba_engine.current` is an **offline calculator**, not a live NBA feed, trained prediction model, or validated probability model.

## Input contract
- Current-season team offensive/defensive rating (points per 100 possessions), pace (possessions per 48 minutes), game count and timezone-aware snapshot timestamp.
- Current league offensive rating and pace, from the **same date and statistical definition** as team ratings.
- Optional *measured* lineup deltas in points per 100 possessions, and an independently measured home-court advantage in points per game.
- A confirmed lineup flag, which does not imply that missing lineup effects are zero.

## Math
Possessions = average of both teams' recent pace.
Home efficiency = home ORtg + away DRtg - league ORtg + supplied lineup adjustments.
Away efficiency = away ORtg + home DRtg - league ORtg + supplied lineup adjustments.
Expected points = possessions × efficiency / 100, with half the home-court point adjustment added to home and subtracted from away.

This is a transparent heuristic requiring empirical testing, **not** a validated predictive model. League pace is accepted as a consistency reference but not used in the current pace formula.

## Live data sourcing gate
No production scraper or source connection is enabled yet. Before fetching official NBA endpoints:
1. Verify official published source terms, robots restrictions and permitted automated access. Do not bypass rate limits or anti-bot controls.
2. Capture provenance: source URL, timestamp, season, competition stage, units and player/team identifiers.
3. Separate confirmed injuries and lineups from unconfirmed projections; never invent missing statuses.
4. Test current-season coverage, freshness, player movement, roster reconciliation and changes in stat definitions.
5. Evaluate next-game forecasts with pre-tipoff snapshots only, and compare against recent baselines and observed outcomes and recent naive statistical baselines.

## Safety / scope
- No historical-season team ratings are silently substituted for current ratings.
- No probability or quantitative scenario viability is output until predictive uncertainty is validated.
- Production Render, Supabase and main branch remain untouched.
