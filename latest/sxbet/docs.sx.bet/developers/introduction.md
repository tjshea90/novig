> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# SX Bet Developer Hub

> Build trading bots, custom frontends, and analytics tools on the SX Bet peer-to-peer sports prediction market. Explore quickstarts, API references, and real-time data guides.

![SX Bet](https://mintcdn.com/sxbet-9c561d83/ylnfp2AKk1IUxDYj/public/sx_assets/sx_bet_logo.svg?fit=max\&auto=format\&n=ylnfp2AKk1IUxDYj\&q=85\&s=79cfd7fbcc454e46770d552f7f77e6eb)

# Developer Hub

Build trading bots, custom frontends, and analytics tools on the only open, peer-to-peer sports prediction market.

## Build on SX with AI

Connect your AI coding assistant to the SX Bet docs by installing the MCP server. The docs MCP allows your agent to search these docs while you build.

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

## Try the API

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

## Start building

> **[Quickstart](/developers/quickstart.md)**
>
> Sign in, deploy and fund a proxy, read the book, place your first order.

> **[API Reference](/api-reference/introduction.md)**
>
> Full endpoint reference with request and response schemas.

> **[Real-time data](/developers/realtime-overview.md)**
>
> Subscribe to books, your own orders, and the public trade feed over one WebSocket.

> **[What can you build?](/developers/what-can-you-build.md)**
>
> Trading bots, custom frontends, analytics tools, and more.

## Explore the platform

> **[Accounts](/developers/accounts.md)**
>
> Signing in with a wallet, and signing orders.

> **[The exchange model](/developers/exchange-model.md)**
>
> Everyone posts an order. The engine decides the aggressor afterwards.

> **[Markets and events](/developers/markets-and-events.md)**
>
> How markets nest, and the identifiers you query by.

> **[The order book](/developers/order-book.md)**
>
> Which side a resting level is liquidity for, and who set the price.

> **[Market making](/developers/market-making.md)**
>
> Post quotes, manage them, and cancel by event.

> **[Taking liquidity](/developers/taking-liquidity.md)**
>
> Take a resting price with an IOC or FOK order.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.