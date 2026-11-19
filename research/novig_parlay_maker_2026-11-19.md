# The parlay maker, round 2: pricing, coverage, same-game legs and the quote that fills (MK12, Tj 2026-11-19)

Tj (2026-11-19, tj Gmail session, six asks): deep research online for everything the parlay system needs (pricing, how to use the Novig API correctly); price and find fair odds for as many legs as possible;
find the prices to offer that keep an edge and still fill; price 2-leg correlated parlays; any other research that helps; **implement it all and update the engine, without disturbing the other session**.
Builds on `research/novig_rfq_maker_2026-11-18.md` (the maker, its modes and its first shadow numbers). The maker stays in **SHADOW** (Tj, 15:47 UTC: "paper only until I have more money"): everything below is
measured and journaled on paper first; nothing here sends a quote.

## 1. What was found, in one table
| # | Finding | Evidence | What it changed |
|:--|:--|:--|:--|
| 1 | **A bug would have locked the maker every ~10 fills**: Novig echoes our 4-decimal price rounded to a thousandth (we quoted 0.4495, it came back 0.450); our last look compared the echo against a 5e-4 bound, floating point made the gap 5.0000000000000044e-4, so **our own win was rejected**, counted against the 99% confirm rate (1 reject in 12 last looks = 92%) and Novig answered `MAKER_LOCKED` for 5 minutes (journal run 44474874762, 10:51-10:52 UTC) | the CONFIRM row `confirmed:false` for quote 0.4495 vs accepted 0.45 | `RfqMaker.PRICE_ECHO_TOLERANCE` = 6e-4; `RfqMakerTest` replays both extremes of the rounding |
| 2 | **Novig will tell us what the other makers charge, for any parlay, for free**: `POST /rfq/v2/quote/unauthenticated` (docs.novig.com/affiliates/parlay-pricing, no key, 1 request a second per address) is a real 3-second auction; the price is the best quote any maker gave. Probed live: 3.1-3.2 s for a new set of legs, 0.1 s repeated (cached 30 s), 200 / 404 (nobody quoted) / 400 (cannot combine) | probes from this session, section 6 | `RfqProbe` + `RfqMarketModel`: about 0.9 observations a second of the competition on the RFQs we price, against about 10 priced trades an hour before |
| 3 | **The market's margin differs 4x by league**: on 7,626 priced different-game game-line parlays the market paid a median **+0.31 pts over our fair in the NBA, +0.35 NHL, +0.45 NCAAB, +0.57 cross-league, +1.27 ATP tennis**; a quote 1 point over our fair would have won 13% / 4% / 26% / 29% / **61%** | section 5 | the class key of the market model includes the league; the old single floor (0.7 pt for everything) was right for nobody |
| 4 | **Same-game game lines are the biggest priceable group and they are nearly independent**: side + total, moneyline + total, team total pairs are 41% of 2-leg same-game RFQs (the rest are player-prop pairs); the margin and total correlate at -0.01 to +0.04 in the NBA, NFL, NHL and NCAAB, but **+0.175 in college football** (858 games, +0.196 and +0.155 in the two halves) | sections 3 and 4 | `SameGame` Gaussian copula replaces the flat 8% premium (the market charged +3% on side + total) and models team-total and first-half pairs (market +47%) |
| 5 | Coverage is 58% of legs priced (weighted), **game lines 70-80%**, the gap is player props (NBA POINTS 31% priced, REBOUNDS 35%, PASSING_YARDS 33%) and three-way soccer moneylines (0%) | section 7 | next: ladder from priced sibling rungs (designed, section 7) |
| 6 | The market is huge and the capital is the limit: 43,790 trades, **$1.09 million staked in 26 hours** (1,684 trades an hour, median stake $9, 55% of trades 4+ legs) | section 2 | caps, not flow, decide profit; return on collateral is the objective |

