# Parlay maker study (tools/rfq_calibration_study.py)

Graded pregame RFQs: 21425 (executed: 5698, sampled others: 15727); distinct legs graded: 3827; days ['11-17', '11-18', '11-19', '11-20']

## A. Is OUR leg fair calibrated? (every leg of every priced RFQ, executed or not)

**per distinct leg** (2729 legs, 51 games)

| our fair | legs | mean fair | won | won - fair | se over games |
|---|---|---|---|---|---|
| 0.0-0.2 | 199 | 0.113 | 0.106 | -0.7 pts | 2.8 pts |
| 0.2-0.3 | 145 | 0.251 | 0.221 | -3.0 pts | 3.0 pts |
| 0.3-0.4 | 193 | 0.357 | 0.337 | -2.0 pts | 3.9 pts |
| 0.4-0.5 | 487 | 0.466 | 0.446 | -2.0 pts | 1.8 pts |
| 0.5-0.6 | 696 | 0.543 | 0.543 | -0.0 pts | 2.7 pts |
| 0.6-0.7 | 385 | 0.645 | 0.647 | +0.2 pts | 2.8 pts |
| 0.7-0.8 | 290 | 0.748 | 0.824 | +7.6 pts | 2.9 pts |
| 0.8-0.9 | 240 | 0.850 | 0.921 | +7.0 pts | 2.1 pts |
| 0.9-1.0 | 94 | 0.927 | 0.957 | +3.0 pts | 2.1 pts |

**weighted by how often buyers put the leg in a parlay** (2729 legs, 51 games)

| our fair | legs | mean fair | won | won - fair | se over games |
|---|---|---|---|---|---|
| 0.0-0.2 | 199 | 0.118 | 0.037 | -8.1 pts | 2.6 pts |
| 0.2-0.3 | 145 | 0.241 | 0.154 | -8.7 pts | 5.2 pts |
| 0.3-0.4 | 193 | 0.353 | 0.326 | -2.7 pts | 12.2 pts |
| 0.4-0.5 | 487 | 0.478 | 0.388 | -9.0 pts | 2.9 pts |
| 0.5-0.6 | 696 | 0.531 | 0.532 | +0.0 pts | 4.7 pts |
| 0.6-0.7 | 385 | 0.653 | 0.727 | +7.4 pts | 8.4 pts |
| 0.7-0.8 | 290 | 0.762 | 0.808 | +4.6 pts | 8.6 pts |
| 0.8-0.9 | 240 | 0.860 | 0.977 | +11.6 pts | 1.0 pts |
| 0.9-1.0 | 94 | 0.944 | 0.993 | +4.9 pts | 0.8 pts |

By market type (weighted): 
MONEY: 77 legs, fair 0.635, won 0.675 (+4.0 pts); 
SPREAD: 685 legs, fair 0.553, won 0.581 (+2.8 pts); 
TOTAL: 899 legs, fair 0.543, won 0.499 (-4.3 pts); 


## B. Parlay calibration by shape: all sampled RFQs against the ones that executed (buyer selection)

| shape | all: n | fair | buyer won | executed: n | fair | buyer won |
|---|---|---|---|---|---|---|
| 2 legs, no leg >= .8 | 9836 | 0.290 | 0.256 | 2792 | 0.288 | 0.228 |
| 2 legs, both .5-.8 | 4476 | 0.380 | 0.323 | 1145 | 0.372 | 0.254 |
| 2 legs, a leg < .5 | 5360 | 0.215 | 0.200 | 1647 | 0.229 | 0.209 |
| 2 legs, a leg >= .8 | 3016 | 0.553 | 0.658 | 420 | 0.534 | 0.648 |
| 3 legs | 5338 | 0.263 | 0.361 | 1493 | 0.248 | 0.339 |
| 4 legs | 3235 | 0.162 | 0.234 | 993 | 0.145 | 0.193 |

## C. Maker P&L per $ of pot at the clearing price, executed parlays (the profile class: 2 legs, no leg >= .8, fair >= .25); `null` = what the segment earns if our fair is exactly right and legs are linked

