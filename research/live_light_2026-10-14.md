# Live light (Tj, 2026-10-14): obviously wrong or stale live prices, bet and bid fast

Ask: a "light" live lane that finds live prices that are obviously wrong in Tj's favour (a team up big offered near 50/50; a low over in a high-scoring game), on Novig's websocket alone or with other live sockets (not Pinnodds: the subscription ran out), auto-bets fast, plus a live auto-bid toggle, running beside the pregame scanner / auto-bet / bids only if those are on.

## What already existed
- `TailScan` / `TailTaker` / `TailLiveSettings` (RESEARCH.md §125): ESPN score + clock, centre read off Novig's OWN liquid lines, far alternate strikes, real money behind two switches. Polled every 20 s over 8 games through REST books.
- `LiveBidDesk` (order lifecycle for live bids), driven by Pinnodds' fair; `BoltLiveConsensus` (soft-book consensus from BoltOdds' socket).

## Gaps (why the example was not caught)
1. Cadence: 20 s, REST.
2. The centre came from Novig's own lines, so a stale WHOLE ladder looks consistent: a 25-point lead at 50/50 is invisible.
3. `TailTradeLimits.MAX_EDGE` = 30% refuses a 90% edge as "more likely a wrong game than a gift" (right, unless something independent agrees).

## Built (v0.99.0)
- `LiveLightScan` (pure): P(cover) from the SCORE and CLOCK alone: margin ~ normal around the margin now (sd = sport margin sd x sqrt(time left) x 1.4, or sqrt(league scoring x time left) for goals/runs), expected final moved 2 points (1 goal/run) against the bet; totals bounded by 0.7x / 1.3x the league's usual scoring for the time left (and the pace for an Under). Whole-number lines skipped (pushes); a moneyline's tie credit left out of both sides; fair >= 93%, edge >= 3% after the live fee, ask <= 98.5c, >= 20 contracts, 2%-75% of the game left.
- `LiveLightRunner`: one shared Novig stream view pinned on the live games' ML/spread/total markets (48 markets max = 384 of the key's 512 stream tokens at bbo), ESPN scoreboard every 3 s, re-judges ONE game 120 ms after a push or a score/clock change, hands offers to the existing `TailTaker` (its switches, caps, daily halt, IOC orders, journal). Score older than 12 s, closed market, unmatched team, unreadable clock: never judged.
- `LiveConfirm` / `BoltLiveConfirm`: BoltOdds' soft-book moneyline consensus (quorum of companies, fresh within 10 s, agreeing). When it agrees (>= 85%) the offer is priced at min(model, consensus - 2 points) and the taker's edge cap rises from 30% to 95% (`TailOffer.confirm`). Without it, an edge over 30% is counted as "no second live source agrees" and not offered.
- Settings: Live bids > Live tail bets > "Live light: real-time push lane" (default on; the tail switches are still the master switch and the real-money switch keeps its confirm dialog). With the key's live feed connected the 20 s pass stops; without it the 20 s pass is the fallback.

## Not built yet
- Kalshi as a second opinion (its live game markets; matching + staleness unverified from this container).
- Live auto-bids (TLL4): a second `LiveBidDesk` with its own store/limits fed by model-priced wants.
- Nothing here has met a live key or a live game from this container: Diagnostics > LIVE LIGHT (pushes, judgments, ms from push, what was not judged and why) is the evidence.

## v0.99.1: alternate lines and the ladder math (Tj, same day)
- `LiveLightLines.window`: each alternate spread/total is its own Novig market; the 48-market stream budget now goes to the moneyline plus the strikes within 3.5 sigma of where the score says the game is going (nearest first, 14 a game), re-picked every 20 s and at once when a game's first score arrives.
- `LadderFit` (probit regression of a ladder's liquid middles on the strike: mu, sigma, rms) + `LiveLightCurve`: every strike is priced from the curve fitted WITHOUT it (>= 4 other liquid lines, rms <= 3 points, sigma within 2x of what the sport and time left allow, strike inside the liquid range + 0.5 sigma), cautious (centre 0.25 sigma against the bet, width x1.25), fair >= 60%, edge >= 8% after the live fee; the score model only vetoes (a side it rates under 2% while the ladder says likely = a stale ladder). Rule "curve": the taker's fair floor is 60% and edge floor 8% for it.
- Not built: monotonicity arbitrage (a lower Over priced under a higher one) as an auto-bet (two legs; `LadderScan` covers stay paper).
