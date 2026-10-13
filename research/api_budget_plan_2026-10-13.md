# $50/month plan: what to buy, what to farm free (Tj, 2026-10-13)
Evidence: our two study files (research/api_study_v0.95.5_2026-10-12.md), vendor pages (unverified claims marked), PropLine /v1/freshness (measured keyless).

## Measured / documented, per API
| API | Free tier | What it is good for | Verdict |
| :- | :- | :- | :- |
| PinnWire / pinnapi | 100 requests/day per key | real-time Pinnacle, every line + alts + props: the sharp anchor | FARM free keys (best value per dollar); nothing to buy |
| PropLine | 1,000 requests/day per key, same freshness on every tier | 114 books; measured p90 70 s, median ~0; majors 0 s | FARM free keys; paid only buys redacted analytics + pushes |
| ParlayAPI | 1,000 credits/MONTH per free key; Starter $5 = 20,000 | props in one call; Pinnacle closes; heartbeat age 0-32 s on lines, props 110 s median | KEEP the $5 plan, add free keys as credit overflow |
| Pinnodds | free Trial 100 requests/day | Pinnacle REST/WS; Pro REST $99, socket $198+ | free trial key only for now; do not buy (over budget) |
| Unabated | free, 1 request / 5 s | cheap extra books; bulk fresh but tail hours old | keep free, last in chain |
| BoltOdds | free trial (refreshable) | 34 soft books pushed, 0 s; NO sharp book; $349 paid | USE trials as a live soft-book average / cross-check only; never buy |
| SGO | trial/paid | measured 229-334 s old; flat-rate | do not buy; free/trial as a cross-check and closes |
| The Odds API | 500 credits/month per key; $30 = 20,000 | per credit cost = markets x regions; props per market | farm free keys as the LAST backup (your 7 keys run short); do not buy: $30 for 20k credits is poor value |
| SharpAPI | 12 req/min, only DraftKings + FanDuel, 60 s delay | soft books only; Pinnacle not stated below $399 | free key = a second DK/FD cross-check only |
| MoneyLine Sports API (moneylineapp.com) | 1,000 credits/month, 10 req/min, card on file, auto-upgrades unless turned off | odds, props, EV, scores, exchanges (Kalshi/Polymarket/ProphetX); Pinnacle and latency NOT stated | free key only, TURN OFF auto-upgrade; unmeasured: add to the study before relying on it |
| Tennis API (API-Tennis / Live Tennis API) | scores/fixtures free; odds usually paid (Live Tennis API odds need PRO) | tennis scores and results (grading), not a sharp-odds source | free key for tennis scores only; Pinnacle + PropLine already price tennis |

## The $50 budget
1. ParlayAPI Starter $5 (keep).
2. Hold $45. Best uses, in order, only if the Source race still shows a gap after the free keys:
   a. nothing else, if PinnWire/pinnapi/PropLine free keys cover the day (the likely case: 9+ Pinnacle keys = 900+ requests/day, PropLine 3-25 keys = 3,000-25,000/day);
   b. PropLine Hobby $9 / Pro $19 (more daily requests in one key, same freshness) if you want fewer keys to manage;
   c. PropLine Streaming Lite $39 ONLY for live pushes (suspend/line-pulled, Kalshi/Polymarket at ~1 s) after the paper test of how those would be used (TPL1); it is not a pregame upgrade.
3. Do NOT spend on BoltOdds ($349), SharpAPI ($79+), SGO ($299+), The Odds API ($30+), Pinnodds ($99+).

## Free keys to add, in order
Pinnwire and pinnapi (as many as allowed) > PropLine (10+) > ParlayAPI free keys (overflow credits) > Unabated > MoneyLine free (one, auto-upgrade OFF) > SharpAPI free (one) > The Odds API (backup only) > Tennis API (scores) > BoltOdds trial (refresh when it lapses, live paper tests only).

## What the app does with them automatically (v0.95.9)
After every scan it measures each API (age by the books' own stamps, distance from Pinnacle), asks the freshest accurate one first, auditions a standby feed every 6 h so it can earn the top, rotates keys of one API when one is spent, and falls to the next on a failure. Each bet shows "Fair odds from: book via API age".
