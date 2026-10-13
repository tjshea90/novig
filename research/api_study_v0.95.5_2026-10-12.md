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

## Second file: v0.95.6 (e65e94fb, scan 20:36-20:39 ET, study mode on for the scan, off when the file was made)
File: research/uploads/2026-10-12/e65e94fb-vigilant-diagnostics-v0.95.6-2026-10-12-2040.txt.gz. The Odds API and OddsPapi Free were off, as asked.
| API | quotes | age median / p90 | notes |
| :- | -: | :- | :- |
| PropLine | 46,725 | 0 s / 70 s | REAL stamps; stalest Unibet 66 s, 1xBet 106 s; the majors (BetMGM, BetOnline, BetRivers, BetUS) at 0 s. Only 6 requests used. Matched 20 Novig games (coverage narrower than Pinnacle's 106 or ParlayAPI's 96) |
| PropLine props | 0 stamped | - | answered 4 leagues but matched no Novig game (health WATCH): a prop-matching problem to look at |
| SGO | 8,280 (+1,165 props) | 334 s / 382 s (props 299 / 347); bet365 665-819 s | slower than in the first file (229 s): 5.5 min typical, 11-14 min worst |
| ParlayAPI | 699 | 0 s / 32 s (heartbeat) | props 110 s median / 365 s p90 (DraftKings props 125 s) - the real change age |
| Unabated | 1,216 | 0 s / 15,920 s | fanduel 554 s, fanatics 5,167 s: the bulk is fresh, the tail is hours old |
| BoltOdds, Pinnacle, Kalshi, Polymarket | | 0 / 0 | push or read at request time (no stamp of their own) |
- Scan time: 176 s, fair odds 163 s because the chain is SEQUENTIAL (a lower feed waits for every better one: Unabated 163 s, PropLine 123 s, ParlayAPI 1st half 94 s are end-to-end waits, not their own request times).
- Verdict so far: freshness ranking among feeds with real stamps: PropLine > ParlayAPI (lines) > Unabated (bulk) > SGO. SGO is first on the chain and stalest. Not reordered yet: accuracy against Pinnacle had no number; v0.95.8 adds "vs Pinnacle (DK/FD main lines) off by N pts, X% within 1 pt" per feed to the Source race. Reorder (PropLine first) after that file.
