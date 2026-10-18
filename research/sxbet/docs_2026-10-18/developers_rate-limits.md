> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Rate limits

> Request limits for the SX Bet REST API.

## Authenticated limits

Each row is the per-API-key budget for that route.

### Orders

| Endpoint | Max requests | Window |
| - | - | - |
| `GET` `/orders-v3` | 1,200 | 60s |
| `GET` `/orders-v3/{orderId}` | 1,200 | 60s |
| `GET` `/orders-v3/client/{clientOrderId}` | 1,200 | 60s |
| `POST` `/orders-v3` | 5,500 | 60s |
| `DELETE` `/orders-v3` | 10,000 | 60s |
| `DELETE` `/orders-v3/market` | 3,000 | 60s |
| `DELETE` `/orders-v3/event` | 1,000 | 60s |
| `DELETE` `/orders-v3/all` | 60 | 60s |
| `POST` `/heartbeat/v3` | 600 | 60s |

### Order book

| Endpoint | Max requests | Window |
| - | - | - |
| `GET` `/orderbook-v3/snapshot/market` | 27,500 | 300s |

### Positions & trades

| Endpoint | Max requests | Window |
| - | - | - |
| `GET` `/fills-v3` | 300 | 60s |
| `GET` `/positions-v3` | 300 | 60s |
| `GET` `/trades-v3` | 300 | 60s |
| `GET` `/trades-v3/{tradeId}` | 300 | 60s |
| `GET` `/positions-v3/settled/pnl` | 120 | 60s |

### Account

| Endpoint | Max requests | Window |
| - | - | - |
| `GET` `/user/balance-v3` | 300 | 60s |
| `GET` `/user/fees-v3` | 120 | 60s |
| `GET` `/user/realtime-token-v3/api-key` | 30 | 60s |

### Proxy wallet

| Endpoint | Max requests | Window |
| - | - | - |
| `GET` `/user/proxy` | 120 | 60s |
| `GET` `/user/pending-deploy-proxy` | 120 | 60s |
| `GET` `/user/transfer-to-proxy/pending` | 120 | 60s |
| `GET` `/user/transfer-to-proxy/status` | 120 | 60s |
| `POST` `/user/deploy-proxy` | 20 | 60s |
| `POST` `/user/transfer-to-proxy` | 20 | 60s |

### Multisig

| Endpoint | Max requests | Window |
| - | - | - |
| `/user/multisig/*` | 60 | 60s |

## Public limits

Public read endpoints are not metered against your API-key budget. They are limited per client IP at
the edge.

### Order book

| Endpoint | Max requests | Window |
| - | - | - |
| `GET` `/orders-v3/odds/best` | 5,500 | 60s |

### Summary & metadata

| Endpoint | Max requests | Window |
| - | - | - |
| `GET` `/metadata/obv3` | 500 | 60s |
| `GET` `/summary-v3/*` | 500 | 60s |

### Trades

| Endpoint | Max requests | Window |
| - | - | - |
| `GET` `/trades-v3/public` | 200 | 60s |
| `GET` `/trades-v3/public/count` | 200 | 60s |

### Markets & sports data

These endpoint families share a single combined budget of **500 requests** per **60s** per client IP.
A burst on any one of them draws down the budget for all of them.

| Endpoint |
| - |
| `GET` `/markets/*` |
| `GET` `/fixture/*` |
| `GET` `/leagues` and `/leagues/*` |
| `GET` `/sports` |
| `GET` `/teams` |
| `GET` `/live-scores` |
| `GET` `/search` |

## Response headers

Every authenticated response carries the state of your budget for that route, so read the limit in
force from the headers rather than hardcoding a value.

| Header | On | Meaning |
| - | - | - |
| `X-RateLimit-Limit` | every metered response | Max requests allowed in the window for this route. |
| `X-RateLimit-Remaining` | every metered response | Requests left in the current window. |
| `Retry-After` | `429` only | Seconds to wait before retrying. |

## Global limit

Beyond the per-key budgets above, all unauthenticated traffic shares a single global bucket at the
edge of **35,000 requests** per **10 min** window.

## When limits are exceeded

A request over the limit returns HTTP `429 Too Many Requests`. On a `429`, `X-RateLimit-Remaining`
is `0` and the `Retry-After` header gives the seconds to wait before retrying — back off until then
rather than retrying immediately.

## Use realtime instead of polling

Subscriptions do not use your request budget.

| Instead of polling | Subscribe to |
| - | - |
| `GET /orderbook-v3/snapshot/market` in a loop | `orderbook_v3:{marketHash}` |
| the snapshot across a whole event | `orderbook_v3_event:{eventId}` |
| `GET /orders-v3/odds/best` in a loop | `best_odds_v3:global` |
| `GET /orders-v3` to track your own orders | `account:orders_v3_#{address}` |
| `GET /trades-v3` to see your bets land | `account:trades_v3_#{address}` |
| `GET /fills-v3` for fill detail | `account:fills_v3_#{address}` |

## Related

<CardGroup cols={2}>
  <Card title="Real-time data" icon="bolt" href="/developers/realtime-overview">
    Setting up a channel, and what it sends.
  </Card>

  <Card title="API key permissions" icon="key" href="/developers/api-key-permissions">
    Scopes you can grant a key, and the routes each one allows.
  </Card>
</CardGroup>


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.