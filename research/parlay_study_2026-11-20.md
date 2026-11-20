# How to profit as a parlay maker, and are the live settings right? (PS1, tj Gmail, 2026-11-20 20:00 UTC)

Tj, 19:2x UTC: "Do another study on how to best profit with the parlay system. Verify your current settings."
Tool: `tools/rfq_calibration_study.py` (new; output in `research/parlay_study_2026-11-20_output.md`; also run daily by `rfq-pnl-study.yml`, Release asset `STUDY.md`). Data: the engine's journals of 11-17 to 11-20 (600 MB, `cloud-archive-*` Releases), **21,425 graded pregame parlay requests** (5,698 of them executed on Novig, the rest a 1-in-6 sample of the requests nobody took), 3,827 distinct legs graded from ESPN's final scores, four sports days (11-20 partly). What is new against the earlier studies: it looks at EVERY request, not only the ones that traded (no buyer selection), at the fair line's calibration leg by leg, at lead time, and at the wallet.

## 1. What the data says
1. **The edge is real but small, and the evidence is moderate.** The live class (2 legs, no leg >= .8, our fair >= .25): maker P&L at the clearing price **+9.0% of pot** (n 1,770; if our fair were exactly right and the legs linked it would be +0.1% with a spread of 5.2%, so z +1.7). Unselected check: of ALL requests of that shape buyers win 25.6% against our fair of 29.0%; of the ones that executed 22.8% against 28.8%: the buyers who pull the trigger are worse than the average request (the sucker selection), and even the average request is below our fair. Shrunk toward zero with a prior spread of 3% the best estimate of the edge is **about +2.3% of pot (+/- 2.6%)**, not +9%.
2. **Shape still decides.** 3 legs: buyers win 36.1% against a 26.3% fair (+9.8 points, unselected); 4 legs 23.4% against 16.2%; a leg >= .8: 65.8% against 55.3% (and -9.6% / -17.1% for the maker at .80-.85 / .85-.90). Leg by leg, our fair is right within 3 points below .70 and runs LOW for favourites: legs at .70-.80 win 82.4% against 74.8% (+7.6, se 2.9 over games), .80-.90 92.1% against 85.0% (+7.0, se 2.1). The legs buyers actually choose are worse still: favourites win +4.6 to +11.6 points over fair, longshot and pick-em legs win 8 to 9 points UNDER it. That is the mechanism of "sharps take heavy favourites, suckers take longshots" and why the engine's shape filters are the lever.
3. **The margin is not the lever** (confirmed again on this larger set): in the tier we quote, the ROI of the parlays we win is +9.4% at fair + 0 and +10.7% at +0.5 point (the wallet is full either way, section 3), -4.0% at +2.
4. **Lead time matters.** Maker P&L by hours from the request to the first start: under 15 min +0.6% (z 0.0), 15-60 min +3.7% (z +0.5), 1-3 h +7.8% (z +1.3), **3-8 h +14.5% (z +2.0)**, 8-24 h +10.6% (z +1.0). Late buyers know lineups and moves. Locked collateral earns per day of lock: +37% (under 1 h), +57%, +63% (3-8 h), +28% (over 8 h: the lock is 15 h on average).
5. **Same-game pairs are the best subclass**: +13.0% (n 1,006, z +2.0) against +3.7% for pairs from two games (z +0.5); SPREAD + TOTAL +13.4%.
6. **No signal** in: companies behind the fair (2-4 companies +9.4% on 104, 5-7 -1.6% on 53: no order), hour of day beyond the lead effect, league (NBA +4.6, NFL +12.0, NHL +6.0, NCAAB +8.4: all inside the noise), market pair beyond same-game.

## 2. The wallet is the limit, not the flow
We would WIN about 430 of these parlays a day (executed parlays whose price was at or above our quote: the market trades thousands an hour); $145 of cash, locked until each game ends, funds about 10-13 a day. 20-40 wins are turned away for cash for every one taken (replay, `skipped for cash 1,128`), so (a) volume is not what to chase, the quality and the capital efficiency of each fill are; (b) a bigger wallet helps less than linearly while the concentration guards bind ($300: 12 fills a day instead of 10); (c) profit per day is wallet x return per day of lock: at the shrunk +2.3% edge a day is worth about $6 (+/- $40: one fill is a $33 pot with a 46% standard deviation, ten fills a day), at the raw +9% about $30. The replay's dollar results per variant (`D` in the output) swing by +/- $100 from one lucky parlay and say nothing about a setting; only the per-row tables with their null do.

## 3. The settings, one by one
| setting (live profile) | verdict | evidence |
|---|---|---|
| `maxLegs` 2 | KEEP | 3 legs buyers win 36.1% vs fair 26.3%, 4 legs 23.4% vs 16.2%, unselected |
| `maxLegFair` .80 | KEEP | strongest leg .80-.85 -9.6%, .85-.90 -17.1%, .70-.80 +12.9% (z +1.4); legs .8-.9 win +7.0 over fair |
| `maxFair` .65 | KEEP | no parlay above .55 in volume (27): no evidence either way |
| `minFair` .25 -> **.35** | CHANGED | ROI +11.0% -> +11.8% (T2 -> T3), return per day of lock +61% -> +78%, collateral per $10 stake $12 instead of $20 (about 60% more parlays per dollar: lower variance), still 8x the wallet's fills |
| `minLeadMs` 1 min -> **1 h** | CHANGED | under 1 h: +0.6% / +3.7% (z 0.0 / 0.5) vs +7.8 / +14.5% (z 1.3 / 2.0); the 24% of the flow it drops earns about nothing |
| `maxLeadMs` (new) **12 h** | ADDED | beyond 8 h the lock averages 15 h and returns +28% per day vs +61-63% within 8 h; ROI is fine (+10.6%) so the cap is a capital rule at 12 h |
| margin floors (fair + ~0.1 point) | KEEP | section 1.3: +1 point of ROI at +0.5 point is inside the noise; a higher margin is the first lever if cash is full all day AND the flow is thin |
| `sameGamePremium` 0 | KEEP | same-game +13.0%; the premium costs half the auctions |
| `minBooksGameLines` 2 | KEEP, unverified | no order across 2-4 / 5-7 / 8+ companies |
| `onlyToday` off, `maxDaysAhead` 2 | KEEP | covered by `maxLeadMs` |
| guards (1 same parlay, 2 per outcome, leg share .25) | KEEP | without them the unconstrained book swings -$672 on 11-18 and +$1,528 on 11-19 |
| `dayLossStopPct` .30 | KEEP | variance is large against the edge |

Expected effect of the three changes (T0 -> T8, the live tier with 1-12 h of lead and fair >= .35): ROI at the clearing price +9.0% -> +12.1% (z +1.7 -> +1.6, the same evidence), return per day of lock +48% -> +73%, the wins still 8-10x what the wallet funds. They are a better use of the same capital; they do not make the edge larger than the data shows.

## 4. What would raise profit next (not done)
1. **Capital** (Tj): the single lever that scales profit; the guards bind at about $300, scale them with the wallet when it grows.
2. **Same-game coverage**: the best subclass (+13.0%) and the second decline reason live (15% of requests: "two legs in one game, correlation unmodelled"); those declines carry no fair in the journal, so their return is unmeasured: journal them with legs and the independent product, then grade them (`rfq_pnl_study` needs the fair).
3. A cash-scarcity margin (raise the margin when free cash is under a third of the wallet), worth about +1 point of pot per fill: too small to build now.
4. More days: the daily study adds a slate each day; every number above is a point estimate on four slates and about 50 distinct games.
