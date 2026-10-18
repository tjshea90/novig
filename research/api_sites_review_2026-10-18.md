# Review of nine sports-data sites for Vigilant / the engine (acct-27a37e tj Gmail, 2026-10-18)

Asked by Tj (chat, 2026-10-18). Pages read with WebFetch; api-sports.io and sitepoint answered 403, so those two rest on search results (marked). Nothing was signed up for or keyed.

| Site | What it is | Useful to us? |
| :- | :- | :- |
| odds-api.net | Odds aggregator, 125+ books incl. Pinnacle and Betfair (exchange ladders), pre-match only (no in-play), SSE + WebSocket on every plan, fastest refresh ~1/min. $30 Starter (50K req, 60/min, 1 stream, 2 books), $50, $90, $250, $500. No free tier. Not Novig/Kalshi/SX/Circa. | Only new thing: a Betfair exchange (sharp) reference; Starter's 2 books = Pinnacle + Betfair for $30/mo. Pinnacle we already have (SGO, Pinnapi, PinnWire, Odds API). No in-play, no trial. Tj's call (money); not recommended before a free trial. |
| omkar.cloud Flashscore scraper | ~45 GET endpoints over Flashscore: live scores (37 sports), match odds, lineups, stats, commentary, standings, H2H. Free "no signup" playground; pricing page not readable (404). | Best free candidate for LIVE SCORES + lineups as a fallback where ESPN has no match (TASKS TCL94c) and for injury/lineup checks. Scraper = grey area and soft-book odds only. Test the playground first. |
| sportmicro.com | 21 sports, live scores, incidents, lineups, news, WebSocket "one-time connection", <400 ms claim, free start with no card, per-sport pricing (no figures shown), has MCP server + llms.txt + OpenAPI. Odds not shown on the pages read. | Candidate live-score/incident WebSocket for the live lane (the lane has no score source of its own). Try the free tier on NBA/NHL/NFL; check odds endpoint list at docs.sportmicro.com/ai/sportmicro-all-endpoints.txt. |
| api-sports.io (search only) | One API per sport; free plan ~100 requests/day per API; paid from ~$10/mo (7,500/day, 300/min) on RapidAPI. Odds exist; book list/live odds unverified. | 100/day is too thin for live polling; fine as a free daily-schedule/injury/lineup fallback. Low priority. |
| thesports.com | Data feeds, Live Tracker widgets, 15+ sports, quote-based pricing, no odds mentioned, no push mentioned. | No: no odds, price on request. |
| betting.exchange (/betting-exchange-apis/) | Old educational site: Betfair API-NG (free for private individuals), BETDAQ (funded accounts only), Matchbook/Smarkets none. | Background only. Betfair is a sharp reference but account/region limits apply and odds-api.net would carry it. |
| coinsqueens blog | Marketing for building a betting platform; lists API categories only. | Nothing concrete. |
| sitepoint (search only) | Ratchet (PHP) in-memory broker pushing score events to websocket clients. | Nothing new: the engine/phone already push (cloud-feed, sockets). |
| postman.com | Free: Postman CLI, unlimited Collection Runner, 1,000 monitor requests/month, mock servers, MCP server, public API network. | Marginal: our probes run in Actions (ApiProbe, propline.yml). Postman's public collections can speed reading an API's docs; not worth wiring. |

## Recommended order (all free first)
1. Flashscore-scraper playground: test live scores/lineups on today's NBA/NHL/NFL games vs ESPN (fallback for TCL94c).
2. Sportmicro free tier: test the WebSocket scores for the live lane.
3. Ask Tj only if he wants to pay: odds-api.net Starter $30 for Pinnacle + Betfair exchange (decide after step 1-2; no trial exists).
Skip: thesports, coinsqueens, sitepoint, Postman, betting.exchange.

## Test results (2026-10-18, same session)
Neither could be called live from here: no key exists for either, this container's shell blocks direct curl egress, and a signup is Tj's account action. What the docs say instead:

**Flashscore scraper (omkar.cloud).** `GET /matches?sport=&day=&timezone=&status=live` returns competitions with matches (id, status, start_time, teams, scores, has_statistics, has_live_coverage); separate Live Matches, Match Odds, Match Lineups endpoints. Pricing page: Free $0 = **1,000 requests/month**, Starter $16 = 20,000, Grow $48 = 100,000, Scale $148 = 400,000. Verdict: the free tier is far too small for live polling (one 30 s poll is 2,880 calls a day); useful only as a rare lookup (lineups, a missing score). Live-score use would need Grow ($48) or Scale ($148), and it is a scraper of Flashscore (terms risk), so it does not beat ESPN for the live lane. API host and key header were not on the pages read.

**Sportmicro.** WebSocket `wss://{sport}.sportmicro.com/live/{type}?token=KEY` (also SSE on the same path), types score/time/incident/statistics/point-by-points/live-count, no subscribe message, "any active API key" works and WebSocket traffic is not charged. Endpoint index: basketball 14 endpoints (10 odds markets, 2 live, lineups, injuries), hockey 16 (12 odds markets), american football 7 (3 odds markets); odds endpoints exist, with a `/bookmakers` endpoint, but which books is unknown. Verdict: the best candidate of the nine for live scores + incidents on NBA/NHL/NFL; to know if it beats ESPN we need a free key and a 1-game listen (latency vs ESPN).

## To finish the test (needs Tj, once)
Create a free Sportmicro key (sportmicro.com/dashboard/subscriptions/new, no card). Per the repo's key rules, do not paste it in chat: it goes into a Settings key field with a Test button (a new ApiEntry), then a session reads the saved sample. Not built yet; TCL118b stays open. The Flashscore scraper is dropped (TCL118a closed as not worth it).