## 2. The market, from 26 hours of journals (`rfq-<day>` Releases, 2026-11-18 07:36 -> 11-19 14:32 UTC, six largest runs)
- 552,663 RFQs journaled (priced ones whole, a 1-in-25 sample of the rest), **43,790 distinct trades, $1,091,499 of stakes**, 1,684 trades and $42k of stake per covered hour.
- Legs per trade: 2 legs 27% of trades ($462k), 3 legs 18% ($219k), 4 legs 14%, 5 legs 8%, 6+ legs 33%. Stake median $9 (p90 about $37); the mean is $25.
- Only about 3% of RFQs ever trade: most are price checks (the Novig app shows the best quote while a user builds a slip; affiliates show it in their UIs). A quote that is lowest costs nothing unless the user accepts.
- Rules that shape what can be asked (docs.novig.com/api/rfq/eligibility, fetched 2026-11-19): 2-20 legs; any market, pregame or live; **no two legs on the same kind of line in one game** (spread + moneyline, two spreads, two totals; first-half versions too), same-game legs of different kinds are allowed; two legs from different games cannot share a team; the NO side of yes/no markets is not allowed. A voided leg settles at its fair market value; the scalar is a product of leg values rounded to 3 decimals, never below 0.001.
- Changelog items that touch us: parlay taker fee `0.055 x (-ln P) x stake` from 2026-11-19 (maker side unchanged); `subjectId` on team outcomes (2026-11-11); participation-protected NBA prop twins (`POINTS_PROTECTED` etc., QA from 11-12, production date unset) that follow the parlay RFQ rules of protected NFL markets.
- Comparable venues (their docs, fetched 2026-11-19): Gemini's combo RFQ is a sealed 1-second quoting window, lowest price wins, a tie goes to the EARLIEST quote, collateral re-checked at close, a 1-second last look; Polymarket's combos run a quote competition the same way. Novig's tie rule is the larger `maxWager`, which needs the collateral for the whole `maxWager` (so a big maxWager is not a free tie-break).

