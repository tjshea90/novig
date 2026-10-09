# Pinnacle (Pinnodds socket) vs Novig, live study 2026-10-08/09 (RESEARCH.md §120.2, §120.3)

Files (all keyless; the Pinnodds key was read from the environment and is in none of them):
- `lag_20261008.ndjson.gz` 23:33-23:40Z and `lag_20261008b.ndjson.gz` 23:43Z-00:00Z: tapes written by `tools/research/pinn_novig_lag.py record` (k=pin Pinnacle market change, nov Novig book read, mkt, match, score, close).
  The recorder died at 00:00Z when the container restarted: ~24 min of live data in all (15 matched games: NBA preseason 4, NHL 7, football 4). Not a representative sample: most scores are NBA baskets.
- `trades_live.ndjson.gz`: Novig's public trade history for the matched markets (`tools/research/pinn_novig_maker.py fetch`).
- `deep_live_1.txt`: `tools/research/pinn_novig_deep.py` output on the two tapes (sections A-G).
- `maker_live_ttl{30,60,180}.txt`, `maker_live_ttl60_cancel5.3.txt`: `pinn_novig_maker.py sim` outputs (resting bids priced from Pinnacle's fair).
Re-run: `python3 -I tools/research/pinn_novig_deep.py lag_20261008.ndjson.gz ...` needs the files gunzipped first.
