# v0.92.0 first evidence (Tj's diagnostics 2026-10-12 05:12 EDT)
- Order socket: placed 1, answered in 60 ms, 0 refused, 0 handed to REST. Works.
- Compression on: 95 frames, 226 KB became 770 KB (3.4x). Works.
- Pushed orders: events heard, 0 gaps. refused by Novig: nothing. Allowance line says "this key's own /v3/limits" (512 at 4/s).
- REGRESSION found: 2 of 4 scans took 98-135 s (feed held 19-120, 1,350-1,440 by REST); the good one 31 s. Cause: v0.92.0 carried the token model across reconnects (bulk subscribe waited for a full bucket, up to 128 s) and a compression switch reconnected a live feed. v0.92.1 reverts both.
- Canary: not run yet at report time (connection only 45 s old).

## v0.92.1 file (05:36 EDT)
- Bet check (the sheet's price read): 91-158 ms each (book read 80-148 ms, nothing before it). The bet-slip lag is NOT in the check.
- Scans 16-19 s (342 pushed + ~227 by key); the 2 before the feed connected 37-42 s. Order socket: placed 3, last ack 68 ms.
- Every connect gets one RATE_LIMIT_EXCEEDED ~8 s in (the key's stream bucket is per key and still drained from the last connection); the canary was throttled once (inconclusive). Compression 2.5x.
- v0.92.2 adds a "bet placed" timing line (re-check / order / waiting for end + reading fills) to find the lag after the confirm tap.
