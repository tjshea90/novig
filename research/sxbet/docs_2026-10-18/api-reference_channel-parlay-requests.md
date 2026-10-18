> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Parlay Market Requests

> Subscribe to real-time parlay market requests

When a bettor requests a Parlay Market, a message is sent via the `parlay_markets:global` channel. In order to offer orders on Parlay Markets, you will need to subscribe to this channel. The payload will contain the `marketHash` associated with the Parlay Market.

You can post orders to this market as you would for any other market using this `marketHash`. The payload also contains the token and size the bettor is requesting. The `legs` contain the underlying markets that make up the parlay — query each leg's `marketHash` to get current orders for that individual market.

**CHANNEL NAME**

`parlay_markets:global`

***

**`ParlayMarket` PAYLOAD FORMAT**

| Name | Type | Description |
| - | - | - |
| channelName | string | Legacy field — always `"markets:parlay"`. |
| marketHash | string | The parlay market associated with this request |
| requestorId | string | An anonymized stable ID for the bettor who requested the parlay market |
| baseToken | string | The token this request is denominated in |
| requestSize | string | The size in baseTokens that the bettor is requesting. See [unit conversions](/developers/unit-conversions). May be absent. |
| chainVersion | string | An internal classification copied from the legs. May be absent. |
| legs | ParlayMarketLeg\[] | An array of legs that make up the parlay |

**`ParlayMarketLeg` PAYLOAD FORMAT**

| Name | Type | Description |
| - | - | - |
| marketHash | string | The market for an individual leg within the parlay |
| bettingOutcomeOne | boolean | The side the bettor is betting for this leg |

<Info>The `requestSize` only indicates what the user is requesting and does not limit how much you can offer. You are allowed to offer any size.</Info>

***

<CodeGroup>
  ```javascript JavaScript theme={null}
  // To subscribe
  const sub = client.newSubscription("parlay_markets:global");

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
      sub = client.new_subscription("parlay_markets:global", handler)
      await sub.subscribe()
      await asyncio.Future()  # keep running

  asyncio.run(main())
  ```
</CodeGroup>

The above returns JSON structured like this:

```json theme={null}
{
  "channelName": "markets:parlay",
  "marketHash": "0x3f8893a68554eca5aaee57896505ea345e757b8809e8301b8ad8873a98b1c73b",
  "requestorId": "08e489e45313812cf236b43ab68c6236e3e8d63375a07d6bafdec1ecbed85c1b",
  "baseToken": "0x1BC6326EA6aF2aB8E4b6Bc83418044B1923b2956",
  "requestSize": "50000000",
  "chainVersion": "SXR",
  "legs": [
    {
      "marketHash": "0x22ed4cf508418f44e787f9c8e79f76eb31587efa20fe700b3582e09f01775944",
      "bettingOutcomeOne": false
    },
    {
      "marketHash": "0x186685cf65e1a22952ad42982e1ec75b6a457fa3bdec59fa6f891258a718ddfe",
      "bettingOutcomeOne": true
    }
  ]
}
```


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.