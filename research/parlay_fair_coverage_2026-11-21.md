# Getting more parlay legs a fair line: what blocks them, what PropLine and the other APIs can add (FV1, Tj 2026-11-21)

Tj, 2026-11-21: "Figure out how to get more legs fair odds priced from my apis, especially the propline api with paid socket."
Earlier work this builds on (do not redo): MK18 `parlay_coverage_2026-11-19.md`, MK22 `prop_ladder_fair_2026-11-20.md` (36% -> 99% of Novig prop strikes), MK40/41/46/48 (protected twins, Yes-only anytime-TD, interpolated and extrapolated alternate numbers), MK53 (what is left and why not built).

## 1. Method (and what the journal hides)
Data: the current engine job's RFQ journals, `rfq-2026-11-20-run44863668546` + `rfq-2026-11-21-run44863668546` (Release `cloud-archive-2026-11-21`, 04:00-05:38 UTC, 138,685 journaled RFQs, 6,163 executed) joined to a fresh public Novig catalog (`tools/rfq_legs.py catalog`, 469 events, 46,558 markets) by outcome id.
**The journal samples the declines that carry no fair line 1 in 25** (`RfqMaker.sampled`, `sampleEvery` 25; quotes and priced declines are kept whole), so every no-fair row stands for 25 RFQs. Unweighted, "no fair line" looks like 2% of RFQs; weighted it is the 38% the engine's own tally shows. Any count taken from CREATED rows without that weight is wrong by a factor of 25 for this class.
A leg's `classes` entry is `priced` (has a fair), `scanned` (the scan holds the outcome but has no fair) or `unscanned` (the scan does not hold the outcome at all).

## 2. What "a leg has no fair line" is made of
- Of the 2-leg no-fair RFQs (the only kind the research profile prices), **97% of the missing legs are `unscanned`**: not a pricing failure but "the engine's scan never looked at that Novig outcome" (1,433 of 1,475 sampled rows have an unscanned leg and no scanned-without-fair one; 32 + 9 + 1 the other way).
- Weighted, by what the unscanned legs are (36,875 no-fair 2-leg RFQs in 98 minutes, 750 of them traded):

| class of the blocking leg(s) | RFQs | share | traded | share |
|---|--:|--:|--:|--:|
| outcome already closed when the catalog was read (the games were in play: late-night NBA / NHL / NCAAB) | 22,475 | 61% | 325 | 43% |
| a leg starts more than 12 h away (the profile declines it anyway: lead cap, PS1) | 9,750 | 26% | 300 | 40% |
| soccer (EPL, La Liga, Serie A, Ligue 1, Bundesliga: 3-way moneyline, totals, BTTS) | 1,975 | 5.4% | 25 | 3.3% |
| in play (a live lane leg: shadow by design) | 1,775 | 4.8% | 50 | 6.7% |
| UFC (moneyline, KO / method, totals) | 500 | 1.4% | 25 | 3.3% |
| NCAAF / ATP / NCAAB leagues that ARE scanned (an alternate number, a challenger match) | 400 | 1.1% | 25 | 3.3% |

Reading: **in this (overnight) window fair-line coverage of pregame legs inside the profile's window is not what holds fills back.** The no-fair count is mostly legs the profile would decline for another reason (in play, closed, too far out), so pricing them would change nothing. The real pregame coverage gaps are leagues the scan does not include (soccer, UFC), about 7% of no-fair 2-leg RFQs overnight; Saturday daytime will weigh soccer and UFC more. **Not measured:** the daytime pregame mix (the catalog only holds open markets, so a journal's finished games cannot be classified: see plan 1).
Traded 2-leg RFQs by the profile's other filters (weighted): in play 53% (shadow), more than 12 h out 24%, no fair line 44% of the weighted total (mostly the classes above), so volume is governed more by the in-play shadow, the 12 h lead cap and `maxLegs` 2 than by coverage.

## 3. The engine's prop census (ladder level, 05:32 UTC): 35,950 of 50,380 prop outcomes (71%) have a fair
Missing, largest first: NHL `PLAYER_GOALS` strike not posted x1,442; alternate TOTAL / SPREAD numbers outside the posted ones x4,541 together (NCAAB/NCAAF alternates the books do not post: interpolated inside the posted range, extrapolated a bounded distance beyond: already live, MK41/MK46/MK48); NBA `POINTS_REBOUNDS` player not posted by any book x720; NFL receiving yards / receptions "side outside 8-93%" x1,147 (the extreme rungs); NFL `TOUCHDOWNS` strike not posted x548.

