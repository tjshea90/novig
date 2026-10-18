> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# SX Bet API Reference

> REST endpoints and real-time WebSocket data for markets, orders, trades, and positions on the SX Bet exchange.

<div id="hero-section" className="flex flex-col items-center justify-center text-center px-8 pt-8 pb-0">
  <img src="https://mintcdn.com/sxbet-9c561d83/ylnfp2AKk1IUxDYj/public/sx_assets/sx_bet_logo.svg?fit=max&auto=format&n=ylnfp2AKk1IUxDYj&q=85&s=79cfd7fbcc454e46770d552f7f77e6eb" alt="SX Bet" className="h-12 mb-4" width="616" height="191" data-path="public/sx_assets/sx_bet_logo.svg" />

  <h1 className="text-5xl font-bold tracking-tight text-gray-900 dark:text-zinc-50 max-w-3xl">
    API Reference
  </h1>

  <p className="mt-3 text-xl text-gray-500 dark:text-gray-400 max-w-xl leading-snug">
    Fetch markets, read the book, post orders, and subscribe to real-time channels.
  </p>
</div>

<div className="px-8 pb-16 max-w-5xl mx-auto">
  ## Explore the API

  <CardGroup cols={2}>
    <Card title="Exchange metadata" icon="gear" href="/api-reference/get-metadata-obv3">
      The one call every integration starts with: chain id, escrow, decimals, limits, odds ladder.
    </Card>

    <Card title="Create orders" icon="paper-plane" href="/api-reference/post-orders-v3">
      Submit a signed order. `timeInForce` decides whether it rests or executes.
    </Card>

    <Card title="Channel and limit reference" icon="plug" href="/developers/realtime-reference">
      Every channel, which namespace it lands in, and what it recovers.
    </Card>

    <Card title="Order book snapshot" icon="layer-group" href="/api-reference/get-market-orderbook-snapshot">
      The whole book for one market, and the REST seed a realtime subscription starts from.
    </Card>
  </CardGroup>
</div>


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.