```
profile class                                n= 1770 price 0.34 ROI   +9.0%  null  +0.1% sd 5.2%  z +1.7
all executed                                 n= 5698 price 0.28 ROI   -0.6%  null  +0.4% sd 5.1%  z -0.2
-- lead time (hours from the RFQ to the first start)
lead [0,0.25) h                              n=  144 price 0.37 ROI   +0.6%  null  +0.3% sd 7.8%  z +0.0
lead [0.25,1) h                              n=  281 price 0.34 ROI   +3.7%  null  -0.3% sd 7.4%  z +0.5
lead [1,3) h                                 n=  579 price 0.34 ROI   +7.8%  null  +0.0% sd 6.2%  z +1.3
lead [3,8) h                                 n=  540 price 0.33 ROI  +14.5%  null  +0.0% sd 7.2%  z +2.0
lead [8,24) h                                n=  224 price 0.35 ROI  +10.6%  null  +0.5% sd 10.3%  z +1.0
lead [24,99) h                               n=2 (too few)
-- fewest companies behind a leg's fair
books [0,2)                                  n=38 (too few)
books [2,3)                                  n=31 (too few)
books [3,5)                                  n=35 (too few)
books [5,8)                                  n=   53 price 0.38 ROI   -1.6%  null  +0.4% sd 8.6%  z -0.2
books [8,99)                                 n= 1613 price 0.33 ROI   +9.3%  null  +0.0% sd 5.6%  z +1.6
-- hour of the day (ET) the RFQ arrived
hour [0,6) ET                                n=   62 price 0.39 ROI   +3.3%  null  +0.9% sd 11.1%  z +0.2
hour [6,12) ET                               n=  260 price 0.36 ROI  +14.5%  null  +0.3% sd 9.5%  z +1.5
hour [12,18) ET                              n=  837 price 0.33 ROI  +12.6%  null  +0.2% sd 6.2%  z +2.0
hour [18,24) ET                              n=  611 price 0.34 ROI   +2.3%  null  -0.3% sd 7.1%  z +0.4
-- same game or two games; the market pair; the league of the first leg
same game                                    n=  802 price 0.35 ROI  +14.2%  null  +0.0% sd 7.4%  z +1.9
two games                                    n=  968 price 0.33 ROI   +4.7%  null  +0.1% sd 6.5%  z +0.7
types +                                      n=  611 price 0.36 ROI  +12.2%  null  +0.4% sd 8.5%  z +1.4
types MONEY+MONEY                            n=  156 price 0.37 ROI  +11.2%  null  -0.4% sd 15.0%  z +0.8
types MONEY+SPREAD                           n=   71 price 0.35 ROI   -6.9%  null  +1.4% sd 11.1%  z -0.7
types MONEY+TOTAL                            n=   46 price 0.36 ROI   +8.2%  null  +0.2% sd 11.4%  z +0.7
types SPREAD+SPREAD                          n=  124 price 0.32 ROI   +7.6%  null  +0.3% sd 11.4%  z +0.6
types SPREAD+TOTAL                           n=  408 price 0.35 ROI  +13.4%  null  -0.1% sd 9.9%  z +1.4
types TOTAL+TOTAL                            n=  354 price 0.27 ROI   +1.2%  null  -0.6% sd 13.0%  z +0.1
league                                       n=  611 price 0.36 ROI  +12.2%  null  +0.4% sd 8.5%  z +1.4
league NBA                                   n=  401 price 0.29 ROI   +4.6%  null  -0.1% sd 12.6%  z +0.4
league NCAAB                                 n=  122 price 0.31 ROI   +8.4%  null  -0.0% sd 8.2%  z +1.0
league NCAAF                                 n=18 (too few)
league NFL                                   n=  294 price 0.38 ROI  +12.0%  null  +0.0% sd 12.9%  z +0.9
league NHL                                   n=  324 price 0.33 ROI   +6.0%  null  -0.5% sd 12.4%  z +0.5
-- fair of the parlay
fair [0.25,0.30)                             n=  924 price 0.26 ROI   +7.9%  null  -0.5% sd 7.9%  z +1.1
fair [0.30,0.35)                             n=  225 price 0.33 ROI   +7.4%  null  -0.2% sd 7.9%  z +1.0
fair [0.35,0.45)                             n=  325 price 0.41 ROI   +8.3%  null  +0.7% sd 6.8%  z +1.1
fair [0.45,0.55)                             n=  269 price 0.50 ROI  +16.7%  null  +1.3% sd 10.8%  z +1.4
fair [0.55,0.70)                             n=27 (too few)
```

