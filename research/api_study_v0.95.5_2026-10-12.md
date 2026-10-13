# API study, first data: v0.95.5 Source race (Tj's file d284cd75, scan 20:07-20:09 ET, 144 s)
File: research/uploads/2026-10-12/d284cd75-vigilant-diagnostics-v0.95.5-2026-10-12-2009.txt.gz. Quote age = when the board was read minus the book's own stamp.

| API | quotes | age median / p90 | what it means |
| :- | -: | :- | :- |
| SGO | 8,975 (+3,125 props) | 229 s / 278 s (props 221 / 283); bet365 482 s | REAL stamps: 3.8 min old typical, 8 min worst. Slowest of the stamped feeds, yet first on the aggregator chain, so it answers before fresher ones are asked |
| ParlayAPI | 496 | 0 s / 64 s (bet365 53 s, BetOnline 40 s) | its stamp is the "last observed" heartbeat, so 0 s only means "seen just now", not "price changed just now" (Tj measured 5-10 min on changes). Slowest to READ: 41 s (1st half 43 s) set the scan's whole fair-odds time |
| BoltOdds | 50,009 (+3,695 props) | 0 / 0 | push socket: books lifted to "just heard"; read in 13 s from memory. 34 soft books, no sharp |
| Pinnacle (PinnWire/pinnapi) | 2,953 | 0 / 0 | no stamp of its own; read at request time, vendor says real-time; 106 games matched |
| Kalshi / Polymarket | 2,119 / 378 | 0 / 0 | read at request time; Kalshi 23 s to read |
| PropLine, The Odds API, Unabated, OddsPapi Free | 0 | not measured | NEVER ASKED: the standby chain let SGO/ParlayAPI answer first. OddsPapi Free (v4) has no stamps and 20 of 250 monthly requests left |

Reading
- The only independently stamped feed measured is SGO: it is ~4 minutes stale, so as the FIRST aggregator it can feed a stale fair line. Pinnacle (real-time) is the anchor and should stay; SGO should not outrank fresher feeds once they are proven.
- PropLine, the feed Tj cares about, has NO measurement yet. v0.95.6 adds "API study mode": one scan asks everything that is on so PropLine's true per-quote ages (it stamps real update times) appear beside SGO's.
- Other facts in the file: Novig order socket refused by a location check (451 ANONYMIZED_NETWORK, a VPN/proxy on the phone), The Odds API short of credits (last ones Oct 24), OddsPapi Free short (Oct 14), streaming engine "skipped for a running scan 458" with slowest 47.9 s (the scan was stopped by the Stop button at 20:01; Tj said ignore).
- Accuracy (do the prices agree with Pinnacle?) is not measured yet; the next step after PropLine's ages is a per-source agreement-with-Pinnacle number in the same report (TFR5).
