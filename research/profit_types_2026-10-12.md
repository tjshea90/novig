# Which types of bet and bid make money in Tj's history (TPF1; 1,331 bets and fills to 2026-10-11)
Raw tables: `research/profit_types_raw_2026-10-12.txt` (`tools/profit_types.py`). Source: the "all-bets" history file (research/uploads/2026-10-12/). Realised ROI is noisy: read t before believing a number; CLV (does the bet beat the close) is steadier and is positive nearly everywhere.

## The headline
All bets -2.1% ROI (-$36.90) against +3.3% expected: results 2.3 sd under the edges. Taker bets -4.0% (983 settled), bid fills +4.0% (266). Auto bets -0.2%, manual -5.7%.

## Bets (taker) that lose, with enough sample to say so
- **NFL player props: -12.0% ROI, n=412, t=-2.5.** Overs -15.8% (n=224, t=-2.61), unders -7.6% (n=188). All player-prop Overs: -15.4% (n=399, t=-3.05) while Overs' CLV is +1.8%.
- **Edge 4-6% when placed: -14.3% (n=218, t=-2.08)** against -2.4% at 2-4%: the bigger the model's edge, the worse it did (the usual overestimate).
- 1-3 h before the start: -20.8% (n=108, t=-1.76); 6-13 h: -11.5%.
- WNBA overs -25% (n=29), MLB overs -26% (n=27), NHL overs -14% (n=103): too thin (t -2.4 only for WNBA with n=29) or not clear: left neutral.
## Bets that win (not yet proof)
- NCAAF spreads +33.0% (n=64, t=1.75; CLV -0.8%), game-total overs +34% (n=55, t=2.27; CLV -0.1%), NCAAF totals +20%: their CLV is zero or negative, so this is more likely luck than edge. The profile favours them mildly (shrunk), it does not stake them up.
- <1 h before the start +6.5% (CLV +3.4%).
## Bids (fills)
- Team totals **+33.9% (n=65, t=2.32, CLV +2.4%)**, NHL team totals +53% (n=22, thin). Player-prop bids -5.7%; NFL player-prop bids -24.5% (n=66, t=-1.56: not yet blocked); unders +8.6%, overs -1.3%. Bids at 6-13 h do best (+9.8%), 1-3 h worst.

## What the app now does (v0.93.0, Settings › Betting & Novig account › Profit-proven only, default OFF)
`data/.../scanner/ProfitProfile.kt` reads `profit_profile.json` (built by `tools/profit-profile.py` from the history files; rerun it when Tj sends more). A group counts from n>=40 (marginal dimensions 80); LOSER = ROI<0 and t<=-1.5; WINNER = ROI>0 and t>=1.5; a specific winner outranks a general loser. ON: the auto-bet is refused a type with a LOSER ("Not bet: your bet history says ..."), the bid desk skips such a bid and puts up the best-scoring types first, the auto-bet's Vigilant lane orders by score, a bet by hand is warned (not blocked: Tj's standing rule that he can bet anything by hand). Small samples are neutral. On this history it blocks NFL player-prop Overs/Unders (both sides, at the league|kind level) and bets with a 4-6% edge.
## Caveats (said to Tj)
Losing realised ROI with positive CLV is mostly variance; the filter removes what HAS lost, which is not a promise it keeps losing. It also cuts volume (NFL player props were 40% of bets).
