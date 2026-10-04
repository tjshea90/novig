# Vigilant scan study v0.58.3 — analysis
File analysed: `vigilant-scan-study-v0.58.3-2026-10-03-2248.txt` (622 bets). Numbers below were computed with code from the JSON lines, not from the file's summary. CIs are bootstrap over **games** (63 games), not bets.

## Bottom line
1. **The data is one evening** (first looks 5:56–10:47 PM EDT Oct 3). It cannot support day, hour or "out of sample by date" findings. Only 16 games have a graded result and only 41 bets have a usable close.
2. **The file's own summary is distorted by 2 bad closes.** Two "ParlayAPI · Pinnacle" closes are wrong-side/wrong-game matches (Washington State ML -117 → "+272", CLV -50%; Arkansas State +186 → +216, CLV -9%). Both sit in the *hidden* group. Remove them and overall CLV goes **+0.72% → +2.20%** (41 closes, 23 games, CI +1.4% to +3.1%), and the hidden group goes **-0.39% → +1.96%** (shown +2.59%). The "hidden bets lose to the close" reading was an artifact.
3. **But +2.2% CLV is not proof of a +2.2% edge.** Bets listed at ~0% EV show about the same CLV (EV 0–0.5%: +1.7% on 16 closes). A regression of CLV on listed EV has an intercept of ~+1.4% (CI +0.3% to +3.0%). The closes (DraftKings via ESPN, Tracker reads) price these bets about 1–2 points above CNO's conservative-devig fair, whatever the listed EV. Read **differences between groups**, not the absolute level. Those differences are small and not significant yet.
4. **Results are exactly on expectation:** 25 wins vs 24.2 expected from the fair probabilities (47 graded, ROI +1.9%, CI -25% to +40%). Shown +27% (30 bets) and hidden -42% (17 bets) are noise: shown won 20 vs 16.2 expected, hidden 5 vs 8.0.
5. **The props/sharp-book question cannot be answered from this file** (details in §7). Requiring a sharp book would have kept 22 of 365 props.

