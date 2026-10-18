> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Parlays

> How SX Bet's request-for-quote parlay system works.

A parlay is a multi-leg bet. There is no standing parlay orderbook. A bettor picks their legs, and the
request is broadcast. Market makers price it inside a **3-second window**. The bettor then has a minute
to take one of the resulting quotes.

## Parlay markets are real markets

Once it exists, a parlay market is an ordinary market. It has its own `marketHash` and behaves like
every other market on the exchange. You post an ordinary signed order to it, you choose outcome one or
outcome two, and you cancel it the same way.

The difference is that a parlay market has **legs** — an array of underlying single markets and the
outcomes the bettor selected for each.

## The flow

```
Bettor selects multiple legs
       ↓
Parlay request broadcast on `parlay_markets:global`
       ↓
Market makers have 3 seconds to post orders
       ↓
All submitted orders shown to the bettor at once
       ↓
Bettor has 1 minute to take one before the book closes
       ↓
The fill executes like any other bet
```

## The request

Makers receive this on the
[`parlay_markets:global`](/api-reference/channel-parlay-requests) channel:

```json theme={null}
{
  "marketHash": "0x38cceead7bda65c18574a34994ebd8af154725d08aa735dcbf26247a7dcc67bd",
  "requestSize": "100000000",
  "legs": [
    {
      "marketHash": "0x0d64c52e8781acdada86920a2d1e5acd6f29dcfe285cf9cae367b671dff05f7d",
      "bettingOutcomeOne": true
    },
    {
      "marketHash": "0xe609a49d083cd41214a0db276c1ba323c4a947eefd2e4260386fec7b5d258188",
      "bettingOutcomeOne": false
    }
  ]
}
```

The top-level `marketHash` is the **parlay parent** — the market you post orders against. Each entry
in `legs` names an underlying market and which side of it the parlay needs.

The full field list (including the `requestorId` pseudonym) and looking up each leg via
`GET /markets/find` are on [Market making parlays](/developers/market-making-parlays).

## Betting a parlay

You post an ordinary order on the parlay parent hash — nothing about signing is parlay-specific. See
[Market making parlays](/developers/market-making-parlays) for the field-by-field walkthrough and
working code.

**A parlay taker uses `FOK` orders only**

## How a parlay settles early

**One VOID leg voids the whole parlay.** If any leg is reported void, the parlay is void and everyone
gets their stake back.

**A non-VOID leg going against the parlay decides it early.** When one leg is reported as a loss, the
parlay cannot win.

## Parlay early refunds

Parlay bets are eligible for early [capital-efficiency](/developers/capital-efficiency) refunds — you
can get the stake on a decided position back before the whole parlay has finished settling.

A parlay can end one of a few ways: it wins, it loses, or it is voided if any leg comes back void. The
early refund kicks in the moment a reported leg takes loss off the table for one side. Once that side
can only end up winning or getting voided, it can no longer lose its stake, so the stake is returned
right away without waiting for the remaining legs to report.

Which side that is depends on what you backed. If you bet against the parlay and one leg goes against
it, the parlay can no longer win, so your stake comes back early — and the same in reverse, where
whoever is now guaranteed not to lose is refunded as soon as the outcome is locked in.

This only returns the stake on the position that's already decided. Any remaining legs keep reporting,
and the parlay finishes settling in the usual way.

## Parlay vs single bets

| | Single Bet | Parlay |
| - | - | - |
| **Orderbook** | Persistent, always available | Created on-demand per request |
| **Discovery** | Browse existing orders | Receive RFQ via WebSocket |
| **Timing** | Post anytime | 3-second maker window |
| **Market creation** | Markets exist for scheduled events | Market created when bettor requests |
| **Legs** | Single market | Multiple underlying markets |
| **Fees** | Single-bet rates | Separate parlay rates |

## Fees

Parlays use separate rates — `makerParlayPayoutFee` and `takerParlayPayoutFee` — at
[`GET /user/fees-v3`](/api-reference/get-user-fees-v3).

## Related

<CardGroup cols={2}>
  <Card title="Market making parlays" icon="sitemap" href="/developers/market-making-parlays">
    Listening, pricing and quoting inside the 3-second window.
  </Card>

  <Card title="Parlay requests channel" icon="bolt" href="/api-reference/channel-parlay-requests">
    The request payload, and the window it opens.
  </Card>

  <Card title="Parlay markets" icon="book" href="/api-reference/parlay-markets">
    The fields and behaviour of parlay markets.
  </Card>

  <Card title="Time in force" icon="clock" href="/developers/time-in-force">
    `GTC`, `IOC` and `FOK`, and which of them a parlay accepts.
  </Card>
</CardGroup>


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.