> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Fill Updates

> Subscribe to real-time updates for your own fills

Subscribe to your own matches at fill grain — one row per match between your order and a counterparty's. A bet filled by three makers produces three messages here and one on [`account:trades_v3_#{address}`](/api-reference/channel-trades-v3).

<Note>
  **The first message for a new fill is `MATCHED`, which comes before the on-chain lock**. A
  second message follows once the funds are escrowed (`LOCKED`) or the
  lock fails (`FAILED`). Act on `MATCHED` for speed, or wait for `LOCKED` if a revised bet would cost
  you — see [which status to act on](/developers/bet-lifecycle#which-status-to-act-on).
</Note>

**CHANNEL NAME FORMAT**

`account:fills_v3_#{address}`

For example: `account:fills_v3_#0xbcc6D643e4159A75ED1dB4e13330230B82F2AEe5`. The `#` is part of the channel name.

| Name | Type | Description |
| - | - | - |
| address | string | Your checksummed address. It must equal your realtime token's `sub` — see [subscribing to your own channels](/developers/realtime-overview#subscribing-to-your-own-channels) |

**MESSAGE PAYLOAD FORMAT**

One fill per message, wrapped in `fill`. Quarter-line legs each get their own message, distinguished by `quarterlineMarketHash`.

| Name | Type | Description |
| - | - | - |
| id | string | The row's key, `{matchId}:{orderId}:{marketHash}`. |
| matchId | string | The engine's match. Unique per fill |
| orderId | string | The order you signed that produced this fill, not the counterparty's |
| tradeId | string | The bet all of this fill's siblings belong to |
| marketHash | string | The market. For a parlay or quarter-line bet, the parent market |
| quarterlineMarketHash | string or `null` | The specific half-line leg, when the bet was on a quarter line |
| isParlay | boolean | `true` when `marketHash` is a parlay parent |
| userAddress | string | Your user address, checksummed |
| wallet | string | Your proxy address |
| fillAmount | string | Your stake in this fill, in base units. See [unit conversions](/developers/unit-conversions). |
| fillOdds | string | The odds this fill got, in the sx.bet protocol format. To convert to implied odds divide by 10^20. See [unit conversions](/developers/unit-conversions). |
| returnAmount | string | Gross return for this fill if it wins, stake included |
| netReturnAmount | string | `returnAmount` less the projected settlement payout fee. |
| isBettingOutcomeOne | boolean | The side you backed |
| status | string | `"MATCHED"`, `"LOCKED"`, `"FAILED"` or `"SETTLED"`. See [bet lifecycle](/developers/bet-lifecycle) |
| isMaker | boolean | Which side of the match this fill was on. `true` on the maker side — your order was the resting one; `false` on the taker side |
| txHash | string or `null` | The on-chain lock transaction. `null` until it lands |
| ceRefundAmount | string | Capital-efficiency refund on this fill. See [capital efficiency](/developers/capital-efficiency) |
| ceRefundFeeAmount | string | The fee taken on that refund |
| usedBetCredits | boolean | Whether bet credits paid for it |
| clientOrderId | string | Your own tag from the order that produced this fill, present only when set — the maker's tag on maker rows, the taker's on taker rows. See [client order id](/developers/client-order-id) |
| externalUserId | string | Optional partner tag from the order that produced this fill, present only when set — the maker's tag on maker rows, the taker's on taker rows. See [External user id](/developers/external-user-id) |
| createdAt | string | ISO timestamp for when the row was created |
| updatedAt | string | ISO timestamp for the last modification of this row |
| settlement | object or `null` | `null` until the fill is `"SETTLED"`. A different shape from the bet-grain block — it carries `settleTxHash`, has no `settleDate`, and its `settleFeeAmount` is nullable. Do not share a parser between the two |

***

<CodeGroup>
  ```javascript JavaScript theme={null}
  // To subscribe
  const address = "0xbcc6D643e4159A75ED1dB4e13330230B82F2AEe5"; // checksummed
  const sub = client.newSubscription(`account:fills_v3_#${address}`, { recoverable: true });

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
      sub = client.new_subscription(f"account:fills_v3_#{address}", handler)
      await sub.subscribe()
      await asyncio.Future()  # keep running

  asyncio.run(main())
  ```
</CodeGroup>

The above returns JSON structured like this:

```json theme={null}
{
  "fill": {
    "id": "0xec8eaaa2…5793:0x02d22c2a…c55ea:0x1fec4ed9…3266",
    "matchId": "0xec8eaaa21ef52bf5f2a3fee5f139258a63f65cf2218dfee40362f2fc90e55793",
    "orderId": "0x02d22c2ac3f2aa27ca96e156aa2fa4affb15064a169e02b55881bdd6867c55ea",
    "tradeId": "0xac6bb30bba6ea82d8717219a4fa49d15b58fd638fba479f294b4d402376d14d6",
    "marketHash": "0x1fec4ed9b71c2f812db900af7d12446158a0eda56041106f99551c496efd3266",
    "quarterlineMarketHash": null,
    "isParlay": false,
    "userAddress": "0xbcc6D643e4159A75ED1dB4e13330230B82F2AEe5",
    "wallet": "0x4361123dbdc1D812fdf7D27045aF358C9C8AA70A",
    "fillAmount": "333334",
    "fillOdds": "40000000000000000000",
    "returnAmount": "833335",
    "netReturnAmount": "829335",
    "isBettingOutcomeOne": false,
    "isMaker": false,
    "status": "LOCKED",
    "txHash": "0x64307e2f35d0a81b0f44e9043e99cf01def3b2f079fb68cf2c5a942b88d95e9a",
    "ceRefundAmount": "0",
    "ceRefundFeeAmount": "0",
    "usedBetCredits": false,
    "createdAt": "2026-07-31T18:47:09.577Z",
    "updatedAt": "2026-07-31T18:47:12.837Z",
    "settlement": null
  }
}
```


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.