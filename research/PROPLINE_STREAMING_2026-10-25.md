# PropLine Streaming Lite: what it gives and how Vigilant uses it (Tj, 2026-10-25, PSL)

Tj upgraded the PropLine key on GitHub (`PROPLINE_API_KEY`) to **Streaming Lite** ($39/mo). Docs saved verbatim
2026-10-25 in `research/propline/docs_2026-10-25/` (llms-full.txt dated 2026-10-24, llms.txt, openapi.json (same
bytes as `openapi_2026-10-24.json`), docs, recipes, api-versioning, sportsbook-accuracy; `.txt` = HTML stripped).
Earlier reads: `PROPLINE_PRO_2026-10-16.md`, `propline_streaming_lite_2026-10-12.md`,
`propline_streaming_consolidation_2026-10-24.md`.

## The plan (llms-full "Pricing & rate limits")
| | Pro (before) | **Streaming Lite (now)** |
|:--|--:|--:|
| requests a day (`X-Daily-Limit`) | 25,000 | **250,000** |
| burst / sustained (per key token bucket) | 50 / 20 a s | **100 / 35 a s** |
| in flight at once (every tier) | 20 | 20 (bound own pool at 8-10) |
| webhooks active | 0 | **5** |
| WebSocket connections | 0 | **2** |
| resolved-props export | 90 d lookback, 50 calls a day | **180 d, 100 calls a day** |
| push deliveries | - | **not metered** (neither quota nor burst) |
`/replay` and `/deliveries` do not count against the daily quota. Quota resets 00:00 UTC (hard).

## Push: one subscription = one transport
- `POST /v1/webhooks` body: `transport: "websocket"` (no `url`), `events: [...]`, filters (AND-ed; at least one of
  `filter_sport_key`, `filter_event_id`, `filter_market_key`, `filter_player_name`, `filter_bookmaker_key` is
  REQUIRED below Enterprise, else 400), `min_price_change_pct`, `min_steam_score`, `steam_props`,
  `min_books_agreeing`, `min_ev_pct`/`max_ev_pct`/`ev_fair_source`, `batch_max` (webhooks only). List filters up to
  1,000 chars; no wildcards. `GET/PATCH/DELETE /v1/webhooks/{id}`, `POST .../test`, `GET .../deliveries`, `GET .../replay`.
- Events: `line_movement` (price/point change), `line_added`/`line_pulled` (opt-in board deltas: subscribe all three
  to mirror a board), `market_suspended` (pregame, per book+event+player, `books_agreeing`; 3+ = late scratch; last
  30 min before start filtered), `steam` (one per market move, `steam_score` 0-100, `steam_props: true` adds
  NFL/NCAAF/NBA/NHL/MLB/WNBA prop steam), `resolution` (a prop graded), `ev` (push of `/ev` every ~2 min, pregame
  within 24 h, only prices the book delivered in the last 5 min), `availability` (beta, FanDuel in play).
- `filter_bookmaker_key` applies to line_movement, line_added/pulled, resolution, market_suspended, ev; steam ignores it.
- Payload of line_movement/added/pulled: `sport_key`, `event{id,home_team,away_team,commence_time}`, `bookmaker_key`,
  `market_id`, `market_key`, `market_description`, `period`, `outcome_id` (= `/odds?includeBookIds=true`'s
  `outcome_id`; stable across price and point changes for one market/side/player, except alt-ladder books whose line
  sits in the market description), `book_outcome_id`, `player_id`, `player_name`, `outcome_name`,
  `previous`/`current` {`price_american`, `point`, `liquidity`, `liquidity_updated_at`}, `reason`, `last_seen_at`, `timestamp`.
- WebSocket `wss://ws.prop-line.com/v1/stream`: first frame `{"type":"auth","api_key":K,"webhook_id":N,"since_seq":S}`;
  answers `ready` (`latest_seq`, `oldest_available_seq`, `truncated`), `event` (`seq`, `event_type`, `delivery_id`,
  `created_at`, `sent_at`, `data`), `ping` every 25 s, `heartbeat` on connect and every 25 s (per book+sport covered:
  `pregame_read_at`, `read_age_seconds`, `reread_seconds`, `events`). "While read_age_seconds stays near
  reread_seconds, we are still reading that book and no event means no change." Resume buffer 30 min;
  `truncated: true` = events lost, resync from REST. seq increases, not dense; dedupe on seq per subscription.
  Close codes: 4401 key, 4403 tier/paused, 4404 no such subscription, 4429 connection limit, 4400 bad auth or http
  subscription, 4008 we did not read for 2 min (reconnect with last seq, nothing lost).
- Latency: DraftKings, FanDuel, Fanatics, Kalshi, Polymarket are ingested from their own push feeds (receipt p50
  0.1-1.1 s, then a few seconds to write); every other book is polled: 30-45 s in play, 60-90 s pregame.

## REST additions since 2026-10-16 that matter
- `max_age=N` on both `/odds` endpoints: markets not delivered in N s are dropped server-side.
- `includePregameOnly` (default off since 10-12: books that do not price a live game are left out of it).
- `X-PropLine-Missing-Markets` header; `X-PropLine-Hint` header (use bulk `/sports/{sport}/odds` for a slate).
- `/odds/closing`: `closing_last_seen_at` (when the price was last seen, capped at kickoff); `is_stale` now means
  the book stopped offering the line > 10 min before kickoff.
- `/v1/freshness` (no auth, free): per book `staleness_seconds`, `is_stale`, `worst_staleness_seconds`,
  `oldest_market_update`, `suspended_market_count`, and `market_classes.game_lines` / `.props` with the same
  fields; top-level `stale_threshold_seconds`, `as_of`.
- `/v1/coverage/history` (when each book's archive starts), `/v1/books/accuracy` (props Brier skill by book).
- `/sports/{sport}/injuries` (+ `/history`), `/players?search=` with team, `/players/{p}/games` paging, season filters.
- SGP legs/batch/hold (Hobby+), `book_outcome_id_ontario`, new markets (`player_blocks_steals`, team first scorer,
  soccer combos), esports and European basketball leagues, Betdaq retired 2026-10-22.

## Books (bookmaker table, 2026-10-24)
Sharp / exchange keys Vigilant had NOT been asking PropLine for: `sbobet`, `bookmaker_eu`, `betfair_ex` (Betfair
Exchange), plus offshore `heritage`, `jazzsports`, `youwager`, Canadian `thescorebet`, `tonybet`, `bet99`, AU `tab_au`,
sweepstakes `rebet`, `courtside`, `sportzino`, `thrillzz`, and ~70 European/LatAm/African books (pregame, mostly
soccer). Not carried: Circa, Caesars, bet365.

## How Vigilant uses it (built 2026-10-25, see TASKS.md PSL)
See the PSL items in TASKS.md for what was built, with the tests that prove it.
