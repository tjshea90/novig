# CLV consensus: one accurate close per bet and bid (design, CH57, 2026-10-21)

Tj (chat 05:14 UTC): "always get clv data from multiple sources ... have the engine check it for accuracy before counting each bet as beating clv or not ... most accurate clv possible for all of my bet and bid records", provided no API waste.

## What exists
One close per bet: `TrackedBet.closeFair` / `closeVia`, filled by `CloseBackfill` (first source in a chain that answers: SGO, OddsPapi, ESPN DraftKings, Novig's last trades...). `clv = close_fair / cost - 1`. The dataset (`lines-*.csv.gz`) carries `close_fair`, `close_via`, `clv`, `clv_note`.

## What the first audit found (research/clv_audit_2026-10-21.md)
Only 27% of listed bets have a close; sources mix per bet; "Novig last trades" (3-10 trades) is the noisiest; sample SEs are large.

## Design
1. Per bet, a list of closes, each `(source, fair, weight, n)`: SGO Pinnacle / Circa, ESPN DraftKings, PropLine archive Pinnacle and Novig (already exported daily, no extra calls), OddsPapi clv, Novig last trades (weight by trade count, ignore under 5), the engine's last pre-start scan fair (`ev_last`/last looks, free).
2. Consensus: sharp sources (Pinnacle, Circa) weigh most; compute the weighted median; drop a source that differs from the median by more than 3 points of probability when at least two others agree (an outlier is recorded, not used).
3. Beat the close = consensus CLV > 0 AND (at least two surviving sources agree on the sign, or one sharp source with a clean quote). Otherwise the bet's CLV is "unconfirmed", reported apart and never counted as beat or missed.
4. Output columns: `clv_consensus`, `clv_n_sources`, `clv_sources`, `clv_agree` (all surviving sources same sign), `clv_confirmed`. Bids use the same join on their journal.
5. No new paid calls: only data already fetched; a new read must fit the existing daily budget (CLAUDE.md: PropLine 50 export calls/day, SGO flat rate).
6. Where: `tools/clv_consensus.py` (pure, selftest) joined into `tools/lines_dataset.py`; the grader in `cloud/GradeMain.kt` later keeps all candidate closes (list) rather than the first.

## Steps
- [ ] tools/clv_consensus.py + selftest, run on the existing dataset and PropLine archive; report coverage and agreement.
- [ ] dataset columns + DATA_DICTIONARY.
- [ ] grader keeps every close it fetches (GradeMain.kt, TrackedBet.closes list), phone and engine.
- [ ] bids.
