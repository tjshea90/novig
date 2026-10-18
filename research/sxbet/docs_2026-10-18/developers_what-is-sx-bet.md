> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# What is SX Bet?

> An overview of how SX Bet works as a peer-to-peer prediction market.

SX Bet is a decentralised, peer-to-peer sports prediction market. Instead of betting against a house,
users trade against each other through an open order book — the model financial exchanges use, applied
to sports.

## How it works

<Steps>
  <Step title="Someone posts an order">
    A user signs an order naming a market, an outcome, a size and a price, and submits it.
  </Step>

  <Step title="The engine matches compatible orders">
    Two orders on opposite sides at compatible prices match, wholly or partly. Collateral is locked in
    escrow per match.
  </Step>

  <Step title="The event is reported, and settlement pays out">
    Once the market is graded, the winning side is paid. A void is a settled bet with an outcome of
    zero, and stakes return.
  </Step>
</Steps>

## All bets in USDC

<Note>
  You bet in USDC from your [proxy wallet](/developers/accounts), a wallet the exchange deploys for you that only you control.
</Note>

## Visibility

Markets, order books and the trade tape are public. Your orders, bets, positions
and balances are private to you.

## Related

<CardGroup cols={2}>
  <Card title="The exchange model" icon="arrows-left-right" href="/developers/exchange-model">
    Why maker and taker are a classification, not a choice.
  </Card>

  <Card title="What can you build?" icon="lightbulb" href="/developers/what-can-you-build">
    What an orderbook exchange makes possible.
  </Card>

  <Card title="Quickstart" icon="rocket" href="/developers/quickstart">
    A resting order in about twenty minutes.
  </Card>

  <Card title="Unit conversions" icon="calculator" href="/developers/unit-conversions">
    The scales, and which side a price is quoted from.
  </Card>
</CardGroup>


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.