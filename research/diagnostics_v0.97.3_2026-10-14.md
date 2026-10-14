# Tj's v0.97.3 diagnostics (2026-10-13 20:38 local = 2026-10-14 00:38Z; archived in research/uploads/2026-10-13/) and what was done (v0.98.0)

## Read first: every socket was OFF in this file
Settings › APIs › Sockets: BoltOdds `REST snapshot (socket off)`, Kalshi `socket off`, Novig `0 pushed` (live feed asked for 649, held 0). Everything below measured the **request** mode, not the socket mode (the v0.97.0 file at 6:47 PM had them on: Kalshi 45,000 pushes, 0 gaps). Diagnostics now says so in a SOCKETS section and a WARN health check.

## Why the last scan took 116 s
769 Novig prices through the key at **6.7 a second of the key's 16** (115 s). The paper lab made **19,949 of the 29,593 Novig book reads (67%)**, scans 7,527, CNO live prices 2,031. Fix: the lab waits while a scan runs or a bet is held (`LabRecorder.busy`; Diagnostics counts the waits). With the Novig socket on most of those reads are pushes.

## Novig public-route refusals (1,186 of 7,780 public calls, Retry-After 1)
The public edge limit is per IP. Single-market lookups (`market()`: the lab's grading, CNO live prices, the settler), `event()` and `trades()` went through the public gate but a 429 did NOT slow it (only book reads did), so the next lookups were refused too; the same markets were asked for again and again. Fixed: a 429 pauses and slows the public pace and a lookup is retried once; a market looked up is remembered 30 s (a 404, 2 min); the connection list keeps the endpoint's last word (`/markets/{id}`, `/{id}/book`, `/{id}/trades` were one line `/markets/…`).

## BoltOdds refresh 17 s p50 / 133 s max
Socket off: the streaming engine counted every REST snapshot's games as "changes" (28,435 signals) and re-ran a pass per snapshot: a self-feeding loop of passes. Fixed: with BoltOdds' socket off the engine stands down (it streams pushes). The v0.97.3 classification memo (v0.97.3) is for the socket-on case; not yet measured on the phone.

## PropLine "no props"
Not an outage: in the v0.97.0 file the props feed was on STANDBY behind better feeds (ApiChain). In this file it answered: 769 prop quotes, 3 boards, median age 141 s. PropLine is not fresh for LINES: p90 19 min (DraftKings 1,326 s, BetRivers 1,485 s), 1.6 pts off Pinnacle, so the measured ranking puts ParlayAPI (p90 3-9 min here) and SportsGameOdds (5 min) ahead of it; the static PropLine-first order only breaks ties.

## Heap: fine now (163 of 512 MB, 32%)
maker.json fell from 22 MB to 7 MB (v0.97.2's 6 h keep), oddspapi_free_cache is gone from the findings. GC still 35% of a core in a scan (the request-mode scan allocates 769 books + boards).

## The wallet (Tj's screenshot: $73.31, 32 bids up = $4.55)
7,529 bids posted in 24 h, 784 bid-hours (about 33 up at a time), 72 filled; one bid is $0.14 on average tonight (1/16 Kelly of a 2-4% edge on $185) with 5 games on Novig. Supply and stake size both limit it. Built (v0.98.0): **Bids › Keep the wallet working** (default on, target 90%): while resting bids are under the target, every new bid's stake scales up (at most 4x, never over ¼ Kelly for its own edge or the per-bid maximum), and if bids that pass every rule still cannot reach the target, riskier ones fill the gap: games up to 36 h out, a sharp book required, margin 4 points wider (so a higher edge), half the stake, last in and first out. Diagnostics WALLET DEPLOYMENT says what was wanted and what limits it.

## Other numbers
- Bids: 350 fills, CLV +2.3% (309 closes, 75% beat), results +$0.92 on $410 staked (283 settled). By kind: team totals ROI +23% (70), props -6.5% (192), NFL -17.2% (101), NHL +10.2% (76). 2% picked off.
- Results vs edges 2.7 SD below promise (1,386 bets: expected +64.23, actual -84.56) with CLV +2.3% within 10 h: the closes say the edges are real; the shortfall is concentrated in props overs (already blocked by Profit-proven) and NFL.
- cycle p95 43 s, slowest step CNO 35 s p95 (game pages, 6,626 reads/day, 926 ms first byte): unchanged.
- Fair-odds time 24 s per scan: Unabated props 24 s (one request per 5.2 s by its free plan), BoltOdds props 12 s, ParlayAPI 12 s.
