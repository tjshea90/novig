# PINNODDS_API — pinnodds.com (Pinnacle's live push feed), as verified 2026-10-08

Tj, 2026-10-08: "I was given a 3 day trial of pinnodds.com websocket … research and implement a live betting feature in vigilant". This file is the permanent memory of what the API is, the rules
it sets, what was measured on its real socket, and how Vigilant uses it. Canonical docs: **https://pinnodds.com/llms-full.txt** (plain text, ~65 KB; the HTML at /docs renders the same). Where the
docs and the running API disagree, the running API wins (the docs say so). **Never commit a Pinnodds key** (this repo is public; the key lives in the app's key store and in a gitignored `.env`).
`RESEARCH.md` §116 holds the study (does Novig lag Pinnacle, and does the edge hold up).

## 1. What it is
- Pinnacle's own push feed (its MQTT), re-served four ways: **REST** `/kit/v1/*` (snapshots), **`/api/drops`** (a queryable buffer of recent price drops), **SSE** `/odds-drop` and
  `/odds-drop-prematch` (drops ≥ 5% by default, `min_drop` floor 1%), and the **WebSocket passthrough** `wss://pinnodds.com/ws/feed` (every raw Pinnacle frame, byte-identical, in a thin envelope).
  Vigilant uses the WebSocket only; REST is used once, for the key test (`GET /panel/api/me`).
- Odds are **American** on the socket (the REST `/kit` shape is decimal). Pinnacle's vig is in them: devig before comparing (Vigilant: `engine.Devig`, default WORST_CASE = the least favourable of the four methods).

## 2. Plans, prices, the trial (docs "Plans & Pricing" and the key's own `/panel/api/me`)
| Plan | Price | REST | SSE | WebSocket |
| :- | -: | :- | :-: | :-: |
| Trial (default on signup) | $0 | 100 requests/day (20/min, 100/h) | no | no |
| **Full demo (3 days, on request, by hand)** | $0 | 10 req/s, no daily cap | yes | **yes** |
| Stream | $99/mo | 100/day | yes | not eligible |
| Pro | $99/mo | 10 req/s | no | add-on $99/mo |
| Pro+SSE | $149/mo | 10 req/s | yes | add-on $99/mo |
| Scale | $229/mo | 30 req/s | yes | add-on $99/mo |
- **Tj's key (2026-10-08): `trial_demo`, WebSocket add-on active, expires 2026-10-10 23:34Z.** After that the key keeps working as the free Trial (REST 100/day) and the socket answers `403 plan_lacks_ws`.
- **The WebSocket needs a REST-bearing paid plan plus the $99 add-on: at least $198 a month** (Pro + add-on). The SSE-only Stream plan ($99) has no raw socket. Billing is crypto only (NowPayments), no auto-renewal.
- Settings › Pinnodds live › Test key calls `GET /panel/api/me` (one request) and reports the plan, its end and `ws_addon.active/until`. It never opens the socket: **one WebSocket per account**.

## 3. The rules Vigilant follows (all from the docs)
- **One WebSocket per account** (keyed on the account, not the IP). A second connection **evicts the oldest** (`1001 "evicted by newer connection"`). Two consumers kick each other in a loop. Fan out inside the process.
  A study recorder and the app must therefore never run together. One SSE slot per endpoint is separate.
- Key: `x-api-key` header (also `x-portal-apikey`) or `?key=`. Vigilant sends the header (a URL would land in logs).
- Send `subscribe` **within 5 s** of opening, or the server closes the socket. `{"type":"subscribe","streams":["live"],"sport_ids":[1,2,…]}`; `sport_ids` ≤ 32 (the socket does not accept 14 and 15), `event_ids` ≤ 200 a stream,
  ≤ 30 subscribe messages per 10 s. A frame is delivered if it matches the sport filter **or** the event filter.
- **Heartbeat**: the server sends `{"type":"ping","ts":…,"buffered_max_bytes":N}` every 30 s (measured: 30.0 s apart); reply `{"type":"pong"}` or it closes after ~75 s (`1001 "stale"`).
  `buffered_max_bytes` is the server-side send backlog for your socket (0 = draining as fast as sent); at 128 MB you are dropped (`1011 "deregistered: slow_consumer"`, retry at once).
- **Compression: off.** `permessage-deflate` is negotiated by the *client*; it serialises frames and the docs measured a p99 of 1241 ms against 187 ms without. OkHttp offers it on every upgrade, so
  `PinnSocket` removes the `Sec-WebSocket-Extensions` request header in an APPLICATION interceptor (OkHttp does not run network interceptors for a WebSocket call; `PinnSocketTest` pins it). Max message 8 MB (2 MB minimum); snapshots are chunked ≤ 512 KB (`seq`, `final`; a single-frame snapshot has neither).
- Close codes: `1001` evicted/stale/server shutdown, `1011 deregistered:` (match the prefix; retry immediately), `1006` (an oversized frame). Reconnect 1, 2, 4 … 30 s and subscribe again (nothing is kept server-side).
- HTTP errors: 401 `missing_key`/`invalid_key`; 403 `plan_lacks_ws`/`plan_lacks_sse`; 429 `rate_limited` (+ `Retry-After`; also on connection floods); 503 `prematch_disabled`.
- Key sharing/resale is detected by connection counts per key; a phone using Tj's own key is the intended use. More connections = more accounts (email info@pinnodds.com; support @ArbitrageXpro on Telegram).

## 4. The WebSocket frames (verified on the real socket 2026-10-08, ~60 s and 6 min tapes; fixtures in `data/src/test/resources/pinnodds-frames.jsonl`)
- Server → client: `snapshot` (one per (stream, sport) at subscribe; `events[]` are full Pinnacle records), `subscribed` acks, `live` (continuous), `ping`, and for `prematch` stream: `prematch_matchups`,
  `prematch_markets`, `prematch_membership` (not used by Vigilant). `live` = `{type, sport_id, topic, op, rec, ts}`; `ts` is the hub's time in ms.
- `topic` = `matchups/reg/sp/<arcadia sport>/live/<ch>` for a live matchup or `…/<sp>/pre` for a prematch one (`spc` = specials). The **channel** is the last segment:
  - `ld` (live_delay) = **the price**, the source of truth (12% of frames on a busy night);
  - `both` = ld and dz combined (19%, mostly Pinnacle's special books such as corners);
  - `dz` (danger_zone) = a volatility signal: a goal threat, a break point, an imminent suspension. **Not a second price**: merging it with ld makes prices "spike and revert". 3% of frames. Vigilant marks the
    matchup volatile for 3 s and never reads a price from it;
  - `pre` = a prematch price update on the same stream (65% of frames; the prematch book moves constantly).
- Measured (this container, through the agent proxy, 6 min, 6,863 pre + 3,628 live frames): **~51 frames/s over all 13 sports** at the evening peak; `recv - frame.ts` p50 17–22 ms (ld/both/dz) and 47 ms (pre),
  p99 ≈ 1.8–2.0 s (the proxy path; the docs' own p99 is 187 ms without compression). Live snapshot sizes at 00:50Z: soccer 168 live matchups, tennis 96, hockey 19, basketball 16, football 2, baseball 2.
- A `rec` is a Pinnacle **matchup**: `id`, `parentId`, `isLive`, `status`, `startTime`, `league{name,sport{id,name}}`, `participants[]{name,alignment home|away,state{score,…}}`, `parent{participants[]{state{score,scoreByQuarter}},state{quarter,timeRemainingInQtr}}`,
  `state` (soccer `{state,minutes}`), `periods[]{period,status open|closed|settled,cutoffAt}`, `units` ("Regular"; "Corners" etc. for special books), `liveMode` (live_delay|danger_zone|both), `markets[]`.
  **Scores and the clock ARE on the socket**: a soccer/tennis child matchup carries `participants[].state.score`; basketball/hockey carry it on `parent.participants[].state` with `parent.state.quarter/timeRemainingInQtr`.
- A `markets[]` entry: `{matchupId, version, key, period, status "open"|…, type "moneyline"|"spread"|"total"|"team_total", isAlternate, side (team totals), cutoffAt, prices[]{designation home|away|over|under|draw, price (American), points}, limits[{type "maxRiskStake", amount}]}`.
  Keys: `s;<period>;m` moneyline, `s;<period>;s;<home handicap>` spread, `s;<period>;ou;<line>` total, `s;<period>;tt;<line>;<home|away>` team total. Period 0 = the game. The same fixture has a parent matchup and re-issued
  live children, and the same key can appear under two matchup ids (one stale): **key books by `rec.id` + market `key`, never by team names**.
- **Ordering/dedupe: use `markets[i].version`, not `rec.version`** (Pinnacle freezes `rec.version` once a match is in play). Pinnacle re-sends unchanged markets often (more than half of updates): skip versions already applied.
  A market with `status != "open"` is a close; `periods[n].status` closed/settled closes all that period's markets; `op:"del"` removes the matchup; `op:"upd"` carries only changed markets (merge by key).
- An older shape (prices with `participantId` instead of `designation`, no `status`/`version`) appears on prematch rows: Vigilant does not read it.

## 5. How Vigilant uses it (v0.76.0; code `data/.../pinnodds/`)
`PinnSocket` (the one connection) → `PinnBook` (state, devig, history) → `LiveMatcher` (Pinnacle matchup ↔ Novig game ↔ markets) → `LiveEdge.judge` (the rule) → `PinnLiveTrader` (one `IOC` order, caps, journal, Tracker)
inside `PinnLiveRunner` (one consumer coroutine; ticks every 100 ms; follow-ups at 30 s and 120 s). Settings › **Pinnodds live**: key, Test key, feed switch (paper), "Place real bets" switch, limits.
- **The rule** (all must hold): Pinnacle line open, game live (or pregame if on), no `dz` frame in the last 3 s, price unchanged ≥ 0.5 s, Pinnacle margin ≤ 9%, Pinnacle's own limit ≥ $100, fair in [8%, 92%],
  the trigger (default **a score-driven move**: Pinnacle's fair for the side rose ≥ 3 points within 20 s AND the game's score changed in the last 20 s; alternatives: any move, or any steady edge), EV after Novig's in-play
  taker fee ≥ 5% at the ask, ≥ 20 contracts on offer at ≥ 5%. Main lines only, half-point spreads/totals only, full game only. One bet per Pinnacle move per outcome. Orders are `IOC` at the worst price that still clears the EV,
  never resting, sized by the stake and the caps. The defaults come from the replay in RESEARCH.md §116.
- Real bets are OFF by default (the feed alone is PAPER: it journals what it would have bet). Every decision, real or paper, is followed up 30 s and 120 s later with Pinnacle's fair and Novig's ask
  (`files/pinn-live/pinn-live-<day>.jsonl`, `pinn-follow-<day>.jsonl`); Diagnostics prints the verdict (`PinnReport`).

## 6. What is NOT known (update when verified)
- Novig's **in-play order delay** and whether `IOC` is accepted on every live game line: no real in-play order has ever been sent by this app (this container has no Novig key). The first real order is the test.
- Whether the edge is real: RESEARCH.md §116. Replay over 52 minutes of tape (12 games): at the app's defaults score-driven moves were +6.2% against Pinnacle's own fair two minutes later (n=20) and price-only moves -2.7% (n=24); the sample is small and the outcome (win/loss) of a bet is not in it.
- Pregame: `pre` frames give Pinnacle's prematch moves in real time; whether Novig's pregame quotes lag them is untested (off by default).
