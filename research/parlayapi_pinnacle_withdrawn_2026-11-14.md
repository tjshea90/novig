# ParlayAPI stopped serving Pinnacle (2026-11-09): what changed, what Vigilant did, what is left to decide (PA1, Tj 2026-11-14)

**Trigger.** Tj sent a photo of ParlayAPI Support's e-mail (`research/uploads/2026-11-14/parlayapi-support-email-2026-11-14.png`) and asked: "Fix the vigilant app and GitHub engine to reflect the changes in parlayapi".
The evidence, the answers of the live API and the code map are in `PARLAY_API.md` §11; this note is the short verdict and the decisions that are Tj's.

## The failing job
`engine-grade.yml` (twice a day) asks every close source for up to 120 unclosed bets (`CloseBackfill.MAX_PER_RUN`). ParlayAPI's was FIRST in the engine (`GradeMain`) because Pinnacle's close was "the sharpest there is".
Since Nov 9 each of its `closing-lines?bookmakers=pinnacle` calls is refused, 400 `UNKNOWN_BOOKMAKER`; the client turned the refusal into `CloseLookup.Later("ParlayAPI didn't answer")`, which the back-fill asks again after 3 hours: the same
117 calls a run, for ever, and every moneyline bet it was asked about stayed "not closed yet". Fixed three ways: the request no longer names Pinnacle, a refused book is a "never" (and the look goes on with the other book), and
the source is no longer first.

## What ParlayAPI can still close
Only **DraftKings' and FanDuel's moneylines**, from `closing-lines` (flat rows, all `h2h`, a price before the start only, 5 credits a league). Spreads, totals and props came from the closes file, which has no Pinnacle row for any day since at least Nov 2,
and whose DraftKings/FanDuel rows are mostly prices after the start (FanDuel NBA 298 of 300 game-line rows, DraftKings NFL 42 of 42) in a 23,000-row, 24-credit file for one NBA day. Not worth asking: ESPN (DraftKings' own close, free)
and Novig's trades (every market) answer those. A soft close is NOT a sharp one: `ClosingLine.sharpHistory` used to treat any "ParlayAPI ·" close as sharp, so it would have replaced a read before the start; it now does so only when the close says
Pinnacle (the ones already stored do). The sharp closes left are SGO and OddsPapi Free (both switched off or empty now), and offline `tools/propline_clv.py` (PropLine's resolved archive: Pinnacle's devigged close, Novig's own, PropLine's grade),
which is where the engine's CLV studies already got Pinnacle's close. **A per-bet Pinnacle close in the Tracker would now need a PropLine close source (`/events/{id}/odds/closing`, PropLine Pro); not built, see PA1f in TASKS.md.**

## Scans were never broken, but two things were wasted
- A withdrawn key named beside a served one is ignored by `/odds` and `/props` (200, DraftKings only), so no scan failed. Still removed from every list (`ParlayWithdrawn`, one guard test), because "ignored" is ParlayAPI's choice to keep.
- **Alternates**: every ParlayAPI game-line call asked for `alternate_spreads,alternate_totals` (5 credits instead of 3 a league). They were Pinnacle's alone; the probe got 0 events from all seven engine leagues, 2 credits charged each. Off now: 40% fewer credits per game-line scan, nothing lost.

## What is left of Pinnacle at ParlayAPI
`/live/period_markets` (no book named) still returned Pinnacle's 1st-half lines for NFL (494 rows) and NCAAF (1,214) on Nov 14, and the engine's `parlay_1h` feed wins Pinnacle quotes of age under 5 minutes: unchanged, and valuable while it lasts.
`pinnacle_delayed` (game lines only, pregame, 1 to 16 minutes old, 489 to 601 s at the probe, served only by name and alone) exists: NFL 13, NCAAF 58, NBA 2, NCAAB 27, NHL 15, ATP 10, WTA 4 events. **Not built**, on judgement: a Pinnacle line that is 8 to 10 minutes old by
construction is a poor fair for a +EV engine (PropLine's own Pinnacle is as fresh or fresher, and the engine's freshness rules already count age), it must be asked alone (a call a league for nothing else), and the plan that pays for it ends Nov 20. If Tj wants it
anyway: a `ReferenceSource` of its own with every quote stamped `last_update` (Pinnacle's own time) and `delay_seconds`, ranked below PropLine by `ApiChain`, paper-judged by CLV before it may break a tie.

## For Tj: the Business plan ends Nov 20 (the API says `period_end_iso` 2026-11-20T23:37:04Z, state "active")
What ParlayAPI adds beside PropLine (engine `apiCards`, 2026-11-14 04:56 UTC): it fed 2,304 lines of which **32** were lines no other source priced (PropLine: 16,772 and 6,978; Kalshi 8,192 and 648); p90 quote age 8.7 minutes (PropLine 17.5 minutes by that card's own measure, 63 s by the census's).
Its reason to be one of "the two majors" (Tj, 2026-10-25) was "the best Pinnacle freshest odds": that is gone except for the 1st-half lines. 760,000 of its 1,000,000 credits will expire unused (the engine's advice counts to Nov 30, not Nov 20; it only asks more).
After Nov 20 the engine falls to the Starter key (10,801 of 20,000 left, renews Nov 16) and seven free keys at their last 100; its streams (Business) stop by themselves when refused; nothing in the code assumes Business. Renewing is Tj's call (money): the evidence above says the extra
value is the 1st-half Pinnacle lines, the props board (3 credits a league) and the streams' freshness, none of it unique.

## Reliability (the e-mail's second point)
Outages Oct 10 and 20; hardware move Nov 3 to 11; now 2 of 26,000 requests a server error; NFL board about 1.5 s. The shared HTTP client (connect 10 s, read 20 s, call 30 s) and the one retry on a 502/503/504 cover that; the engine's feed report the same morning
shows ParlayAPI answering all seven leagues with no error. The engine's hot and odds SSE streams still ended with `StreamResetException: INTERNAL_ERROR` several times an hour (they reconnect by themselves, 1 to 30 s back-off); re-read after a few days.

## Open
- PA1f (optional): a PropLine close source for the Tracker/grader (Pinnacle's close, sharp) to replace what ParlayAPI's Pinnacle closes gave. The offline CLV tool already has it.
- Re-check `/v1/bookmakers` and `/v1/meta/movers` for the return of `pinnacle` (movers' `_note` says "temporarily"): `python3 -I tools/parlay.py closeprobe` shows it in one run.
