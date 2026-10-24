# SX: the star system built on all the history (2026-10-24, in progress: findings are added as they come)

Tj, verbatim (TASKS.md SX): "Build a star rating system based on solid data from all the sources including apis historical data and GitHub saved scans and novig historical data ... 1 star means the bet is still positive EV but low confidence/support from history 5 Star means the bet is a type that is proven profitable from data and is not held back by safeguards against bad bets ... It involves real money".

## Sources (SX1)
| source | what | size | answers |
| :- | :- | -: | :- |
| `engine-dataset` lines-*.csv.gz | every line the GitHub engine scanned (EV >= -2%), rating inputs, sharp close, result | 20,840 lines (2,880 +EV) | Vigilant's own +EV signal, conditional |
| `phone-data` phone/study (CNO + Vigilant scans on the phone) | each sighting with every book's price, fair, edge | 8,517 lines (8,384 +EV) | same, CNO's signal too |
| `propline-archive-*` (PropLine Pro export) | Novig and Pinnacle: opening and closing price + time, result, every resolved prop and game line | 907,496 outcomes, 5,542 games, 2026-04-20..10-23 | type-level truth with results |
| `propline-multibook-*` (new task, SX2) | the same for DraftKings, FanDuel, BetMGM, Caesars, ESPN Bet, Bovada, 90 days | (running) | multi-book support |
| PropLine `/odds/history` (`tools/star_backtest.py`, `star-study.yml`, SX2) | every book's price changes before the start | (running) | point-in-time backtest: the fair Vigilant would have had at 24 h..15 min |
| Novig public trade archive (data.novig.com, NOVIG_API.md §10) | every trade since 2026-08-03 | ~240k rows a day | actual taker prices (not yet joined) |
| `cloud-archive-*`, `engine-grades`, `lab-archive-*` | engine journals behind the dataset, grades, paper lab | 506 MB | already in the dataset |

## Finding 1: at the close, Novig is as sharp as Pinnacle (propline-archive)
Novig's closing price on each side against Pinnacle's devigged close, both captured within 90 minutes of the start; real results.
- Same hour (35,848 bets, 2,469 games): stated edge +1-2% ROI -1.1%, +3-5% -6.1%, +5-8% -10.4%, +8-15% -15.4%; the +EV sides won LESS than Pinnacle's fair said (e.g. 35.8% vs 39.1% at +3-5%), the sides that looked bad won MORE (53.5% vs 50.5% under -3%).
- Captures within 5 minutes of each other (9,174 bets): +1-2% won 40.3% vs Pinnacle's 43.2%, +2-4% 36.1% vs 41.3%; Novig's own devigged price was closer to the real win rate in every band.
- So: an edge against Pinnacle measured AT the close is not money; where the two disagree, the truth is nearer Novig. Consequences: (1) lead time is part of the rating's evidence (finding 2 measures it point in time); (2) CLV against Pinnacle's close alone overstates a Novig bet; a fair close is the average of Pinnacle's and Novig's devigged closes, and results carry more weight.

## The design being built (written before the backtest's numbers, so the rule is set before the data is seen)
- **Evidence table** (`data/src/main/resources/star_evidence.json`, `tools/star_table.py`): per (sport, kind, side, lead band, edge band) and each coarser level, games, mean CLV against the blended close (Pinnacle + Novig devigged, finding 1) with its standard error, ROI with its standard error. `StarEvidence.lookup` takes the most specific cell with 150+ games.
- **Stars = how strongly the history proves this type of bet makes money** (Tj's definition): z = CLV / its standard error for the bet's cell; 1 = +EV, unproven (z < 1); 2 = z >= 1; 3 = z >= 1.64 (90%); 4 = z >= 1.96 (95%); 5 = z >= 2.58 (99%) and a mean CLV that is worth having; half steps between. A type the history shows LOSING (CLV below zero with z <= -1.64, or ROI significantly negative) has no trusted edge.
- **Safeguards hold a bet back from the top**: the hard floors (under +0.5% or over +25% EV, a sharp book against, a small edge from one or two books) give no stars; the soft ones (to be set from the data: e.g. lead time, longshot odds, few companies) cap the stars below 5.
- **Validation**: the stars, fitted on the backtest (PropLine games, July-October), must order Vigilant's and CNO's own graded lines (the engine dataset and the phone study: a different signal, partly a different period) by CLV and ROI. If they do not, the design is wrong.
