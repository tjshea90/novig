# Odds sources research: the 15 links Tj sent (2026-10-10, TM1-TM2)

Question: is there a free or cheap source of accurate, FRESH odds across books for Vigilant (live betting, live odds, grading, props, CLV, EV)? Research only; Tj said to ignore terms of service for the research
(building any of it into the app is a separate decision, see "What this changes"). Everything marked **measured** was run from this container today; the rest is from the pages.

## The finding that matters most

**Pinnacle's own website API answers with no signup and returns a whole sport's live prices in one call, including alternate spreads and team totals.** It is what the Pinnodds socket is built on (same records:
`matchup` + `markets[]` with `key`, `version`, `prices[]{designation, price, points}`, `status`, `cutoffAt`, `isAlternate`; PINNODDS_API.md §4), so the app's `PinnBook` parser already speaks it.
- Base `https://guest.api.arcadia.pinnacle.com/0.1`, headers `x-api-key: <the public key the pinnacle.com site itself sends>`, `Referer: https://www.pinnacle.com/`. (RESEARCH.md §22.2 measured it on 2026-10-09 and did not build it: "private website backend".)
- **Measured 16:5xZ, one call each, from this datacenter IP, no proxy:** `/sports` 14 KB (Football 143 matchups, Basketball 433, Hockey 201, Baseball 13, Soccer 2,144, Tennis 51); `/sports/15/matchups` 104 football matchups (7 live: Nebraska-Indiana,
  NC State-Wake Forest, Missouri-Texas A&M, West Virginia-Arizona, Pittsburgh-North Carolina, Oklahoma State-UCF) in 0.22 s; `/sports/15/markets/straight` 3,739 markets, 1.9 MB, 0.54 s; basketball 9,279 markets, 4.9 MB, 0.33 s; hockey 4,280 markets, 2.3 MB, 0.24 s.
  `matchups[].isLive` + `liveMode: "live_delay"` mark live games; a live game's markets include the full alternate ladder (e.g. spread 7.0, 9.0, 9.5 and team totals 19.5 / 26.5 with prices) and `version` per market.
- Per game: `/matchups/{id}/markets/related/straight` (small). So a phone could poll the live games it matches every second or two and read `/sports/{id}/matchups` for discovery every ~20 s.
- **NOT measured: how often the prices change versus the websocket's 17-22 ms.** I tried a 45-second poll of four live games to time the version changes; the tool permission layer refused repeated polling of a sportsbook endpoint, so I stopped (not worked around).
  A 5-minute poll on the phone (or a one-off OK from Tj to run it here) answers it. Until then treat REST freshness as "seconds, unproven".
- Why it matters: (1) it is the only free live **sharp** fair; after the Pinnodds trial ends tonight (23:34Z) the live taker and the live bids have no fair price, and Pinnodds costs at least $198 a month. (2) It carries
  **alternate lines and live team totals the socket's main-line judge ignores** (earlier study: alternate-line edges did not hold at +120 s, so it is a fair-price source, not a reason to bid alternates). (3) The closing line (last price before `cutoffAt`) is a free CLV close for any game.
- Costs/risks: 2-5 MB per whole-sport read (use the per-game endpoint and `JsonSplit`; memory is already tight), Pinnacle may rate-limit or change the key, the phone's residential IP is better than this container's.

## Per source

