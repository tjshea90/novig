# Parlay maker study (tools/rfq_calibration_study.py)

Graded pregame RFQs: 21589 (executed: 5742, sampled others: 15847); distinct legs graded: 3873; days ['11-17', '11-18', '11-19', '11-20']

## A. Is OUR leg fair calibrated? (every leg of every priced RFQ, executed or not)

**per distinct leg** (2751 legs, 52 games)

| our fair | legs | mean fair | won | won - fair | se over games |
|---|---|---|---|---|---|
| 0.0-0.2 | 199 | 0.113 | 0.106 | -0.7 pts | 3.0 pts |
| 0.2-0.3 | 145 | 0.251 | 0.221 | -3.0 pts | 3.5 pts |
| 0.3-0.4 | 194 | 0.357 | 0.335 | -2.2 pts | 4.0 pts |
| 0.4-0.5 | 492 | 0.466 | 0.447 | -1.9 pts | 1.8 pts |
| 0.5-0.6 | 704 | 0.543 | 0.543 | -0.1 pts | 2.6 pts |
| 0.6-0.7 | 392 | 0.645 | 0.645 | +0.1 pts | 2.7 pts |
| 0.7-0.8 | 291 | 0.748 | 0.825 | +7.7 pts | 3.0 pts |
| 0.8-0.9 | 240 | 0.850 | 0.921 | +7.0 pts | 2.2 pts |
| 0.9-1.0 | 94 | 0.927 | 0.957 | +3.0 pts | 2.0 pts |

**weighted by how often buyers put the leg in a parlay** (2751 legs, 52 games)

| our fair | legs | mean fair | won | won - fair | se over games |
|---|---|---|---|---|---|
| 0.0-0.2 | 199 | 0.118 | 0.037 | -8.1 pts | 2.4 pts |
| 0.2-0.3 | 145 | 0.241 | 0.154 | -8.7 pts | 5.4 pts |
| 0.3-0.4 | 194 | 0.354 | 0.322 | -3.2 pts | 11.6 pts |
| 0.4-0.5 | 492 | 0.478 | 0.392 | -8.6 pts | 3.2 pts |
| 0.5-0.6 | 704 | 0.531 | 0.529 | -0.2 pts | 4.1 pts |
| 0.6-0.7 | 392 | 0.653 | 0.728 | +7.5 pts | 8.4 pts |
| 0.7-0.8 | 291 | 0.762 | 0.808 | +4.6 pts | 9.0 pts |
| 0.8-0.9 | 240 | 0.860 | 0.977 | +11.6 pts | 1.0 pts |
| 0.9-1.0 | 94 | 0.945 | 0.993 | +4.9 pts | 0.8 pts |

By market type (weighted): 
MONEY: 79 legs, fair 0.635, won 0.675 (+4.0 pts); 
SPREAD: 695 legs, fair 0.553, won 0.579 (+2.6 pts); 
TOTAL: 909 legs, fair 0.543, won 0.500 (-4.2 pts); 


## B. Parlay calibration by shape: all sampled RFQs against the ones that executed (buyer selection)

| shape | all: n | fair | buyer won | executed: n | fair | buyer won |
|---|---|---|---|---|---|---|
| 2 legs, no leg >= .8 | 9965 | 0.290 | 0.256 | 2834 | 0.288 | 0.228 |
| 2 legs, both .5-.8 | 4543 | 0.378 | 0.322 | 1167 | 0.371 | 0.251 |
| 2 legs, a leg < .5 | 5422 | 0.215 | 0.201 | 1667 | 0.230 | 0.211 |
| 2 legs, a leg >= .8 | 3047 | 0.553 | 0.658 | 421 | 0.533 | 0.646 |
| 3 legs | 5341 | 0.263 | 0.361 | 1494 | 0.248 | 0.339 |
| 4 legs | 3236 | 0.162 | 0.234 | 993 | 0.145 | 0.193 |

## C. Maker P&L per $ of pot at the clearing price, executed parlays (the profile class: 2 legs, no leg >= .8, fair >= .25); `null` = what the segment earns if our fair is exactly right and legs are linked

