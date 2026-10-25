# PropLine + ParlayAPI streams: the audit (PS1-PS12, Tj 2026-10-25 ~14:25 UTC)

Tj: "Audit the two new streaming sockets - propline and parlayapi" (nine points, verbatim in INBOX.md), then "https://parlay-api.com/cookbook" and "make sure both parlayapi and propline are both logically
used together and both are used to their full ability but don't interfere with each other. They should compliment each other".
Docs read: PropLine `research/propline/docs_2026-10-25/` (llms-full, recipes, sportsbook-accuracy, openapi), ParlayAPI `research/parlayapi_business_2026-10-25/` (llms-full, openapi, cookbook).

## What each point found and what changed

| # | Question | Finding | Done |
| :- | :- | :- | :- |
| 1 | Full speed and ability | PropLine: both sockets Streaming Lite allows (2), resume by `since_seq`, heartbeat confirmation, filters as documented; REST paced at 25/s, 10 in flight (docs: burst 100 / 35 a second, 20 in flight). ParlayAPI: one `/sse/odds` per sport, but its fast-cadence `/sse/hot` (Pinnacle + FanDuel, sub-3 s, free) was not used, and **the ParlayAPI stream moved nothing onto the boards all run** (status: "0 markets moved, 0 books' polls known"). | `/sse/hot` per sport beside `/sse/odds` (`ParlayStream`, `ParlayStreamTest`). Root cause of the dead overlay: the scan merges a game under the FIRST feed's team names and orientation (often Kalshi's "Brooklyn", sometimes flipped), and ParlayLive rebuilt its key from those; quotes are now tagged at parse time in ParlayAPI's own names (`ParlayLive.tag`, `ParlayLiveTest` "renamed and flipped"). Source-health failures are now logged with a 60 s timeout (it never reported why it read nothing). |
| 2 | Why so few books on a bet | Not a matching bug: moneylines are priced by a median **14** books, spreads/totals **5**, because Novig lists many alternate numbers that few books post (NBA spreads: 29 books at Novig's number, 87 at any number). PropLine was asked for 22 of its ~100 books. | 13 books added under documented desks; measured same-desk pairs grouped (`research/propline_coverage_2026-10-25.md`, `BookCompanyTest`). |
| 3 | Grade Tj's real bets with `/clv/grade` | Not built. | `tools/propline_grade.py` + `propline.yml` task `grade` (daily after the archive): every hand bet, phone auto-bet, imported Novig fill and the engine's bets and bids, graded by PropLine's de-vigged close (`ev_vs_close_pct`, the honest number), by who placed it, kind and league -> Release `propline-grade`. First runs: a missing price crashed it, then every batch got 422 (event ids must be integers), several prop labels were unmapped: all fixed. Also fixed: the +EV-bets grader never found Novig's result (it looked Novig up through Pinnacle's market label), and its job failed daily on a missing state file. |
| 4 | Use the plans' usage freely | ParlayAPI Business: 939,382 of 1,000,000 credits LEFT on Oct 25; PropLine: ~4k of 250,000 a day. | PropLine boards re-read every 90 s under the stream (was 3 min); a 1st-half board per league (below); the hot streams. Streams cost no credits on either. |
| 5 | PropLine batched delivery | `batch_max` exists for HTTP webhooks only (it amortizes one POST per event). The engine uses the WebSocket transport, which has no per-event round trip (buffered to 5,000 frames, measured network delay 18 ms, 0 gaps). | Nothing to change: batching would add nothing over the socket. |
| 6 | More PropLine sockets | Streaming Lite allows **2 concurrent WebSockets** (both used: board + signals) and 5 active subscriptions. The 3 spare subscriptions could only be HTTP webhooks; the two in use already carry every event type the engine reads (`line_movement`, `line_added`, `line_pulled`, `market_suspended`, `steam`, `resolution`, `ev`) for every scanned sport and book. A sixth event type, `availability` (FanDuel open/suspended, beta), is the only thing left; it is pull-side "Streaming and Enterprise" and not clearly on Lite. | Nothing to add on Lite. Streaming (5 sockets) would allow `availability` on its own socket. |
| 7 | sportsbook-accuracy | Already read into `research/propline_book_accuracy_2026-10-24.md` (update 2026-10-25): on PROPS no book beats the many-book average; Pinnacle trails it. | Supports point 2 (more books = a better prop average). Lever: research/LEVERS.md "prop calibration". |
| 8 | sportsbook-webhook-api | The page answers 404; its content is the "Webhooks" section of llms-full. The engine's subscriptions are WebSocket-transport subscriptions made through the same `/v1/webhooks` API, filtered, resumed and re-activated as documented (`PropLineStream`). | Correct as built. |
| 9 | PropLine recipes | Used already: /ev, /best-line, /results, /odds/closing, /clv/grade (now), exports, trends, hit-rates, market_suspended scratches, steam. New: **`?period=h1` / `f5`**: Vigilant's 1st-half lines were priced by a median of ONE book. | `PropLinePeriodSource` (`propline_1h`, beside ParlayAPI's 1st-half source, not instead; `PropLinePeriodTest`). Not useful here: cross-book arbitrage and low-juice pairs (Tj bets Novig and Kalshi only), DFS multipliers, futures. Open: the Kalshi ticker crosswalk (`includeBookIds`) could replace name matching for the Kalshi venue (TASKS PS9b). |
| 11 | ParlayAPI cookbook | Its recipes are /ev, /arbitrage, /compare, closing odds for CLV, line-movement, the WS/SSE streams, event-market search. Vigilant computes its own fair and +EV; closing odds are used by the grader; the hot stream is now used. `include=verification` gives `verified_at_ms` / `line_changed_at_ms`: the REST board is already dated by `last_update_ms` (the freshest of a move or a verification poll). | Nothing else to build. |
| 12 | Both used together, complementing | (below) | PB21b cross-check built. |

## How the two feeds work together (PS12, PB21)
- **Each fills the other's gaps.** Every book either carries is priced; where both carry one, the quote with the newer stamp wins (`Pricing.chosenMarkets`, by more than 20 s; inside that the feed order stands). PropLine brings ~35 books, props for every game and now 1st halves from all of them; ParlayAPI brings Pinnacle, Bookmaker, BetOnline, ProphetX, Fliff, Rebet and Novig itself, and its own props and 1st halves.
- **Each checks the other.** Two aggregator copies of one book dated within 20 s of each other but 3+ points apart in no-vig chance are not both right: the book is left out of that line and counted (`Pricing.crossDropped`, in the engine's ParlayAPI status line). A book's own feed (Pinnacle's hosts, the exchanges) is never doubted against a copy.
- **They do not interfere.** Each stream overlays only its own feed's quotes (PropLine by its outcome ids, ParlayAPI by its tags), neither stream has a credit cost, and neither feed's REST budget is near its cap.
- **One company is counted once** whichever feed sent it (`BookCompany`).

## Still open
- PB17b: ParlayAPI's hot stream as the LIVE (in-play) lane's Pinnacle source.
- PS2b: pricing Novig's alternate numbers from a book's other number (a model; nothing measures its error yet).
- PS9b: Kalshi ticker crosswalk from PropLine's `includeBookIds`.

## Point 3 result: Tj's real bets graded by PropLine `/clv/grade` (run 40210139397, 2026-10-25 16:35 UTC; Release `propline-grade`)
Grading was blocked by PropLine's 30 s gateway: one bet answers in ~3.5 s, a request of 6+ bets 504s; the grader now sends one bet a request, 8 at once (~28 min for 2,800 bets), and reuses final grades from the last run. "EV vs the de-vigged close" is the honest number (Pinnacle-led fair close); only games already started count.

| who placed it | graded | beat the close | EV vs de-vigged close | CLV price vs price |
| :- | -: | -: | -: | -: |
| ALL | 2,762 | 64% | +1.19% | +3.67% |
| engine bid | 578 | 66% | **+3.26%** | +4.84% |
| phone auto bet | 451 | 64% | +1.42% | +3.90% |
| phone auto bid | 313 | 77% | +0.58% | +4.11% |
| hand bet | 1,212 | 61% | +0.23% | +3.12% |
| hand (Novig app) bet | 106 | 59% | +2.02% | +3.60% |
| engine bet | 89 | 51% | **-0.66%** | +1.47% |

By kind: player props +1.20% (1,492 graded, 74% beat), spreads +2.04% (460), 1st halves +3.78% (120), game totals **-0.34%** (502), moneylines **-0.68%** (78). By league: NHL +2.63%, NBA +2.03%, NCAAF +1.40%, WNBA +0.85%, MLB +0.37%, NFL +0.05%.
Not graded: team totals (573: the grader does not send them yet), games PropLine does not list (159), tennis/UFC/soccer (PropLine grades none of them).
Reading (not yet a rule change: class B wants this with an interval and a second digest): the engine's bids beat the fair close by the most; the engine's own taker bets are the one group below zero; game totals and moneylines trail. Feeds research/LEVERS.md (CLV closes) and the next ANALYST run.
