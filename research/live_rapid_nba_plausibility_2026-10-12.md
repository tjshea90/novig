# Can rapid live NBA bets be +EV on Novig? (Tj, 2026-10-12) — verdict from what the repo already measured

Tj: NBA odds shift too fast to place bets at the listed odds; (1) Novig and Pinnacle must match in time or the EV is on stale odds; (2) no fills. Asked for a rapid auto-bet with settings (min live EV, max stake...), a price RANGE instead of one price, a plausibility check, and alternatives.

## What already exists (nothing new needs inventing)
- **Rapid auto taker:** Settings › Pinnodds live (`PinnLiveTrader`, `LiveEdge`): min EV (4%), min move, stake, per-game and per-day dollar caps, halt at a loss, trigger (score / stale order / either), real-bets switch (paper by default). **The "range" is built:** `LiveEdge.reachPrice` sets the IOC limit to the highest grid price whose EV, fee in, still clears the minimum, so a requote of a step or two still fills (RESEARCH.md §125).
- **Timing match (point 1):** the Feed's live lane shows a bet only while BOTH sockets spoke within 5 s (`LiveScan.MAX_AGE_MS`); Novig's side is the live-pushed book (current, not a poll). A price Novig has not changed is the opportunity, not a stale reading. Never widened.
- **Live bids** (`LiveBidDesk`, safety/volume slider) and the **live tail bettor** (`TailTaker`, `TailModel`: basketball, football, hockey, baseball, soccer models; Settings › live tail bets).

## Why fills fail (measured, not guessed)
- Order time on live orders: median 5.3-5.5 s, slowest 10 s, against 94 ms for pregame `/v3/orders` (§120.1, Diagnostics 2026-10-11: 50 sent, 1 filled, 49 "offer gone"). A fixed ~5.3 s answer on in-play orders only is an in-play acceptance delay.
- Novig re-quotes after a basket in a median 3.5 s (basketball 4.0 s after a Pinnacle move); a stale quote lives 2-4 s. The order lands after the quote is gone (§120.2, §125).
- §120.2 item 4, **basketball, n=72-75**: buying the team that just scored at Novig's ask d seconds after the score, judged against Pinnacle's fair 30 s later: d=2 s median EV -0.6%; 3.5 s -0.0%; 5.3 s -0.6%; 8 s -0.7%; 12 s -1.0%. Even an instant order has no edge, because Pinnacle's own score-driven jump overshoots (only 52% still there after 30 s, 28% after 120 s). Price-only jumps keep 100% at 30 s.
- Basketball moneyline "edge" rows are mostly a standing disagreement (+4.0% now, +1.3% against Pinnacle 30 s later), not a lag.

## Verdict
**Rapid NBA taking is not plausible as a positive-EV strategy with a ~5 s order delay**: the takeable part of the lag shrinks to nothing after Pinnacle's overshoot and Novig's own 3.5 s re-quote. A faster client would not help (HTTP is 94 ms; the delay is Novig's). A wider price range does not help either: the misses are vanished quotes, not one-tick requotes. Do not run the lag taker with real money on NBA.

## What is plausible instead (all already built; evidence)
1. **Live tail bets** (Settings › live tail bets, `TailTaker`): near-certain outcomes late in a game (far alternate lines the game has decided). Real run 2026-10-10: **6 filled, 4 missed, 1 refused**, edges +5% to +13% (NFL). These do not need speed: prices at 0.82-0.93 sit for minutes. Basketball and the other sports are in the model; NBA fourth-quarter blowout totals/spreads are the natural fit. This is the one engine with real fills.
2. **Live bids with the cancel-on-Pinnacle-move guard** (slider): kept +2-4% at the fill in the tape study, 2.6-6.6 fills per bid-hour, but only ~1.7% of posted live bids filled in Tj's file; fills and edge trade off by margin.
3. **Slow-lag sports:** hockey trails Pinnacle by about 20 s (9 jumps only): measure before trusting (`pinn_novig_lag.py record`, TASKS.md follow-up).
4. **Price-only Pinnacle moves** (injury news, line moves with no score): 100% kept at 30 s and the lag is multi-second: the "stale order" trigger of the taker targets these, but orders still wait 5 s, so only edges that last more than ~6 s fill.

## Next steps worth doing (TASKS.md TAM)
- Keep the taker on PAPER for NBA; read its 30 s / 120 s follow-ups as the live verdict.
- Turn on the tail bettor for basketball and raise its caps if its fills keep their edge (it is paper/real behind its own switch).
- Add the missing evidence: the order's own reason when an IOC ends with nothing, and Novig's ask at that moment, to the journal, to prove or refute "the quote vanished".
