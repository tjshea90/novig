# BoltOdds live feed, v0.95.2 diagnostics (Tj, 2026-10-12 16:58 ET)

File: research/uploads/2026-10-12/d15d640a-vigilant-diagnostics-v0.95.2-2026-10-12-1658.txt.gz

- Note: "BoltOdds flags 9 games in play; 0 have a quorum of 3 companies pushed within 5 s (best game has 0 fresh companies)". 8 NBA (preseason, 4:56 PM ET Monday: almost certainly NOT in play) + 1 MLB (Brewers v Dodgers, caesars pushing at 20:52Z).
- So `info.is_live` is probably true for games that merely start the same day, or the book that set it is stale. Not yet proven: v0.95.3 adds per-game detail (quotes, companies fresh within 5 s / 30 s / 5 min) to the note to settle it.
- Tennis: board has Tennis / Table Tennis games now (names not priced: Tennis Spread x28001, Moneyline x26313 "not priced" because none is subscribed through get_markets filter; moneyline conversion exists only for live tennis).
- Lines socket last error: "Re-subscription rate limit hit. Please wait 5 minutes" -> a re-subscribe fired too often (filters change when liveTennis toggles / sports list differs). TODO TBL7: only resubscribe when filters differ for real, and back off 5 min after that error.
- Streaming engine: 78 updates, priced 18 ms for 1377 markets, 103 passes skipped for a running scan, slowest 9375 ms (a pass that waited on a scan).
- Novig order socket: ready, 2 placed, last answered in 61 ms. Compression only 1.3x.
