> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Market making

> An overview of market making on SX Bet — how the maker role works, how exposure is budgeted, and where the book's design constrains a quoting loop.

Market making means resting `GTC` orders on the book and managing what happens to them — earning the
spread between the price you post and what a taker receives. The mechanics of posting are on
[Posting orders](/developers/posting-orders.md).

## Prerequisites

> [!NOTE]
>
> Your proxy must be [deployed and funded](/developers/posting-orders.md#prerequisites) before
> `POST /orders-v3` accepts anything.

## Minimal example

1. **`GET /metadata/obv3`** — once, at startup.
2. **`GET /markets/active`** — discover the markets you cover.
3. **`GET /orderbook-v3/snapshot/market`** — read the aggregated levels and the book's version.
4. **Price** — your model's output, snapped to the ladder.
5. **`POST /orders-v3`** — up to 10 orders per request.
6. **Watch** — `account:orders_v3_#{address}` for order state, `account:fills_v3_#{address}` for
   fills (joined on `orderId`), `orderbook_v3:{marketHash}` for the book moving.
7. **Reprice** — `DELETE /orders-v3` by id, then `POST` the replacement.

## Related

> **[Posting orders](/developers/posting-orders.md)**
>
> Fields, signing, and the validation rules on create.

> **[Cancelling orders](/developers/cancelling-orders.md)**
>
> Picking a cancel route, and confirming before you exit.

> **[Tracking your orders](/developers/my-orders.md)**
>
> The order row in full, and why a missing order has more than one explanation.

> **[Odds rounding](/developers/odds-rounding.md)**
>
> Rounding a model price onto the odds ladder.

> **[Market making parlays](/developers/market-making-parlays.md)**
>
> Quoting parlay requests.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.