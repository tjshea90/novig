# Can PropLine's Streaming tier replace other APIs? (Tj, 2026-10-24, screenshot of PropLine's pricing: Streaming $79/mo)

Tj: "If I consolidate my budget into this propline plan in the screenshot, can it replace a lot of my other apis? Consider the sports books it offers and its features and accuracy. Is it a good purchase?"

## What the tiers add (PropLine llms-full.txt, api-docs mirror, read 2026-10-24)
| Tier | $/mo | requests/day | burst / sustained | push |
|:--|--:|--:|:--|:--|
| Pro (Tj has it) | 19 | 25,000 | 50 / 20 per s | none |
| Streaming Lite | 39 | 250,000 | 100 / 35 per s | `line_movement` + `resolution` by webhook (5) or WebSocket (2) |
| Streaming | 79 | 1,000,000 | 200 / 50 per s | webhook (10) or WebSocket (5), 365-day CSV export |
Push deliveries are not metered. The engine runs on a GitHub runner with no public URL, so it would use the WebSocket (webhooks need a reachable server). It needs ONE socket.

## Books (docs' bookmaker table)
Sharp / exchange: **Pinnacle** (game lines and props, with its posted max stake), **BookMaker.eu**, **SBOBET**, **Betfair Exchange**, **Betdaq**, BetOnline/LowVig, BetUS, **Kalshi, Polymarket, Polymarket US, Novig, ProphetX, SX Bet, Matchbook, Smarkets**. Soft US: DraftKings, FanDuel, BetMGM, BetRivers/Unibet, Fanatics, Hard Rock, Fliff, theScore, ESPN-era books, Bovada; DFS apps; ~90 European/Latin American/African books (mostly soccer, pregame only). **Not carried: Circa, Caesars, bet365** (the docs list bet365 and Caesars as books it does not poll).

## Measured on the engine (apiq, 21-23 Oct, medians per scan while on)
PropLine: 12,484 lines fed, 926 that no other feed had, p90 quote age 5 min, 0% failing. SGO: 7,382 / 182 / 8 min. SharpAPI: 5,962 / 31-67 / 3 min. BoltOdds: 1,404 / 8 when working. ParlayAPI: 724 / 2. Engine orders by kind: player props +25% ROI (137 settled), the feed PropLine leads.

## What it could replace
| API (list price) | Replace with PropLine Streaming? | Why |
|:--|:--|:--|
| SharpAPI ($399 + $99 stream + $79 game state) | **Yes** | Its value is a fresh Pinnacle plus Bookmaker/SBOBET; PropLine carries all three, and push removes the 5-minute poll age. Loses Circa only. |
| BoltOdds ($219-349) | **Yes** | Soft books pushed: PropLine pushes the same books' moves. BoltOdds has no sharp book and is off now. |
| ParlayAPI (~$25-45) | Mostly | Pinnacle alternates, periods and injuries are in PropLine; its live play-by-play stream (live lane) is not (PropLine refreshes box scores ~90 s, "not for play-by-play"). |
| SportsGameOdds ($299+) | **Not yet: test first** | 182 lines a scan nobody else has (Circa, Caesars, bet365 among them) and most of the engine's closing lines. PropLine has `/odds/closing` (Pinnacle-anchored). Run both two weeks, then drop SGO if the lost lines are few and the closes match. |
| Pinnodds (phone live, $198) | No | The live lane needs Pinnacle in play within 5 s; PropLine is a poller underneath. |
| Kalshi, Polymarket, SX (free) | Keep direct | Their own sockets are fresher than any relay, and free. |

## Verdict
- **Buy Streaming Lite ($39), not Streaming ($79).** Lite has push and 2 sockets; the engine needs one socket and uses ~7,000 of Pro's 25,000 requests a day. Streaming's extra 750,000 requests, 5 sockets and 365-day export would sit unused.
- With Lite, SharpAPI need not be bought and BoltOdds can be cancelled; SGO can follow after a two-week overlap. At list prices that is roughly $39 instead of ~$800+ a month.
- Risk: one provider for most fair odds. The free feeds (Kalshi, Polymarket, SX, PinnWire) stay on as a backstop, and the API manager already handles an outage.
- Work needed: the engine has no PropLine WebSocket client yet (`line_movement` events merged into the PropLine board, like `SharpApiStream`). Built once a Streaming key is on GitHub (task PS1); until then Lite behaves like Pro with more requests.
