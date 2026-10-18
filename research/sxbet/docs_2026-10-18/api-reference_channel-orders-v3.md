> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Active Order Updates

> Subscribe to real-time changes in a user's orders

Subscribe to changes in your own orders. You will receive updates when orders rest, are filled, are cancelled, or expire.

**CHANNEL NAME FORMAT**

`account:orders_v3_#{address}`

For example: `account:orders_v3_#0xbcc6D643e4159A75ED1dB4e13330230B82F2AEe5`. The `#` is part of the channel name.

| Name | Type | Description |
| - | - | - |
| address | string | Your checksummed address. It must equal your realtime token's `sub` — see [subscribing to your own channels](/developers/realtime-overview#subscribing-to-your-own-channels) |

**MESSAGE PAYLOAD FORMAT**

One order per message, wrapped in `order`, even when one engine command changed several of your orders. Those arrive as separate publications. The inner object is the same one [`GET /orders-v3`](/api-reference/get-orders-v3) returns in its `data.orders` array.

| Name | Type | Description |
| - | - | - |
| id | string | A unique identifier for this order. |
| marketHash | string | The market for this order |
| userAddress | string | Your user address, checksummed — the account that signed |
| wallet | string | Your proxy address, which is what holds the funds |
| isBettingOutcomeOne | boolean | The outcome this order backs. |
| percentageOdds | string | The odds the order's owner receives in the sx.bet protocol format. To convert to implied odds divide by 10^20. To get taker implied odds: `takerOdds = 1 - percentageOdds / 10^20`. See [unit conversions](/developers/unit-conversions). |
| totalBetSize | string | Total size of this order in base units. See [unit conversions](/developers/unit-conversions). |
| remainingSize | string | The unfilled portion of the original order size. The filled amount is `totalBetSize - remainingSize`. Use the `status` field to determine whether the remaining amount is still active. |
| expiry | string | ISO 8601 timestamp after which this order is no longer valid. Note the asymmetry: you **send** `expiry` to [`POST /orders-v3`](/api-reference/post-orders-v3) as a unix epoch timestamp in seconds, and **read** it back here as an ISO 8601 string. |
| status | string | `"ACTIVE"` if resting, `"INACTIVE"` if no longer resting. |
| inactiveReason | string or `null` | `null` while active; one of ten values when terminal. See [Order Lifecycle](/developers/order-lifecycle) |
| eventId | string | The event related to this order |
| clientOrderId | string | Your own tag, present only when you set one |
| externalUserId | string | Optional partner tag, present only when you set one on [`POST /orders-v3`](/api-reference/post-orders-v3). See [External user id](/developers/external-user-id) |
| createdAt | string | ISO timestamp for when the order first rested |
| updatedAt | string | ISO timestamp for when the change was committed. |

***

<CodeGroup>
  ```javascript JavaScript theme={null}
  // To subscribe
  const address = "0xbcc6D643e4159A75ED1dB4e13330230B82F2AEe5"; // checksummed
  const sub = client.newSubscription(`account:orders_v3_#${address}`, { recoverable: true });

  sub.on("publication", (ctx) => {
    const data = ctx.data;
    // message handler logic
  });

  sub.subscribe();
  ```

  ```python Python theme={null}
  import asyncio
  from centrifuge import Client, PublicationContext, SubscriptionEventHandler

  async def on_publication(ctx: PublicationContext) -> None:
      print(ctx.data)

  async def main():
      address = "0xbcc6D643e4159A75ED1dB4e13330230B82F2AEe5"  # checksummed
      client = Client(
          "wss://realtime.sx.bet/connection/websocket",
          token="YOUR_TOKEN",  # from /user/realtime-token-v3/api-key
      )
      await client.connect()
      handler = SubscriptionEventHandler(on_publication=on_publication)
      sub = client.new_subscription(f"account:orders_v3_#{address}", handler)
      await sub.subscribe()
      await asyncio.Future()  # keep running

  asyncio.run(main())
  ```
</CodeGroup>

The above returns JSON structured like this:

```json theme={null}
{
  "order": {
    "id": "0xbc9c298f6584e484dc23a8b4ac95755faabbb5f966b37b869b3a74acf0790fe6",
    "marketHash": "0xbf06c6379c922d8118612d1d7493b20f6df6929437fbf6022904327f9516a2eb",
    "userAddress": "0xbcc6D643e4159A75ED1dB4e13330230B82F2AEe5",
    "wallet": "0x4361123dbdc1D812fdf7D27045aF358C9C8AA70A",
    "isBettingOutcomeOne": true,
    "percentageOdds": "40000000000000000000",
    "totalBetSize": "1000000",
    "remainingSize": "1000000",
    "expiry": "2026-08-03T14:58:31.000Z",
    "status": "ACTIVE",
    "inactiveReason": null,
    "eventId": "L12003787",
    "createdAt": "2026-08-03T13:58:31.947Z",
    "updatedAt": "2026-08-03T13:58:32.123Z"
  }
}
```


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.