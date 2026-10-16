# PropLine Pro: what the tier unlocks and how Vigilant uses it (TCL56, Tj 2026-10-16)

Source: https://prop-line.com/llms-full.txt, saved verbatim as `research/propline/llms-full_2026-10-16.txt` (170 KB, "Last updated 2026-10-16"). Read in full on 2026-10-16. **Nothing here has met Tj's Pro key yet.**

## Tiers and limits (docs, "Pricing & rate limits")
| Tier | $/mo | requests/day | burst / sustained | history depth | notable |
|---|---|---|---|---|---|
| Free | 0 | 1,000 | 10 / 5 per s | none | all sports, real-time odds, scores. History/results/analytics REDACTED (200 with nulls + `redacted: true`, not 403) |
| Hobby | 9 | 5,000 | 20 / 10 per s | 30 d | + /odds/history, /odds/closing, /movement, /ev, /ev/calc, /best-line, /projections, /results, /clv/grade, /sgp, /trends, player /history |
| **Pro** | 19 | **25,000** | **50 / 20 per s** | **90 d** | + bulk CSV export `/v1/exports/resolved-props` (50 calls/day, 1 per sport), priority support |
| Streaming Lite | 39 | 250,000 | 100 / 35 | 180 d | + webhooks/websocket push (2 sockets) |
Also: at most 20 requests in flight per key (21st = 503 Retry-After 1; use 8-10). Quota headers on EVERY authenticated response: `X-Daily-Limit`, `X-Daily-Used`, `X-Daily-Remaining`, `X-Daily-Reset` (unix s, 00:00 UTC hard reset), `RateLimit-*`. 429 `daily_limit_exceeded` / `burst_limit_exceeded` (+`Retry-After`). So **the plan of a key = its `X-Daily-Limit`** (25,000 = Pro): readable from any one cheap call (`GET /v1/sports`).

