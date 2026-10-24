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

## Finding 2: point in time, the multiplicative-devig edge did not make money (star-backtest-2026-10-24, first run)
469,485 rows from 3,288 games (MLB 174k, NCAAF 120k, NFL 66k, NHL 39k, tennis 32k, WNBA 23k, NBA 14k), Novig's price at 24 h..15 min against the fair Vigilant would have had then (Pinnacle 0.7 + the US books, MULTIPLICATIVE devig). ROI by stated edge: under 0 -2.9% ±1.1; 0.5-1% -6.0% ±2.8; 1-2% -5.5% ±2.8; 2-3% -2.2% ±3.6; 3-5% -5.6% ±3.6; 5-8% -6.4% ±4.3; 8-12% -5.0% ±5.3. The +EV rows did worse than the rest. By type: spread favourites +3.8% ±4.5, moneyline favourites +1.3%, prop Unders -0.8%, totals -2 to -3%, spread dogs -5.0%, prop Overs -7.5% ±2.5.

## Finding 3: Novig's favourite-longshot bias, and the multiplicative close misreads it
Every row by Novig's price: +400 or longer ROI -15.1% ±6.9 while CLV against Pinnacle's multiplicative close read +7.6%; +233..+400 -7.5%; +150..+233 -2.8%; -110..+110 -2.5%; -150..-110 -1.7%; -233 or shorter -0.8%. Multiplicative devig gives longshots too much chance, so (1) it manufactures "edges" on underdogs and Overs, and (2) CLV measured on a multiplicative close is not money for longshots. The app's own fair is the worst case of four methods (engine/Devig.kt); the backtest was rerun with it and with the raw prices kept (run 2), so the close can be devigged with a method that is calibrated on the results.
