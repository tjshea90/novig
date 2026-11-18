# Novig and PropLine: API and socket review, what changed in Novig's docs (NVR, Tj 2026-11-18)

Tj: "Review that both novig api and socket and propline api and socket are used correctly and to their full potential by the app and github engine and look for any changes or updates in novig api docs".
Method: the docs mirror (`api-docs` branch, workflow `api-docs.yml`, run 2026-11-18) diffed against the snapshot of the last review (2026-10-25), every changelog entry traced into the code, the OpenAPI spec's 67 operations checked against the code, and the engine's live status and log read.
Details of each Novig change: `NOVIG_API.md` section 32. PropLine: `propline_streaming_review_2026-11-17.md`, `propline_unused_features_2026-11-17.md`.

## 1. Novig: used correctly (checked, no change needed)
| Area | Evidence |
|:--|:--|
| Signing, throttles per subaccount, `/v3/limits` pacing | engine log 01:00 UTC: "Novig read-only key active: 16 reads a second, websocket on", 112 prices a second by the live feed, "Novig refused none"; stream allowance 512 refilling 32 a second read from `/v3/limits` |
| bbo for screening, full `book` where depth is needed, one connection | scan note: 1,324 markets held of room 8,144 (1,278 on bbo, 46 on book, 278 deepened) |
| Lifecycle deltas | docs changed `["GOLIVE"]` to `[{kind, status}]`; `StreamBooks.applyLifecycle` reads both; `UNLIVE` (no longer drains the book) only clears `live` |
| Private orders stream | every event now carries `outcomeId`, `price`, `originalQty`; `OrderEvents` and the `readable` check in `NovigStream` read both shapes |
| Subscribe refused as a whole (`MARKET_NOT_FOUND`) | `NovigStream.refused` halves the batch until the bad market stands alone |
| A refused order | `CloudNovigPort.refusal`: 451/401/403/423 stop ordering EXCEPT a bet lock (`betLocked`: EVENT/MARKET/LEAGUE/COMPETITOR_LOCKED), so one locked game never halts the engine; `DEPOSIT_RETURN_HOLD` is a stop, correctly |
| IOC orders | docs: an IOC that fills nothing is `REJECTED`: read as "nothing filled", never re-sent |
| Parlay price route | `/nbx/v1/parlay/request/unauthenticated` is gone (404 since 2026-10-28); `NovigParlay` uses `/rfq/v2/quote/unauthenticated`, the documented one |
| Straight-contract fee | unchanged: 0.06 x P x (1-P), live only on game markets; maker credit 0.25 (11-17 doc: dropped 10-22) |
| `*_PROTECTED` NFL/NBA twins | not priced: the scanner takes exact types from `PropStats.NOVIG_TYPES` |

## 2. Novig: what was unused or wrong, and what I built
- **Device check (`GET /v3/account/geolocation`)**: the engine learned its pass had lapsed only when an order was refused. Now `GeoWatch` reads it every 10 min (2 min once under 12 h) and tells Tj in Messages ahead of time; `status.geoNote`. A runner-address refusal is told apart (AddressWatch's), and nothing here renews or works around the check.
- **Locks (`GET /v3/exchange/locks`)**: the engine's bets and bids now skip a locked game or market (`NovigLocks` in `CloudSources.unavailable`, read every 30 s, fail-open), instead of trying and being refused.
- **`unsubscribed` notice (2026-11-12)**: Novig now drops markets that left the exchange from a lagging connection and says so in a nonce-less message; the stream ignored it, so a dead market stayed counted as held. Handled in `NovigStream.handle` (test on a mock socket).
- **Tier and open-order cap** from `/v3/limits` are read and shown in the route text. Vigilant is at Base (Tier 1 needs $100,000 of volume in a month).
- Tests: `NovigGeoLocksTest` (client shapes from the OpenAPI spec, device-check levels and when Tj is told, lock list rules), `NovigPublicClientTest` (tier), `NovigStreamTest` (dropped market). Full `:cloud :data :app` run before landing.

## 3. Novig: not built, and why
| Item | Verdict |
|:--|:--|
| RFQ v3 maker (`POST /rfq/v3/maker`) | **A business decision for Tj, not plumbing.** PropLine's trial priced Novig's two-leg parlay quote 4-8% below the fair price: that spread is the maker's. Quoting means capital at risk and a 99% confirm rate (a 5-minute lockout below it). From 2026-11-19 the parlay taker fee becomes 0.055 x (-ln P) x stake, which changes who gets what by probability. NVR6: collect the SGP log (`propline-sgp-summary`) across 11-19 first. |
| `GET /v3/catalog/consensus` (a family's main line) | A possible exact main-number pick for the NCAAB board in place of the "both sides 30-70 cents" heuristic (NVR5). Needs a signed read and board plumbing; the heuristic has no known miss today. |
| `subjectId` on outcomes | Exact team join; matching by name has no known miss. |
| `depth` (1-20) on subscribe | A snapshot holds at most the best 20 levels by default; the engine never sizes past a few levels. No change. |
| `query_locks` on the socket | The 30 s REST read costs one token; no gain. |
| Protected props | Not priced (ignored by exact type). A protected twin is a different bet: price it only with its own evidence once production lists it. |
| Tier earning, PrivateLink ($100M a month) | Out of reach by volume. |

## 4. PropLine: running now (engine log and status, 2026-11-18 01:09 UTC)
Both sockets live on one connect each since the 00:56 job started (no reconnect, no 4429), PropLine's queue delay p50 900 ms, network 54 ms, 214 book boards on the heartbeat; pushes so far: line_movement 246,383, resolution 190, ev 131, game_state 76 (11 games in play), stat_update 64, steam 6. Plan `streaming-lite` (318 of 250,000 requests used by 00:56). `game_state` and `stat_update` are journaled for the live-stat timeline. Nothing wrong found. Open: PLR5-PLR7 (settle as a fallback grader, SGP study result, `/scores` live-state REST fallback).

## 5. Open leads (TASKS.md NVR5-NVR7)
NVR5 consensus main line for NCAAB; NVR6 RFQ maker evaluation after the 11-19 fee change (needs Tj); NVR7 re-run `propline.yml trial` on 11-19/20 to compare Novig's parlay quote EV before and after the fee change.