## C2. The strongest leg, and capital: lock time and return on the collateral (executed parlays, 2 legs)

```
2 legs, strongest leg [0.00,0.50)            n=  626 price 0.20 ROI   +2.9%  null  +0.7% sd 8.0%  z +0.3
2 legs, strongest leg [0.50,0.60)            n= 1359 price 0.25 ROI   +5.8%  null  -0.2% sd 6.7%  z +0.9
2 legs, strongest leg [0.60,0.70)            n=  423 price 0.38 ROI   +6.9%  null  +0.6% sd 6.8%  z +0.9
2 legs, strongest leg [0.70,0.80)            n=  384 price 0.47 ROI  +12.9%  null  +1.1% sd 8.7%  z +1.4
2 legs, strongest leg [0.80,0.85)            n=  256 price 0.50 ROI   -9.6%  null  +0.1% sd 13.4%  z -0.7
2 legs, strongest leg [0.85,0.90)            n=  129 price 0.60 ROI  -17.1%  null  +0.4% sd 16.0%  z -1.1
2 legs, strongest leg [0.90,1.01)            n=35 (too few)
-- per lead bucket (profile class): lock = lead + game (3.5 h); ROC = ROI / (1 - price) = return on the collateral; per day = ROC x 24 / lock hours
lead [0,1) h  n= 425  ROI  +2.6%  ROC  +6.0%  lock 3.9 h  ROC per day of lock  +36.9%
lead [1,3) h  n= 579  ROI  +7.8%  ROC +12.7%  lock 5.4 h  ROC per day of lock  +56.6%
lead [3,8) h  n= 540  ROI +14.5%  ROC +21.8%  lock 8.3 h  ROC per day of lock  +63.3%
lead [8,99) h  n= 226  ROI +10.8%  ROC +17.6%  lock 15.3 h  ROC per day of lock  +27.6%
```

## C3. Tiers: how picky can the maker be and still fill the wallet? (the wallet is the limit, so the quality of the fills is what the filters can buy)

`wins/day` = executed parlays of the tier whose price was at or above our quote (what we would win with unlimited cash), average over the 3 days with real flow; `wallet fills/day` = the replay's count with $145 of cash locked until the games end.

