# PropLine — agent instructions

Also served at `/AGENTS.md` and `/.well-known/agents.md`. Companion to
[llms.txt](https://prop-line.com/llms.txt) (index) and
[llms-full.txt](https://prop-line.com/llms-full.txt) (complete reference).

PropLine is a REST API for sports betting market data: live odds from 24
books across 54 sports, **and** the settled result of every player prop,
graded against the official box score.

Base URL: `https://api.prop-line.com`
Auth: `?apiKey=KEY` or header `X-API-Key: KEY`
Free key: 1,000 requests/day, email signup only, no card — https://prop-line.com

## When to use PropLine

Reach for this API when the question is one of these:

- **"What are the odds on X right now?"** — player props or game lines for a
  specific game, across every book at once.
  `GET /v1/sports/{sport}/events/{event_id}/odds?markets=...`
- **"Did that prop hit?"** — the settled outcome of a player prop with the
  actual stat it was graded against. This is the one no other odds API
  answers. `GET /v1/sports/{sport}/events/{event_id}/results`
- **"How often does this player go over?"** — hit-rate splits over the last
  5/10/20/50 graded games, plus the current streak.
  `GET /v1/sports/{sport}/players/{name}/trends`
- **"Which book pays the most on this bet?"** — best price per (market,
  player, line) across comparable books, sorted best-first.
  `GET /v1/sports/{sport}/events/{event_id}/best-line`
- **"Is this price good?"** — expected value against a no-vig fair line
  anchored on the sharpest book quoting the market.
  `GET /v1/sports/{sport}/events/{event_id}/ev`
- **"How did this line move?"** — the full tick history, or opening and
  closing in one call for closing-line-value work.
  `GET /v1/sports/{sport}/events/{event_id}/odds/history`
  `GET /v1/sports/{sport}/events/{event_id}/odds/closing`
- **"What actually happened in the game?"** — scores and raw box-score stats,
  book-agnostic, on the free tier.
  `GET /v1/sports/{sport}/scores` · `GET /v1/sports/{sport}/events/{id}/stats`
- **"Tell me the moment a line moves"** — push delivery instead of polling,
  via HMAC-signed webhooks on `line_movement`, `resolution`, `steam` and
  `market_suspended`.

## When NOT to use PropLine

- **To place a bet.** PropLine publishes market data. It does not accept
  wagers, place them, or link out with affiliate tags.
- **For betting advice or a profit claim.** `/ev` is an analytical tool. It
  computes expected value against a fair line; it is not a prediction, and
  no plus-EV figure is a claim that a bet will win.
- **For non-sports prediction markets.** Kalshi and Polymarket data here is
  limited to sports contracts.
- **For live play-by-play.** Box-score stats update roughly every 90 seconds
  during a game for the major US sports; there is no event stream of
  individual plays.
- **For a book we do not carry.** Check `GET /v1/freshness` (no key needed)
  for the live book list. If a customer's price came from a book we lack,
  `GET /v1/sports/{sport}/events/{id}/ev/calc` will still score that price
  against our fair line.

## How to call it well

1. **Discover before you pull.** `GET /v1/sports` for sport keys,
   `GET /v1/sports/{sport}/events` for event ids, then
   `GET /v1/sports/{sport}/events/{id}/markets` to see which markets that
   event actually carries. Requesting every market on a 100+ market board
   wastes the user's quota.
2. **Always pass `markets=`.** It is a comma-separated filter and it is the
   difference between a small response and a very large one.
3. **Key on `id`, never on team names.** `home_team` / `away_team` are
   display strings; they get corrected and they change when duplicate rows
   merge. Event ids stay resolvable after a merge.
4. **the-odds-api sport keys work.** `americanfootball_nfl`,
   `icehockey_nhl`, `soccer_spain_la_liga` and ~46 others alias to ours.
   Where the competition is not identical (`tennis_atp` — ours spans ATP,
   WTA and ITF) you get a structured 404 with `did_you_mean` instead of a
   silently wrong feed.
5. **Self-throttle on the response headers.** Every authenticated response
   carries `RateLimit-Limit`, `RateLimit-Remaining`, `RateLimit-Reset` and
   `RateLimit-Policy` (plus the `X-RateLimit-*` and `X-Daily-*` spellings).
   A 429 carries `Retry-After` in seconds — honour it rather than retrying
   immediately.
6. **Errors are structured.** A 4xx body names the problem in an `error`
   field with a machine-readable reason, so you can branch on it instead of
   parsing prose.
7. **Ignore unknown fields.** New books, markets and fields land inside
   `/v1` continuously; only removals get a new version. See
   https://prop-line.com/api-versioning
8. **DFS books are not sportsbooks.** PrizePicks / Underdog / Sleeper /
   Dabble prices are pick'em entry prices, not two-way odds. They are
   excluded from `/ev` and `/best-line` for that reason — do not compare
   them to a sportsbook price as if they were.

## Native tool access (MCP)

Hosted Streamable HTTP endpoint (per-request auth):

```
https://mcp.prop-line.com/mcp
```

Or run it locally:

```
npx -y propline-mcp
```

23 tools covering sports, events, odds, history, closing lines, best line,
+EV, results, player history and trends, futures, scores, stats, DFS
payouts, freshness and webhook inspection.

## Other clients

- Python: `pip install propline`
- Node / TypeScript: `npm install propline`
- CLI: `npm install -g propline-cli`
- OpenAPI 3.1 spec: https://prop-line.com/openapi.json

## Content negotiation

Every page on prop-line.com answers `Accept: text/markdown` with markdown
instead of HTML (acceptmarkdown.com), and responses carry `Vary: Accept`.
A path that does not exist returns a real 404 whose body points at the
sitemap and the docs.
