> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Channel and limit reference

> Channels, per-namespace history, and connection limits for the SX Bet WebSocket API.

| | |
| - | - |
| Endpoint | `wss://realtime.sx.bet/connection/websocket` |
| Transport | **WebSocket only.** There is no public HTTP API for realtime |
| Server | Centrifugo v6.9.1 |

## The channels

**Public** — subscribe with any valid connection:

| Channel | Carries |
| - | - |
| [`orderbook_v3:{marketHash}`](/api-reference/channel-orderbook-v3) | The full aggregated book for one market |
| [`orderbook_v3_event:{eventId}`](/api-reference/channel-orderbook-v3-event) | The same bodies, for every market in an event |
| [`best_odds_v3:global`](/api-reference/channel-best-odds-v3) | Top-of-book changes across all markets |
| [`recent_trades_v3:global`](/api-reference/channel-recent-trades-v3) | The anonymized public tape |
| [`markets:global`](/api-reference/channel-markets) | Market create and update |
| [`main_line:global`](/api-reference/channel-line-changes) | Main-line changes |
| [`fixtures:global`](/api-reference/channel-fixtures) · `fixtures:live_scores` | Fixture metadata and live scores |
| [`parlay_markets:global`](/api-reference/channel-parlay-requests) | Parlay quote requests |

**Account** — scoped to you, and only reachable with a matching token:

| Channel | Carries |
| - | - |
| [`account:orders_v3_#{address}`](/api-reference/channel-orders-v3) | Your order state changes |
| [`account:trades_v3_#{address}`](/api-reference/channel-trades-v3) | Your bets |
| [`account:fills_v3_#{address}`](/api-reference/channel-fills-v3) | Your individual fills |

## History and recovery, per namespace

The namespace is the part of the channel name before the colon, and it decides everything about what
a reconnect can recover.

| Namespace | History | Retention window | Client `history()` |
| - | - | - | - |
| `orderbook_v3` | **1** | **6 h** | **No** |
| `orderbook_v3_event` | 100 | 5 min | Yes |
| `account` | 100 | 5 min | Yes |
| `recent_trades_v3` | 100 | 5 min | Yes |
| `markets` | 1,000 | 5 min | Yes |
| `main_line` | 500 | 5 min | Yes |
| `fixtures` | 100 | 5 min | Yes |
| `best_odds_v3` · `parlay_markets` | **None** | — | — |

<Note>
  History is bounded by **both** the message limit and the retention window, whichever is reached
  first. A publication is only recoverable while it is still within the retention window **and** has
  not been pushed out by newer messages once the limit is hit.
</Note>

## Limits

| Parameter | Value |
| - | - |
| Max channel subscriptions per connection | 512 |
| Max history items returned per request | 1,000 |
| Max messages recoverable per reconnect | 1,000 |
| Ping interval | 30 seconds |
| Pong timeout | 8 seconds |
| Outbound queue limit | 1 MB |


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.