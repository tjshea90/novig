# ParlayAPI Pro spend, 2026-10-22 (ENGINEER, acct-27a37e) — CH57a

Source: the engine's `ParlayAPI plans:` and `Spend:` log lines (cloud-archive-2026-10-21/22 engine logs; no key was used by this session).

**Balance** (Pro key #2, credits left of 100,000, from `ParlayAPI plans:`): 85,610 (10-21 23:02Z) -> 82,064 -> 80,580 -> 79,564 (10-22 05:44Z) -> 76,693 (15:02Z) -> 75,276 (17:15Z).
- Active-hours burn: ~900/h overnight (10-21 23:00 -> 10-22 05:44), ~640/h on 15:02 -> 17:15Z. At ~640/h over 17 engine hours that is ~10,900 a day.
- The plan is MONTH_UTC (`QuotaPolicy.PARLAY`): 75,276 left over the 9 days to Nov 1 = ~8,100 a day available. The burn is about a third over the day's share, so `CreditPace` (which spreads what was left over the days to the reset) is the brake that is working; it is not a runaway.

**By purpose** (one job's `Spend:` line at 15:02Z, credits/calls, per ~2 h job): props 3 credits a call (wnba/nfl/mlb/nhl props 273-294 each) ~ 1,400; game lines 1 credit x (nba, wnba, nhl, mlb, ncaaf 117-120 each) ~ 600. Props are ~70% of the spend, game lines ~30%. The Odds API only reads ncaaf/other game lines (~156 credits per job, a free key).
- NFL game lines and props are read on a day with no NFL game on the slate except Thursday night (3 credits/call, 288 per job): the biggest cut that costs nothing (CH57b).

**Not changed this run:** Tj's 2026-10-21 complaint was 18-minute-old odds; `HOT_METERED_MS` (150 s) is what keeps the soonest games young, and the pace already caps the month. No purpose was cut blind, because which league's props SharpAPI/SGO/BoltOdds cover better is not yet measured. CH57b stays open: (1) skip a league's props when no game of it starts within the hot window; (2) compare ParlayAPI's prop CLV against SharpAPI/SGO's before dropping a purpose.
