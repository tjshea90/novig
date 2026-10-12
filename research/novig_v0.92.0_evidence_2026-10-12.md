# v0.92.0 first evidence (Tj's diagnostics 2026-10-12 05:12 EDT)
- Order socket: placed 1, answered in 60 ms, 0 refused, 0 handed to REST. Works.
- Compression on: 95 frames, 226 KB became 770 KB (3.4x). Works.
- Pushed orders: events heard, 0 gaps. refused by Novig: nothing. Allowance line says "this key's own /v3/limits" (512 at 4/s).
- REGRESSION found: 2 of 4 scans took 98-135 s (feed held 19-120, 1,350-1,440 by REST); the good one 31 s. Cause: v0.92.0 carried the token model across reconnects (bulk subscribe waited for a full bucket, up to 128 s) and a compression switch reconnected a live feed. v0.92.1 reverts both.
- Canary: not run yet at report time (connection only 45 s old).
