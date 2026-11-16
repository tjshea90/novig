> ## Documentation Index
> Fetch the complete documentation index at: https://docs.polymarket.com/llms.txt
> Use this file to discover all available pages before exploring further.

# WebSocket Feeds

> Subscribe to order books, prices and your own orders and fills.

Complete [connection setup](https://docs.polymarket.com/nex/connect.md), then append a path below to
`wss://nex-ws.preprod.defi.euw2.polymarket.sh`.

| Feed | Path | Connection Limit |
| - | - | - |
| Original market | `/ws/market` | No application connection limit |
| Original user | `/ws/user` | No application connection limit |
| subs-v2 market | `/beta/ws/market` | 50 per IP |
| subs-v2 user | `/beta/ws/user` | 20 per IP and 10 per API key; both apply |

Public clients behind the same NAT share its per-IP allowance.

## Market Feed

Send this after connecting. Replace `OUTCOME_ID` with the identifier from your
[sandbox market](https://docs.polymarket.com/nex/start-trading.md#find-a-market).

```json theme={null}
{
  "type": "market",
  "assets_ids": ["OUTCOME_ID"]
}
```

You'll receive a book snapshot followed by updates. See the
[Market Channel reference](https://docs.polymarket.com/api-reference/wss/market.md) for event schemas.

## User Feed

Send your sandbox trading credentials after connecting. Replace the credential
values below; Gamma credentials do not authenticate this feed.

```json theme={null}
{
  "type": "user",
  "auth": {
    "apiKey": "YOUR_API_KEY",
    "secret": "YOUR_API_SECRET",
    "passphrase": "YOUR_API_PASSPHRASE"
  }
}
```

Add `markets` with condition IDs to filter activity by market. See the
[User Channel reference](https://docs.polymarket.com/api-reference/wss/user.md).

## Heartbeats and Reconnects

Send the text `PING` every 10 seconds; expect `PONG`. Idle connections close, so
send activity at least once a minute.

After disconnecting, reconnect with backoff and resubscribe. Rebuild your book
from the new snapshot and reconcile account activity with authenticated reads.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.