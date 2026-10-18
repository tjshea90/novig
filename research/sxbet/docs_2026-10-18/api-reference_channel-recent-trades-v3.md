> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Trade Updates

> Subscribe to real-time trade updates

Subscribe to trade updates on the exchange. A row carries `userAddress` and `username` only when that bet was de-anonymized at placement. Every other row is anonymized. Only the taker's side of a match is published, so the tape is one-sided.

**CHANNEL NAME**

`recent_trades_v3:global`

**MESSAGE PAYLOAD FORMAT**

The body is `{"trade": {…}}` — note the wrapper. The inner object is the same one [`GET /trades-v3/public`](/api-reference/get-trades-v3-public) returns.

| Name | Type | Description |
| - | - | - |
| tradeId | string | Groups the fills that made up this bet. |
| marketHash | string | The market. For a parlay or quarter-line bet, the parent market |
| isBettingOutcomeOne | boolean | The side the taker backed |
| isParlay | boolean | `true` for a parlay bet |
| totalStake | string | The stake in base units. See [unit conversions](/developers/unit-conversions). |
| totalReturn | string | Total payout if the bet wins, stake included |
| weightedAverageOdds | string | The odds in the sx.bet protocol format, blended across the bet's fills, from the bettor's perspective. To convert to implied odds divide by 10^20. See [unit conversions](/developers/unit-conversions). |
| betTime | string | ISO timestamp for when the bet was placed |
| gameTime | string | ISO timestamp for kickoff |
| outcomeLabel | string (optional) | Label for the side that was backed — outcome one when `isBettingOutcomeOne` is true, otherwise outcome two. Absent when market metadata cannot be resolved (e.g. some parlay parents) |
| teamOneName | string (optional) | Team one display name. Absent when market metadata cannot be resolved |
| teamTwoName | string (optional) | Team two display name. Absent when market metadata cannot be resolved |
| type | number (optional) | Market type id. See [Market types](/api-reference/market-types). Absent when market metadata cannot be resolved |
| leagueLabel | string (optional) | League display name. Absent when market metadata cannot be resolved |
| sportId | number (optional) | Sport id. Absent when market metadata cannot be resolved |
| eventId | string (optional) | Prefixed event id (e.g. `L12003787`). Absent when market metadata cannot be resolved |

<CodeGroup>
  ```javascript JavaScript theme={null}
  // To subscribe
  const sub = client.newSubscription("recent_trades_v3:global", { recoverable: true });

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
      sub = client.new_subscription("recent_trades_v3:global", handler)
      await sub.subscribe()
      await asyncio.Future()  # keep running

  asyncio.run(main())
  ```
</CodeGroup>

The above returns JSON structured like this:

```json theme={null}
{
  "trade": {
    "tradeId": "0xac6bb30bba6ea82d8717219a4fa49d15b58fd638fba479f294b4d402376d14d6",
    "marketHash": "0x1fec4ed9b71c2f812db900af7d12446158a0eda56041106f99551c496efd3266",
    "isBettingOutcomeOne": true,
    "isParlay": false,
    "totalStake": "1000000",
    "totalReturn": "2500000",
    "weightedAverageOdds": "40000000000000000000",
    "betTime": "2026-07-31T18:22:41.000Z",
    "gameTime": "2026-08-01T23:00:00.000Z",
    "outcomeLabel": "Raptors",
    "teamOneName": "Toronto Raptors",
    "teamTwoName": "Charlotte Hornets",
    "type": 1,
    "leagueLabel": "NBA",
    "sportId": 1,
    "eventId": "L12003787"
  }
}
```


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.