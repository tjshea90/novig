# Why most listed bets show only a few books (PS2, Tj 2026-10-25)

Tj: "even though they offer dozens of sports books odds, most of my scanned results only show odds for a few sports books. Is this a matching issue?"

## Measured
1. **PropLine's coverage** (`propline.yml` task `coverage`, `tools/propline.py coverage`, every book's game lines for NFL/NCAAF/NBA/NHL, 2026-10-25 14:35 UTC; full table below): about 100 books price these games.
   On Novig's own lines, the books posting the SAME number are far fewer than the books pricing the market at all:

   | league | market | Novig lines | books at Novig's number (median) | books at any number (median) |
   | :- | :- | -: | -: | -: |
   | NBA | moneyline | 11 | 92 | 92 |
   | NBA | spreads | 165 | 29 | 87 |
   | NBA | totals | 251 | 21 | 86 |
   | NCAAF | spreads | 123 | 9 | 30 |
   | NCAAF | totals | 84 | 19 | 37 |
   | NFL | spreads | 234 | 30 | 79 |
   | NFL | totals | 374 | 26 | 79 |
   | NHL | totals | 37 | 40 | 58 |

2. **What the engine priced with** (its study journal, run 40138528848, 2026-10-24, the Vigilant lines within 2 points of +EV): books per line, median: moneyline **14**, spread **5**, total **5**, team total 3, prop 2, period 1.
   The live list at 14:36 UTC (17 bets) had 1-4 books each, five of them Kalshi alone (alternate numbers: Giants +9.5, Over 38.5, Under 44.5).

3. **Freshness is not the cause**: PropLine's re-read ages of the books Vigilant uses were 0-30 s (`status.propLineFresh`).

## Why
- **Not a matching bug.** A moneyline needs no number, and it is priced by a median 14 books. Spreads and totals are matched at Novig's exact number (a price at another number is a different bet), and Novig lists many alternate numbers that only a few books post. The +EV list is biased toward exactly those thin lines: where 20+ books agree, Novig is rarely off.
- **PropLine's allow-list:** Vigilant asked PropLine for 22 books (`PropLineClient.KNOWN`); the ~70 others were left out until their desks were known, because books on one odds provider must never be counted twice (`BookCompany`).

## Changed
- **13 books added** whose desk is documented (`PropLineClient.ADDED_2026_10_25`): Heritage, TAB (Tabcorp), Tipico, Winamax, Betclic, Stoiximan (Kaizen/Betano) trade their own lines; Paddy Power (Flutter), LiveScore Bet and Superbet (Kambi), Ladbrokes AU and SuperSport (Entain), Betway ZA (Super Group) join their group, filling lines the group's other books lack and never counted twice.
- **Measured one-desk pairs** (identical price on 80%+ of 100+ shared quotes): BetRivers = Unibet (100% of 880, already one Kambi group), Placard = Solverde (95% of 736), Marca Apuestas = Wplay (88% of 330): the last two grouped in `BookCompany`. No other pair of the ~100 books met the rule on game lines.
- The rest of PropLine's list (sweepstakes books, regional lotteries, offshore books with an unknown provider) stays out until a desk is documented; re-run `propline.yml` task `coverage` to re-measure.

## Not done (and why)
- Pricing Novig's alternate number from a book's other number (a push/half-point conversion) would add books to the thin lines, but it is a model with its own error; nothing in the repo measures it yet. Open as TASKS.md PS2b.

## Full probe output

## Games priced by each book (game lines, football_nfl, football_ncaaf, basketball_nba, hockey_nhl)

- polymarket_us: 331
- kalshi: 266
- polymarket: 143
- novig: 83
- sportium: 82
- bet9ja: 81
- betway_za: 81
- pamestoixima: 81
- sportybet_ng: 81
- sts: 81
- teapuesto: 81
- tonybet: 81
- marcaapuestas: 80
- supabets: 80
- thrillzz: 80
- wplay: 80
- bet25: 79
- fortuna_cz: 76
- meridianbet: 76
- novibet: 76
- totalbet: 76
- heritage: 75
- rebet: 75
- betathome: 74
- palmerbet: 74
- draftkings: 73
- fanduel: 73
- psk: 71
- placard: 70
- playalberta: 70
- livescorebet: 69
- betrivers: 68
- fliff: 66
- prophetx: 65
- jazzsports: 65
- sxbet: 65
- fanatics: 63
- coderemx: 62
- betr_au: 61
- stoiximan: 59
- sportzino: 57
- courtside: 56
- strendus: 54
- betclic_pt: 51
- thescorebet: 51
- bet99: 46
- betonlineag: 46
- lowvig: 46
- pmu: 42
- supersport: 42
- onexbet: 40
- playup: 39
- synot: 39
- veikkaus: 39
- hardrock: 38
- playnow: 38
- admiralbet_de: 37
- paddypower: 37
- winamax: 37
- danskespil: 36
- jouezsport: 33
- betus: 29
- lvbet: 29
- miseojeu: 29
- netbet_ro: 29
- toto: 29
- tab_au: 27
- betmgm: 25
- bovada: 25
- norsktipping: 25
- olybet: 25
- betfair_ex: 24
- bookmaker_eu: 24
- ladbrokes_au: 24
- pinnacle: 24
- starsports: 24
- superbet_pl: 24
- superbet_ro: 24
- tipico: 24
- tipp3: 24
- tippmix: 24
- youwager: 24
- betika: 23
- betway: 20
- sbobet: 19
- unibet: 17
- codere: 12
- fortuna_pl: 12
- matchbook: 12
- sazka: 12
- smarkets: 12
- germania: 7
- ladbrokes_be: 7
- napoleon: 7
- soccerbet: 7
- solverde: 7
- sportna: 7
- merkurxbet: 6
- mozzart: 6

## Novig's lines: books posting the SAME number vs books pricing that market at all

- basketball_nba h2h: 11 Novig lines, books at the same number median 92 (any number: median 92)
- basketball_nba spreads: 165 Novig lines, books at the same number median 29 (any number: median 87)
- basketball_nba totals: 251 Novig lines, books at the same number median 21 (any number: median 86)
- football_ncaaf h2h: 30 Novig lines, books at the same number median 41 (any number: median 41)
- football_ncaaf spreads: 123 Novig lines, books at the same number median 9 (any number: median 30)
- football_ncaaf totals: 84 Novig lines, books at the same number median 19 (any number: median 37)
- football_nfl h2h: 27 Novig lines, books at the same number median 42 (any number: median 42)
- football_nfl spreads: 234 Novig lines, books at the same number median 30 (any number: median 79)
- football_nfl totals: 374 Novig lines, books at the same number median 26 (any number: median 79)
- hockey_nhl h2h: 5 Novig lines, books at the same number median 81 (any number: median 81)
- hockey_nhl spreads: 19 Novig lines, books at the same number median 43 (any number: median 61)
- hockey_nhl totals: 37 Novig lines, books at the same number median 40 (any number: median 58)

## Pairs of books with identical prices on 80%+ of 100+ shared quotes (one odds desk)

- betrivers = unibet: 100% of 880
- placard = solverde: 95% of 736
- marcaapuestas = wplay: 88% of 330
