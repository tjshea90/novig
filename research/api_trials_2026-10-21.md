# Four free-trial APIs judged for Vigilant (TR, Tj 2026-10-21)

Tj: "I can get free trials for all of the following apis. Research what they offer and if any of them are good for the vigilant app, GitHub engine, or live betting". Read from each vendor's public site and docs on 2026-10-21 (no key used). What Vigilant already has, for comparison: Pinnacle (PinnWire, pinnapi, SGO's Pinnacle, Pinnodds socket on the phone), Kalshi, Polymarket, SX Bet, SGO, OddsPapi, PropLine Pro, ParlayAPI Pro (incl. play-by-play stream), BoltOdds (soft books, no sharp book), Unabated, The Odds API; scores from ESPN/MLB/SGO and ParlayAPI's PBP.

## SharpAPI (sharpapi.io): YES, take the trial at the Sharp tier
- Books (docs.sharpapi.io/api-reference/sportsbooks, ~61): Free = DraftKings + FanDuel only (60 s delay, 12 req/min); Hobby $79 = 5 books, 120/min; Pro $229 = 15 books, 300/min, EV + middles + closing lines (`get_closing_lines`); Sharp $399 = 25 books, 1,000/min, adds the SHARP books: **Pinnacle, Circa, Bookmaker, Betfair (back; lay listed but unavailable), ProphetX, SBOBET, 1xBet**. Novig, Kalshi, Polymarket, BetOnline are Hobby books. Historical odds: Enterprise only.
- Streaming: SSE (`GET /api/v1/stream`, channels odds / ev / arb), a **$99/mo add-on on any paid plan**, not on Free. No WebSocket. Every book row carries `last_update`; the docs say capture frequency per book "varies materially and is the dominant term" in freshness and give no figure (marketing claims sub-second / P50 89 ms: unverified).
- 3-day free trial on Hobby, Pro and Sharp. The docs disagree with themselves on the book list (per-book table vs llms-full.txt); `GET /api/v1/sportsbooks` is authoritative.
- **Why it is worth it:** the sharp books Vigilant does NOT have today: **Circa** (game lines AND props), **Bookmaker** (props), **Betfair exchange**, **ProphetX** (exchange, props). More independent sharp prices = better fair lines, more sharp-confirmed bids (CH59's step-down needs a sharp book in the fair), and a second Pinnacle copy to race against PinnWire. Its SSE stream could be the **live** sharp feed the engine lacks (BoltOdds has no sharp book; Pinnodds is one connection on the phone). Closing lines (Pro+) add a CLV source.
- **What the trial must measure before we pay $399-498/mo:** (1) each sharp book's quote age by its own `last_update`, pregame and live, against PinnWire's Pinnacle and Novig; (2) props coverage per league for Circa/Bookmaker/ProphetX; (3) how many of our Novig lines it matches; (4) SSE update lag in play. Build: `SharpApiClient` + `SharpApiSource` on the engine (`CloudSources`, a `CloudKeys` secret, an `ApiChain` entry, a switch), the measurement recorded through the existing `apiq` journal, then the decision.

## Tennis-API (tennis-api.com): not now
- ATP/WTA/Challenger/ITF scores, point-by-point, rankings, H2H, "live odds, opening odds, odds history" (bookmakers not named), "sub-second" claim, 90+ years of history; pricing via RapidAPI or "contact us for a trial"; WebSocket only mentioned in a blog title.
- Vigilant already gets tennis point-by-point from ParlayAPI Pro's PBP stream (CH47f) and tennis prices from Pinnacle/Kalshi. Worth a trial only if that stream proves too slow or misses Challenger/ITF matches Novig lists: then its WebSocket as a live tennis score source for the live lane.

## Sportmicro (sportmicro.com): no
- 21 sports of scores, incidents, lineups, stats, "points-by-points" WebSocket, "<400 ms" responses; no odds documented, no public prices (per sport, by demo). It is a score feed: ESPN, MLB, SGO and ParlayAPI's PBP already cover what the live lane and grading need. Revisit only as a backup live-score socket.

## SportsDataIO (sportsdata.io): no
- Broad US/global data (scores polled every 5 s live, odds "consensus" of US books, Pinnacle not confirmed), REST only. **The free trial only covers the UEFA Champions League**, so it cannot be tested on the leagues Vigilant bets; enterprise pricing through sales. Nothing Vigilant lacks.

## Recommendation
Take **SharpAPI's 3-day trial on the Sharp plan, with the SSE add-on if the trial allows it**, and send the key from the app (Settings › APIs, then Send keys to GitHub) once the client is built. Skip the other three for now.
