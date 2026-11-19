# Two player props of one game: how much more often do they hit together? (MK20, 2026-11-19)

**Why.** Tj 2026-11-19: "The problem is the engine prices almost nothing. It doesn't have enough fair odds." The journals (59,028 RFQs declined as "two legs in one game (correlation unmodelled)") split into: 30,221 pairs of
props of two DIFFERENT players (OO 16,388, OU 9,063, UU 4,292), 3,939 pairs of props of the SAME player (OO 2,882, UU 616, OU 441), 14,541 a game line with a prop, the rest three or more legs. The maker's
same-game copula only knows game lines, so none of the 34,160 prop pairs could be priced, however good both legs' fair lines were.

**Data.** PropLine's resolved-prop archive (Release `propline-archive-2026-11-18`: Novig's CLOSING price as the fair, the result as the outcome), 2026-11-15..18: NBA 217 games, NHL 30, NFL 13, NCAAB none.
One line per (player, stat): the one nearest even. `tools/prop_pairs.py` (reproducible, `selftest`). Ratio = how often both hit / what the closing probabilities multiply to; 95% interval by bootstrap over games.

**Findings.**
1. **Different players: close to independent.** NBA Over-Over 1.12 (1.00-1.25), Over-Under 1.01, Under-Under 0.90; NHL 1.09 / 0.97 / 1.01; NFL 1.00 / 0.98 / 1.03. The archive has no team for a player, so the blend
   of teammates (higher: pace, one team's blowout) and opponents (about 1.0) cannot be split: the model takes 1.55 for Over-Over (covers a teammate pair at the blend's upper bound), 1.15 for Under-Under, 1.05 for Over-Under.
2. **The same player: depends hard on how the stats overlap, and a pooled ratio (1.41 NBA, 1.47 NHL) hides it.** By pair of stats, Over-Over: NBA points with points+rebounds+assists 1.75 (to 1.95), points+rebounds with
   PRA 1.99 (to 2.21), points with assists 1.90 (to 2.17); NHL assists with points 2.13 (to 2.50), goals with points 2.09 (to 2.43); NFL longest reception with receiving yards 1.88 (to 2.10), receptions with yards
   1.56 (to 1.84). Under-Under up to 1.63 (NBA) and 1.73 (NFL, to 2.00). Over-Under mostly well under 1 (NHL assists with points 0.30; NFL receptions with yards 0.36), the top 1.24 (NFL rushing yards with receptions, n=74, to 1.5).
   The model takes the highest upper bound of any pair of stats for each side combination: 2.5 / 2.0 / 1.6, and caps every joint at the smaller leg (a joint cannot exceed it). That is deliberately dear: a loosely
   linked pair is priced as if tightly linked, so it fills less. Pair-by-stat ratios are the next refinement, once there is paper evidence that these pairs fill.
3. **The maker sells the parlay**: an UNDER-estimate of the joint loses money, an over-estimate only loses fills, so every ratio used is an upper bound and none is below 1.0 (an Over with an Under of one player gets 1.6, not its measured 0.72).
4. **Not covered, still declined**: a prop with a game line (14,541), three or more legs of one game, a leg whose player the label does not give (2% of prop legs: ladder notes and combined-yards stats), two lines of one market.

**What was built (all SHADOW).** `data/.../rfq/PropPairs.kt` + `RfqPricer.decide`: a covered pair is declined as "two player props of one game (modelled, shadow: not quoted)" with the model's joint as the journaled fair, so the
journals and `tools/rfq_summary.py` can compare it with what the market's trades charge for the same pairs. `RfqConfig.propPairs` (model on, default) and `propPairsLive` (quote it, default OFF; `tools/rfq_set.py propPairsLive=true`).
Tests: `PropPairsTest`. **Before `propPairsLive` goes on:** the same-class evidence in two journals (the market's EXECUTED price for such pairs against the model's price: the model must price at or under the market's median on
at least a third of the pairs, or it will not fill), the class B gates in `research/review/ROUTINE.md`, and the first paper results of the live pairs.

**Caveat.** 30 NHL games and 13 NFL games are few; NBA is the only sport with a sample large enough for the different-player ratio. The intervals above are the reason for the generous ratios.
