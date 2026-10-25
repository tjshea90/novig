> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# V3 Improvements

> An overview of the V3 exchange model and how it's structured.

## V3 Improvements

V3 rebuilds the exchange around the proxy-wallet model below. What you get for it:

* **Reliable settlement** — the fill failures of V2 are eliminated.
* **Elimination of trade ordering issues** - race conditions between cancels and fills are eliminated.
* **Faster orders API** — placing an order via [`POST /orders-v3`](https://docs.sx.bet/api-reference/post-orders-v3) is roughly 50% lower latency than V2.
* **Faster confirmation** — the time from placing a bet to on-chain settlement drops 2–3×.
* **Automatic order bashing** — an order whose price crosses the book fills immediately against resting liquidity instead of resting.
* **Withdrawal controls** — you can add multiple signers to your wallet to approve withdrawals. See [Accounts](https://docs.sx.bet/developers/accounts.md).
* **Privacy** — your orders, positions and trades are not attributable to your address.

## Capital lives in a proxy wallet

Every bet is placed, collateralised and paid out through a proxy contract you own, not your EOA / user address. [Accounts](https://docs.sx.bet/developers/accounts.md) · [Funding](https://docs.sx.bet/developers/funding.md) · [Reading balances](https://docs.sx.bet/developers/balances-and-ledger.md)

## One signed order does everything

Every order — a resting quote or an immediate bet — is a call submitted to [`POST /orders-v3`](https://docs.sx.bet/api-reference/post-orders-v3). `timeInForce` sets its behaviour: `GTC` rests on the book ("maker" orders), `IOC` and `FOK` execute against it immediately ("taker" or "market" orders).

[Posting orders](https://docs.sx.bet/developers/posting-orders.md) · [Taking liquidity](https://docs.sx.bet/developers/taking-liquidity.md) · [Time in force](https://docs.sx.bet/developers/time-in-force.md) · [EIP-712 order signing](https://docs.sx.bet/api-reference/eip712-order-signing.md) ·

## The order book is aggregated and anonymous

The public book is price levels, not individual orders. Each snapshot carries a `version`, so you can reconcile a REST seed against the realtime stream and always keep the newer state.

[Read the order book](https://docs.sx.bet/developers/order-book.md) · [Book versioning](https://docs.sx.bet/developers/book-versioning.md) · [GET /orderbook-v3/snapshot/market](https://docs.sx.bet/api-reference/get-market-orderbook-snapshot.md)

## Your activity is private

The public book shows aggregated price levels, not individual orders, so your resting quotes are not attributable to you.

## Where everything lives

| To… | Read |
| - | - |
| Set up and fund an account | [Accounts](https://docs.sx.bet/developers/accounts.md), [Funding](https://docs.sx.bet/developers/funding.md), [Reading balances](https://docs.sx.bet/developers/balances-and-ledger.md) |
| Understand the exchange | [Exchange model](https://docs.sx.bet/developers/exchange-model.md), [Time in force](https://docs.sx.bet/developers/time-in-force.md), [Unit conversions](https://docs.sx.bet/developers/unit-conversions.md) |
| Read market data | [Markets overview](https://docs.sx.bet/developers/markets-overview.md), [Fetch markets](https://docs.sx.bet/developers/fetch-markets.md), [Read the order book](https://docs.sx.bet/developers/order-book.md), [Odds](https://docs.sx.bet/developers/odds.md), [Real-time](https://docs.sx.bet/developers/realtime-overview.md) |
| Post and manage quotes | [Posting orders](https://docs.sx.bet/developers/posting-orders.md), [Cancelling orders](https://docs.sx.bet/developers/cancelling-orders.md), [Tracking your orders](https://docs.sx.bet/developers/my-orders.md), [Market making](https://docs.sx.bet/developers/market-making.md) |
| Take liquidity | [Taking liquidity](https://docs.sx.bet/developers/taking-liquidity.md), [Betting delays](https://docs.sx.bet/developers/betting-delays.md) |
| Build parlays | [Parlays](https://docs.sx.bet/developers/parlays.md), [Market-making parlays](https://docs.sx.bet/developers/market-making-parlays.md) |
| Track your activity | [Which grain?](https://docs.sx.bet/developers/which-grain.md), [Tracking your orders](https://docs.sx.bet/developers/my-orders.md), [Reading your bets](https://docs.sx.bet/developers/which-grain.md#bets), [Reconciling fills](https://docs.sx.bet/developers/which-grain.md#fills), [Tracking positions](https://docs.sx.bet/developers/which-grain.md#positions) |
| Handle lifecycles | [Order lifecycle](https://docs.sx.bet/developers/order-lifecycle.md), [Bet lifecycle](https://docs.sx.bet/developers/bet-lifecycle.md) |

> **[Quickstart](https://docs.sx.bet/developers/quickstart.md)**
>
> Deploy a proxy, read the book, and place a bet end to end.

> **[Migrate to V3](https://docs.sx.bet/developers/migrate-to-v3.md)**
>
> Port an existing client, subsystem by subsystem.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.