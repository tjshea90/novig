# Vigilant v0.70.1 diagnostics + scan study: what the analysis found

Written 2026-10-07 from the saved agent results in `research/v0701_partial/` (numbers only; the raw files never entered the repo).
**Scope, said plainly:** 9 of 9 study analysts, 5 of 5 diagnostics analysts and 3 of 3 strategy builders ran. Of the 10 best candidate rules (24 were built, ranked by the lower bound of their CLV), rules 1-8 were each tested by three lenses (reproduce, luck, feasibility); rule 9 by the `reproduce` lens only; **rule 10 and the 14 weaker candidate rules were not verified.** The planned synthesis / critic / final agents were **not run** (stopped by Tj's cost call on 2026-10-07: the remaining verifiers are the two weakest rules and would not change the pattern); this report was written directly from the saved results. Nothing in the app's betting rules was changed.

## 1. The answer, in bullets
- **Is the edge real?** Overall CLV is +0.19% [-0.37, +0.81] over 683 closes in 69 games, ROI +1.58% [-2.0, +6.3] over 1,705 settled bets: both statistically zero. 2.3 days of data cannot separate a +1% edge from nothing.
- **The one robust signal is time to the start.** Bets first listed within 6 h of the start: CLV +1.41% [+1.01, +2.00] (256 closes, 66 games). 6-24 h: about -0.4%. Beyond 24 h: -5.07% (15 closes). The break sits at about 6 h, where the code's trap guard default already is; the phone ran **24 h**.
- **Listed EV carries real signal** (slope about +0.66 CLV points per EV point; the close erases about 1.4 points of listed EV, break-even listed EV about 1.3%). EV >= 2.5% and first listed within 6 h: about +3.0%. EV >= 2.5% listed earlier than 6 h: indistinguishable from zero.
- **Every add-on gate is unproven or refuted** (late sniper, stack, wait-and-confirm, plus-money only, falling edge, EV-gated entry): their headline CLV leans on Tracker closes, which exist only for bets Tj placed and are a re-read of CNO's own consensus, so CLV there equals the listed EV. On independent closes (ESPN, Pinnacle, Novig last trades of 3+) the whole 6 h / EV >= 2.5% family shrinks to about +0.6..+0.8%.
- **Hidden bets (what CNO's filters drop) are not where the profit is**: CLV lower than shown bets by about what their lower EV predicts; 31% of them were listed later at a better price. Leave CNO's three filters as they are.
- **Props do not need a sharp book to be bet**: PASSED vs VETOED props differ by +0.88 [-1.05, +2.8], the sign flips by date half, and time to start explains most of it. No change to the veto.
- **Bids**: 17 fills (6 games) beat the close by +2.6% per fill, but the sample cannot say bids add an edge. A bid fills only while it leads its side; Quick & likely bids sit 18-23 h out where no fill has ever been recorded.
- **Diagnostics file**: the app is stable (no crash, ANR, memory kill, frozen frame; 9 late cycles of 7,328). Nothing in it puts money at risk except the Novig 451 windows (bids can be neither posted nor cancelled while refused), one fail-open check (an unread Novig-trades look lets a game-line auto-bet through; 0 of 5 reads failed) and one app fault (Novig's batch-place reply is unreadable; section 3).
- **ParlayAPI**: its credits buy about 16 full scans a day (40 credits a scan against a 635 a day pace); a slow, flaky provider (8.5% of calls failed). v0.70.4 now spends free keys too and rotates keys (PARLAY_API.md §4a).

## 2. The strategy candidates and the verdicts (default: refuted)
| # | Rule | Builder's CLV | Verdict |
| -: | :- | -: | :- |
| 1 | Late sniper: buy at 5 min before the start, EV >= 2.5% | +3.7% (16 games) | Refuted: Tracker-close circularity, 36% fail the auto-bet's own checks |
| 2 | Stack: late sniper + first seen in window + plus-money | +3.2% (40) | Refuted: adds +0.67 pts over rule 8 for dropping 65 of 184 bets; 70% of closes are Tracker reads |
| 3 | First listed <= 6 h and EV >= 2% | +3.0% (51) | Not luck (family-wise p 0.0006-0.0011), but the app cannot run it as measured (25% of its bets pass today's gates) and the EV add-on is the weak part |
| 4 | 6 h guard + skip when the book page's edge is 1 pt under the list's | +3.3% (20) | Refuted as one rule (family-wise p 0.09-0.47 over 107 tries); only its 6 h half is feasible |
| 5 | First listed <= 6 h and EV >= 2.5% (the auto-bet with a 6 h guard) | +3.0% (43) | Not luck on all closes (sign-flip p 5.5e-6), **unproven on independent closes**; the app cannot "run the rule", only the setting `trapEarlyHours` |
| 6 | Wait-and-confirm 5 min | +2.9% (28) | Refuted: family-wise p 0.92; the gain is the EV gate, not the wait; needs a new hold state |
| 7 | Plus-money only | +2.8% (41) | Refuted as a rule (+0.28 pts over rule 5, wrong sign on the app's real bets: plus +1.90% vs favourites +2.66%); the broad price gradient is probably real |
| 8 | EV-gated window entry at first look inside 6 h with EV >= 2.5% | +2.5% (44) | Refuted on `reproduce` and `luck`: 184 bets / 44 games, CLV +2.55% [+1.77, +3.52] on 116 closes, but 75 of those are Tracker closes (a re-read of CNO's own consensus: CLV +3.49% there simply restates the listed EV); on the 33 independent closes (23 games) +0.81% [-0.04, +2.10], family-wise p 0.82 over 810 variants, and dropping the top 1 / 3 / 5 games gives +0.53 / +0.23 / -0.05%. 165 of 184 entries are the same look the app's existing EV gate already takes; the rule minus that baseline is -0.26 pts [-1.48, +0.72] on independent closes. About 80 games (~115 independent closes) would settle CLV > 0. Feasibility lens: see `verify-8-feasibility.json` |
| 9 | Trap guard 6 h (today 24 h) | +2.6% (32) | `reproduce` only (luck and feasibility not run): the rule's own CLV reproduces (+2.59% on 51 closes) but kept minus dropped, the only valid test, is +2.12 pts [-0.58, +5.23] over 35 games (p about 0.15); one cohort drives it (66 of 69 dropped closes are Oct 4 football-prop games; without the Oct 3 first looks the gap is +0.16), and Tracker closes carry the effect (kept +3.41% vs +0.95%). Not refuted, not shown: about 190 games with closes (about 10 more days) would settle it |
| 10 | Skip books whose two sides cost 2 cents or more over $1 | +2.5% (27) | **Not verified**; the study says it adds nothing once the 6 h cut applies |

**What the verifiers' pattern says.** Timing is the signal, and it is already a setting. The 6 h guard cuts about half the volume and about $0.6 a day of expected edge; its clearest argument is tail risk (1 of 149 vs 13 of 145 placed bets closed 10+ points worse, p 0.0007). Simulated at decision time, 6 h gave +2.90% on 70 bets vs 24 h +2.16% on 106 (gap +1.99 [-0.35, +4.80]); the auto-bet's own real bets: +1.05 [-0.71, +3.22].

## 3. The diagnostics file
**Fine:** keyed Novig route clean (126,792 signed book reads, no 429, 69/118 ms p50/p95); the last scan took 32 s; the auto-bet obeyed every printed rule in all 99 recorded bets (0 violations); the trap guard did not fail (15 of 16 "early bets" predate it, the 16th was a hand bet); matching 69 of 69 games; bids behave as designed; STOP ALL cancelled 32 bids in the same second; the DNS-over-HTTPS "FAILURE" lines are the phone having no route (82 s on Oct 5 19:33), not the app.
**App faults (proposed fixes, none shipped):**
- **BUG (DA7):** Novig's batch-place reply is unreadable on the first batch of every app run (3 of 3 runs, 35 bids): bids then go one at a time, and the real-money burst trader shares the code and would halt "UNCONFIRMED" on its first live trade. Fix: log the reply's **shape** (keys and types, no values) so the cause can be read next time.
- Scan-study export: the close denominator prints 683 of 2,080 (32.8%) but 683 of 1,767 started bets is 38.7%; the "why no close" reasons are cut at 90 characters and hide the biggest (824 of 1,084 started bets with no close have no Novig outcome id on record); the VOID count is not printed.
- Diagnostics timeline says "every warning and error of the last day" but is capped at 120 lines (49+ errors missing); health checks mislead in places: "ParlayAPI 1st half matched no Novig game" (no market to match), Runway "SHORT" lines (linear averages that ignore the credit pacer), "most fills came within 2 minutes" fires on 3 of 6 and contradicts its own split, "App stability" judged from the 6 newest exit records, cumulative 429 counts shown as current, 451 refusals not counted as refusals.
- Novig 451 (ANONYMIZED_NETWORK): 145 of 6,336 order calls refused; after one the live feed waits 5 min while the key route returns in 2, so two scans ran 16-29 s. `maker.json` is rewritten whole with fsync on every bid update (3.4 MB).
- Credits: 2 of 4 ParlayAPI props calls (6 of 40 credits) went to leagues with no game in the window (a guard exists in `LowUsageSource`); when the day's share runs short, leagues are bought in a fixed order that holds tennis back first.
**Not verified / unknown:** why 603 failed calls cluster at 03:00-05:00 EDT; why two cycles started late (30 s, 7 min); whether failed paid ParlayAPI calls are charged.

## 4. Questions that need Tj's yes (none applied)
1. **Trap guard 24 h -> 6 h** (about 46% fewer bets; the one robust signal). Evidence above; the auto-bet's own post-guard 6-24 h bets are +1.65% [+0.02, +3.19] on 32 closes, so the pooled "no edge at 6-24 h" is not yet settled for the auto-bet alone: about 50 more auto-bet closes in 6-24 h settle it.
2. **Remember a bet's first-listed time** so a listing that already existed more than 6 h out is skipped when it enters the window (bets already listed > 6 h that later enter the window close at about +0.07% vs +1.29% for fresh ones; the guard checks the clock now, not the first look).
3. Keep the 2.5% EV floor (the data cannot separate 2% from 2.5%).
4. Plus-money only, or a higher EV bar for favourites (`autoBetMinOdds -100` already works in the engine; Settings lacks the chip).
5. Gate and size the auto-bet on the lower of CNO's EV and the app's own book-check EV (9 of 99 had a check EV under 2.5%): about 60 bets settle it.
6. Keep spreads in the auto-bet? Spread CLV -1.03% on 68 closes/54 games: about 110 closes within 24 h settle it.
7. CNO list / alert EV floor 2.5% for bets listed > 6 h before the start.
8. Skip markets whose two sides cost $1.02+ together (a wide Novig book): not verified; adds nothing once the 6 h cut applies.
9. Kalshi: allow a measured test of 3 requests a second (about 300 requests, stop at the first 429 burst): scan 32 s -> about 23 s.
10. Trap guard: should an unread Novig-trades check (429, 451, no route) skip a game-line auto-bet instead of letting it through (0 of 5 reads failed so far)?
11. May the app grade the two imported lock legs itself (a half-point two-way market whose other leg a score feed graded lost)?

## 5. Limits of all of this
- The study covers about 2.3 days (Oct 3 17:56 ET - Oct 6 00:05 ET); 73-79% of closes are NFL; about 78% of closes come from the 29 games of Oct 4. The effective sample is games, not bets.
- Only 683 of 2,080 bets (33%) have a close, and they are a selected set (listed earlier, higher EV, more often placed by Tj). Correcting for who has a close moves the headline CLV from +0.19% to between -0.07% and +0.17%; unselected closes give -0.01%.
- Tracker closes exist only for bets Tj placed and re-read CNO's own consensus: any rule leaning on them is partly circular. Close sources disagree by 1.5-2.6 points at the same time to start.
- 14 candidate rules (the weaker ones) and rule 10 were never verified; the three-section synthesis and the critic pass were not run.