## Endpoints that matter (all `https://api.prop-line.com`, header `X-API-Key`)
- `/v1/sports/{sport}/events`, `/odds?markets=&bookmakers=` (bulk game lines, 1 call per league), `/events/{id}/odds?markets=` (per-game props, up to 500+ markets), `/events/{id}/markets` (discovery, free).
- Params on both odds endpoints: `bookmakers`, `max_age=N` (drop prices older than N s), `includeBookIds`, `includeLinks`, `includeDepth`, `period`, `includePregameOnly`. Per outcome: `last_change_at`, `last_seen_at`, `liquidity` (exchanges incl. **Novig**), `player_id`, `outcome_id`.
- **`novig` is a PropLine bookmaker key** (P2P exchange): its prices, liquidity and `liquidity_updated_at` are in `/odds`, `/ev`, `/best-line` (props refreshed 15-45 min, game lines every cycle).
- `/events/{id}/ev?markets=&bookmakers=&devig=&fair_source=&max_age=` (Hobby+, cached 45 s): per line `fair_source` (pinnacle > polymarket > kalshi > bovada, or `consensus`), `fair_probs`, per book `ev_pct`/`is_plus_ev`. `bookmakers=novig` = the +EV at Novig. `/ev/calc` scores your own price.
- `/events/{id}/best-line` (best price across books, `all_prices` sorted, `last_update`), `/movement` (opening vs latest, steam), `/projections`.
- **CLV:** `/events/{id}/odds/closing?markets=&opening_window=` (closing + opening price/point/age per book/outcome, `is_stale` if the close was captured 10+ min out) and **`POST /v1/clv/grade`** (up to 500 bets: `{ref, sport_key, event_id, market, bookmaker, selection, side, point, price, stake}` -> closing price, `closing_fair_prob` de-vigged from the sharpest book (Pinnacle first), `clv_pct` (price vs price), `ev_vs_close_pct` (honest: vs the de-vigged close), `beat_close`, `resolution`, `actual_value`; summary `avg_clv_pct`, `avg_ev_vs_close_pct`, `beat_close_pct`, `profit_units`; fail-closed `unmatched_reason`).
- **Resolution:** `/events/{id}/results?markets=&bookmakers=` (won/lost/push/void + `actual_value`, per outcome incl. closing), `/events/{id}/stats` (free, raw box score), `/scores` (free), `/events/{id}/context` (free: pitchers, weather, umpire).
- **Players:** `/players?search=` (player_id), `/players/{name or id}/history?market=` (graded prop history, Hobby+), `/players/{..}/trends` (over/under over last 5/10/20/50, streak, avg_actual: Hobby+), `/players/{..}/games` (free raw game log + H2H).
- **Export (Pro):** `GET /v1/exports/resolved-props?sport=&market=&bookmaker=&since=&until=` streamed CSV (gzip OK), 50 calls/day, 90-day lookback on Pro, archive starts April 2026; every row has closing AND opening price/point/time, `outcome_id`, `player_id`, `resolution`, `actual_value`; two canary `(Watermark)` rows per export to filter out. Headers `X-PropLine-Archive-Starts`, `X-PropLine-Export-Daily-Remaining`.
- Free: `/books/accuracy` (Brier of each book's props), `/coverage/history`, `/markets/hit-rates`, `/freshness`, `/ids` (event id crosswalk incl. `espn_event_id`, `mlb_game_pk`).
- Push (Streaming only): webhooks/websocket (`line_movement`, `ev`, `steam`, `resolution`...). Not Pro.
Market keys: MLB `pitcher_*`/`batter_*`; **NBA** `player_points|rebounds|assists|threes|steals|blocks|turnovers|points_assists|points_rebounds|points_rebounds_assists|rebounds_assists|double_double|triple_double`; **NHL** `player_goals|first_goal|goals_2plus|shots_on_goal|points_1plus|points|assists|goalie_saves|blocked_shots|power_play_points|hits`; NFL/NCAAF `player_pass_yds ...` (already mapped); soccer `anytime_goal_scorer ...`. Sport keys `baseball_mlb basketball_nba basketball_wnba hockey_nhl football_nfl football_ncaaf tennis golf mma_ufc soccer_*` (aliases of the-odds-api accepted).

## What Vigilant did before (audit, 2026-10-16)
`data/.../reference/PropLineClient.kt` + `PropLinePropsSource` use ONLY `/events`, `/odds` (game lines) and `/events/{id}/odds` (props) as one more fair-line source. Gaps found: (1) props mapped for NFL/NCAAF, MLB, WNBA only, **no NBA, no NHL** (Novig lists both); (2) budget sized for the free key (1,000/day, 4 requests/s, `propLineGamesPerScan` 12); (3) key order = the order Tj typed (no paid-first); (4) no use of `/ev`, `/best-line`, `/odds/closing`, `/clv/grade`, `/results`, `/movement`, `/trends`, exports: every one is redacted on a free key.

## Design (TASKS.md TCL56)
1. **Plan detection + paid first**: the key's `X-Daily-Limit` is its plan (already fed to the usage meter); PropLine keys with a limit above 1,000 go first (as ParlayAPI's paid key does); the engine logs `PropLine plans: #1 pro 25000 ...` at start and hourly.
2. **Pro budget**: pacing from the real daily limit (25,000), 10 req/s (under the 20/s sustained), up to 8 in flight, shorter reuse (fresher odds), props for NBA and NHL.
3. **Archive (free, Actions)**: `propline-archive.yml` pulls `/exports/resolved-props` once a day per sport (and the full 90 days at first) into `propline-archive-YYYY-MM-DD` Releases (permanent), so the graded archive is Tj's even after the subscription ends.
4. **CLV on every bet**: `/clv/grade` over the engine's saved bets (shown or not) and the phone's bets, in batches of 500; results to a Release + a digest table (beat-the-close %, ev_vs_close by type/book/stars) that Claude reads when tuning.
5. **Grading**: `/results` as one more source for prop grading and for the engine's trade settlement (box-score actual_value).
6. **+EV section in the app**: the engine asks `/ev?bookmakers=novig&max_age=300` per pregame event, publishes the lines, the phone shows them in a PropLine +EV screen.
7. **Research feeds for Claude**: `/movement` steam, `/best-line` at Novig, `/trends` and `/history` hit rates, `/books/accuracy`, saved as small daily files in `research/propline/`.
