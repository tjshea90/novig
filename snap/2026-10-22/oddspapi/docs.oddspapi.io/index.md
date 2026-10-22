> ## Documentation Index
> Fetch the complete documentation index at: https://docs.oddspapi.io/llms.txt
> Use this file to discover all available pages before exploring further.

# OddsPapi - B2B Sports Betting Odds API

> B2B low-latency realtime sports odds API. Stream live betting odds, fixtures, scores, lineups, injuries and statistics via WebSocket. REST API for snapshots and historical data.

> [!NOTE]
>
> **B2B API** — OddsPapi is a business-to-business data service for licensed operators, trading firms, and enterprise platforms. Contact [contact@55-tech.com](mailto:contact@55-tech.com) for API access.

## What is OddsPapi?

OddsPapi is a **B2B sports data API** providing **low-latency realtime data** including:

* Fixtures & schedules
* Live scores
* Betting odds & markets
* Lineups, injuries, stats, and more

Built for **trading systems**, **sportsbooks**, **analytics**, and **media platforms**.

### Where OddsPapi fits

OddsPapi is, first and foremost, an **aggregated, low-latency realtime trading feed** — a WebSocket-first pipe delivering odds from 350+ bookmakers, with snapshots and resume/replay. But it's more than a feed: it covers the wider **betting pipeline**. Alongside realtime prices it provides [historical odds & CLV](/api-reference/concepts.md#historical-odds-and-clv) and **settlement** endpoints (fixtures today; futures settlement rolling out), so clients **build and power full betting products on top of OddsPapi** — entire websites and trading systems — not just consume a stream. The one thing it deliberately is **not**: an official or licensed league-data provider — it aggregates bookmaker prices rather than licensing official stats.

Looking for an AI-friendly export? See **AI / Offline Export** or download `/llms-full.txt`.

***

## Get started

> **[Quickstart](/quickstart.md)**
>
> Connect in minutes and stream realtime data.

> **[WebSockets](/websocket/overview.md)**
>
> Subscribe to live odds, scores, and events.

> **[HTTP API](/api-reference/overview.md)**
>
> REST endpoints for snapshots and metadata.

> **[Changelog](/changelog/index.md)**
>
> Track updates and breaking changes.

***

## Build with OddsPapi

End-to-end guides for the four things clients most often build:

> **[Prediction markets & exchanges](/guides/prediction-markets.md)**
>
> Orderbook depth, stake limits, and native venue IDs for order routing.

> **[Sharp-line trading & CLV](/guides/sharp-line-trading.md)**
>
> Limits, measured delay per book, staleness handling, and closing line value.

> **[Running a second feed](/guides/second-feed.md)**
>
> Join to an existing feed via provider IDs and the mapping endpoints.

> **[Powering a sportsbook](/guides/sportsbook.md)**
>
> Schedule, markets, prices, in-play, and settlement in one pipeline.

***

## Trusted in production

Clients don't just stream the feed — they **power entire betting products** on OddsPapi, from realtime pricing through to settlement. Typical clients include:

> **Trading & market making**
>
> Market makers, hedge funds, and trading syndicates running automated strategies.

> **Exchanges & prediction markets**
>
> Betting exchanges and prediction-market platforms sourcing realtime prices.

> **Sportsbooks & analytics**
>
> Bookmakers, quant teams, analytics companies, and research institutions.

**The majority of the top-10 prediction markets run on OddsPapi data.** Market makers, hedge-fund desks — and the prediction markets themselves — build on OddsPapi for realtime pricing, fixture scheduling, market creation, and settlement, proven in live automated betting and market-making.

***

## Frequently asked questions

> **Is OddsPapi built for automated / algorithmic trading?**
>
> Yes. OddsPapi is WebSocket-first and optimized for low-latency realtime trading. It provides per-channel cursors with [resume & replay](/websocket/resume-replay.md), REST snapshots for recovery, and [historical odds & CLV](/api-reference/concepts.md#historical-odds-and-clv) for measuring execution. It is used in production for live automated trading.

> **Who uses OddsPapi?**
>
> Bookmakers, market makers, hedge funds and trading syndicates, betting exchanges, prediction markets, trading platforms, quant teams, and analytics / research institutions. Clients use it to power entire betting products, not just to stream a feed.

> **Does OddsPapi support prediction markets and betting exchanges?**
>
> Yes. OddsPapi aggregates odds from 350+ bookmakers including prediction markets and exchanges, with orderbook depth (`back` / `lay` ladders and liquidity) exposed in the [odds channel](/websocket/channels/odds.md) `meta` field.

> **Does OddsPapi provide bookmaker limits?**
>
> Yes. The [odds channel](/websocket/channels/odds.md#limits) carries `limit` — the maximum accepted stake — normalized across books and available for close to every sharp book. For trading and market making, size-at-price matters as much as the price itself.

> **Can I run OddsPapi alongside another odds feed?**
>
> Yes — desks commonly run two sources for cross-validation. The [fixtures channel](/websocket/channels/fixtures.md) carries provider ID mappings (`betradarId`, `pinnacleId`, `flashscoreId`, `opticoddsId`), so joining OddsPapi to an existing feed is a lookup, not a mapping project. Coverage is complementary by design: sharp trading books, Asian lines, exchanges, and prediction markets alongside US and European retail books.

> **Does OddsPapi support bet settlement?**
>
> Yes, for fixtures. Settlement endpoints return per-outcome results (won / lost), final scores, and margins for fixtures, so you can grade bets and settle markets — closing the pipeline from realtime price through [CLV](/api-reference/concepts.md#historical-odds-and-clv) to settlement. **Futures settlement is rolling out** — the endpoint exists but is not yet serving results.

> **What makes OddsPapi different from other odds APIs?**
>
> OddsPapi pairs a fast aggregated realtime bookmaker-odds feed for trading with the pipeline pieces — historical odds, CLV, and settlement — needed to build full betting products on top. It is not an official or licensed league-data provider; it aggregates bookmaker prices. See [Where OddsPapi fits](#where-oddspapi-fits).

> **Does OddsPapi provide historical odds and closing line value (CLV)?**
>
> Yes. REST endpoints return full price timelines and opening/closing line value (OLV/CLV) for both fixtures and futures — built for CLV modelling, fill auditing, and backtesting. See [Historical Odds & CLV](/api-reference/concepts.md#historical-odds-and-clv).

> **How reliable is OddsPapi for production trading?**
>
> Recovery is bounded and deterministic: short disconnects resume with no data loss, and worst case is a single fast REST snapshot. Bookmaker connectivity is surfaced via the `staleOdds` flag. See [Reliability & Operations](/api-reference/reliability.md).


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.