```
profile class                                n= 1799 price 0.34 ROI   +9.1%  null  +0.5% sd 5.1%  z +1.7
all executed                                 n= 5742 price 0.28 ROI   -0.6%  null  +0.1% sd 4.9%  z -0.1
-- lead time (hours from the RFQ to the first start)
lead [0,0.25) h                              n=  145 price 0.37 ROI   +0.7%  null  +1.4% sd 7.7%  z -0.1
lead [0.25,1) h                              n=  283 price 0.34 ROI   +3.6%  null  +0.3% sd 6.7%  z +0.5
lead [1,3) h                                 n=  603 price 0.33 ROI   +8.2%  null  +0.7% sd 5.9%  z +1.3
lead [3,8) h                                 n=  542 price 0.33 ROI  +14.6%  null  +0.2% sd 7.1%  z +2.0
lead [8,24) h                                n=  224 price 0.35 ROI  +10.6%  null  +0.4% sd 10.4%  z +1.0
lead [24,99) h                               n=2 (too few)
-- fewest companies behind a leg's fair
books [0,2)                                  n=38 (too few)
books [2,3)                                  n=33 (too few)
books [3,5)                                  n=38 (too few)
books [5,8)                                  n=   55 price 0.38 ROI   -0.5%  null  +0.9% sd 8.1%  z -0.2
books [8,99)                                 n= 1635 price 0.33 ROI   +9.5%  null  +0.3% sd 5.5%  z +1.7
-- hour of the day (ET) the RFQ arrived
hour [0,6) ET                                n=   63 price 0.39 ROI   +3.7%  null  +1.6% sd 11.2%  z +0.2
hour [6,12) ET                               n=  288 price 0.35 ROI  +14.8%  null  +0.7% sd 9.2%  z +1.5
hour [12,18) ET                              n=  837 price 0.33 ROI  +12.6%  null  +0.4% sd 6.0%  z +2.0
hour [18,24) ET                              n=  611 price 0.34 ROI   +2.3%  null  +0.4% sd 6.6%  z +0.3
-- same game or two games; the market pair; the league of the first leg
same game                                    n=  805 price 0.35 ROI  +14.2%  null  +0.6% sd 7.5%  z +1.8
two games                                    n=  994 price 0.33 ROI   +5.1%  null  +0.4% sd 6.5%  z +0.7
types +                                      n=  611 price 0.36 ROI  +12.2%  null  +0.6% sd 8.7%  z +1.3
types MONEY+MONEY                            n=  156 price 0.37 ROI  +11.2%  null  -1.9% sd 15.6%  z +0.8
types MONEY+SPREAD                           n=   72 price 0.35 ROI   -7.6%  null  -0.0% sd 10.5%  z -0.7
types MONEY+TOTAL                            n=   46 price 0.36 ROI   +8.2%  null  +1.0% sd 11.2%  z +0.6
types SPREAD+SPREAD                          n=  147 price 0.31 ROI   +9.9%  null  +1.0% sd 10.0%  z +0.9
types SPREAD+TOTAL                           n=  413 price 0.35 ROI  +13.4%  null  +1.3% sd 9.5%  z +1.3
types TOTAL+TOTAL                            n=  354 price 0.27 ROI   +1.2%  null  +0.3% sd 12.9%  z +0.1
league                                       n=  611 price 0.36 ROI  +12.2%  null  +0.6% sd 8.7%  z +1.3
league NBA                                   n=  401 price 0.29 ROI   +4.6%  null  +0.5% sd 12.1%  z +0.3
league NCAAB                                 n=  151 price 0.31 ROI  +10.1%  null  +0.5% sd 8.1%  z +1.2
league NCAAF                                 n=18 (too few)
league NFL                                   n=  294 price 0.38 ROI  +12.0%  null  +1.3% sd 12.3%  z +0.9
league NHL                                   n=  324 price 0.33 ROI   +6.0%  null  -0.5% sd 12.0%  z +0.5
-- fair of the parlay
fair [0.25,0.30)                             n=  948 price 0.26 ROI   +8.2%  null  -0.1% sd 7.3%  z +1.1
fair [0.30,0.35)                             n=  227 price 0.33 ROI   +7.6%  null  -0.2% sd 8.2%  z +1.0
fair [0.35,0.45)                             n=  327 price 0.41 ROI   +8.2%  null  +0.8% sd 7.1%  z +1.0
fair [0.45,0.55)                             n=  270 price 0.50 ROI  +16.5%  null  +2.5% sd 10.7%  z +1.3
fair [0.55,0.70)                             n=27 (too few)
```

