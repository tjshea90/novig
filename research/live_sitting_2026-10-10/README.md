# Live sitting quotes and live make bids on Novig (2026-10-10, RESEARCH.md §123, TASKS.md SV)
Public reads only: no key, no order, no Pinnodds call by this session.
- `sweep1.ndjson.gz` / `sweep1_report.txt`: one read of 462 live books (4 NCAAF games + 1 WNBA game, TOTAL / SPREAD / TEAM_TOTAL / MONEY), 2026-10-10 ~03:15-03:30Z. Tool: `tools/research/novig_live_sitting.py sweep|report`.
- `survive1.ndjson.gz` / `survive1_report.txt`: 24 of those books re-read 31 times over 4 minutes (about 8 s a pass); every resting order followed by its id. Tool: `... survive` and `... survival`.
- `maker_latency_sensitivity.txt`: `tools/research/pinn_novig_maker.py sim` on the 2026-10-08/09 tapes in `research/pinnodds_2026-10-08b/` with Novig's measured in-play order time (5.3 s) as both the place and cancel delay, and ttl 20 / 60 / 120 s.