```
T0 live profile                              n= 1770 price 0.34 ROI   +9.0%  null  +0.1% sd 5.2%  z +1.7
      wins/day 432 | ROC +14.5% over 7.2 h of lock = +48% per day | wallet fills/day 13.0 (skipped for cash 1128)
T1  + lead >= 1 h                            n= 1345 price 0.34 ROI  +11.0%  null  +0.1% sd 5.5%  z +2.0
      wins/day 333 | ROC +17.2% over 8.2 h of lock = +50% per day | wallet fills/day 9.7 (skipped for cash 867)
T2  + lead 1-8 h                             n= 1119 price 0.33 ROI  +11.0%  null  +0.0% sd 5.8%  z +1.9
      wins/day 268 | ROC +17.1% over 6.8 h of lock = +61% per day | wallet fills/day 11.0 (skipped for cash 665)
T3  + fair >= .35                            n=  362 price 0.45 ROI  +11.8%  null  +0.9% sd 7.2%  z +1.5
      wins/day 101 | ROC +21.9% over 6.8 h of lock = +78% per day | wallet fills/day 12.7 (skipped for cash 220)
T4  lead 1-8 h, fair >= .30                  n=  500 price 0.42 ROI  +10.6%  null  +0.6% sd 6.2%  z +1.6
      wins/day 134 | ROC +19.0% over 6.8 h of lock = +66% per day | wallet fills/day 10.3 (skipped for cash 299)
T5  lead 1-24 h, fair >= .35                 n=  453 price 0.46 ROI  +12.8%  null  +1.1% sd 7.0%  z +1.7
      wins/day 131 | ROC +23.6% over 8.6 h of lock = +66% per day | wallet fills/day 11.0 (skipped for cash 276)
T6  lead 1-8 h, fair .25-.35                 n=  757 price 0.27 ROI  +10.7%  null  -0.4% sd 7.7%  z +1.4
      wins/day 167 | ROC +14.8% over 6.8 h of lock = +52% per day | wallet fills/day 6.7 (skipped for cash 416)
T7  first start >= 1 h, last <= 8 h, fair >= .35 n=  325 price 0.46 ROI  +11.1%  null  +0.9% sd 7.6%  z +1.3
      wins/day 89 | ROC +20.7% over 6.4 h of lock = +78% per day | wallet fills/day 11.3 (skipped for cash 192)
T8  first >= 1 h, last <= 12 h, fair >= .35  n=  403 price 0.46 ROI  +12.1%  null  +0.9% sd 7.0%  z +1.6
      wins/day 115 | ROC +22.5% over 7.4 h of lock = +73% per day | wallet fills/day 9.7 (skipped for cash 265)
T9  first >= 1 h, last <= 8 h, fair >= .40   n=  248 price 0.48 ROI  +12.5%  null  +1.0% sd 8.6%  z +1.3
      wins/day 71 | ROC +23.9% over 6.4 h of lock = +90% per day | wallet fills/day 9.3 (skipped for cash 155)
```

## C4. Margin when the wallet is the limit: tier T7, our quote at fair + m points (the wins per day beyond the wallet's capacity are worth nothing, so the margin can rise until the wins just fill it)

```
wallet capacity of T7 (replay, $145): 11.3 fills a day; executed parlays in the tier: 325 over 3 days
m=+0.00 pt: wins/day  89.3 ( 7.9x the wallet's fills) | ROI of the wins  +9.4% | expected profit per RFQ entered +7.74%
m=+0.10 pt: wins/day  88.7 ( 7.8x the wallet's fills) | ROI of the wins  +9.6% | expected profit per RFQ entered +7.87%
m=+0.30 pt: wins/day  83.7 ( 7.4x the wallet's fills) | ROI of the wins  +9.7% | expected profit per RFQ entered +7.51%
m=+0.50 pt: wins/day  77.0 ( 6.8x the wallet's fills) | ROI of the wins +10.7% | expected profit per RFQ entered +7.61%
m=+0.75 pt: wins/day  62.0 ( 5.5x the wallet's fills) | ROI of the wins +10.0% | expected profit per RFQ entered +5.71%
m=+1.00 pt: wins/day  45.7 ( 4.0x the wallet's fills) | ROI of the wins  +9.9% | expected profit per RFQ entered +4.19%
m=+1.50 pt: wins/day  22.7 ( 2.0x the wallet's fills) | ROI of the wins  +9.2% | expected profit per RFQ entered +1.92%
m=+2.00 pt: wins/day   9.3 ( 0.8x the wallet's fills) | ROI of the wins  -4.0% | expected profit per RFQ entered -0.35%
```

## D. Replay of the live profile on the executed flow, wallet $145, every setting varied one at a time

