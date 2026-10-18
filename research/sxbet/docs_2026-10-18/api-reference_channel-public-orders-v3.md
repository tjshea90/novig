> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# De-anonymized Order Updates

> Subscribe to real-time changes in a market's de-anonymized orders

Subscribe to de-anonymized resting-order changes on one market.
**Only orders whose maker placed them while de-anonymized are published here**. Seed from
[`GET /orders-v3/public`](/api-reference/get-orders-v3-public).

<Info>
  This is not a stream of the actual order book and it only shows a very small fraction of the total liquidity. Anonymized orders are omitted. To
  recreate the actual book, subscribe to [order book updates](/api-reference/channel-orderbook-v3) and seed
  from the [snapshot](/api-reference/get-market-orderbook-snapshot).
</Info>

**CHANNEL NAME FORMAT**

`orders_v3:market_{marketHash}`

| Name | Type | Description |
| - | - | - |
| marketHash | string | The market to follow. |

**MESSAGE PAYLOAD FORMAT**

One order per message, wrapped in `order`. Use the `id` of an order and the `updatedAt` field to replace an order wholesale. Published messages are full orders, not deltas.

| Name | Type | Description |
| - | - | - |
| id | string | A unique identifier for this order |
| marketHash | string | The market this order rests on |
| userAddress | string | The maker's checksummed address. Every order on this channel carries one |
| isBettingOutcomeOne | boolean | The outcome this order backs |
| percentageOdds | string | The odds the maker receives in the sx.bet protocol format. To convert to implied odds divide by 10^20. To get taker implied odds: `takerOdds = 1 - percentageOdds / 10^20`. See [unit conversions](/developers/unit-conversions). |
| totalBetSize | string | Total size of this order in base units. See [unit conversions](/developers/unit-conversions). |
| remainingSize | string | The unfilled portion still available to take. The filled amount is `totalBetSize - remainingSize` |
| expiry | string | ISO 8601 timestamp after which this order is no longer valid |
| status | string | `"ACTIVE"` if resting, `"INACTIVE"` if no longer resting. Remove the order from your book when it turns `INACTIVE` |
| eventId | string | Prefixed event id (e.g. `L12003787`) for the order's market |
| createdAt | string | ISO timestamp for when the order first rested |
| updatedAt | string | ISO timestamp for when the change was committed |

<CodeGroup>
  ```javascript JavaScript theme={null}
  // To subscribe
  const marketHash = "0x1fec4ed9b71c2f812db900af7d12446158a0eda56041106f99551c496efd3266";
  const sub = client.newSubscription(`orders_v3:market_${marketHash}`, { recoverable: true });

  sub.on("publication", (ctx) => {
    const { order } = ctx.data;
    if (order.status === "ACTIVE") book.set(order.id, order);
    else book.delete(order.id);
  });

  sub.subscribe();
  ```

  ```python Python theme={null}
  import asyncio
  from centrifuge import Client, PublicationContext, SubscriptionEventHandler

  MARKET_HASH = "0x1fec4ed9b71c2f812db900af7d12446158a0eda56041106f99551c496efd3266"

  async def on_publication(ctx: PublicationContext) -> None:
      print(ctx.data["order"])

  async def main():
      client = Client(
          "wss://realtime.sx.bet/connection/websocket",
          token="YOUR_TOKEN",  # from /user/realtime-token-v3/api-key
      )
      await client.connect()
      handler = SubscriptionEventHandler(on_publication=on_publication)
      sub = client.new_subscription(f"orders_v3:market_{MARKET_HASH}", handler)
      await sub.subscribe()
      await asyncio.Future()  # keep running

  asyncio.run(main())
  ```
</CodeGroup>

The above returns JSON structured like this:

```json theme={null}
{
  "order": {
    "id": "0x5c1bb3b5a7d2d8a2d7dcd2ba00b2aa96b0f0b6e6b0a2f7d2cbe1a2f1f0c1d2e3",
    "marketHash": "0x1fec4ed9b71c2f812db900af7d12446158a0eda56041106f99551c496efd3266",
    "userAddress": "0x685D1D76A2771FEd07297c83e723eAc320a30d7a",
    "isBettingOutcomeOne": true,
    "percentageOdds": "60000000000000000000",
    "totalBetSize": "5000000",
    "remainingSize": "4000000",
    "expiry": "2026-09-19T19:50:20.000Z",
    "status": "ACTIVE",
    "eventId": "L12003787",
    "createdAt": "2026-09-18T19:50:23.840Z",
    "updatedAt": "2026-09-18T19:50:23.850Z"
  }
}
```


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.