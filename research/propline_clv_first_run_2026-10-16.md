# PropLine CLV of the engine's bets (clv_pinn = Pinnacle's devigged close / our cost - 1)

| group | bets | with a close | mean CLV | beat the close | t |
|:--|--:|--:|--:|--:|--:|
| ALL | 2693 | 151 | +0.26% | 34% | 0.7 |
| shown=0 | 2462 | 101 | -0.87% | 19% | -4.5 |
| shown=1 | 231 | 50 | +2.56% | 66% | 2.5 |
| league=ATP | 67 | 29 | -0.52% | 28% | -1.1 |
| league=MLB | 32 | 0 | | | |
| league=NBA | 159 | 18 | +4.10% | 78% | 3.0 |
| league=NCAAF | 1784 | 18 | +2.15% | 56% | 0.9 |
| league=NFL | 517 | 1 | +1.26% | 100% | 0.0 |
| league=NHL | 37 | 11 | +1.26% | 64% | 1.5 |
| league=WNBA | 11 | 0 | | | |
| league=WTA | 86 | 74 | -0.98% | 16% | -5.1 |
| kind=MONEYLINE | 237 | 26 | -1.23% | 27% | -2.4 |
| kind=PERIOD | 117 | 0 | | | |
| kind=PROP | 46 | 1 | +4.84% | 100% | 0.0 |
| kind=SPREAD | 1313 | 54 | +1.08% | 35% | 1.3 |
| kind=TEAM_TOTAL | 76 | 9 | +1.75% | 78% | 3.2 |
| kind=TOTAL | 904 | 61 | -0.12% | 30% | -0.2 |

Match status: event not in the archive 2385; ok 151; kind PERIOD not mapped (1H Spread) 61; kind PERIOD not mapped (1H Total) 52; no Pinnacle close at this line (moved, or not offe 22; prop type not mapped (Passing Completions) 6; prop type not mapped (Rushing Attempts) 5; kind PERIOD not mapped (1st Inning Total) 3; prop type not mapped (Longest Reception) 2; Pinnacle sides not matched 2

First run on real data (2026-10-16, tools/propline_clv.py over `engine-dataset` lines-2026-10-15..25 and the 90-day `propline-archive-2026-10-16` Release, Novig + Pinnacle). Only bets on games that have already finished have a close (151 of 2,693; the rest are upcoming), so every number is thin and will grow daily. Read as hypotheses, not rule changes (class B needs 200+ settled and an interval that excludes zero):
- Bets the engine LISTED (shown=1): CLV vs Pinnacle's devigged close +2.56%, 66% beat the close (n=50, t=2.5). Bets it saw but did NOT list (shown=0): -0.87%, 19% beat it (n=101, t=-4.5). The selection works: what it lists beats the sharp close, what it hides does not.
- NBA +4.1% (n=18, t=3.0); WTA moneylines -0.98% (n=74, t=-5.1: 16% beat the close); ATP -0.5%. WTA/ATP moneylines look like the weakest group: watch it before any rule change.
- Not yet covered: 1H/period lines (needs the period mapping), NFL/MLB props (stat names), alternate lines. `pl_status` says why per bet.