## 4. What the APIs carry that the engine does not ask for
- **The scan covers 7 leagues** (`settings.leagues`: NBA, NFL, NHL, ATP, WTA, NCAAB, NCAAF). The app knows four more (WNBA, MLB, UFC, Boxing: `Leagues.ALL`; MLB is out of season, WNBA too) and **no soccer league and no NCAAWB at all**. The maker's direct fairs are the scan's priced lines (`CloudMain.rfqDirectFairs` reads `latestFair`), so a league outside the scan can never have a fair; PropLine's stream is subscribed to the scanned sports only (`PropLineStream.want`, `filter_sport_key`).
- **PropLine carries all of them** (docs 2026-11-16): soccer keys such as `soccer_epl`, UFC / MMA among its individual sports, WNBA, MLB, tennis, golf. Its game lines `h2h` / `spreads` / `totals` already include alternate spreads, alternate totals and team totals (`line_type: alternate`), so the alternate-number gaps are the books', not a request the engine forgot.
- **NHL goals:** PropLine posts `player_goals`, `player_first_goal`, `player_goals_2plus`, `player_goals_3plus`, `player_points_1plus` and so on. The engine maps only the two-way `player_goals` (`PropLineProps`), and books post the anytime goal scorer as a Yes-only list, the shape MK40 read for anytime touchdown (`PropLineClient.yesOnlyMilestones`, `PropLadderFair.YES_ONLY_HOLD` 1.09 measured on the NFL board). The census's biggest single prop class (PLAYER_GOALS x1,442, single-culprit 675 weighted RFQs more than 12 h out in the sample) is very likely the same drop.
- **The paid socket** (Streaming, 250,000 requests a day, 2 sockets) already pushes every line of the scanned sports (21,000 outcomes moved per 5 minutes, 0 gaps): freshness is not the gap, scope is.
- ParlayAPI is at 6% of its credits (resets Dec 1; about 5-7 a hour) and is not a lever until refilled; SX Bet, Kalshi and Polymarket carry UFC (SX 34 / 10009, Kalshi series, Polymarket tag 279: `Leagues.kt`).

## 5. Ranked plan (nothing below changed a setting today)
1. **Make the next attribution possible for any journal (cheap, no risk):** a GitHub workflow that snapshots Novig's public catalog (`rfq_legs.py catalog`, about 20 s) every 30 minutes into a Release, so outcome ids of games that have since finished can be classified; then re-run section 2 on a full DAY of journals (daytime pregame mix, weekend vs weekday). Decides the order of 2-4. TASKS FV1a.
2. **A fair-only scan pass for the leagues the app already supports (UFC first, then WNBA / MLB / Boxing in season):** a second `Scanner` run on the extra leagues whose priced lines go into `fairKept` only, never to the feed, auto-bet, bids, live lane or Kalshi lane (adding the leagues to `settings.leagues` would let the real-money auto-bet, bids and the live taker trade a sport with no history: there is no league allow-list and `autoBetMinStars` is 0). Needs care: a second scanner must not take over the main scanner's Novig stream pins (`NovigStream.pin(owner, ids)`) or its rate budget. Test: unit-test the isolation (the extra rows never reach `trade` / `bidLines` / `onFeed`), then measure the priced share of UFC legs before and after in the census. TASKS FV1b.
3. **NHL anytime goal scorer as a Yes-only list (and `player_goals_2plus` / `player_first_goal` as milestones):** add the keys to `PropLineProps.YES_NO` / `PropLadderFair.YES_ONLY_STATS`. First measure the books' hold on a real NHL board: the engine logs a hold census line (Yes-only price against the two-sided consensus per book, as MK40 did by hand on the NFL board) for a day. Until measured, a small assumed hold is the safe direction for a maker (an over-estimated fair only costs auctions; an under-estimated one wins them at a loss). TASKS FV1c.
4. **Soccer (the largest build):** new `League` entries (Novig names EPL, La Liga, Serie A, Ligue 1, Bundesliga, Champions League, MLS; PropLine `soccer_*` keys), 3-way moneyline and both-teams-to-score handling in the planner and devig, PropLine's 70 European books admitted only by documented desk (`BookCompany`). About 5% of 2-leg no-fair RFQs overnight, likely more on Saturday and Sunday: decide on the daytime numbers from step 1. TASKS FV1d.
5. **Not worth building (measured):** alternate numbers beyond what the books post (extrapolation exists and is bounded), the extreme-rung props outside 8-93%, NBA combo props derived from components (a prop parlay trades about 100x less per collateral-minute than a game-line parlay: MK45/MK53).
6. **Policy, not coverage (Tj's call, evidence in CHANGES.md MK32/PS1/PF2):** more than half of the traded 2-leg flow is in play (shadow after the adverse-selection finding), 24% starts more than 12 h out (the 12 h lead cap), and the profile prices 2 legs only. Fair lines do not unlock those.

## 6. What this did not change
No setting, no code and no key. The engine, the maker profile and the loss-stop change of earlier today (ON2) are as they were.
