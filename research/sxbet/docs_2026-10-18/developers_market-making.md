> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Market making

> An overview of market making on SX Bet — how the maker role works, how exposure is budgeted, and where the book's design constrains a quoting loop.

Market making means resting `GTC` orders on the book and managing what happens to them — earning the
spread between the price you post and what a taker receives. The mechanics of posting are on
[Posting orders](/developers/posting-orders).

## Prerequisites

<Note>
  Your proxy must be [deployed and funded](/developers/posting-orders#prerequisites) before
  `POST /orders-v3` accepts anything.
</Note>

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

<CardGroup cols={2}>
  <Card title="Posting orders" icon="paper-plane" href="/developers/posting-orders">
    Fields, signing, and the validation rules on create.
  </Card>

  <Card title="Cancelling orders" icon="ban" href="/developers/cancelling-orders">
    Picking a cancel route, and confirming before you exit.
  </Card>

  <Card title="Tracking your orders" icon="list-check" href="/developers/my-orders">
    The order row in full, and why a missing order has more than one explanation.
  </Card>

  <Card title="Odds rounding" icon="ruler" href="/developers/odds-rounding">
    Rounding a model price onto the odds ladder.
  </Card>

  <Card title="Market making parlays" icon="sitemap" href="/developers/market-making-parlays">
    Quoting parlay requests, where `FOK` is required.
  </Card>
</CardGroup>


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.