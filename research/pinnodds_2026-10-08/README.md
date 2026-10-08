# Pinnodds study, 2026-10-08 (RESEARCH.md §116, PINNODDS_API.md)

Recorded from a container (agent proxy, one evening of NBA preseason, WNBA, NCAAF and others), with Tj's 3-day demo key (never stored here).

| file | what |
| :- | :- |
| `pool.ndjson.gz` | the pooled combined tape of `tools/research/pinn_novig_lag.py record` (Pinnacle market changes on games matched to Novig, Novig public book reads, Pinnacle score changes, matches). One JSON object a line, `k` = pin / nov / mkt / match / score / close. |
| `analyze.txt` | `pinn_novig_lag.py analyze pool.ndjson`: how often Novig's ask beats Pinnacle's fair, by seconds since Pinnacle changed; lead-lag after Pinnacle jumps. |
| `simulate_*.txt` | `pinn_novig_lag.py simulate pool.ndjson` for several settings: the app's rule replayed over the tape, and whether Pinnacle's price held 30 s and 120 s later. |
| `score_vs_reprice.txt` | Pinnacle's score frame against its first moneyline reprice (a median 1.8 s ahead). |
| `socket_sample_60s_summary.txt` | `pinnodds_tape.py summary` of a 60 s all-sports tape: frame types, channels, per-sport counts, latency. |

Re-run: `python3 tools/research/pinn_novig_lag.py simulate <(zcat pool.ndjson.gz) --min-ev 0.05 --min-move 0.03`. The key is read from `PINNODDS_KEY` (gitignored `.env`) and is never written to a tape.
