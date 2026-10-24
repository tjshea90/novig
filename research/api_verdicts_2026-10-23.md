# Which APIs to keep, drop or buy (FX4, Tj 2026-10-23, acct-27a37e tj Gmail)

Tj: "Look at all the available apis and how well they respond, their freshness, features, and contributions to the app and GitHub engine, and tell me which ones to continue, which ones are bad or not needed, which ones to consider buying, etc. Make sure this is based on sufficient data and history."

## Data
- **Scorecards**: every finished engine scan writes one card per API (`apiq` journals, cloud-archive Releases): 6,965 cards, 501 scans, 21-23 Oct (`tools/api_quality.py`, now in the daily digest). Medians are over the scans each API was ON.
- **Usage**: the engine's `usage.json` (cloud-feed, 23 Oct 23:27 UTC); **status**: `status.json` feed report and API manager.
- **History**: the 10-17 API review, the 10-22 full audit and waste audit, the 10-12 freshness and PropLine-vs-ParlayAPI studies.
- **Money**: the engine's ledger (digest): 1,008 orders since ~15 Oct, 656 settled, **+$25.32 on $409 at risk (+8.6%)**; auto + bids by origin: +$1.18 on $1,565 (+0.1%); wallet ~$265.
- **Limits of the data**: 3 days of scorecards is enough to judge coverage, freshness and failures, NOT to say which API makes money: a line records the books behind its fair, not which API supplied them (follow-up FX6). Prices below are list prices from the vendors' pages saved in this repo.

## Measured (medians per scan while ON, 21-23 Oct)
| API | lines fed | only it fed | p90 quote age | pts off Pinnacle | failing scans |
|:--|--:|--:|--:|--:|--:|
| PropLine (Pro) | 12,484 | 926 | 5 min | 1.9 | 0% |
| SportsGameOdds | 7,382 | 182 | 8 min | 1.2 | 1% |
| SharpAPI (trial) | 5,962 | 31 (67 on 10-23) | 3 min | 1.2 | 0% |
| Kalshi | 5,066 | 110 | live socket | | 0% |
| Polymarket | 1,486 | 0 | live | | 0% |
| BoltOdds | 1,404 when it works | 8 | <1 s | 0.9 | **87%** (Free/Trial keys refused) |
| ParlayAPI | 724 | 2 | 5 min | 1.5 | 13% (503s) |
| SX Bet | 634 | 0 | <1 s | | 0% |
| The Odds API (21 free keys) | 0 | 0 | 1.5 min | 5.9 | all keys spent to 1 Nov |
| Unabated (free) | 0 | 0 | **16.6 h** | **9.0** | off most scans |
| PinnWire (18 free keys) | 0 | 0 | | | spent by midday; SharpAPI's Pinnacle covers it |
| pinnapi | 0 | 0 | | | **key refused (403)** |
| Pinnodds REST (engine) | 0 | 0 | | | trial tier: 100 a day, spent |
| OddsPapi Free (v4) | 0 (closes only) | | | | 241 of 250 this month |
| OddsPapi (v5) | off | | | | no key |

## Verdicts
**Keep (they carry the engine):**
- **PropLine Pro (~$19/mo)**: the biggest feeder and the only source of 926 lines a scan (props). Best value of all. Props are the engine's best kind so far (auto + bids: player props CLV +2.0%, team totals +2.9% and +21% ROI).
- **SportsGameOdds**: second biggest, 182 lines only it has, carries Pinnacle/Circa, and its closes grade most of the engine's CLV (1,961 closes). Keep, unless you keep SharpAPI instead (below).
- **Kalshi, Polymarket, SX Bet (free)**: exchange prices pushed live; Kalshi alone gives 110 lines a scan. Keep.

**Decide now: SharpAPI (trial since 10-21).** Measured: the freshest paid feed (p90 3 min vs SGO's 8), as close to Pinnacle as SGO, the engine's Pinnacle source since PinnWire, pinnapi and Pinnodds' REST trial are spent or refused, plus closing lines, splits and live Pinnacle for the live lane. But it adds few lines nobody else has (31-67 a scan) and largely duplicates SGO. List price as running: Sharp $399 + stream $99 + game state $79 = **$577/mo**. Recommendation: **do not keep both SharpAPI and SGO**. At today's volume neither pays for itself (below); if you keep one, SGO carries more unique lines and powers the CLV grading. Keep SharpAPI only if you will scale the bankroll and want the fresher Pinnacle; then the plan without the $79 game-state add-on (the live lane has made 38 orders, -5.8%).

**Fix or cancel (paid, giving nothing now):**
- **BoltOdds**: the keys on GitHub are Free/Trial and refused in 87% of scans. Send the Growth key from the app (Settings › APIs › Send keys to GitHub) or cancel the plan. Even working it fed 1,404 lines a scan with 8 unique: soft books only, no Pinnacle. At $219-349/mo it is the weakest paid value.
- **ParlayAPI**: 724 lines, 2 unique, 13% of scans failing, credits left unused each month. Let the paid month run out; renew only the key whose play-by-play stream the live lane uses.

**Turn off / remove (free, add nothing):**
- **Unabated**: 16.6-hour-old quotes, 9 points off Pinnacle, 0 lines in 3 days (and the 10-17 review). Switch off for good.
- **pinnapi**: its key is refused (403). Remove it.
- **The Odds API**: 21 free keys spent for the month, 0 lines in 3 days, 5.9 points off Pinnacle. Switch off; nothing is lost.
- **PinnWire, Pinnodds REST on GitHub, OddsPapi Free**: free and harmless as backups (PinnWire only fills Pinnacle where SharpAPI has none; OddsPapi Free gives closes). Keep switched on, expect little. If you pay Pinnodds for the phone's live feed, take its REST key off GitHub so the engine does not spend the account's daily allowance.

**Consider buying: nothing yet.** The engine has made about $3 a day (+$25 on $409 at risk) on a ~$265 wallet. The data already bought costs several hundred dollars a month (PropLine, SGO, BoltOdds, ParlayAPI, Pinnodds, and SharpAPI if kept). An API pays for itself only if it adds profit of its monthly price; at a 2% realised edge, $577/mo needs roughly $1,000 a day of extra stakes. What holds profit back today is volume (79% of auto-bet looks held by the 6-hour trap guard; bids fill about 1 in 1,000 postings) and bankroll, not missing prices. Revisit buying only when the wallet and daily stakes are much larger.

## Follow-ups
- FX6: record which API supplied each book behind a line's fair (study journal + `lines_dataset.py`), so a later review can rank APIs by the CLV of the lines they priced, not only by coverage.
- Re-read this table in the daily digest (API VALUE) after a week: by then each verdict above has 10 days of scorecards.