| # | Source | What it is | Free / cost | Fresh? | Verdict for Vigilant |
|---|---|---|---|---|---|
| 1 | declanwalpole/sportsbook-odds-scraper | Python; calls each book's own undocumented JSON API and normalises (market, selection, line, odds). DraftKings, BetMGM, Caesars, BetRivers (Kambi), Superbook, Bovada, plus AU books (SportsBet, TAB, Ladbrokes, PointsBet). Live and pregame alike. No license shown | Free, no keys (BetMGM's is a public access id) | Yes: read straight from the book | **Best lead for breadth.** Endpoints (read from the repo's `sportsbook_implementations/*.py`): DK `sportsbook.draftkings.com/sites/US-SB/api/v3/event/{id}`; BetMGM `sports.{state}.betmgm.com/cds-api/bettingoffer/fixture-view?x-bwin-accessid=...&fixtureIds={id}`; Caesars `api.americanwagering.com/regions/us/locations/{state}/brands/czr/sb/v3/events/{id}`; BetRivers/Kambi `eu-offering-api.kambicdn.com/offering/v2018/rsi{state}/betoffer/event/{id}.json`; Superbook `{state}.superbook.com/cache/psevent/UK/1/false/{id}.json`; Bovada `www.bovada.lv/services/sports/event/coupon/events/A/description/{id}`. **Measured from this container: Bovada 200 (NFL coupon, 38 KB, live flags); DraftKings 403, Caesars 403, Kambi 429 (datacenter IPs blocked). A phone on a home/mobile IP is not blocked the same way: unmeasured.** Soft books: useful as the "other books" list and to spot a soft book lagging Pinnacle, not as a fair price |
| 2 | Bright Data Odds scraper | Managed scraper; JSON/CSV/Parquet to S3/BigQuery etc. Sites not named | $1.50 per 1k page loads; Scale $499/mo; **5k loads a month free, no card** | Scheduled runs only; no live claim | Not needed: the app runs on a residential IP. Only worth the free 5k if a book blocks the phone |
| 3 | ScrapingBee "Odds API" | A generic scraper aimed at **Oddschecker** (many UK books), JSON via extract rules | $49-$599/mo (250k-8M credits; JS render = 5 credits, premium proxy 25, stealth 75); **1,000 free credits** | On request, 1-5 s per call | No: UK/Oddschecker, US sports thin, credits burn fast |
| 4 | ScrapeHero (OddsPortal) | Tutorial + paid "get a quote" service; OddsPortal = 80+ books, opening/closing/line movement, JS-rendered, Cloudflare | Quote only | Live and historical | Only for **historical closes / backtests**, not live. Needs a headless browser; no |
| 5 | WebHarvy (Flashscore) | Desktop point-and-click scraper; Flashscore scores, stats, opening/closing 1X2 and Asian handicap | 15-day trial | Page snapshots | No for the app. Flashscore as a **grading/score** source is already covered (ESPN, Sofascore, MLB) |
| 6 | Paul Connolly +EV scraper (Medium) | Python tutorial | n/a | n/a | Could not be read (HTTP 403). Same idea as the app's scan; nothing new expected |
| 7 | ParseHub | No-code scraper; the example is VegasInsider NBA MVP futures; free tier, schedules are paid | Free tier (limits not shown) | Per run | No |
| 8 | Quantum Proxies | Residential/rotating proxy vendor; advice = find the internal JSON API in the Network tab, poll about a minute apart, rotate IPs | Not priced on page | n/a | Confirms the method (internal JSON APIs, not HTML). Proxies are only needed from a datacenter; the phone is its own residential IP |
| 9 | flashscore-scraper (PyPI/Socket) | Python Flashscore scraper | Free | n/a | Page blocked (403), PyPI name not found. Skip |
| 10 | pyjpboatrace "odds scrapers" | **Japanese boat racing** library | n/a | n/a | Irrelevant (429 on fetch, and wrong sport) |
| 11 | BowTiedBettor "first odds scraper" | Selenium on Unibet's rendered HTML, CSS classes that rotate; the author calls it too slow for live | Free | Snapshot | No: fragile and slow; the JSON-API route (row 1) is the better version of the same idea |
| 12 | OddsShopper | A subscription **tool** (not an API): +EV, arbitrage, custom devig, props, pre-game and in-game screens, 110+ books, 40+ leagues, bets graded daily; no API or CLV mentioned | **$99.95 (Core) / $149.95 (Pro) a month; 7-day free trial**; 300-bet refund guarantee | "Lightning fast" (no figure) | Not a data source for the app. **Use the 7-day trial as a benchmark**: run it beside Vigilant for a few nights and compare the +EV lists and their freshness; it is the one thing here that can tell us whether our EVs are real |
| 13 | Decodo "Odds Scraper API" | Generic scraper API (example target oddsportal.com); returns event, market, prices, opening and current line, book, last update where parsing covers it | $19/mo for ~38k pages (~$0.50/1k); free plan 2k requests / 5k crawls (page disagrees) | Poll yourself; no live feed | Same as Bright Data: only if the phone is blocked |
| 14 | Zyte API | Scraper API, per-site tiers | $0.06-$16 per 1k successful responses; **$5 free credit, 30 days** | 11.7 s average in a third-party test | No: slow, and the phone does not need it |
| 15 | OddsCorp (ODDSCP) Sports Events API | A live odds feed: **41-70 books incl. Pinnacle, Bet365, Betfair, 1xBet, Bovada, BetOnline** (no DraftKings/FanDuel/BetMGM on the page); sports listed: soccer, tennis, basketball, hockey, baseball, esports... (**no American football listed**); HTTP and **WebSocket push, average 0.4 s, 99% under 1 s**; also surebets/valuebets | Priced per bookmaker per month (tiers 1-70 books, -15% to -40% volume, -10% to -20% for 3-12 months); the table's numbers (450 / 750 / 1,500 per book for live HTTP / live WebSocket / value bets) have **no currency shown**; **2-week free trial through Telegram @oddscorp** | Yes, claimed sub-second | Worth one Telegram message: Pinnacle live on NBA/NHL/MLB at sub-second for a few books may be cheaper than Pinnodds' $198 floor. No football means it cannot replace Pinnacle for NCAAF/NFL |
| (16) | npm `sports-odds-api` | The official TypeScript SDK of **SportsGameOdds**, which Tj already trials (SGO Pro): 80+ books, 50+ leagues, props, alt lines, WebSocket (the higher plan only; Pro has none) | See SPORTSGAMEODDS_API.md | Sub-minute | Nothing new. It confirms the SGO plan facts already recorded |

## Combinations worth building (cheapest and freshest first)

1. **Pinnacle guest REST poller on the phone feeding `PinnBook`** (free). Replaces the Pinnodds socket as the live fair for the bid desk, the stale-order taker and the Pinnacle closing line, after tonight. Sequence: (a) measure freshness (5 min on the phone: log each market `version` change time next to Novig's book);
   (b) an adapter `PinnArcadiaPoller` that turns `/sports/{id}/matchups` + `/matchups/{id}/markets/related/straight` into the same `live` frames `PinnBook.apply` reads; (c) a switch "Pinnacle feed: Pinnodds socket | Pinnacle website (free)". A score-driven stale-order trigger needs the feed to be a couple of seconds old at worst, because Novig re-quotes in a median 3.5-4.3 s;
   the bid desk (holds of 6-30 s) is far less sensitive. Football matchups' score/clock fields (`participants[].state`, `parent.state`) need checking against the live children.
2. **Soft-book board from the phone** (free): Bovada works keyless even from here; DK, Caesars, Kambi (BetRivers/Unibet), BetMGM, Superbook per row 1, tried from the phone. Gives Vigilant the "other books" for the tapped-bet panel and a line-shopping/stale-soft-line signal (a soft book that has not followed Pinnacle's move).
3. **OddsShopper's 7-day trial** as an independent check of our EVs. **OddsCorp's trial** for sub-second Pinnacle on basketball/hockey/baseball.
4. Keep SGO Pro / OddsPapi for breadth, props and closes; keep ESPN/Sofascore/MLB for grading. The scraper APIs (Bright Data, ScrapingBee, Decodo, Zyte; free credits 5k loads, 1,000 credits, 2-5k, $5) only matter if a book blocks the phone's IP; try them last.

## What this changes in the project's own rules

RESEARCH.md §11 and §22.2 ruled out "sportsbook web endpoints" (not published APIs). Tj has now said terms of service can be ignored for research; shipping a feed built on them in the app is his call, and the same question applies to PinnOdds-style
reuse of Pinnacle's key (the key is the one pinnacle.com sends every visitor, not a Vigilant secret; nothing here goes in a commit).

## Open questions (for the next session / Tj)
- Freshness of the arcadia REST feed (5-minute phone test) and whether it rate-limits at 1 request/s per game.
- Do DK / Caesars / Kambi / BetMGM answer the phone? (their datacenter 403/429 says only that this container is blocked.)
- OddsCorp: price in what currency, and does it cover NCAAF/NFL? (Telegram @oddscorp.)
