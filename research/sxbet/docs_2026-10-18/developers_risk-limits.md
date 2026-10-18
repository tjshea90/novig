> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Exposure limits

> How SX Bet manages exposure

Market making on SX Bet is capital-efficient by design: your balance is not divided across the markets
you quote. The same funds can back liquidity everywhere at once.

## How maker exposure is limited

When you post resting (`GTC`) liquidity, the exchange checks:

```
resting size + in-flight size + new order size  ≤  your balance
```

That check runs **per market and outcome**. Orders on different markets — or on the opposite outcome of
the same market — do not draw down each other's budget. Every market:outcome is allowed up to your full
balance.

An order that would push a single `market:outcome` over your balance is rejected.

## Example

With a \$1000 balance you can post up to \$1000 of liquidity on **any number of markets at the
same time**:

* \$1000 on market A, outcome 1
* \$1000 on market B, outcome 1
* \$1000 on market C, outcome 2

Each is measured against the full \$1000 — there is no shared pool being divided up, so all three rest at
once. What you cannot do is exceed \$1000 on a **single** `market:outcome`; if you already have \$600
resting on market A outcome 1, a further order there is capped at \$400. Anything larger is rejected.

## Capital is not locked until a match

Posting an order does **not** move any funds into escrow, regardless of its
[`timeInForce`](/developers/time-in-force). Capital is locked only when an order **matches** — the
moment a taker fills your resting quote, or your own order crosses the book.

## Scale-down when your exposure exceeds your balance

When a match locks capital and drops your available balance below what your resting orders still require,
the exchange automatically cancels resting orders to bring you back within budget.

Cancellation is **worst-odds-first, across all your markets**:

1. The orders **furthest from 50%** implied odds — the least likely to ever fill — are cancelled first.
2. Larger orders break ties, so the deficit is covered in as few cancellations as possible.
3. Cancellation stops as soon as the total exposure on each `market:outcome`is back within your balance. Orders already
   within budget are left untouched.

Orders removed this way become `INACTIVE` with `inactiveReason: INSUFFICIENT_BALANCE`. Watch your
[`account:orders_v3`](/api-reference/channel-orders-v3) stream to react.

## Takers work differently

Non-resting (`IOC` / `FOK`) orders lock real capital the instant they fill. Unlike maker exposure,
taker size is measured **globally** — the sum of your in-flight taker orders across every market must
fit within the one balance, because each fill draws real funds immediately. A taker order that would
exceed your balance is rejected with the same `Insufficient available balance for this order size`
error.

## Related

<CardGroup cols={2}>
  <Card title="Market making" icon="chart-line" href="/developers/market-making">
    Quoting, exposure, and the re-quote loop.
  </Card>

  <Card title="Posting orders" icon="paper-plane" href="/developers/posting-orders">
    Build, sign, and submit an order.
  </Card>

  <Card title="Capital efficiency" icon="coins" href="/developers/capital-efficiency">
    How offsetting positions release escrow at fill time.
  </Card>

  <Card title="Time in force" icon="clock" href="/developers/time-in-force">
    GTC rests; IOC and FOK execute now.
  </Card>
</CardGroup>


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.