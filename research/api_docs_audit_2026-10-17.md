# API docs audit, 2026-10-17 (TCL107, Tj chat t1792277336265)

Method: the engine's live `status.json` (apiCards, feedReport, scanNote), `usage.json`, the code paths, and Novig's published OpenAPI (49 routes) and changelog read today. Not done: a line-by-line re-read of the BoltOdds / PropLine / ParlayAPI docs (their notes BOLTODDS_API.md, research/propline/llms-full_2026-10-16.txt and PARLAY_API.md are current as of 2026-10-16/17).

## Novig (docs.novig.com/api-reference/changelog, two entries)
- **2026-10-15: live taker fee 0.03 -> 0.06 on EVERY straight contract** (was NCAAF/MLB/NHL only, since 10-08). Vigilant's own pricing reads each market's `fee` object, so the scan, bids and auto-bet were right; the fallback table (`MarketFee.gameFor`, used by CNO picks, tracker hand bets, sharp checks) still said 0.03 for NFL/NBA/etc. **Fixed:** `ALL_RAISED_FROM_MS` (Eastern midnight 10-15); `FeesTest` + four tests that hard-coded 0.03 updated. Still 0.03 in research-only tools (`FeedRace.FEE_C`, `Ladders`, `BurstExport` text): not money paths.
- 2026-10-14: soccer lists `TEAM_TOTAL` and `TOTAL_1H`. Matching code already knows both kinds (`BookBoard`); not yet checked whether any fair-odds feed prices soccer team totals (TASKS TCL107c).
- Route coverage: spec has `history/events|markets|positions`, `orders/batch`, `portfolio/*`, `account/*`; the app uses catalog, book, trades, limits, orders, fills, stream. `history/*` (settled markets) is unused: a possible free source of closes/results for grading (TCL107d).

## ParlayAPI
- Feeds 0 lines per scan. Cause: the key the engine uses is a Starter key at its paced share for today (10,815 of 20,000 used, 55% of the month elapsed). The second Starter key (3,385 used) is paced from the day the app first met it (Oct 15), so it too is near its share. Pacing is working as designed; I tried pacing from "now" and reverted it (it ignores what was already spent today; `CreditPaceTest` caught it).
- Real bug found: the scorecard's `surplus` flag (and the advice "using more of the plan you pay for") is computed on the aggregate (64% left vs 47% time) while the pacer works per key, so the card says "surplus" while every key is held. Needs a per-key surplus (TCL107e).

## BoltOdds
- Now feeding: 13,822 lines, 1,138 only it supplied, p90 age under 1 s, 1.1 pts off Pinnacle (the 10-17 morning review said no key: the key has since been sent). Good.

## PropLine
- Feeding 12k lines, 1.3 pts off Pinnacle; the +EV list stays retired.
