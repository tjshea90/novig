# SB: the stars on a bigger sample (2026-10-24)

Tj, verbatim: "Since the sample size is small  can't you use some of the apis features of historical odds and clv and results"

## What was available (SB1)
- **Free, already saved (used here):** the phone's scan study (`phone-data` `phone/study/study-*.jsonl.gz`, Oct 13 to Nov 2: 275,710 journal records, 8,517 lines, 8,384 +EV, 2,342 with a Pinnacle/Circa close, 5,920 settled; each sighting records every book's price, fair and edge) beside the engine's dataset (`engine-dataset`, 2,880 +EV lines). Built with the engine's own `tools/lines_dataset.py`; the phone's lines that the engine also logged (same outcome, same day) were dropped: 10,428 +EV lines, 1,883 with a sharp close.
- **API history (not used yet; keys live in Actions secrets, so it runs as a workflow, SB3):** SportsGameOdds Pro `includeOpenCloseOdds` (every book's open and close incl. Novig, past events), OddsPapi Free v4 `/v4/historical-odds` (free, never counted, full price timelines since January 2026, 3 books a call, 5 s apart), PropLine Pro `/v1/exports/resolved-props` (90 days, open + close per book + result, 50 calls a day; `propline-archive-*` already keeps Novig and Pinnacle) and `/events/{id}/odds/history`, OddsPapi v5 `/fixtures/odds/historical` and `/clv`. SharpAPI has no history (live closing lines only). The weak point of open/close data: a book's "open" is when IT first priced the line, so a multi-book snapshot at one moment needs the timelines (OddsPapi v4, PropLine history).

## Findings (SB2, both datasets)
1. **The stated edge predicts the close, monotone, in both samples.** Phone, sharp CLV by stated EV: under 0.5% +0.21% (845 lines), 0.5-3% +0.7 to +1.1%, 3-4% +3.17% (96), 4-6% +2.64% (71), 6-9% +3.80% (25), 9-15% +7.48% (3). The engine's lines had the same shape.
2. **Support does not**, except in one place: companies, a sharp book agreeing and consensus show no CLV of their own in either sample; agreement share a little. **Small edges from one or two books lose**: one company at 0.5-1.5% ROI -44.6% (33 settled), two companies -39.3% ±27.5 (51); from four companies up the same small edges beat the close (+3.6%).
3. **The S5 stars (v1.0.68: SR's support-heavy points) do not order the bets**: sharp CLV per star over games on the 3,481 rated lines with inputs -0.12 (t -0.2); on the phone's lines alone they run backwards (ROI per star -12, t -1.5). Stars from the edge alone: +0.43 (t 1.0), ROI +6.1 (t 1.5), positive in both halves; on the phone's lines +0.80 and ROI +12.4.
4. **Rules tested and rejected:** a thin-market haircut on the edge (CLV per star about 0, ROI -6), the old company caps on the ladder including "two small books with no sharp book at most 2" (cut the slope to +0.15: two-book lines at 3%+ edges made +8.9% ROI over 139 settled), the type-of-bet history points (not predictive for the third time).

## The proposed change (S6; parked on branch sx-s6-wip, superseded by SX: see research/star_rating_sx_2026-10-24.md)
Stars from the edge (the profit), support where the data and the definition need it:
- **0 = no trusted edge**: under +0.5% or over +25% EV, a sharp book against (S5), and now **an edge under +1.5% from one or two companies**.
- **The edge sets the stars**: +0.5-1% = 1, +1-1.5% = 1½, +1.5-2% = 2, +2-2.5% = 2½, +2.5-3% = 3, +3-4% = 3½, +4-5% = 4, +5-6% = 4½, +6% and more = 5 (full marks at 6%, as the edge score always had).
- **The top needs breadth** (Tj: "5 Star bets ... with good support"): one company at most 2★, two at most 3★, three at most 4★; 5★ needs four or more.
- The SR points stay as the support score (`Rating.points`, `Rating.support`); the engine's stake sizing keeps reading the old support stars (`CloudKelly`), so no stake changes with this.

| S6 on every +EV line (10,428) | lines | sharp CLV (lines) | ROI |
| :- | -: | -: | -: |
| 0 no trusted edge | 3,317 | -0.01% ±0.53 (893) | -3.8% ±7.0 |
| 1-1½★ | 2,703 | +1.37% ±0.44 (923) | +0.6% ±8.0 |
| 2-2½★ | 2,489 | +1.64% ±0.72 (584) | -2.3% ±7.6 |
| 3-3½★ | 1,301 | +3.14% ±0.89 (252) | -3.4% ±9.7 |
| 4-4½★ | 467 | +2.87% ±1.61 (93) | -2.1% ±14.6 |
| 5★ | 151 | +3.41% ±3.22 (29) | +26.4% ±28.6 |

On the 2,929 lines with every input: 0 = sharp CLV -1.03%, ROI -15.2% ±12.3; 1-1½★ +3.41%, ROI -0.7%. Sharp CLV per star +0.24 (S5: -0.12).

## How sure
Sharp CLV per star is t 1.2 over 330 games on every line: better than any alternative and the right shape, still not proven at 95%. ROI by level is noise (±7 to ±29 points). The next evidence: SB3 (the APIs' history, a far bigger sample) and the digest's daily STAR RATING CHECK, which scores S6 beside S5's points and the pure ladder.

Revert: `BetRating.CURRENT_SCALE = Scale.S5` (one line) if over two digests with 300+ new sharp-closed rated lines S5's sharp CLV per star beats S6's.
