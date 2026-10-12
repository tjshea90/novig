# Novig: full use of the API, as fast as Novig allows (Tj, 2026-10-12 06:56Z)

Tj verbatim: "Build the novig bbo feature with a backup to default toggle in case it doesn't work right. Make sure vigilant app is making full use of the novig API. The app should go as fast as novig allows. Optimize the vigilant app to take full advantage of everything the API offers. If it can make scanning or live betting or any other part or function of vigilant better, do it. If you need me to do anything to install the new novig features, such as the bbo, let me know"

## What Tj's own v0.89.34 diagnostics (2026-10-11 23:18, research/uploads/2026-10-12/e36cbe37-...) say about speed

- `api.novig.com`: **713,538 calls in the day, 651,623 of them REST book reads** (`/v3/catalog/markets/{id}/book`), 4.1 GB downloaded, 613 websocket upgrades (101), 2,597 x 429 (2,302 on the public route), 130 x 451 ANONYMIZED_NETWORK.
- Every scan: "live feed held 0 of N asked at the end", "Novig prices: 318 read (318 through the key, 0 pushed)". The scan's websocket never delivered: 29 s scans for ~300 prices at 11/s through REST.
- A 429 on the websocket upgrade ("Expected HTTP 101 response but was '429 Too Many Requests'") is treated like any failure: the feed is not tried again for 5 MINUTES (`RETRY_AFTER_FAILURE_MS`), so every scan in that time reads by REST. The docs: the upgrade costs 32 `stream` tokens; 429 = bucket empty, "wait Retry-After, then retry" (seconds). Cause of the empty bucket: another connection of the same key (the burst recorder has its own `NovigStream`), or the previous scan's bulk subscribe (a request over the cap empties the bucket).
- The stream is closed 2 minutes after a scan last used it (`IDLE_CLOSE_MS`) and auto-scan cycles are ~2m10s apart: EVERY scan opens a new connection, waits for a full bucket, subscribes once, closes. Books are never held between scans.

## What the docs (re-read 2026-10-12) offer that the app does not use

| Feature | Docs | Use |
| --- | --- | --- |
| `bbo` channel | 8 tokens/market, each outcome's best bid {price, qty}, includes lifecycle | Tj's ask. Light screening feed. |
| key `/v3/limits` | stream 512 @ 32/s (was 4/s), watch cap 8,192 (was 2,048) | `tune()` reads them; Scanner still asks for <= 2,000 (`streamRoom`). |
| Persistent socket | one connection, snapshot on subscribe, deltas | Close after 2 min idle: wasted. |
| `orders` / `positions` private channels | 1 token each; `open`/`fill`/`cancel`/`reject`; "An unfilled IOC/FOK/PO arrives as `reject`, no HTTP status reports it" | `ApiBetPlacer.finish` polls REST `GET /orders/{id}` every 400 ms + fills; recovery polls fills. Push = instant result. |
| `lifecycle` (included in book/bbo) | status OPEN/CLOSED/SETTLED + OPEN/CLOSE/GRADE/START/END/GOLIVE/UNLIVE | Ignored today. |
| WS `place` / `cancel` / `cancel_all` | no TLS/HTTP setup, 1 `place` token each | Later, behind a toggle. |
| WS compression `X-Novig-WS-Compress: deflate` | raw DEFLATE binary frames | Later, with fallback. |
| book `depth` (1-20) on subscribe / REST | snapshot levels; deltas not limited | Keep 20 (a truncated local ladder goes wrong when the top levels fill). |
| `update` kind in book deltas, field names `orderId`/`outcomeId` (OpenAPI) vs `order`/`outcome` (prose) | spec vs prose differ | `StreamBooks` reads only the prose names and no `update`: accept both. |
| lifecycle delta shape: prose `["GOLIVE"]`, spec `{kind,status}` | | accept both |

## Design

1. **Parse everything both ways** (`StreamBooks`): book snapshot/delta field aliases + `update`; `bbo` snapshot `{seq, bbo:{outcomeId:{price,qty}}}` and delta `{seq, deltas:[{outcomeId, bbo:{price,qty}|null}]}`; `lifecycle` snapshot `{seq,status}` and delta (strings or objects). A bbo market is a `NovigBook` with one level per outcome and `partial = true`.
2. **Per-market channel in `NovigStream`**: BBO for screening (cheap), BOOK for markets that need depth: live-pinned, resting orders (bid desk), +EV candidates (`deepen`). `ApiGates.novigBbo` (Settings > Novig API: "Best-bid feed (bbo)") default ON; **Off = the original all-`book` behaviour**. Automatic fallback to `book` (sticky for the session, reported in Diagnostics) when: Novig refuses a `bbo` subscribe, no bbo snapshot arrives for a subscribed market within N s while book markets do, or the canary check (a `snapshot` verb with channel `book` for a few bbo markets) disagrees with the bbo state.
3. **Depth stays correct**: `NovigPublicClient.books(ids, depth = true)` serves a pushed book only if it is complete; partial (bbo) books satisfy `depth=false` screening calls only; `deepen(ids)` moves a market to `book` (stream) and the REST path reads it meanwhile. The scan deepens every market with a positive-EV quote before the Feed/auto-bet/bids use it, so results (stake, depth, ladder, bidLevels) equal the all-`book` ones.
4. **Persistent connection, honour the limits**: 429 on upgrade waits `Retry-After` (<= 30 s) not 5 min; idle close 2 min -> long while the scan/auto-scan/bids/live lanes are on; the watch cap and `streamRoom` come from `/v3/limits`; subscribes in the right order; one connection per key shared by scan, live lane, bids and the burst recorder.
5. **Private stream**: `orders` + `positions` on the same socket; `OrderFeed` keeps the latest event per orderId; `ApiBetPlacer.finish` waits for the push (REST fallback unchanged); `AutoBetRecovery`, Bid desk and Tracker sync learn fills at once.
6. **Lifecycle**: status per market to the planner, scan and bid desk (GOLIVE voids resting orders; CLOSED = no bet).
7. **Test live feed button** (Settings > Novig API): subscribes a few real markets on bbo and book, compares, reports bbo cost/latency, so Tj can verify with one tap.

## Safety rules for everything above

- Cannot be tested against a live key from the container: every protocol piece is tested against a mock server speaking BOTH the prose and the OpenAPI shapes; every new behaviour has a toggle that returns to the old one, and an automatic fallback with a Diagnostics line.
- Money paths (placement, recovery) never rely on a push alone: REST confirmation remains the fallback and the record of truth (fees come from REST fills).
- IOC limit price is the bound: a wrong top-of-book can only cost a missed fill, never a worse price.
