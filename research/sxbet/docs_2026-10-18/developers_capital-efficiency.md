> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Capital efficiency

> How SX Bet's capital efficiency system reduces locked escrow by recognizing hedged positions.

SX Bet locks tokens in escrow when you place a bet and releases them at settlement. Capital efficiency
(CE) short-circuits that: when your positions within a market offset each other, the exchange
calculates your true worst-case loss and returns the excess escrow to your wallet immediately — at fill
time, without waiting for the market to settle.

It is automatic: **there is nothing to opt into and no
endpoint to call.**

## How it works

Every market pool has a worst-case loss that escrow must cover. On each fill, the exchange recomputes
that worst case across all of your open positions in the pool, and refunds anything held above it.

The
recalculation runs for every party to the trade — makers and takers alike, not just the taker who
triggered it.

If your worst case has dropped, escrow adjusts down to match and releases the excess to
your wallet in the same on-chain batch that locks the fill.

## Example

You bet 100 USDC on outcome one at even odds (50% implied) — a win returns 200, so your worst case is
the 100 you staked, and 100 stays locked. Later you bet 100 USDC on outcome two in the same market,
also at even odds.

Now one side always pays the other, so your worst-case net loss is 0. The exchange
recalculates and refunds the full 200 USDC on the second fill.

## Tracking CE refunds

Refunds are reported as fields on rows you already read, not through a dedicated endpoint or channel:

* **Per bet** — `ceRefundAmount` on [`GET /trades-v3`](/api-reference/get-trades-v3).
* **Per fill** — `ceRefundAmount` on [`GET /fills-v3`](/api-reference/get-fills-v3).
* **Realtime** — the affected rows are republished on the
  [trades](/api-reference/channel-trades-v3) and [fills](/api-reference/channel-fills-v3) channels with
  new `ceRefundAmount` values and a fresh `updatedAt`. There is no separate "refund happened" event.

`ceRefundAmount` is **post-fee** — the amount you actually received. `ceRefundFeeAmount` is the fee
taken on that refund.

## Reclaim at settlement

Refunds issued during a market's life are reclaimed when the market settles. Within each market, the
total refunded to you is subtracted from the returns on your winning bets — or, if the market is
voided, from your returned stakes — taken oldest bet first. The reclaim follows the order your bets
were placed rather than any internal identifier, so the same bets in the same sequence always settle
to the same amounts, including when a settlement is retried. The amount reclaimed is reported as
`settleCeRefundAmount`.

## P\&L accounting

CE refunds return to your wallet before settlement, so they are not in `settleReturnAmount`. When
reconciling P\&L for a market that issued refunds, add them back in:

```
actual_return = settleReturnAmount + ceRefundAmount − totalStake
```

`settleCeRefundAmount` on the settlement object is a different number — the refund *consumed against
your gross payout* at settlement, not what you received. P\&L uses the received figure.

<Warning>
  **Do not add refunds on top of a figure that already includes them.** The `pnl` field on
  [`GET /positions-v3?status=SETTLED`](/api-reference/get-positions-v3) is
  `settleReturnAmount + ceRefundAmount − totalStake`, with the refund already in it.
</Warning>

## Related

<CardGroup cols={2}>
  <Card title="Reconciling fills" icon="list-check" href="/developers/which-grain#fills">
    Where to find the refund on each leg.
  </Card>

  <Card title="Tracking positions" icon="chart-pie" href="/developers/which-grain#positions">
    Netted exposure — the same idea, read-only.
  </Card>

  <Card title="Parlays" icon="link" href="/developers/parlays">
    The other meaning of `ceRefundAmount`.
  </Card>

  <Card title="Quarter-line markets" icon="ruler-combined" href="/developers/quarter-line-markets">
    Why one bet produces two refund calculations.
  </Card>
</CardGroup>


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.