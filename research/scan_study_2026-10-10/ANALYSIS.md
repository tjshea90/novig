# Scan study v0.85.3, made 2026-10-10 03:02 ET (5,576 bets 2026-10-03..10-10, 19,317 bids) — analysis, 2026-10-10

File: `research/uploads/2026-10-10/5d6ed178-vigilant-scan-study-v0.85.3-2026-10-10-0302.txt.gz`. Scripts here (`parse.py` → `splits.py` → `rules.py`, `bids_and_timing.py`; paths point at the uncompressed file). Intervals are bootstrap 95% over GAMES (not bets). "Sharp close" = Pinnacle (SportsGameOdds / ParlayAPI), Circa, or ESPN's DraftKings line. **Nothing here changes a rule: every item under "To decide" is Tj's call.**

## 1. What can be trusted
- 5,576 bets, no duplicate ids; 1,895 still open; 3,681 settled (W 1,874 / L 1,786, 21 void). **2,345 have a close (42%)**: Pinnacle/Circa 862, ESPN-DK 244, **Novig's last trades 818** (about 100 of them are 1-3 trades, a price not a close), Tracker's own pre-start reads 421. Closes missing mostly because no Novig outcome / no sharp prop close exists (the file's own list).
- Results are noise at this size (ROI -1.19% ±2.7 over 209 games): use CLV.
- **The close source decides the sign** and is confounded with league and kind (below), so no pooled CLV is trustworthy.

## 2. Overall
Pooled: CLV +0.25% [-0.11, +0.58], beat the close 54%, 186 games. By date half: +0.30% / +0.20% (stable and indistinguishable from zero). Listed EV averages +1.38% against that +0.25%: **the lists' average edge is mostly not real**.

| close | n | games | CLV | beat |
|---|---|---|---|---|
| Pinnacle/Circa | 862 | 102 | **+1.33% [+0.95, +1.74]** | 61% |
| ESPN-DK | 244 | 114 | +0.68% [+0.18, +1.11] | 63% |
| **Novig's last trades** | 818 | 123 | **-1.20% [-1.74, -0.66]** | 39% (-1.30% with 10+ trades) |
| Tracker pre-start read (CNO's books) | 421 | 96 | +0.63% [-0.13, +1.46] | 64% |

Why they differ: the Novig-close bets are NFL (474), MLB (110), NHL (96) props mostly — props no sharp book closes; the Pinnacle-close bets are NHL (376) and NFL (306). NHL CLV +1.21% [+0.94, +1.50] on 677 bets is the one league that clears zero on its own. **Reading: where a sharp book prices it, the lists find real edge; where the only independent benchmark is Novig's own market, they do not** (a soft-book consensus is not +EV against the market that has no sharp book to copy). The two cannot be separated further in this data (league and close source move together).

## 3. What separates good bets from bad (sharp closes: 1,106 bets, 144 games, CLV +1.18% [+0.90, +1.48], both halves +1.06% / +1.29%)
- **Listed EV is monotone and real above ~2%:** EV <1%: +0.66% · 1-2%: +1.35% · **2-3%: +3.81% [+2.64, +4.89], 87% beat (68 bets)** · 3-4%: +4.03% (26) · 4%+: +5.33% (30). All-source (incl. Novig closes) the 3%+ bands are +1.9..+2.8%, below 3% about zero. The app's edge floor (3.3%) sits where the edge is real; the 2-3% band is also good against sharp closes but thin (68 bets).
- **Time to the start (first listing):** all closes: <30m +0.89% · 30m-2h +1.48% · 2-6h +0.81% · 6-12h +0.17% · **12-24h -0.55% · 24h+ -1.31%** (n 570 / 202; the monotone fall holds for both close classes, weaker against sharp closes: 12-24h +0.26%, 24h+ +0.78%, CIs cross zero). Tj's own 818 taker bets say the same (CLV +3.9% under 30 min, +2.4% at 2-6 h, +0.0% at 6-24 h, -0.9% at 24 h+; 586 of the 818 were placed over 6 h out).
- **A bet that stays listed an hour or more is a trap:** all closes -0.85% [-1.45, -0.14] (923 bets) against +0.6..+1.2% for bets gone within the hour; against sharp closes +0.45% vs +1.3..+1.75%. (A long-listed bet is an old line the market has not bothered to correct, or one that was first seen far from the start; the two overlap.)
- **Vigilant's own scan agreeing is not a plus:** src c+v+w (-1.37%, 431) and v+w (-0.95%, 208) are the worst groups, c+w and w alone the best (+0.87%, +1.19%): confounded with kind (Vigilant lists game lines, the wide read lists props), so not a finding on its own.
- By kind (sharp closes): props +1.29% (679), spreads +1.91% (109), totals +1.25% (167), **moneylines -0.48% (98)**. Books behind the fair: 7-9 books +0.91% [+0.35, +1.50]; 2 or fewer books -1.54% (50); 10+ +0.31% (the more books, the softer the average and the smaller the edge).
- **Prop sharp-book question (Tj, 2026-10-03):** today's rule keeps 449 props, CLV +0.54% [±0.56], and drops 143 at -0.93% (n 106 closes). Requiring a sharp-ranked book (Kalshi, ProphetX, FanDuel, Caesars, DraftKings) to price both sides and agree: kept 412, +0.49%; dropped 180, -0.65%; 2,742 props were never judged (no book page read), +0.31% on 1,213 closes. Requiring an exchange only: kept +0.43%, dropped -0.08%: **an exchange's agreement is not what matters**, and "require a sharp book" does NOT raise the kept set's CLV (+0.49% against today's +0.54%) while it drops about 5 more props a day (the dropped set is worse, -0.65% against -0.93%, but both are small): not worth it on this evidence. The judged set is the top of each scan (selection bias): compare only inside it.
- Timing inside a bet's life (price at every look against the close, sharp closes): no clear best moment (+1.0..+2.3% at every distance); last look +1.55% vs first look +1.18%: **waiting does not reliably improve the price**.

## 4. Candidate rules (sharp closes; split by date; per-day uses 7 days)
| rule | bets (games, per day) | CLV all | first half | second half |
|---|---|---|---|---|
| EV ≥ 3% | 56 (40, 8) | +4.73% [+3.20, +5.98] | +4.91% | +4.62% |
| EV ≥ 3% and start ≤ 6 h | 29 (25, 4) | +5.81% [+3.44, +8.18] | +7.17% (9) | +5.19% (20) |
| **EV ≥ 2% and start ≤ 6 h** | 62 (46, 9) | **+5.15% [+4.01, +6.60], 92% beat** | +5.01% | +5.23% |
| EV ≥ 3%, listed ≤ 60 min | 41 (35, 6) | +5.19% | +4.92% | +5.34% |
| start ≤ 6 h, any EV | 589 (124, 84) | +1.42% [+1.09, +1.78] | +1.15% | +1.69% |
Held on both halves, all with CIs above zero, but **small (30-60 bets, 25-46 games) and CLV against the same books that fed the EV (partly circular)**: say "the data suggests", about 4-9 bets a day, and ROI on the settled ones is noise (e.g. ≥2% & ≤6 h: +6.7% on a few dozen). Nothing here is enough to widen any limit.

## 5. Bids (make orders; 19,317 posted, 150 filled = 0.8%; Tj's tracker: 160 filled)
- **Bids are the best-supported result in the file:** CLV +2.08%, beat 70% on 126 closes (30 events), picked off 1% (1 of 136), EV at post +3.7% vs at fill +3.6%, ROI +9.1% on 138 settled ($257 staked; noise). Halves: +2.57% (27) / +1.95% (99). **Against Novig's own last trades (an independent close): +2.14%, 61% beat (57 bids, 17 events)**; against the other closes +2.15% (60). 9 CNO-priced bids filled (+1.8% CLV, all under 30 s old data): too few to say.
- **Time to the start when posted:** under 2 h +3.55% (21, 95% beat) · 2-6 h +1.98% (41) · 6-12 h +2.25% (51) · **12-24 h -0.66% (13)**. Fill rate per 100 bid-hours: under 2 h 29.5, 2-6 h 15.6, 6-12 h 17.8, **12-24 h 1.5**, 24 h+ 0.9. The 12-24 h bids used **1,508 of 2,412 bid-hours (62%) for 23 of 150 fills** and the 24 h+ ones 211 hours for 2 fills.
- **Leading the side matters for fills:** 81% of fills led their side (no bid as high) against 52% of the unfilled.
- 18,518 of 19,167 unfilled ended Cancelled, 639 Expired, 10 Refused; the top reasons are the app's own re-posting ("About to expire: re-posted" 8,639, "fair goes old within a minute" 5,741), i.e. most posts are re-posts of the same bid.
- By league: NHL 54 fills +2.4% · NFL 34 +1.6% · MLB 28 +2.8% · WNBA 32 +1.7% · NCAAF 2 -2.7% (too few). 118 of 150 fills are props.

## 6. To decide (Tj; none applied)
1. **Bids: stop posting more than ~12 h before the start** (CLV -0.66% on 13 fills and 62% of the bid-hours for 15% of the fills). Cheapest change with the clearest evidence. Needs: how many more bids a day the freed budget could put up inside 12 h (the 2-12 h fill rate is 16-30 per 100 bid-hours).
2. **Auto-bet: only within 6 h of the start** (the trap guard already skips games 6 h+ away; 586 of Tj's 818 taker bets were placed beyond 6 h: check the dates they were placed against when the guard went in). In the app's own data the 6 h+ bets lose to the close.
3. **Edge floor:** keep 3.3% for takers; the 2-3% band is good against sharp closes (+3.8%, 68 bets) but not shown elsewhere: a test, not a switch.
4. **Drop bets that have stayed listed an hour or more** from auto-bet (CLV -0.85% all closes).
5. **Props: do not add "require a sharp book"** (kept CLV +0.49% vs +0.54% today, 5 fewer bets a day); the veto stays.
6. **Moneylines look negative** (-0.48% sharp-close, 98 bets; -0.63% all): too thin to act, watch.
7. NFL and MLB props that no sharp book prices are -1% to Novig's own close (about 630 bets): a possible rule is to list them only with a sharp-ranked or exchange price, but this data cannot separate that from the NFL/MLB league effect; ask for a second close first (section 7).

## 7. What to log next time
Close per bet from a SECOND independent source (Novig's last trades AND Pinnacle where it exists) so the two benchmarks can be compared on the same bets; the Pinnacle price at listing time, to separate "EV from soft books" from "EV including Pinnacle"; the time the bet was still available at the first price; for bids, the `minToStart` at the fill and a Novig-trades close for every filled bid.
