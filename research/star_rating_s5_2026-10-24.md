# S5: is the 1-5 star scale sound for betting by hand? (2026-10-24)

Tj, verbatim: "Do another check of the bet star rating system. I will be using it heavily to make bets. Its accuracy is very important. Make sure the 1 star to 5 Star scale for bets is sound and logical and based on solid data, with 5 Star bets being excellent, profitable bets with good support and 1 star bets still being positive ev, but less support and less profitable" / "If any adjustments to the star rating system are warranted, do them and release the update next".

## Data
The engine's whole graded dataset (`engine-dataset` Release, `lines-*.csv.gz`, game days 2026-10-15 to 11-01): 20,840 lines, 2,880 of them +EV, 4,561 with a close. The lines at -2% to 0% EV are a control group: they show how much of a stated edge survives. 1,968 +EV lines carry the rating's inputs (companies, agreement) and can be re-rated; only 199 of those have a **sharp** close.
- **CLV is judged against Pinnacle/Circa closes ("sharp CLV")** as well as every close. A third of all closes are Novig's own last trades (3-8 trades), a weak and partly circular yardstick.
- Every ± is a 95% interval over GAMES (a game's lines averaged first). ROI swings ±15 to ±50 points per star level at this size: results cannot grade the stars yet; CLV can, roughly.
- Analysis scripts: a Python replica of `BetRating.rate` (0 mismatches over 1,968 lines against the Kotlin, via `StarCheck.dump`), run on the downloaded files. The same tables now print daily in the digest's STAR RATING CHECK.

## What the data says
1. **The +EV list is real value overall**: +EV lines with a Pinnacle close beat it by +2.05% (±1.0, 136 games).
2. **Tiny edges are not real.** Stated EV 0-0.25%: CLV -2.09% (all) / -0.96% (sharp), ROI -14%; 0.25-0.5%: -0.43% / -0.51%, ROI -18% (440 lines). From +0.5% up every band beats the close; +1% up, steadily (1-2%: +1.3% / +2.5%; 3-5%: +2.0% / +4.2%; 5-8%: +3.3% / +4.7%).
3. **Huge edges are not real.** Over +25%: 105 lines, ROI -27.5% (98 of them over +30%) (mostly two-book props: a stale or mismatched quote). The list hides them by default (25% cap), but a star minimum on the auto-bet bypasses the list's filters (only the too-good cap and a 0.5% floor stay).
4. **A sharp book against**: 48 lines, mean EV +0.31%, CLV -2.7% (9 closes). Few, but the sharpest price there is says no edge.
5. **The stated edge is the one input with a measurable effect** (regression of CLV on the inputs, clustered by game): about 0.43 of each point of stated edge survives to the close (t 2.3). Coverage, agreement and consensus show no positive effect on this sample (coverage -1.9 t); single-company lines and lines where half or fewer books agree DID lose on ROI (-31% and -22%).
6. **Bet type / history points**: lines the history scores +0.5..+5 did worst again (ROI -22.8%, 149 games), as in the 10-23 review. Not confirmed out of sample (twice).

## The scale before S5 (v1.0.67, SR)
- Lines the data does not trust were rated 1½ to 3★: a 0.3% edge with strong support could be 3★, a 30% two-book "edge" 2★ (112 lines at 2★ had EV over 15%), a sharp book against up to 2½★. So **"1★ is still +EV" was false**: the bottom of the scale mixed real small edges with lines that lose.
- Above that, no rating orders the real bets yet: over games, on the rated lines, CLV per star is flat for every weighting tried (|t| < 1): SR, ST1, no type, edge 40, and an edge ladder with the company caps. The edge ladder without caps was the only design positive in both halves (sharp CLV/★ +0.74, t 1.3), but it drops your 2026-10-23 company caps ("two small names agreeing: at most 2 stars"), which ROI backs.

## The change (S5, v1.0.68)
- **0 = "no trusted edge"** (`BetRating.NONE`, shown "☆ no trusted edge" with the reason on the bet sheet): stated EV under +0.5%, over +25%, or a sharp book for the market saying it is not +EV. Below every star minimum (the choices start at 2★); still on the list only if your own filters show it.
- **Under +1% EV: 1 star** (the smallest real edges make the least).
- Everything above is unchanged (SR's weights and every ceiling).

| Re-rated with S5 (1,968 lines) | lines | CLV (all closes) | sharp CLV | ROI |
| :- | -: | -: | -: | -: |
| 0 no trusted edge | 552 | -1.82% ±1.45 (87 g) | -0.79% ±2.08 (40 g) | -13.3% ±13.7 |
| 1-1½★ | 346 | +1.39% ±1.48 (67 g) | +2.81% ±2.29 (32 g) | -15.7% ±15.9 |
| 2-2½★ | 254 | +3.82% ±6.34 (21 g) | +20.7% (5 g) | +1.5% ±22.0 |
| 3-3½★ | 530 | +0.30% ±1.23 (107 g) | +0.98% ±1.67 (55 g) | -5.0% ±14.6 |
| 4-5★ | 286 | +1.83% ±2.11 (64 g) | +2.84% ±2.36 (34 g) | +2.8% ±18.5 |

The lines your list actually showed (default 1-25% filter): 3-3½★ 346 lines sharp CLV +0.13%, ROI +0.2%; 4-5★ 259 lines sharp CLV +2.28%, ROI +4.3%.

## What the stars mean now, and how sure this is
- **0 (no trusted edge)**: the data says these lose. Do not bet them.
- **1-5★**: every level beat the close on average. More stars = a bigger edge backed by more books (5★ needs about +5% or more with every input strong). The top group (4-5★) has the best CLV and the best ROI of the lines shown, but between 1★ and 5★ the data cannot yet PROVE that more stars beat the close by more: 199 re-ratable lines with a sharp close is two weeks of data. Treat the stars as "how much of the edge is likely to be real", not as a win probability.
- **Results**: no star level's ROI is distinguishable from zero yet (±15-50 points). That is sample size, not a broken scale.

## The rule for the next change (LEVERS.md: star rating)
The digest's STAR RATING CHECK now judges on sharp closes, over games, on rated lines only, and scores six ratings side by side every day. Move to the edge ladder (or a heavier edge weight) only when its sharp CLV/★ beats SR's in BOTH halves over two digests with 300+ more sharp-closed rated lines. Revert S5 (`doubtOf` returning null, the `MIN_EV_FOR_TWO_STARS` line removed) if over two digests the 0 group's sharp CLV is above the 1-1½★ group's with 100+ sharp closes in each.
