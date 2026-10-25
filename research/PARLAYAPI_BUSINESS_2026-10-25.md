# ParlayAPI Business: what the plan gives and how Vigilant uses it (PB10-PB11, PB15-PB16, PB19, PB21; Tj 2026-10-25)

Docs saved verbatim in `research/parlayapi_business_2026-10-25/` (llms.txt, llms-full.txt, openapi.json, docs, changelog, integrations, versioning, best practices, the Claude-agent blog post). Real answers of Tj's key
(keyless, e-mail stripped) in `research/parlayapi_business_2026-10-25/streamprobe/` and the `parlay-samples` Release (`tools/parlay.py streamprobe`, `parlay.yml` task `streamprobe`).

## 1. The plan, as the key's own `/v1/usage` says (2026-10-25 08:00 UTC)
- `tier: business`, 1,000,000 credits a month (58,361 used on the 25th), `rate_limit_per_sec: 10000`; the limits page gives a burst of 20 requests at once and no per-minute cap.
- **WebSocket and SSE are Business+ only** and are **not credit-charged** ("Subscription is tier-gated, not credit-charged", 0/msg). No concurrent-connection cap is published for Business (close code 4002 exists: "concurrent-connection cap").
- REST costs are unchanged (llms-full §3): `/odds` markets x regions (bookmakers= one region per ten books), `/props` 3, `/scores` 1-2, `/line-movement` 2, `/historical/.../closing-odds` 10, `/meta/*` 0, `/v1/bookmakers/{key}/freshness` and `/v1/meta/source-health` free. REST data refreshes every 30-60 s upstream: polling faster buys nothing.

## 2. The streams
| route | what | notes |
| :- | :- | :- |
| `/v1/sse/odds/{sport}` (= `wss://parlay-api.com/ws/odds/{sport}`) | every book's game-line and prop rows: `initial_state` (recent rows, `limit` per frame) then `odds_update` diffs "within seconds of ParlayAPI receiving a real book update", `target_delivery_s: 2` | `kinds=game,prop`, `bookmakers=`, `markets=`, `event_id=`, `max_age_s` (default 600, max 3600: older game rows are not sent), `diff=true`, `since=` (best effort), `heartbeat_s` |
| `/v1/sse/hot/{sport}` | same rows, fast-cadence books (Pinnacle + FanDuel on hot leagues, sub-3 s) | |
| `/v1/odds-drop/{sport}` | line MOVES only, `threshold` in American cents (default 10), `sports=all` for every sport on one connection | first sight of a side is silent; `tracked_sides` in heartbeats |
| `/ws/live/{sport}` | the live board (game lines + moneylines), no initial_state | `bookmakers=` gives per-book heartbeat freshness |
| `/v1/sports/{sport}/live/sse` | play-by-play (Starter+, 5 credits a connect) | already used: `ParlayPbpStream` |

Every streamed row carries `last_update` (ms: last write OR re-verify), `price_age_s` (server-computed seconds since the price last MOVED: "a frozen line never reads as fresh") and `line_changed_at_ms`. "A fast book's line that stays frozen beyond about 150 s during a commenced game is dropped from the stream." Frames also carry `hot_feed_status` (per book: `latest_ms`, `age_s`, `rows_60s`).

Row shapes seen (NBA, 2026-10-25): moneyline rows `market_key h2h|moneyline` with `home_ml`/`away_ml`; spread rows one side per row (`team`, `line`, `price`); totals as `kind: prop, player: "Total", market_key: totals` with `line`, `over_price`, `under_price`; Pinnacle rows add a `pinnacle` object (matchup id, market key `s;0;s;-7.0`, type, side, line, is_alternate, status). Event ids are `YYYY-MM-DD_Home_Team_Away_Team` (the REST board's ids: checked in `rest_odds_*.json`).

## 3. Freshness, per book
- `/v1/meta/source-health` (free): per source `poll_age_s` (when ParlayAPI last POLLED the book), `price_write_age_s`, `prices_stale`, `sla` (ok/degraded/breach/stale), `collector_lag_s` percentiles, thresholds. Already read by `ParlaySourceQuality` (via `/v1/meta/source-quality`) to leave books that aren't keeping up out of ParlayAPI's quotes.
- `/v1/bookmakers/{key}/freshness` (free): per table (`odds_snapshots`, `prop_snapshots`, `prop_snapshots_game_lines`, `period_odds_*`) `latest_iso`, `age_seconds`, rows in the last hour.
- In the stream a row's own `last_update` / `price_age_s` is the freshness: Vigilant dates each ParlayAPI quote by `last_update` (when ParlayAPI last confirmed the price at the book), the same rule as its REST board (`seenByBook`).

## 4. Why Tj saw "fair odds: median 9m 53s" (PB15)
The engine's list came from **full scans**: a scan reads every fair-odds board once at its start, then reads ~9,600 Novig books for several minutes; the fair odds of a listed bet were the board read at the scan's start, already 30 s-8 min old at the book (REST boards refresh every 30-60 s; PropLine's REST boards were dated by their read). The sockets (PropLine's moves, Novig's bbo) were held, but **nothing re-priced the list between two scans**: `Scanner.streamUpdate` (the phone's streaming engine) was never called on the engine, and the hot refresh re-reads only the soonest leagues' boards by REST every 45 s. The phone then re-read the listed bets' Novig prices every 15 s over ITS OWN connection (`CloudLink` requote) because the engine published only after each pass.

## 5. What is built (PB11 / PB19 / PB14 / PB21)
1. **`ParlayLive` + `ParlayStream` (engine):** one `/v1/sse/odds/{sport}` per scanned sport on the Business key (biggest plan first, `KeyPool`), `kinds=game,prop`, `max_age_s=600`; every row kept by (game, book, market, side, line), dated by `last_update`. Laid over ParlayAPI's REST boards exactly as `PropLineLive` is over PropLine's: a moved price replaces the board's, the quote's date moves to the stream's stamp. Reconnects with 1-2-4-30 s back-off; 402/4001 = not Business (stop until the key changes).
2. **The live lane (`CloudLiveLane`, engine):** every 3 s, the last scan's listed bets AND every priced side within 4 points of +EV are re-priced: fair odds from the boards with both streams laid over them (PropLine + ParlayAPI), Novig's price from the engine's bbo socket (`pushedTop`). Runs during scans too (it does not take the scanner's lock). A bet stays listed while it is +EV at Novig's live price on fresh fair odds; it leaves the moment a feed or Novig says otherwise; a side that BECOMES +EV (a sharp line moved, Novig did not: steam) is listed at once and handed to the trader (`sendTrades`) and the bid desk the same pass.
3. **Tandem (PB21):** the two feeds are not ranked against each other for a book they both carry: the freshest stamp wins (`Pricing.FRESHER_BY_MS`), and each fills the books and markets the other lacks (ParlayAPI: Pinnacle, Bovada, Fliff, ProphetX, Rebet, Novig itself ... ; PropLine: its 40+ US books and props). Where both carry a book's line and the prices differ by more than 3 cents of probability on a fresh stamp from both, the quote is dropped from that fair line (one feed is wrong) and counted in the status.
4. **The phone** stops its own 15 s Novig re-read while the engine's live lane runs (the engine's list is already re-priced every 3 s), and reads the list over the engine's live status socket (`liveUrl`, SSE) the moment it changes instead of polling the `cloud-feed` branch (PB16).
