# CH86: where Tj's "7 Novig orders vs 1 logged tap" come from (ENGINEER, 2026-10-24)

Finding (from the phone's bet log on `phone-data`, `desk.json` / `trades.json` on `cloud-feed`; Novig's own order list is not readable from here):
- UAPB-FAMU Over 55.5: the phone logged ONE tap (`f9f042f2`, 284 contracts, $1.3774). Novig SETTLED exactly 284 contracts on that outcome ("paid $1.29 on 284 contracts"), so only one order ever filled. The engine's only order in that market is a bid on the OTHER side (Under 55.5, 129 contracts, $0.63).
- Tj's $6.88 / $1.3774 = 4.995: five orders of the same stake. UND-SDAK Under 57.5: one import of 380 contracts / $1.786 against three Novig orders totalling $5.62 (3.15 x the stake).
- Reading: the extra Novig orders are IOC attempts that matched nothing (NOFILL, CH81e/f: "nobody selling"). Novig keeps each as an order; the Tracker records fills only, and a re-tap after a miss is not blocked by the repeat guard because nothing was filled (no exposure). No path was found in the code or the ledgers that places a second FILLING order for one decision (the engine's 33 two-fill outcomes are bid rungs and small; `RepeatGuard` caps a side at 2 bets / $100).
- Not proven: the pre-1.0.78 phone did not log unfilled orders. v1.0.78 keeps NOFILL and order-end timing in the diagnostics report; the next report lists every attempt per outcome, which settles it. If an attempt there is NOT a tap of Tj's, that is the bug.

Possible follow-up (class A, not built): show "attempt N on this side, M unfilled" in the Bet sheet so repeated taps on a dead price are visible; the repeat guard's $100 a side is far above a $185 bankroll (CLAUDE.md lets a session tighten it with evidence).
