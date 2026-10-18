> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# V3 Improvements

> An overview of the V3 exchange model and how it's structured.

## V3 Improvements

V3 rebuilds the exchange around the proxy-wallet model below. What you get for it:

* **Reliable settlement** — the fill failures of V2 are eliminated.
* **Elimination of trade ordering issues** - race conditions between cancels and fills are eliminated.
* **Faster orders API** — placing an order via [`POST /orders-v3`](/api-reference/post-orders-v3) is roughly 50% lower latency than V2.
* **Faster confirmation** — the time from placing a bet to on-chain settlement drops 2–3×.
* **Automatic order bashing** — an order whose price crosses the book fills immediately against resting liquidity instead of resting.
* **Withdrawal controls** — you can add multiple signers to your wallet to approve withdrawals. See [Accounts](/developers/accounts).
* **Privacy** — your orders, positions and trades are not attributable to your address.

## Capital lives in a proxy wallet

Every bet is placed, collateralised and paid out through a proxy contract you own, not your EOA / user address. [Accounts](/developers/accounts) · [Funding](/developers/funding) · [Reading balances](/developers/balances-and-ledger)

## One signed order does everything

Every order — a resting quote or an immediate bet — is a call submitted to [`POST /orders-v3`](/api-reference/post-orders-v3). `timeInForce` sets its behaviour: `GTC` rests on the book ("maker" orders), `IOC` and `FOK` execute against it immediately ("taker" or "market" orders).

[Posting orders](/developers/posting-orders) · [Taking liquidity](/developers/taking-liquidity) · [Time in force](/developers/time-in-force) · [EIP-712 order signing](/api-reference/eip712-order-signing) ·

## The order book is aggregated and anonymous

The public book is price levels, not individual orders. Each snapshot carries a `version`, so you can reconcile a REST seed against the realtime stream and always keep the newer state.

[Read the order book](/developers/order-book) · [Book versioning](/developers/book-versioning) · [GET /orderbook-v3/snapshot/market](/api-reference/get-market-orderbook-snapshot)

## Your activity is private

The public book shows aggregated price levels, not individual orders, so your resting quotes are not attributable to you.

## Where everything lives

| To… | Read |
| - | - |
| Set up and fund an account | [Accounts](/developers/accounts), [Funding](/developers/funding), [Reading balances](/developers/balances-and-ledger) |
| Understand the exchange | [Exchange model](/developers/exchange-model), [Time in force](/developers/time-in-force), [Unit conversions](/developers/unit-conversions) |
| Read market data | [Markets overview](/developers/markets-overview), [Fetch markets](/developers/fetch-markets), [Read the order book](/developers/order-book), [Odds](/developers/odds), [Real-time](/developers/realtime-overview) |
| Post and manage quotes | [Posting orders](/developers/posting-orders), [Cancelling orders](/developers/cancelling-orders), [Tracking your orders](/developers/my-orders), [Market making](/developers/market-making) |
| Take liquidity | [Taking liquidity](/developers/taking-liquidity), [Betting delays](/developers/betting-delays) |
| Build parlays | [Parlays](/developers/parlays), [Market-making parlays](/developers/market-making-parlays) |
| Track your activity | [Which grain?](/developers/which-grain), [Tracking your orders](/developers/my-orders), [Reading your bets](/developers/which-grain#bets), [Reconciling fills](/developers/which-grain#fills), [Tracking positions](/developers/which-grain#positions) |
| Handle lifecycles | [Order lifecycle](/developers/order-lifecycle), [Bet lifecycle](/developers/bet-lifecycle) |

<CardGroup cols={2}>
  <Card title="Quickstart" icon="rocket" href="/developers/quickstart">
    Deploy a proxy, read the book, and place a bet end to end.
  </Card>

  <Card title="Migrate to V3" icon="arrow-right-arrow-left" href="/developers/migrate-to-v3">
    Port an existing client, subsystem by subsystem.
  </Card>
</CardGroup>


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.