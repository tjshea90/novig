> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# SX Bet Developer Hub

> Build trading bots, custom frontends, and analytics tools on the SX Bet peer-to-peer sports prediction market. Explore quickstarts, API references, and real-time data guides.

<div id="hero-section" className="flex flex-col items-center justify-center text-center px-8 pt-8 pb-0">
  <img src="https://mintcdn.com/sxbet-9c561d83/ylnfp2AKk1IUxDYj/public/sx_assets/sx_bet_logo.svg?fit=max&auto=format&n=ylnfp2AKk1IUxDYj&q=85&s=79cfd7fbcc454e46770d552f7f77e6eb" alt="SX Bet" className="h-12 mb-4" width="616" height="191" data-path="public/sx_assets/sx_bet_logo.svg" />

  <h1 className="text-5xl font-bold tracking-tight text-gray-900 dark:text-zinc-50 max-w-3xl">
    Developer Hub
  </h1>

  <p className="mt-3 text-xl text-gray-500 dark:text-gray-400 max-w-xl leading-snug">
    Build trading bots, custom frontends, and analytics tools on the only open, peer-to-peer sports prediction market.
  </p>
</div>

<div className="px-8 pb-16 max-w-5xl mx-auto">
  ## Build on SX with AI

  Connect your AI coding assistant to the SX Bet docs by installing the MCP server. The docs MCP allows your agent to search these docs while you build.

  <CodeGroup>
    ```bash Claude Code theme={null}
    claude mcp add --transport http sx-bet https://docs.sx.bet/mcp
    ```

    ```json Cursor theme={null}
    // .cursor/mcp.json
    {
      "mcpServers": {
        "sx-bet": { "url": "https://docs.sx.bet/mcp" }
      }
    }
    ```

    ```json VS Code theme={null}
    // .vscode/mcp.json
    {
      "servers": {
        "sx-bet": { "type": "http", "url": "https://docs.sx.bet/mcp" }
      }
    }
    ```

    ```json Windsurf theme={null}
    // Windsurf Settings → MCP Configuration
    {
      "mcpServers": {
        "sx-bet": { "url": "https://docs.sx.bet/mcp" }
      }
    }
    ```
  </CodeGroup>

  ## Try the API

  <CodeGroup>
    ```javascript JavaScript theme={null}
    const res = await fetch("https://api.sx.bet/markets/active?sportIds=5&pageSize=5");
    const { data } = await res.json();
    console.log(data.markets);   // array of active markets; data.nextKey pages on
    ```

    ```python Python theme={null}
    import requests

    res = requests.get("https://api.sx.bet/markets/active", params={"sportIds": 5, "pageSize": 5})
    markets = res.json()["data"]["markets"]   # data["nextKey"] pages on
    print(markets)
    ```
  </CodeGroup>

  ## Start building

  <CardGroup cols={2}>
    <Card title="Quickstart" img="https://mintcdn.com/sxbet-9c561d83/ylnfp2AKk1IUxDYj/public/sx_assets/quickstart_card.svg?fit=max&auto=format&n=ylnfp2AKk1IUxDYj&q=85&s=6512007069382fe3578ad92d6dfe2798" href="/developers/quickstart" width="1920" height="1080" data-path="public/sx_assets/quickstart_card.svg">
      Sign in, deploy and fund a proxy, read the book, place your first order.
    </Card>

    <Card title="API Reference" img="https://mintcdn.com/sxbet-9c561d83/ylnfp2AKk1IUxDYj/public/sx_assets/api_ref_card.svg?fit=max&auto=format&n=ylnfp2AKk1IUxDYj&q=85&s=2f270bb087ac55da036fcf087eedad94" href="/api-reference/introduction" width="1920" height="1080" data-path="public/sx_assets/api_ref_card.svg">
      Full endpoint reference with request and response schemas.
    </Card>

    <Card title="Real-time data" img="https://mintcdn.com/sxbet-9c561d83/ylnfp2AKk1IUxDYj/public/sx_assets/real-time_card.svg?fit=max&auto=format&n=ylnfp2AKk1IUxDYj&q=85&s=2ecb0bed95322e6bfc8702051abc04c2" href="/developers/realtime-overview" width="1920" height="1080" data-path="public/sx_assets/real-time_card.svg">
      Subscribe to books, your own orders, and the public trade feed over one WebSocket.
    </Card>

    <Card title="What can you build?" img="https://mintcdn.com/sxbet-9c561d83/ylnfp2AKk1IUxDYj/public/sx_assets/build_card.svg?fit=max&auto=format&n=ylnfp2AKk1IUxDYj&q=85&s=a7d5261e27dbede382ca07ef330f3c3a" href="/developers/what-can-you-build" width="1920" height="1080" data-path="public/sx_assets/build_card.svg">
      Trading bots, custom frontends, analytics tools, and more.
    </Card>
  </CardGroup>

  ## Explore the platform

  <CardGroup cols={3}>
    <Card title="Accounts" icon="key" href="/developers/accounts">
      Signing in with a wallet, and signing orders.
    </Card>

    <Card title="The exchange model" icon="arrows-left-right" href="/developers/exchange-model">
      Everyone posts an order. The engine decides the aggressor afterwards.
    </Card>

    <Card title="Markets and events" icon="calendar" href="/developers/markets-and-events">
      How markets nest, and the identifiers you query by.
    </Card>

    <Card title="The order book" icon="book-open" href="/developers/order-book">
      Which side a resting level is liquidity for, and who set the price.
    </Card>

    <Card title="Market making" icon="chart-line" href="/developers/market-making">
      Post quotes, manage them, and cancel by event.
    </Card>

    <Card title="Taking liquidity" icon="bolt" href="/developers/taking-liquidity">
      Take a resting price with an IOC or FOK order.
    </Card>
  </CardGroup>
</div>


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.