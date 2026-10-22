# API waste audit (Tj chat t1792705931238-8a94, 2026-10-22 21:5x UTC) - first pass from the engine's usage.json

Read: `usage.json` on cloud-feed (run 39858513306), engine log `*plans:` lines. "calls" = the ledger's callsToday.

| API | today | state | verdict |
| --- | --- | --- | --- |
| PinnWire | 2,391 calls | 11 of 12 keys 100/100 by 21:38 UTC | WASTE found and fixed: asked every scan though SharpAPI pushes Pinnacle (research/pinnwire_waste_2026-10-22.md, ApiChain.pinnacleNeeded) |
| pinnapi | 134 calls | both keys "HTTP 403, key refused" | dead keys; tell Tj to replace or remove; check the pool rests a refused key (~22 tries each) |
| Pinnodds REST | 1 | "daily limit reached" | nothing to save |
| SharpAPI | 455k | flat, 1,000/min plan | flat rate, fine; counts stream/delta reads |
| SGO, Unabated, Polymarket, Kalshi, SX, BoltOdds | flat | no credits | no credit waste |
| PropLine | 12.9k | pro key 12.3k/25k at 90% of the UTC day | on pace |
| ParlayAPI | 85 calls | starter 13.7k/20k and 12.7k/20k, pro 27k/100k, 6 free keys 92-96% spent | free keys spent early; CH57b (cut purposes others serve) still open |
| The Odds API | 8.7k calls, 6 of 21 keys at 0/500 | last-in-line since 10-21; "Spend today: nothing" | the call count and the credit count disagree: check what the 8.7k are (refusals from spent keys cost no credit but cost time) |
| OddsPapi free | 434 | 1 key 241/250 | fine |

Open: CH57b, the Odds API call count, pinnapi's refused keys.
