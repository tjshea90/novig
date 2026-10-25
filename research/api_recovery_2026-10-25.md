# What the dropped and downgraded APIs gave, and where it comes from now (PB17, Tj 2026-10-25)

Tj, 2026-10-25: "make sure all functions and features that were provided by apis which have since been paused or discontinued can be recovered as much as possible from the features offered by the apis I still
have, included but not limited to, grading and results, clv studies, sports book coverage, etc. propline and parlayapi offer most or all of these features."

Changed on 2026-10-25: Unabated and OddsPapi v5 removed (PB3); SharpAPI on its Free tier (PB1: 12 requests a minute, 2 books, no stream, no extras); Pinnodds a permanent free trial without its WebSocket (PB2);
BoltOdds off (PB4). Paid and kept: ParlayAPI Business, PropLine Streaming Lite, SportsGameOdds when on, Kalshi, SX Bet, Polymarket, ESPN (free).

| What it gave | Who gave it | Now from | Status |
| :- | :- | :- | :- |
| Pinnacle's closing line for CLV (game lines and props) | OddsPapi v5 `/clv`, SharpAPI's closing journal | ParlayAPI's Pinnacle closes (`ParlayCloses`: game lines 5 credits a league, props 1 per 1,000 rows, Business reaches back 90 days) on the phone's Tracker AND now first in the engine's grader (`GradeMain`, `engine-grade.yml` gets `PARLAY_API_KEY`); PropLine's `/odds/closing` + `/clv/grade` in `propline_clv.py`; Novig's own close (`NovigTradeCloses`, the star rating's yardstick) | done |
| Game results and prop grading | OddsPapi v5 scores, SharpAPI game state | ESPN/MLB box scores (free, first), SportsGameOdds when on, PropLine `/results` (`PropLineScores`), ParlayAPI's play-by-play stream for live scores (`ParlayPbpStream`) | done (already wired) |
| Sharp books' prices (Pinnacle, Bookmaker, Circa, BetOnline) | OddsPapi v5, SharpAPI Sharp plan, Pinnodds socket | ParlayAPI Business: Pinnacle, Bookmaker, BetOnline, ProphetX on its board (15 books since PB11) AND its odds stream (pushes within seconds, laid over the board: `ParlayLive`); PropLine's Pinnacle; SX Bet, Kalshi, Polymarket (exchanges) | done (PB11) |
| Soft-book coverage (25+ US books) | Unabated, BoltOdds, SharpAPI's 25 books | PropLine (40+ books, Streaming Lite pushes) + ParlayAPI (15 books asked, every book's moves in the stream); where both carry a book the fresher price wins (`Pricing.chosenMarkets`, PB21) | done |
| Real-time prices between scans | BoltOdds socket, SharpAPI stream, Pinnodds | the engine's real-time list (`RealtimeList`): PropLine's and ParlayAPI's pushes + Novig's bbo socket, every 3 s (PB19) | done |
| +EV at Novig as a second opinion, for research | SharpAPI's +EV journal | PropLine's `/ev` sweep (`PropLineEvSweep`, journal `propline-ev-*`) and its `ev` pushes on the signals socket | done (already wired) |
| Steam / line moves | SharpAPI deltas, BoltOdds pushes | PropLine `steam` pushes (journal `propline-push-*`), ParlayAPI's `/v1/odds-drop` stream (threshold moves) | journaled; whether steam is a profit signal is PB12 |
| Pinnacle's LIVE (in-play) price for the live lane | Pinnodds socket, SharpAPI live feed | ParlayAPI `/v1/sse/hot/{sport}` (Pinnacle + FanDuel, sub-3 s) and `/ws/live` | not built yet: TASKS.md PB17b |
| Opening lines / line history | OddsPapi v5 historical | PropLine's resolved archive (`propline-archive-*`, opening AND closing), ParlayAPI `/line-movement` (2 credits) and `/historical/.../odds` (10) | done (archive); ParlayAPI's history on demand |