`fills` = parlays the config would have quoted AND won (the market's price at or above our quote), taken in time order from the wallet's free cash; P&L in dollars at the executed stakes (~$10); `pnl/day` over the days with executed flow.

| variant | fills | per day | pnl $ | ROI of pot | peak days | skipped (cash / identical / outcome / leg share) |
|---|---|---|---|---|---|---|
| PROFILE (live) | 39 | 9.8 | +96.98 | +14.4% | 11-17:+44.1 11-18:+40.7 11-19:+12.2 | 1128 / 9 / 18 / 8 |
| no wallet limit | 284 | 71.0 | +1389.23 | +4.4% | 11-17:-80.6 11-18:-671.7 11-19:+1528.2 11-20:+613.4 | 0 / 382 / 536 / 0 |
| wallet $300 | 48 | 12.0 | +256.97 | +19.4% | 11-17:+47.3 11-18:+119.6 11-19:+39.6 11-20:+50.4 | 1116 / 13 / 17 / 8 |
| maxLegs 3 | 41 | 10.2 | +82.07 | +12.3% | 11-17:+45.2 11-18:+14.5 11-19:+22.4 | 1227 / 10 / 21 / 9 |
| maxLegFair .7 | 25 | 6.2 | +5.92 | +1.5% | 11-17:-35.6 11-18:+27.0 11-19:+14.5 | 830 / 4 / 3 / 5 |
| maxLegFair .9 | 40 | 10.0 | +30.93 | +5.0% | 11-17:+4.5 11-18:+13.6 11-19:+12.8 | 1340 / 17 / 25 / 8 |
| minFair .15 | 44 | 11.0 | +124.69 | +16.5% | 11-17:+47.1 11-18:+59.1 11-19:+17.9 11-20:+0.5 | 1494 / 9 / 15 / 8 |
| minFair .35 | 35 | 8.8 | +120.31 | +16.6% | 11-17:+57.3 11-18:+37.6 11-19:+25.4 | 436 / 21 / 31 / 9 |
| maxFair .5 | 41 | 10.2 | +95.79 | +14.8% | 11-17:+44.1 11-18:+25.2 11-19:+26.5 | 1020 / 6 / 16 / 7 |
| maxFair .8 | 39 | 9.8 | +96.98 | +14.4% | 11-17:+44.1 11-18:+40.7 11-19:+12.2 | 1128 / 9 / 18 / 8 |
| onlyToday | 35 | 8.8 | +125.27 | +16.9% | 11-17:+23.6 11-18:+57.4 11-19:+35.2 11-20:+9.0 | 1073 / 11 / 9 / 14 |
| maxDaysAhead 1 | 39 | 9.8 | +96.98 | +14.4% | 11-17:+44.1 11-18:+40.7 11-19:+12.2 | 1124 / 9 / 18 / 8 |
| minLead 10 min | 38 | 9.5 | +110.22 | +16.3% | 11-17:+44.1 11-18:+40.7 11-19:+25.4 | 1062 / 9 / 17 / 8 |
| minLead 30 min | 38 | 9.5 | +110.22 | +16.3% | 11-17:+44.1 11-18:+40.7 11-19:+25.4 | 980 / 9 / 17 / 5 |
| minLead 1 h | 29 | 7.2 | +4.02 | +0.9% | 11-17:-20.9 11-18:+15.4 11-19:+9.5 | 867 / 11 / 14 / 5 |
| minBooks 3 | 37 | 9.2 | +92.59 | +13.4% | 11-17:+44.1 11-18:+51.9 11-19:-3.4 | 1098 / 11 / 22 / 7 |
| margin +0.5 pt | 37 | 9.2 | +140.52 | +19.7% | 11-17:+31.0 11-18:+86.5 11-19:+23.1 | 486 / 15 / 18 / 8 |
| margin +1 pt | 24 | 6.0 | -19.00 | -3.3% | 11-17:+0.1 11-18:-22.7 11-19:-5.4 11-20:+9.0 | 245 / 3 / 3 / 3 |
| margin -0.5 pt | 39 | 9.8 | +99.57 | +13.7% | 11-17:+45.3 11-18:+44.6 11-19:+9.6 | 1448 / 10 / 19 / 7 |
| no guards | 37 | 9.2 | +189.91 | +17.7% | 11-17:+34.1 11-18:+65.9 11-19:+30.4 11-20:+59.5 | 1165 / 0 / 0 / 0 |