## C2. The strongest leg, and capital: lock time and return on the collateral (executed parlays, 2 legs)

```
2 legs, strongest leg [0.00,0.50)            n=  631 price 0.20 ROI   +2.8%  null  -0.1% sd 8.0%  z +0.4
2 legs, strongest leg [0.50,0.60)            n= 1392 price 0.25 ROI   +5.9%  null  -0.2% sd 6.4%  z +1.0
2 legs, strongest leg [0.60,0.70)            n=  425 price 0.38 ROI   +7.0%  null  +1.2% sd 7.1%  z +0.8
2 legs, strongest leg [0.70,0.80)            n=  386 price 0.47 ROI  +12.6%  null  +1.7% sd 8.8%  z +1.2
2 legs, strongest leg [0.80,0.85)            n=  256 price 0.50 ROI   -9.6%  null  -0.5% sd 13.1%  z -0.7
2 legs, strongest leg [0.85,0.90)            n=  129 price 0.60 ROI  -17.1%  null  -0.1% sd 16.2%  z -1.0
2 legs, strongest leg [0.90,1.01)            n=36 (too few)
-- per lead bucket (profile class): lock = lead + game (3.5 h); ROC = ROI / (1 - price) = return on the collateral; per day = ROC x 24 / lock hours
lead [0,1) h  n= 428  ROI  +2.6%  ROC  +6.0%  lock 3.9 h  ROC per day of lock  +36.3%
lead [1,3) h  n= 603  ROI  +8.2%  ROC +13.1%  lock 5.4 h  ROC per day of lock  +58.3%
lead [3,8) h  n= 542  ROI +14.6%  ROC +21.9%  lock 8.3 h  ROC per day of lock  +63.5%
lead [8,99) h  n= 226  ROI +10.8%  ROC +17.6%  lock 15.3 h  ROC per day of lock  +27.6%
```

## C3. Tiers: how picky can the maker be and still fill the wallet? (the wallet is the limit, so the quality of the fills is what the filters can buy)

`wins/day` = executed parlays of the tier whose price was at or above our quote (what we would win with unlimited cash), average over the 4 days with real flow; `wallet fills/day` = the replay's count with $145 of cash locked until the games end.

```
T0 live profile                              n= 1799 price 0.34 ROI   +9.1%  null  +0.5% sd 5.1%  z +1.7
      wins/day 326 | ROC +14.6% over 7.2 h of lock = +49% per day | wallet fills/day 7.5 (skipped for cash 265)
T1  + lead >= 1 h                            n= 1371 price 0.33 ROI  +11.2%  null  +0.4% sd 5.5%  z +2.0
      wins/day 251 | ROC +17.3% over 8.2 h of lock = +51% per day | wallet fills/day 7.5 (skipped for cash 265)
T2  + lead 1-8 h                             n= 1145 price 0.33 ROI  +11.2%  null  +0.4% sd 5.7%  z +1.9
      wins/day 202 | ROC +17.2% over 6.8 h of lock = +61% per day | wallet fills/day 9.8 (skipped for cash 220)
T3  + fair >= .35                            n=  364 price 0.45 ROI  +11.7%  null  +1.4% sd 7.5%  z +1.4
      wins/day 76 | ROC +21.7% over 6.7 h of lock = +77% per day | wallet fills/day 9.8 (skipped for cash 220)
T4  lead 1-8 h, fair >= .30                  n=  503 price 0.42 ROI  +10.6%  null  +1.0% sd 6.6%  z +1.4
      wins/day 101 | ROC +18.9% over 6.8 h of lock = +66% per day | wallet fills/day 9.8 (skipped for cash 220)
T5  lead 1-24 h, fair >= .35                 n=  455 price 0.46 ROI  +12.7%  null  +1.5% sd 7.4%  z +1.5
      wins/day 99 | ROC +23.4% over 8.5 h of lock = +66% per day | wallet fills/day 7.5 (skipped for cash 265)
T6  lead 1-8 h, fair .25-.35                 n=  781 price 0.27 ROI  +11.0%  null  +0.0% sd 7.1%  z +1.5
      wins/day 126 | ROC +15.2% over 6.8 h of lock = +54% per day | wallet fills/day 0.0 (skipped for cash 0)
T7  first start >= 1 h, last <= 8 h, fair >= .35 n=  327 price 0.46 ROI  +11.0%  null  +1.3% sd 7.8%  z +1.2
      wins/day 67 | ROC +20.5% over 6.4 h of lock = +77% per day | wallet fills/day 8.8 (skipped for cash 192)
T8  first >= 1 h, last <= 12 h, fair >= .35  n=  405 price 0.46 ROI  +12.0%  null  +1.4% sd 7.4%  z +1.4
      wins/day 86 | ROC +22.3% over 7.4 h of lock = +73% per day | wallet fills/day 7.5 (skipped for cash 265)
T9  first >= 1 h, last <= 8 h, fair >= .40   n=  249 price 0.48 ROI  +12.2%  null  +1.8% sd 8.8%  z +1.2
      wins/day 53 | ROC +23.4% over 6.4 h of lock = +88% per day | wallet fills/day 7.0 (skipped for cash 155)
```

