# Can the 15 sources replace Tj's trials, or beat the APIs the app already uses? (2026-10-10, TN5)

Tj's question: "consider if any of the sources I already listed can replace the free trials I have or can be better than the apis I already use". Companion to `odds_sources_2026-10-10.md`.
What Tj holds or the app already speaks: **Pinnodds** (trial ends 2026-10-10 23:34Z, then at least $198/mo), **SportsGameOdds Pro** (trial; $299-499/mo after, free tier is 10 minutes old), **OddsPapi v5** (trial by email, free key 250 requests a month), **ParlayAPI** ($5 Starter), plus PinnWire/pinnapi (free Pinnacle REST keys), PropLine (free 1,000 a day), The Odds API (free 500 credits a month), Kalshi, Polymarket, ESPN, Sofascore, MLB, CrazyNinjaOdds.

## Verdict by what each trial gives Vigilant

| Trial / API | What Vigilant uses it for | Can anything on the 15-link list replace it? | Best free or cheap route |
|---|---|---|---|
| **Pinnodds** (live Pinnacle push) | Live fair for the taker and the bid desk; Pinnacle closing line | **Yes, in part: Pinnacle's own website feed** (built, v0.88.0). Same prices and records, free, no key; polled, so seconds old instead of ~20 ms | The website feed. Compare run tonight (trial still live) gives the lag in numbers. OddsCorp (WebSocket, 0.4 s, Pinnacle) is the paid fallback for NBA/NHL/MLB only (no football listed; price has no currency) |
| **SportsGameOdds Pro** ($299-499/mo) | Breadth of books for the scan's fair line, props, alternate lines, closing lines/CLV on old bets, grading | **No single replacement.** Scraper vendors give pages, not US-book props; OddsShopper has no API; Oddschecker (ScrapingBee) is UK-heavy. Pieces: Pinnacle website = Pinnacle's close and alternates; soft-book endpoints (README of sportsbook-odds-scraper) = DraftKings/BetMGM/Caesars/Kambi/Superbook/Bovada game lines and props, one parser each; ESPN/Sofascore/MLB = grading (already free) | Keep SGO only for what the free pieces can't do (historical closes on old bets, props at many books). Free tier is 10 minutes old: useless for live, fine for a slow pregame scan. Decide after a week of the website feed's CLV |
| **OddsPapi v5** (trial) | Circa and 350 other books incl. sharp ones, per-book freshness, WebSocket | **No** (none of the 15 carries Circa/Singbet). OddsCorp has some of the same sharp books (Pinnacle, Bet365, Betfair) without the US books | Free key (250 a month) for spot checks; the email trial for a week of Circa/props only if the free pieces leave a gap |
| **ParlayAPI** ($5) | Odds/props/halves/injuries | No | Keep: $5 |
| **PinnWire / pinnapi** (free keys, game lines, 100 a day) | Pinnacle for the pregame scan and every EV | **Beaten by the website feed** on rate (no daily cap), freshness (live too), alternate lines and `limits`; also Pinnacle's *specials* (player props) are on the same API (`withSpecials=true`; measured 1,025 NFL specials) | Make the website feed the first Pinnacle source of the pregame scan too (TN6) |
| **PropLine** (free 1,000/day) | Many books incl. props, one call a league | No | Keep, it is already the best free multi-book source |
| **CLV for open bets** | The close | Pinnacle website: last price before `cutoffAt` for any game, free | Poll each open bet's game in its last minutes (TN7) |

## The scraper/proxy vendors, answered plainly
Bright Data, ScrapingBee, Decodo, Zyte, ParseHub, WebHarvy, ScrapeHero, Quantum Proxies **can** fetch odds, so in principle they stand in for an odds API. In practice they are worse here:
- they fetch the same pages and internal endpoints the phone can already call (the README of sportsbook-odds-scraper shows the endpoints), so they add a hop, 1-12 s per call (Zyte 11.7 s average in a third-party test), and a per-call price (ScrapingBee: 5 credits per rendered page, a $49 plan buys 50,000 pages; Bright Data $1.50 per 1,000 loads; Decodo ~$0.50 per 1,000);
- their value is getting past a block (datacenter IPs, Cloudflare). The phone is on a residential or mobile IP and is not blocked the way this container is (DraftKings and Caesars return 403 and Kambi 429 here);
- the sites they target are aggregators of other books (Oddschecker, OddsPortal), which are the *slowest* link: the aggregator polls the books, then they poll the aggregator.
Where they would earn their keep: a source with **no usable endpoint** and a one-off need (historical closes from OddsPortal for a backtest). Free credits if wanted: Bright Data 5k loads a month, ScrapingBee 1,000 credits, Decodo 2-5k, Zyte $5.

## flashscore-scraper (uploaded)
Historical results and odds for football, handball and volleyball into SQLite; no live odds, no American sports, ~10 weekly downloads. Not useful.

## SGO after the trial (Tj's question)
Amateur free (10 minutes old), Rookie $99-149 (3 minutes), Pro $299-499 (sub-minute, no WebSocket), All-Star custom (WebSocket). Cheap and fresh do not coexist there; the Pinnacle website feed is the free fresh part, SGO is the breadth part.

## Recommended order
1. Tonight: run **Compare** with the socket on (trial ends 23:34Z), send the Diagnostics block. That turns "seconds old, unproven" into a number.
2. If the median lag is under about 2 s: use the website feed as the live source for bids, and as the first Pinnacle source of the pregame scan (TN6). If it is 3-5 s, the bid desk is fine on it and the stale-order taker is not.
3. Phone test of the soft-book endpoints (DraftKings, Caesars, Kambi) from the app (TM5), then decide whether SGO Pro is worth $299+.
4. Trials worth a week: OddsShopper (benchmark our EVs), OddsCorp via Telegram (ask for NCAAF/NFL and the currency).