### 2b. Context from outside (what is published about maker-side economics; sources at the end)
- Kalshi's parlay ("combo") market is the closest relative: sportsbooks hold about 5% on a straight bet and 20% or more on a parlay; one analysis of Kalshi's blended parlay hold found just over 10% before fees and close to 15% with the taker fee. A July preprint on 23 million Kalshi moneyline trades found single contracts priced about right but seemingly uncorrelated parlays hit less often than their price implied (buyers overpay). Makers there "face real adverse selection and a real capital floor" (every parlay is fully collateralised, no netting against the legs), RFQ liquidity thins as legs are added and makers widen, and the RFQ lives seconds, so the maker side has to be automated. **On Novig the competition is tighter than that**: the market paid 0.3-1.3 points of pot over our fair on 2-3 leg game lines, 1-5% of the price, not 10%; the wide margins are in tennis, college basketball and long parlays (4+ legs: the probe's first answers show +0.9 to +1.2 median).
- Nobody publishes the dependence assumptions makers use (the Quantcha piece makes the point that with three or more legs a price cannot be decomposed into pairwise correlations), which is why the probe measures what the market charges per class instead of assuming it.

## 3. Measured correlation of the margin and the total (closing lines and final scores)
`rho_fav` is the correlation of the FAVOURITE's margin residual (margin - spread) with the total residual (total - line); positive means the favourite covering goes with the Over.
| league | games | sd margin / sd total | rho_fav (se) | halves | favourite covers & Over vs independent |
|:--|--:|:--|:--|:--|:--|
| NBA | 1,489 | 13.55 / 17.62 (ratio 1.30) | -0.012 (0.026) | +0.030 / -0.059 | 0.999 |
| NFL | 555 | 12.43 / 12.77 (1.03) | +0.030 (0.042) | +0.027 / +0.033 | 0.995 |
| NHL | 1,500 | 2.69 / 2.35 (0.87) | +0.040 (0.026) | +0.059 / +0.020 | 1.056 |
| **NCAAF** | 858 | 15.17 / 15.17 (1.00) | **+0.175 (0.034)** | **+0.196 / +0.155** | **1.117** |
| NCAAB | 17,321 | 11.31 / 16.55 (1.46) | -0.014 (0.008) | -0.003 / -0.025 | 0.973 |
(ESPN's closing lines, 2024-25 and 2025-26 to date; `research/ncaab/seed` for NCAAB. A maker that treats college football's favourite + Over as independent gives away 11% of that parlay.)

## 4. How same-game legs are priced now (`data/rfq/RfqCorrelation.kt`, `SameGame`)
- A game is two latent variables, the margin M (home minus away) and the total T, with the league's spread ratio and `rho_fav` signed by who the favourite is (read from the spread leg's home handicap, or a moneyline leg's probability; unknown = independent). A moneyline or spread leg is M, a total is T, a team total is half of each (H = (T+M)/2, A = (T-M)/2), a first-half line is the same variable over half the time (a Brownian increment: its covariance with the full game is the shorter time, so a first-half total and the game total correlate at sqrt(1/2)).
- Each leg keeps the probability the books gave it; only the dependence is modelled (a Gaussian copula: threshold = Phi^-1(p), joint = bivariate/trivariate normal CDF, exact to 1e-9 by 48-point Gauss-Legendre; tested against closed forms and a 3-million-draw Monte Carlo). Two or three legs of one game; four or more, a player prop beside a game line, a duplicate kind of line, and the soccer three-way moneyline are declined as before.
- Results: a team total with the game total correlates at 0.71 (NFL) and the pair's price is **+50% over independent** (Novig's market charged +47% on a side + team total, n=15); a side with the total is within 1% of independent except NCAAF (+11%/-12% by side). The flat 8% premium of MK10c (a side + the total only) is replaced by the copula plus `sameGamePremium` = **2%** for the model's own error (the market paid +3% over independent on 404 side + total trades). **`sameGamePremium` < 0 now means "decline every same-game parlay"; 0 means no premium.**
- Backtest on the 602 priced same-game side/moneyline + total trades whose league resolves (catalog snapshots): the market paid a median +0.44 pts (NBA side+total, 209), **+1.88 (NCAAB side+total, 162)**, +1.48 (NCAAB moneyline + total, 120), +0.84 vs the copula (NHL moneyline + total, 35): the same league spread as the different-game parlays, and the copula moves the median by 0.2 pts or less except in NHL and NCAAF (up to 0.6 pts; n of 35 and 14, too few to call).
- Not modelled (declined, but logged and probed): player-prop pairs (32% of 2-leg same-game RFQs: Assists+Player Goals, Assists+Points, Shots+Goals in the NHL; Touchdowns pairs in the NFL), and prop + game-line pairs. The probe now measures what the market charges over the independent product per class (`psg`), the premium table the next step needs.

