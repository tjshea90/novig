> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Your Trade Updates

> Subscribe to real-time updates for your own trades

Subscribe to your own bets at bet grain — one row per bet you placed, whatever number of makers filled it. For one message per match instead, use [`account:fills_v3_#{address}`](/api-reference/channel-fills-v3). You will receive updates when a bet is matched, locks, fails, or settles.

<Note>
  **The first message for a new bet is `MATCHED`, which comes before the on-chain lock.** A second
  message follows once the funds are escrowed (`LOCKED`) or the escrow fails (`FAILED`). Act on
  `MATCHED` for speed, or wait for `LOCKED` in certain situations — see
  [which status to act on](/developers/bet-lifecycle#which-status-to-act-on).
</Note>

**CHANNEL NAME FORMAT**

`account:trades_v3_#{address}`

For example: `account:trades_v3_#0xbcc6D643e4159A75ED1dB4e13330230B82F2AEe5`. The `#` is part of the channel name.

| Name | Type | Description |
| - | - | - |
| address | string | Your checksummed address. It must equal your realtime token's `sub` — see [subscribing to your own channels](/developers/realtime-overview#subscribing-to-your-own-channels) |

**MESSAGE PAYLOAD FORMAT**

One bet per message, wrapped in `trade`. Every message carries the whole current row, not a delta.

| Name | Type | Description |
| - | - | - |
| tradeId | string | The bet. Every fill for this bet carries the same value |
| userAddress | string | Your user address, checksummed |
| wallet | string | Your proxy address — where the funds live |
| marketHash | string | The market. For a parlay or quarter-line bet, the parent market |
| isBettingOutcomeOne | boolean | The side you backed |
| isMaker | boolean | Which side of the book you were on. `true` when you were the maker — your order rested and was filled; `false` when you were the taker |
| isParlay | boolean | `true` when `marketHash` is a parlay parent |
| totalStake | string | The amount risked, in base units. See [unit conversions](/developers/unit-conversions). |
| totalReturn | string | Gross payout if it wins, stake included |
| netTotalReturn | string | `totalReturn` less the projected settlement payout fee — what you would actually be paid. |
| weightedAverageOdds | string | The odds in the sx.bet protocol format, blended across the bet's fills. To convert to implied odds divide by 10^20. See [unit conversions](/developers/unit-conversions). |
| ceRefundAmount | string | Capital-efficiency refund on this bet. See [capital efficiency](/developers/capital-efficiency) |
| ceRefundFeeAmount | string | The fee taken on that refund |
| usedBetCredits | boolean | Whether bet credits paid for it |
| fillCount | number | How many fills make up this bet. A number, while every amount is a string |
| status | string | `"MATCHED"`, `"LOCKED"`, `"FAILED"` or `"SETTLED"`. See [bet lifecycle](/developers/bet-lifecycle) |
| ownOrderIds | string\[] | Order ids of your own orders behind this bet — the same values as `id` on order rows and `orderId` from [`POST /orders-v3`](/api-reference/post-orders-v3). These are not `clientOrderId`s. |
| externalUserId | string | Optional partner tag from the order that produced this bet, present only when set. See [External user id](/developers/external-user-id) |
| betTime | string | ISO timestamp for when the bet was placed |
| gameTime | string | ISO timestamp for kickoff |
| createdAt | string | ISO timestamp for when the row was created |
| updatedAt | string | ISO timestamp for the last modification of this row |
| settlement | object or `null` | `null` on every message until `status` is `"SETTLED"` |

Where a `settlement` object looks like:

| Name | Type | Description |
| - | - | - |
| outcome | number | The graded outcome |
| settleReturnAmount | string | What was returned on settlement |
| settleFeeAmount | string | The fee taken on settlement |
| settleCeRefundAmount | string | Capital-efficiency refund applied at settlement |
| settleDate | string | ISO timestamp for when the bet settled |

***

<CodeGroup>
  ```javascript JavaScript theme={null}
  // To subscribe
  const address = "0xbcc6D643e4159A75ED1dB4e13330230B82F2AEe5"; // checksummed
  const sub = client.newSubscription(`account:trades_v3_#${address}`, { recoverable: true });

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
      sub = client.new_subscription(f"account:trades_v3_#{address}", handler)
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
    "userAddress": "0xbcc6D643e4159A75ED1dB4e13330230B82F2AEe5",
    "wallet": "0x4361123dbdc1D812fdf7D27045aF358C9C8AA70A",
    "marketHash": "0x1fec4ed9b71c2f812db900af7d12446158a0eda56041106f99551c496efd3266",
    "isBettingOutcomeOne": false,
    "isMaker": false,
    "isParlay": false,
    "totalStake": "1000000",
    "totalReturn": "2500000",
    "netTotalReturn": "2488000",
    "weightedAverageOdds": "40000000000000000000",
    "ceRefundAmount": "0",
    "ceRefundFeeAmount": "0",
    "usedBetCredits": false,
    "fillCount": 2,
    "ownOrderIds": ["0x02d22c2ac3f2aa27ca96e156aa2fa4affb15064a169e02b55881bdd6867c55ea"],
    "status": "LOCKED",
    "betTime": "2026-07-31T18:47:09.577Z",
    "gameTime": "2040-01-01T00:00:00.000Z",
    "createdAt": "2026-07-31T18:47:09.575Z",
    "updatedAt": "2026-07-31T18:47:12.837Z",
    "settlement": null
  }
}
```


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.