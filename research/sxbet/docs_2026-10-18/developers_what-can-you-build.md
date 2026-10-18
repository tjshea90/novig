> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# What can you build?

> Explore the kinds of applications and tools you can build on top of SX Bet.

SX Bet exposes a full REST API with access to markets, orderbooks, order management, and trade history. This opens up a wide range of possible applications — from fully automated trading systems to custom frontends and analytics tools.

<CardGroup cols={1}>
  <Card title="Trading Bots & Automated Strategies" icon="robot">
    The most common use case.

    * **Market making** — post both sides of a market to capture the spread
    * **Arbitrage** — identify pricing discrepancies between SX Bet and other books
    * **Model-driven betting** — feed your own predictive models into automated order placement
    * **Closing positions** — hedge or exit existing positions programmatically
  </Card>

  <Card title="Custom Frontends" icon="browser">
    Build your own interface on top of SX Bet's liquidity:

    * Specialized views for specific sports or leagues
    * Mobile-native betting apps
    * Social/community betting platforms
    * White-label sportsbook interfaces
  </Card>

  <Card title="Analytics & Research Tools" icon="chart-line">
    The open orderbook and trade history make SX Bet a deep data source:

    * Real-time odds tracking and movement alerts
    * Historical market data and closing line analysis
    * Flow analysis on the anonymised public tape — where size went, and at what price
  </Card>
</CardGroup>

## What you'll need

<CardGroup cols={2}>
  <Card title="An account" icon="user">
    Login to sx.bet and create an account
  </Card>

  <Card title="API access" icon="key">
    Market data and the public tape need no credentials. The order book, and anything about your own
    activity — orders, bets, positions, balance — needs your API key.
  </Card>

  <Card title="USDC" icon="coin">
    Orders are denominated in USDC, in integer base units — `"1000000"` is 1.00 USDC.
  </Card>

  <Card title="Python or JS" icon="code">
    All guides and samples in this hub use Python and JavaScript. The API is REST, so any language works.
  </Card>
</CardGroup>

## Ready to start?

<Card title="Quickstart" icon="rocket" href="/developers/quickstart">
  Follow our step-by-step quickstart to fetch markets, read the orderbook, and place your first order.
</Card>


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.