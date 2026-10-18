> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# The exchange model

> How SX Bet works as a peer-to-peer prediction market exchange.

## Overview

SX Bet is modelled on a financial exchange, not a sportsbook. No house takes the other side of your
bet — every bet is matched peer-to-peer between two users.

## Makers and Takers

Every matched bet involves two roles:

<CardGroup cols={2}>
  <Card title="Maker" icon="plus">
    Rests on the orderbook. Specifies an outcome, stake, and desired odds, and sits open until it's matched.
  </Card>

  <Card title="Taker" icon="check">
    Matches an existing resting order immediately, in full or in part.
  </Card>
</CardGroup>

## Order lifecycle

```
Maker or taker signs an order and submits it
       ↓
If the order is matched, the amount is locked in the Escrow contract
       ↓
Fixture plays out
       ↓
Result is graded and settlement is recorded on-chain
       ↓
Escrow pays the winning side automatically
```

## Next

<Card title="The Order Book →" icon="arrow-right" href="/developers/order-book" />


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.