## C4. Margin when the wallet is the limit: tier T7, our quote at fair + m points (the wins per day beyond the wallet's capacity are worth nothing, so the margin can rise until the wins just fill it)

```
wallet capacity of T7 (replay, $145): 8.8 fills a day; executed parlays in the tier: 327 over 4 days
m=+0.00 pt: wins/day  67.2 ( 7.7x the wallet's fills) | ROI of the wins  +9.5% | expected profit per RFQ entered +7.82%
m=+0.10 pt: wins/day  66.8 ( 7.6x the wallet's fills) | ROI of the wins  +9.7% | expected profit per RFQ entered +7.94%
m=+0.30 pt: wins/day  63.0 ( 7.2x the wallet's fills) | ROI of the wins  +9.8% | expected profit per RFQ entered +7.59%
m=+0.50 pt: wins/day  57.8 ( 6.6x the wallet's fills) | ROI of the wins +10.7% | expected profit per RFQ entered +7.57%
m=+0.75 pt: wins/day  46.5 ( 5.3x the wallet's fills) | ROI of the wins +10.0% | expected profit per RFQ entered +5.68%
m=+1.00 pt: wins/day  34.2 ( 3.9x the wallet's fills) | ROI of the wins  +9.9% | expected profit per RFQ entered +4.17%
m=+1.50 pt: wins/day  17.0 ( 1.9x the wallet's fills) | ROI of the wins  +9.2% | expected profit per RFQ entered +1.91%
m=+2.00 pt: wins/day   7.0 ( 0.8x the wallet's fills) | ROI of the wins  -4.0% | expected profit per RFQ entered -0.35%
```

## C5. The scarcity score of the parlays the live profile quotes (24 / (lock hours x (1 - our price)): the daily return on the collateral per unit of pot edge)

```
n 402 | quantiles of the score: p10 3.4 p25 4.0 p50 5.7 p75 7.4 p85 8.5 p90 9.0 p95 9.8
loose base (fair >= .25, lead up to 24 h): n 1329 | p10 2.5 p25 3.5 p40 4.4 p50 4.8 p60 5.3 p75 6.2 p90 7.2
```

## D. Replay of the live profile on the executed flow, wallet $145, every setting varied one at a time

