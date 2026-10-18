> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Best Odds

> Subscribe to real-time best odds updates

Subscribe to best odds changes across all order books. A publication goes out whenever a market's top of book changes on either side — a new best price, a size change at the best price, or the best level disappearing.

This channel has no history, so a subscriber receives nothing until the next change anywhere in the system. Seed from [`GET /orders-v3/odds/best`](/api-reference/get-best-odds-v3) on every connect and treat this channel as updates on top of that snapshot.

**CHANNEL NAME**

`best_odds_v3:global`

**MESSAGE PAYLOAD FORMAT**

One market per publication, as a single object.

| Name | Type | Description |
| - | - | - |
| marketHash | string | The market whose top of book changed |
| outcomeOne | object or `null` | Best maker level on outcome one. `null` when that side is empty |
| outcomeTwo | object or `null` | Best maker level on outcome two. `null` when that side is empty |
| outcomeOne.percentageOdds | string | The odds the `maker` receives in the sx.bet protocol format. To convert to implied odds divide by 10^20. To get taker implied odds: `takerOdds = 1 - percentageOdds / 10^20`. See [unit conversions](/developers/unit-conversions). |
| outcomeOne.size | string | Aggregate maker size at that level, in base units. See [unit conversions](/developers/unit-conversions). |

This is exactly one element of the `bestOdds` array that [`GET /orders-v3/odds/best`](/api-reference/get-best-odds-v3) returns.

***

<CodeGroup>
  ```javascript JavaScript theme={null}
  // To subscribe
  const sub = client.newSubscription("best_odds_v3:global");

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
      client = Client(
          "wss://realtime.sx.bet/connection/websocket",
          token="YOUR_TOKEN",  # from /user/realtime-token-v3/api-key
      )
      await client.connect()
      handler = SubscriptionEventHandler(on_publication=on_publication)
      sub = client.new_subscription("best_odds_v3:global", handler)
      await sub.subscribe()
      await asyncio.Future()  # keep running

  asyncio.run(main())
  ```
</CodeGroup>

The above returns JSON structured like this:

```json theme={null}
{
  "marketHash": "0xbf06c6379c922d8118612d1d7493b20f6df6929437fbf6022904327f9516a2eb",
  "outcomeOne": { "percentageOdds": "40000000000000000000", "size": "1000000" },
  "outcomeTwo": null
}
```


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.