## 1. Data check — what to trust
- 622 bets, 0 duplicate ids, 63 games. Cost matches American odds on every row. EV ≠ fair/cost-1 on 4 rows (the MISMATCH ones). Both sides of a market are never both listed.
- Status: 575 pending, 25 W, 22 L. All graded bets are Oct 3 games (NHL 24, college football 21, MLB 2). **403 NFL bets (Oct 4 and later) have no result and almost no close** — that is 65% of the log.
- Closes: 43 found = ESPN-DraftKings 21, Tracker 20, ParlayAPI-Pinnacle 2. Drop the 2 Pinnacle ones. **All 20 Tracker closes are bets you placed, and all 10 prop closes are Tracker closes**, so the prop CLV comes only from your own picks. Tracker's close source isn't named in the file (it isn't Pinnacle).
- No-close reasons: Pinnacle's prop close not in ParlayAPI's file (36), ESPN has no props/halves (11+), Pinnacle's last price was 26–27 h before the start (14+9).
- Hour-of-day, "6 days" and date splits are meaningless: 100% of first looks fall in 17:56–22:47 EDT on one date. Four app versions (0.57.0 → 0.58.3) and three preset states ran inside those 5 hours.
- The shown list's effective filters were: **EV ≥ 1.0%** (min shown EV is 1.00%; every "hidden: EV" bet is under 1%), **odds ≤ +150** (hidden ODDS starts at +153), **≥ 4 books**. That differs from the "rules in force" line (edge ≥ 3%, odds -200 to +120) — that line describes Vigilant's own scan; CNO's list is governed by the Shared View filters.

## 2. Overall (clean closes, game-clustered)
| | closes | games | CLV | 95% CI | beat close |
|---|---|---|---|---|---|
| All | 41 | 23 | +2.20% | +1.3% to +3.1% | 83% |
| ESPN-DK | 21 | 14 | +1.83% | +0.9% to +2.7% | 81% |
| Tracker | 20 | 15 | +2.60% | +1.0% to +4.3% | 85% |

Listed EV on these bets averages +1.1%. EV vs CLV rank correlation +0.29 (p=0.07): weak support that a higher listed EV goes with a higher CLV. ROI +1.9% at first price is within luck (§1.4). Sample-size effect: CLV needs ~40 independent games to put a ±1-point interval around a mean; ROI needs **~20,000 bets** to detect +2%.

## 3. Splits (clean closes unless noted — every cut below has < 25 closes, so all are "suggestive at best")
- **Shown vs hidden:** shown +2.59% (16 closes, 14 games, CI +0.3 to +4.6); hidden +1.96% (25, 15 games, CI +1.2 to +2.9). No separation. Hidden-by-EV (<1% EV) +1.95% on 23 closes ≈ the level offset, i.e. no extra edge and no obvious trap. Hidden-by-ODDS (>+150): +4.0% on 5 closes/4 games, 0–3 graded: worth watching, not acting on.
- **Kind:** props +4.1% (10 closes, all your picks), spreads +1.3% (18), totals +1.4% (9), moneylines +3.4% (3). Props' closes are the least independent; don't read this as "props are best".
- **Sport:** hockey +3.5% (11 closes, 7 games), college football +1.6% (22, 12 games), baseball 1 close.
- **Listed EV:** 0–0.5% +1.7%, 0.5–1% +2.4%, 1–2% +1.8%, 2–6% +3.65% (7 closes, CI -1.2 to +7.2).
- **Odds:** ≤ even +1.97% (5), +100–115 +1.88% (24), +116–150 +2.2% (7), over +150 +4.0% (5). Cost-implied rank correlation -0.30 (p=0.05): longer prices showed higher CLV, which is also what a devig-method offset does (it grows with longshot-ness).
- **CNO books behind the fair:** 8+ books +1.8% (32 closes); 7–8 books +4.1% (7). Books-agreeing/book-check splits: 8 checked closes total — nothing to say.
- **Who listed it:** c-only +4.7% (4 closes), c+w +2.8% (9), w-only +1.7% (18). Too small.
- **Hour of day:** not testable (one evening).

## 4. Timing
- Time-to-start at first look (clean CLV): ≤30 min +2.2% (19), 30–60 +2.4% (8), 60–120 +3.2% (9), >120 min +0.0% (5, 3 games, CI -7 to +2). The >120 min cell is nearly empty because most early-listed bets (NFL Sunday) haven't closed. **No timing conclusion is possible until those NFL bets close.**
- Price path (360 bets with ≥3 looks): 69 later offered a better price than the first look (median 44 min after the first look, ~0.9 probability points cheaper), 45 ended worse than the first look. On the 41 closes: first price +2.20%, best price +2.88%, last price +2.47%. Taking the first-listed price gave up ~0.3–0.7 points vs the best, but chasing the best isn't a rule you can act on (you don't know it's coming, and dollars are thin).
- Stayed listed long: listed-minutes vs CLV rank correlation +0.08 (n=20). Neither trap nor gift. (Only c/v bets have this; 249 wide-only bets don't.)

## 5. Traps (bets that lost to the close)
Only 6 of 41 clean closes lost CLV: WSU -2.5 (-0.2%), Colorado +13.5 (-0.2%), Over 56.5 (-2.2%), SDSU +6.5 (-0.9%), Miami -17.5 (-5.3%, listed 1 min), Robbie Ray hits-allowed Over (-7.2%, listed 70 min at 3.4% EV, price later improved then reverted). Five of the six had listed EV ≤ 1.3%. No book-count, dollars or listing-duration pattern appears in 6 cases. Honest answer: **no trap signature is identifiable from this file.** The two biggest losers (Ray, Miami) were both ≥ 1.3% EV bets on exactly the kind of bets you'd place.

## 6. Candidate rules (none validated; early/late split is within the same evening, not out of sample)
| Rule | bets | games | closes | CLV [95% CI] | ROI (graded) | median $ at price |
|---|---|---|---|---|---|---|
| Everything logged | 622 | 63 | 41 | +2.20% [1.4, 3.1] | +2% | $75 |
| A. Shown by app (CNO under your filters) | 300 | 54 | 16 | +2.59% [0.3, 4.6] | +27% (30) | $92 |
| B. EV ≥ 1% (the filter's real floor) | 323 | 55 | 18 | +2.53% [0.4, 4.3] | +23% (31) | $90 |
| C. Shown & EV ≥ 2% | 126 | 34 | 7 | +3.65% [-0.9, 7.1] | -6% (9) | $102 |
| D. EV ≥ 1%, ≤ +150, 7+ books | 139 | 47 | 15 | +2.62% [0.2, 4.9] | +29% (27) | $80 |
| E. EV ≥ 1% & first listed ≤ 120 min out | 68 | 29 | 16 | +3.35% [1.6, 5.0] | +23% (31) | $46 |
| F. Props EV ≥ 1% | 230 | 33 | 8 | +4.41% [0.5, 7.3] | +2% (19) | $97 |
| G. Non-prop EV ≥ 1% | 93 | 38 | 10 | +1.02% [-0.8, 2.6] | +56% (12) | $50 |
| H. EV < 1% (hidden:EV) | 299 | 48 | 23 | +1.95% [1.1, 3.1] | -39% (16) | $53 |

Volumes are for this one 5-hour window (not per day). With a level offset of ~+1.5 points built into the yardstick, **no rule is separable from "everything"**: A, B, D, E are all within ±1 point of the all-bets figure and their CIs overlap. E (late entry) is the only one that looks a bit higher, but those are the only bets that have closes at all (early-listed NFL bets haven't closed) so it's confounded. Likelihood each is luck: high for all; I wouldn't rank them as findings.

Ranked "worth keeping/trying" (evidence = weak, risk = low, none needs a safety-limit change):
1. **Keep the current shown list as is** (A/B). It is not worse than anything else and its sample is the largest.
2. **Don't add a stricter EV floor yet.** C (EV ≥ 2%) has the highest CLV (+3.65%) but 7 closes and a CI from -0.9 to +7.1; shown EV<2% had 41% ROI on 21 bets (luck).
3. **Watch** late-listed (≤ 120 min) bets and >+150 odds (5 closes). Need the Sunday NFL closes first.

## 7. Your open question: should a prop need a sharp prop book to agree?
- Of 365 props, **only 33 had a book page read** (12% of the props with EV ≥ 3% were judged). Those are the top ~10 rows at each read, i.e. selected by high EV.
- Verdicts: 22 PASSED (17 games), 8 VETOED (7 games, avg EV +5.0% — the vetoed ones are the highest-EV props), 3 NO_SHARP, 332 not judged.
- Closes: PASSED props 5 closes, mean CLV +4.6% (all beat; 4 of 5 had FanDuel/Caesars/Kalshi as the sharp book). Not judged: 5 closes +3.7%. VETOED: 0 closes. All 10 were your own bets. 5 vs 5 closes does not separate anything. Sharp book's own edge vs CLV correlation +0.76 on 5 points = noise.
- Costs of each rule over this evening's props (365 in 33 games): today's rule keeps 357 (32 games); **requiring a sharp-ranked book to agree keeps 22 (17 games, -94%)**; **requiring an exchange (Kalshi/ProphetX) keeps 14 (11 games, -96%)**. Because only the top rows get a page read, "require a sharp book" is mostly a rule that stops you from betting unjudged props.
- Which book matters: exchanges agreed on 14 props (1 close, +5.1%); originating books agreed on 8 (4 closes, +4.4%). No separation.
- Bias: judged props are the best-EV rows (avg EV +2.0–5.0% vs +1.7% not judged), they're read within seconds of listing, and only your own placed bets have a close. A higher edge for props without a sharp book can't be tested here.
- **What the data says:** nothing for or against requiring one. What it does show is that requiring one would drop nearly all props *because verdict coverage is 12%*, not because they failed. To decide it you need verdicts on every prop ≥ 1.5% EV and ~170 closes per arm (1-point difference).

## 8. Deliver
**Settings:** I'd change none of the safety limits (auto-bet places real money; nothing here justifies loosening). The data doesn't justify changing presets, the sharp veto (-1%), the EV floor (1%), the odds cap (+150) or the book count (4+). For the one-evening data this means "keep".

**Fixes to the log/code (low risk, not money-affecting):**
1. **Close matcher:** reject a close whose side or price is inconsistent with the bet (WSU ML -117 → +272, Arkansas State +186 → +216 were wrong-side/game). Require, for example, the close's fair probability to be within ~12 points of the last Novig price, or fall back to "no close". This alone changes the summary's hidden-bets story.
2. **Record the level offset:** store closeFair under 2–3 devig methods (the one used for the listed EV, plus proportional and power) and CNO's last fair, so CLV can be compared like-for-like with listed EV. Also store the Tracker close's actual source.
3. **Log a close for every bet from one independent source** (Novig's last pre-start price and depth for all listed bets, not just Tracker-placed ones). That removes the "only your bets have closes" selection.
4. **Read the book page for every prop ≥ 1.5% EV** (credits permitting) so a sharp-book verdict exists for the bets it would affect; log the verdict time.
5. **Log the listing-state at every look** (which screen reason applied at that moment), the fill price you got, and the available dollars at each look.
6. **Re-export Sunday night / Monday** after the NFL games. That adds ~400 bets with results and closes, mostly props, and is the first export that could answer your questions.

**How much more data:** ~40 closes put ±1 point around one group's CLV; ~170 closes per group to tell two groups 1 point apart; ~20,000 bets for ROI to confirm a 2% edge. A week of NFL/NHL/NBA-season evenings should reach the first two for the main groups.

*Checkpoint file: `VIGILANT_ANALYSIS_CHECKPOINT.md` (same folder).*
