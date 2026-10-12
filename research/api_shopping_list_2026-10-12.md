# API shopping list (Tj asked to simplify, 2026-10-12). Budget rule: $50/month or less. Everything not named here: do nothing.

## A. Get these FREE keys (no cost, biggest effect)
1. PinnWire + pinnapi (Pinnacle, real-time): as many as you can, 100 requests/day each. THE most valuable thing: Pinnacle is the sharp price the fair line is built on.
2. PropLine free keys: 1,000/day each (all tiers share the same freshness per the vendor; /v1/freshness shows its major books updating every few seconds). 10 keys = 10,000/day, plenty for pregame. Check the terms about many free accounts first.
3. Unabated (free), OddsPapi Free (v4), Polymarket, Kalshi: already in; nothing to buy.

## B. Paid, $50/month or less, in this order (stop when the Source race says enough)
1. ParlayAPI Starter $5: keep (props in one call, Pinnacle closing lines for CLV). Its Pinnacle rows are 11-53 minutes old for quiet sports: backup only.
2. PropLine Hobby $9 / Pro $19: only if the free keys run short; same freshness. Streaming Lite $39 only if paper tests show its pushed suspend/Kalshi events help (not built).
3. Pinnodds REST Pro $99: over budget; the free PinnWire keys give the same Pinnacle data. Let the socket lapse.

## C. Do NOT buy
BoltOdds $349 (no sharp book, over budget), SharpAPI ($79+, Pinnacle unconfirmed), a bigger The Odds API, SGO as the fair line (~5 min refresh; keep only if already paid for closes/grading).

## D. What I changed so the app uses the freshest source
- "Freshest quote wins" (Settings, default ON): when two of your APIs send the same book for one line, the one stamped >20 s newer is used.
- Diagnostics "Source race": per API, how old its quotes were (median, p90, freshest/stalest books). Run a scan with the APIs you want compared all switched on, then send me the Diagnostics file. That is the real speed test; I cannot run it from here because Claude sessions must not use your keys.