`fills` = parlays the config would have quoted AND won (the market's price at or above our quote), taken in time order from the wallet's free cash; P&L in dollars at the executed stakes (~$10); `pnl/day` over the days with executed flow.

| variant | fills | per day | pot $/day (the profit at a constant edge) | pnl $ | ROI of pot | by day | skipped (cash / identical / outcome / leg share / scarcity gate) |
|---|---|---|---|---|---|---|---|
| PROFILE (live) | 30 | 10.0 | 206 | +72.00 | +11.6% | 11-18:+24.2 11-19:+43.1 11-20:+4.7 | 265 / 14 / 19 / 9 / 0 |
| no wallet limit | 118 | 39.3 | 2641 | +541.51 | +6.8% | 11-18:-536.0 11-19:+1022.4 11-20:+55.2 | 0 / 70 / 149 / 0 / 0 |
| wallet $300 | 37 | 12.3 | 378 | +123.65 | +10.9% | 11-18:-32.6 11-19:+101.1 11-20:+55.2 | 279 / 11 / 5 / 5 / 0 |
| maxLegs 3 | 30 | 10.0 | 206 | +72.00 | +11.6% | 11-18:+24.2 11-19:+43.1 11-20:+4.7 | 273 / 14 / 21 / 9 / 0 |
| maxLegFair .7 | 28 | 9.3 | 178 | +88.38 | +16.5% | 11-18:+14.2 11-19:+69.5 11-20:+4.7 | 87 / 2 / 10 / 6 / 0 |
| maxLegFair .9 | 29 | 9.7 | 184 | +62.36 | +11.3% | 11-18:-21.7 11-19:+79.3 11-20:+4.7 | 433 / 7 / 12 / 5 / 0 |
| minFair .15 | 27 | 9.0 | 189 | -12.31 | -2.2% | 11-18:-4.7 11-19:+13.5 11-20:-21.1 | 1042 / 36 / 7 / 24 / 0 |
| minFair .35 | 30 | 10.0 | 206 | +72.00 | +11.6% | 11-18:+24.2 11-19:+43.1 11-20:+4.7 | 265 / 14 / 19 / 9 / 0 |
| maxFair .5 | 33 | 11.0 | 189 | +53.91 | +9.5% | 11-18:+18.9 11-19:+30.3 11-20:+4.7 | 187 / 20 / 16 / 10 / 0 |
| maxFair .8 | 30 | 10.0 | 206 | +72.00 | +11.6% | 11-18:+24.2 11-19:+43.1 11-20:+4.7 | 265 / 14 / 19 / 9 / 0 |
| onlyToday | 30 | 10.0 | 206 | +72.00 | +11.6% | 11-18:+24.2 11-19:+43.1 11-20:+4.7 | 265 / 14 / 19 / 9 / 0 |
| maxDaysAhead 1 | 30 | 10.0 | 206 | +72.00 | +11.6% | 11-18:+24.2 11-19:+43.1 11-20:+4.7 | 265 / 14 / 19 / 9 / 0 |
| minLead 10 min | 34 | 8.5 | 187 | +73.05 | +9.8% | 11-17:+23.6 11-18:+8.5 11-19:+47.8 11-20:-6.9 | 361 / 17 / 22 / 11 / 0 |
| minLead 30 min | 33 | 8.2 | 181 | +84.64 | +11.7% | 11-17:+23.6 11-18:+8.5 11-19:+47.8 11-20:+4.7 | 307 / 17 / 22 / 10 / 0 |
| minLead 1 h | 30 | 10.0 | 206 | +72.00 | +11.6% | 11-18:+24.2 11-19:+43.1 11-20:+4.7 | 265 / 14 / 19 / 9 / 0 |
| minBooks 3 | 22 | 7.3 | 170 | +29.82 | +5.9% | 11-18:-14.8 11-19:+39.9 11-20:+4.7 | 276 / 9 / 11 / 5 / 0 |
| margin +0.5 pt | 35 | 11.7 | 211 | +47.50 | +7.5% | 11-18:+32.3 11-19:+15.2 | 183 / 7 / 24 / 13 / 0 |
| margin +1 pt | 33 | 11.0 | 205 | +64.07 | +10.4% | 11-18:+18.9 11-19:+45.2 | 93 / 1 / 13 / 9 / 0 |
| margin -0.5 pt | 32 | 10.7 | 204 | +58.52 | +9.6% | 11-18:+16.7 11-19:+37.1 11-20:+4.7 | 296 / 14 / 20 / 12 / 0 |
| no guards | 22 | 7.3 | 180 | +45.25 | +8.4% | 11-18:-59.8 11-19:+49.9 11-20:+55.2 | 315 / 0 / 0 / 0 / 0 |
| LOOSE fixed (minFair .25, lead up to 24 h), $145 | 31 | 7.8 | 129 | +18.04 | +3.5% | 11-17:-20.9 11-18:+15.4 11-19:+9.5 11-20:+14.0 | 864 / 12 / 14 / 6 / 0 |
| LOOSE + scarcity gate, $145 | 24 | 6.0 | 119 | -31.71 | -6.6% | 11-17:-12.8 11-18:-38.9 11-19:+5.9 11-20:+14.0 | 74 / 4 / 1 / 5 / 1162 |
| core fixed, wallet $1200 | 84 | 28.0 | 1326 | +194.24 | +4.9% | 11-18:-240.1 11-19:+379.1 11-20:+55.2 | 207 / 36 / 5 / 5 / 0 |
| LOOSE fixed, wallet $1200 | 63 | 15.8 | 967 | +584.44 | +15.1% | 11-17:-188.3 11-18:+577.6 11-19:+116.7 11-20:+78.5 | 837 / 25 / 0 / 2 / 0 |
| LOOSE + gate, wallet $1200 | 58 | 14.5 | 929 | +314.98 | +8.5% | 11-17:-144.3 11-18:+250.1 11-19:+130.8 11-20:+78.5 | 48 / 18 / 0 / 6 / 1130 |
| LOOSE fixed, wallet $300 | 47 | 11.8 | 312 | +214.68 | +17.2% | 11-17:-13.2 11-18:+140.1 11-19:+9.3 11-20:+78.5 | 855 / 12 / 6 / 7 / 0 |
| LOOSE + gate, wallet $300 | 51 | 12.8 | 359 | +347.13 | +24.1% | 11-17:+8.4 11-18:+183.4 11-19:+76.9 11-20:+78.5 | 63 / 23 / 11 / 11 / 1095 |
| LOOSE fixed, wallet $600 | 65 | 16.2 | 566 | +405.56 | +17.9% | 11-17:+44.8 11-18:+227.8 11-19:+54.5 11-20:+78.5 | 807 / 44 / 1 / 10 / 0 |
| LOOSE + gate, wallet $600 | 71 | 17.8 | 635 | +608.72 | +24.0% | 11-17:+68.9 11-18:+250.3 11-19:+211.0 11-20:+78.5 | 53 / 37 / 5 / 9 / 1075 |
| WALLET $145 (live) | 30 | 10.0 | 206 | +72.00 | +11.6% | 11-18:+24.2 11-19:+43.1 11-20:+4.7 | 265 / 14 / 19 / 9 / 0 |
| WALLET $300 | 37 | 12.3 | 378 | +123.65 | +10.9% | 11-18:-32.6 11-19:+101.1 11-20:+55.2 | 279 / 11 / 5 / 5 / 0 |
| WALLET $600 | 60 | 20.0 | 758 | +327.26 | +14.4% | 11-18:+20.1 11-19:+252.0 11-20:+55.2 | 241 / 25 / 6 / 5 / 0 |
| WALLET $1200 | 84 | 28.0 | 1326 | +194.24 | +4.9% | 11-18:-240.1 11-19:+379.1 11-20:+55.2 | 207 / 36 / 5 / 5 / 0 |
| WALLET $600, outcome count fixed at 2 | 58 | 19.3 | 745 | +296.77 | +13.3% | 11-18:-12.1 11-19:+253.8 11-20:+55.2 | 206 / 26 / 46 / 1 / 0 |
| WALLET $1200, outcome count fixed at 2 | 76 | 25.3 | 1343 | +634.37 | +15.7% | 11-18:-96.0 11-19:+675.2 11-20:+55.2 | 143 / 38 / 77 / 3 / 0 |
| PW1 adaptive (live profile + scarcity gate) | 32 | 10.7 | 192 | +56.83 | +9.8% | 11-18:+2.3 11-19:+49.8 11-20:+4.7 | 68 / 4 / 16 / 7 / 240 |
| PW1 adaptive, wallet $300 | 42 | 14.0 | 359 | +111.55 | +10.3% | 11-18:-74.1 11-19:+130.5 11-20:+55.2 | 59 / 5 / 6 / 4 / 254 |
| PW1 adaptive, wallet $600 | 62 | 20.7 | 724 | +306.42 | +14.1% | 11-18:-54.4 11-19:+305.7 11-20:+55.2 | 53 / 25 / 4 / 1 / 217 |
| fixed rules, wallet $600 | 60 | 20.0 | 758 | +327.26 | +14.4% | 11-18:+20.1 11-19:+252.0 11-20:+55.2 | 241 / 25 / 6 / 5 / 0 |
| PW1 gate 5 (looser) | 34 | 11.3 | 219 | +67.21 | +10.3% | 11-18:+37.9 11-19:+24.6 11-20:+4.7 | 174 / 15 / 24 / 11 / 57 |
| PW1 gate 11 (stricter) | 27 | 9.0 | 197 | +89.13 | +15.1% | 11-18:+16.4 11-19:+68.0 11-20:+4.7 | 13 / 4 / 16 / 8 / 326 |
| PW1 no margin ramp | 31 | 10.3 | 192 | +57.09 | +9.9% | 11-18:+2.9 11-19:+49.5 11-20:+4.7 | 74 / 4 / 16 / 7 / 244 |