## 5. The quote that fills: what the market pays over our fair, by league
Different-game parlays of 2-3 game-line legs (moneyline, spread, total, team total) with our fair known (7,626 trades; the fair may be a few minutes old on some). Gap = executed price minus our fair, points of pot; "wins at" = the share of trades a quote that many points over our fair would have beaten (strictly lower price).
| league | trades | median gap | p25 / p75 / p90 | wins at +0.5 | +0.7 | +1.0 | +1.5 | +2.0 |
|:--|--:|--:|:--|--:|--:|--:|--:|--:|
| NBA | 3,107 | +0.31 | +0.09 / +0.62 / +1.21 | 31% | 22% | 13% | 6% | 3% |
| NHL | 1,378 | +0.35 | +0.22 / +0.48 / +0.64 | 22% | 7% | 4% | 2% | 1% |
| NCAAB | 1,204 | +0.45 | +0.11 / +1.06 / +2.09 | 47% | 39% | 26% | 16% | 11% |
| cross-league | 1,052 | +0.57 | +0.29 / +1.11 / +1.81 | 56% | 41% | 29% | 15% | 8% |
| **ATP** | 642 | **+1.27** | +0.74 / +1.81 / +2.64 | 85% | 76% | **61%** | **38%** | 20% |
| NFL | 130 | +0.33 | -1.71 / +1.31 / +1.89 | 44% | 38% | 33% | 21% | 5% |
| NCAAF | 98 | +0.51 | +0.20 / +0.86 / +1.60 | 51% | 35% | 19% | 12% | 6% |
- **Expected points of pot per RFQ we could quote** (win share x margin, if our fair is true): NBA 0.15-0.2 at +0.5 to +0.7; NHL 0.11 (at +0.5); **ATP 0.61 at +1.0, 0.57 at +1.5**: tennis is about four times as lucrative per RFQ as the NBA, and ATP moneylines were the single most common leg of traded parlays (1,711 legs in one morning's journal). A single 0.7-point floor under-prices tennis by half and over-prices the NHL.
- **The optimiser** (`RfqMarketModel.best`): at a quote c over our fair the win share is the share of observed answers with gap > c (nobody quoting counts as a win); a win earns c per dollar of pot, less an allowance for our fair's own error (half the per-leg points floor) and the cost of the collateral held (collateral per dollar of pot is 1 - price, held (lead days + 0.25) at `minReturn` a day): net(c) = c - allowance - cost; score = win share x net; the quote is the c that maximises it, searched upward from the rule's floors, so the model can only RAISE a quote above the old rule (never below its floors) and only when a class has `probeMinSamples` (40) answers in the last 4 hours, else the league falls back on the pooled kind of parlay (`legs|mix|*|band`).
- **What the optimiser says about the old flat floor** (same 7,626 trades; allowance for our fair's error = half the per-leg points floor = 0.35 pt for two legs; collateral cost = 0.4% a day on (1 - price) held a day and a quarter; "score" = win share x net edge, points of pot per priced trade):
| league | trades | flat +0.7 pt: win share, net edge, score | the optimum: margin, win share, score | gain |
|:--|--:|:--|:--|--:|
| NBA | 3,107 | 22%, -0.02 pt, **0.000** | **+1.25 pt, 9%, 0.050** | the rule earned nothing net |
| NHL | 1,378 | 7%, -0.02, 0.000 | +1.60, 2%, 0.016 | thin everywhere |
| NCAAB | 1,204 | 39%, -0.02, 0.000 | +1.95, 12%, **0.144** | |
| cross-league | 1,053 | 41%, -0.02, 0.000 | +1.60, 14%, 0.127 | |
| **ATP** | 642 | 76%, +0.07, 0.055 | **+1.40, 43%, 0.335** | **6x** |
| NFL | 130 | 38%, +0.02, 0.008 | +1.50, 21%, 0.171 | |
| NCAAF | 98 | 35%, -0.02, 0.000 | +1.40, 14%, 0.098 | |
  At +0.7 pt a quote wins a quarter to three quarters of the trades and clears nothing after what the error allowance and the tied-up collateral cost; the model asks 1.25-1.95 pts and wins 2-14% (ATP: 43% at 1.4). The honest size: 0.05-0.34 points of pot per priced trade, a few dollars a day on this wallet: the market is large but the edge per trade is small and the capital is smaller. **Where capital is short, tennis and college basketball are where it earns.**
- **Why a probe and not more trades**: the trades are 10 priced a night; the probe asks Novig what the best OTHER maker quotes for the RFQs we price, whether or not they trade, at up to 0.9 requests a second per address (the documented limit), several in flight because each is a 3-second auction. In SHADOW it probes every priced RFQ (class-balanced: the next probe is the least-sampled class); in LIVE only RFQs we decline (a probe of one we would quote would meet our own quote). Rows `PROBE` in the `rfq-<day>` journal: class, our fair, our rule price, the market's price or `NO_PRICE`, latency; `status.rfq.market` carries the per-class quartiles.
- **What it cannot tell**: whether our fair is as good as the others'. The market's gap over our fair is a demand curve, not a P&L; the winner's curse is not in it. That is why `marketPricing` is OFF until the shadow journals show, per class, the model's price winning where the rule's does not and the first settled results (the 13 live parlays from the first live hours are still settling) say our fair is unbiased.

## 6. Probes from this session (2026-11-19 16:00-16:06 UTC, keyless, from this container's address)
- Two legs from different NBA games (alternate totals): 0.409 in 3.24 s; the same legs again 0.409 in 0.11 s with the same `expiresAt` (a 30 s cache); a same-game side + total 0.073 in 3.09 s.
- Eight random 2-3 leg parlays from the engine's +EV list rows (legs where Novig's single price is below our fair, so Novig's parlay runs below our fair product: -4.8 to +0.9 pts, one +13.8 where the legs' lines did not match): the endpoint works as documented and shows why +EV list legs are the wrong sample for a maker (selection).
- Rate: 3.1 s per probe means one worker does 0.32 requests a second; the probe runs four workers on a shared 1.15 s slot clock.

## 7. Coverage: how many legs can be priced (weighted over 2 days of journals, legs resolved with the 4-hourly catalog snapshots)
| market type | priced | scanned, no fair | not scanned | priced share |
|:--|--:|--:|--:|--:|
| MONEY | 1,473,863 | 1,350 | 364,200 | 80% |
| SPREAD | 514,288 | 12,325 | 142,125 | 77% |
| TOTAL | 428,871 | 12,725 | 172,550 | 70% |
| TEAM_TOTAL | 77,132 | 4,475 | 19,225 | 76% |
| POINTS (NBA/NHL/NFL props) | 341,954 | 93,100 | 670,150 | 31% |
| TOUCHDOWNS | 601,591 | 72,325 | 174,700 | 71% |
| RECEIVING_YARDS | 222,759 | 116,075 | 136,825 | 47% |
| REBOUNDS | 132,881 | 118,600 | 131,775 | 35% |
| PASSING_YARDS | 45,920 | 14,375 | 80,850 | 33% |
| MONEYLINE_3_WAY_WIN (soccer) | 0 | 0 | 92,900 | 0% |
- Alternate spread/total numbers are already priced (SGO alternates and PropLine ladders: of the SPREAD/TOTAL legs, 97% of legs one or more steps from the main number are priced). The gap is **player props and ladder rungs the books do not quote** (3 point-makers, 17.5-point rungs) and the soccer three-way moneyline.
- **Designed, next (needs the engine to hold the whole Novig catalog index; see TASKS MK12)**: a rung the books do not quote is priced from the priced rungs of the SAME player and stat in the same event (the engine's `latestFair` groups): inside the priced span by interpolation in probit space (monotone, error about 1-2% of the leg), one rung beyond by the local slope, never further; every such leg carries a `ladder` flag, an extra points floor per ladder leg and its own class in the market model, so the probes measure what the market charges over a ladder fair before any quote uses one. Three-way soccer moneylines need a draw-aware fair (not built).

## 7b. Same-game player-prop pairs: priced from the market's own ratio, in SHADOW first
32% of the two-leg same-game RFQs are pairs of props (assists + goals, points + shots on goal, TD + TD). No correlation model exists for them, and our prop fair runs high. The probe class `psg` measures the market's best price over the PURE independent product (a declined same-game RFQ now carries the independent product as its fair, not the prop-premium one); `propPairsFromMarket` (default OFF) prices such a pair on the class's MEDIAN market ratio once it has `probeMinSamples` answers (clamped 0.5-4), the floors on top, and journals `via: "market ratio 1.30"`. That is the market's consensus, margin included, so it is a way to quote what the others quote with our floors added, not a view of the truth; LIVE needs the first settled results.

## 8. What shipped (this session, SHADOW)
- `RfqMaker.PRICE_ECHO_TOLERANCE` (the last-look bug); `SameGame` + `Gauss` (RfqCorrelation.kt, 9 tests); `RfqPricer.sameGameJoint` and the `LegFair` descriptors (kind, side, period, home handicap, team-total side, league) filled from the scan in `CloudMain`; `RfqMarketModel` + `RfqProbe` (RfqMarket.kt, 9 tests) wired into `RfqMaker` and the engine; config `probe` (default on), `marketPricing` (default OFF), `propPairsFromMarket` (default OFF), `probeMinSamples` (40), `sameGamePremium` default 0.08 -> 0.02; SHADOW judges RFQs as if the wallet were free (the open collateral no longer declines them); `tools/rfq_set.py` knows the new fields; `status.rfq` carries `probe`, `market` and the model's would-win counters; every CREATED/EXECUTED journal row carries `rulePrice` and `modelPrice` so the two rules are compared on the same trades.
- **Switch on (only with evidence, class B in research/review/ROUTINE.md)**: `python3 tools/rfq_set.py marketPricing=true --why "..."`; undo `marketPricing=false`. LIVE itself stays Tj's call and needs money (the wallet cash was $7.36 with $73 of collateral open).
- Evidence rule for `marketPricing`: 24 h of probes (about 30,000 answers), and in the journals' EXECUTED rows the model's price winning at least as often as the rule's per league with a higher margin, and the settled P&L of the first live parlays not below -$3 per $100 of collateral.

## 9. Not known yet
Whether our fair is as good as the other makers' per league (needs realized outcomes: the catalog forgets settled ids, so the engine must record outcomes itself: MK5); who the other makers are and how many; whether the best price a probe returns is the same one a real RFQ gets (the probe is anonymous and unattached to an account); tennis's wide margin may be our fair running low rather than thin competition (the first settled tennis parlays decide).

## Sources
- Context: [Quantcha, "Of course parlay traders are getting destroyed"](https://quantcha.com/news/?p=670) (Kalshi parlay pricing, adverse selection, fees by price), [Predicted, "Do parlays pay the bills"](https://predicted.beehiiv.com/p/do-parlays-pay-the-bills) (Kalshi combos 22% of volume by April 2026, hold from Sportico's January-April 2026 analysis).
- Novig docs, fetched 2026-11-19: [Parlay Pricing](https://docs.novig.com/affiliates/parlay-pricing.md), [RFQ overview](https://docs.novig.com/api/rfq/overview.md), [Eligibility](https://docs.novig.com/api/rfq/eligibility.md), [Makers](https://docs.novig.com/api/rfq/makers.md), [Settlement](https://docs.novig.com/api/rfq/settlement.md), [Changelog](https://docs.novig.com/api-reference/changelog.md).
- [Gemini combo RFQ](https://developer.gemini.com/prediction-markets/combos-rfq/overview.md) and [Polymarket combos for builders](https://docs.polymarket.com/trading/combos/builders.md): the same sealed lowest-price auction with a last look, for tie and collateral rules.
- [Wizard of Odds: same-game parlays, the mathematics of correlation](https://wizardofodds.com/article/same-game-parlays-the-mathematics-of-correlation/): Gaussian copula as the usual way books price SGPs; [arXiv 1902.10067](https://arxiv.org/pdf/1902.10067) (NBA covers and totals, win/loss vs over/under correlation about -0.04, consistent with section 3).
- Bid shading in first-price auctions ([arXiv 2107.06650](https://arxiv.org/pdf/2107.06650), [Berkeley winner's curse notes](https://elsa.berkeley.edu/~mcfadden/eC103_f03/curse2.pdf)): estimate the minimum winning price's distribution per segment and bid where expected surplus peaks; the winner's curse grows with competitors: the shape of `RfqMarketModel.best`.
