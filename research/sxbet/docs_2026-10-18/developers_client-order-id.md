> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Client order id

> Attach your own id to an order so you can reconcile it across HTTP and realtime.

`clientOrderId` is an optional tag you attach to an order on
[`POST /orders-v3`](/api-reference/post-orders-v3). It is your own value, echoed back to you on the
create response, the order row, the orders channel, and in the fills grain.
`clientOrderId` has no effect on funds, fees, or matching.

## Why it is useful

You do not know an order's id until [`POST /orders-v3`](/api-reference/post-orders-v3) responds. That makes correlation awkward when
the pieces of one order's lifecycle arrive on different paths: the HTTP response, and then separate
publications on [`account:orders_v3_#{address}`](/api-reference/channel-orders-v3). A `clientOrderId`
is a value you choose *before* you submit, so you can stitch those messages together against your own
records the moment each one lands, rather than waiting on the id.

This is the standard pattern on async trading platforms: attach your own reference at submit time,
then match it back as updates flow in.

## Format

| Rule | Value |
| - | - |
| Optional | Omit it and the field is absent from responses |
| Max length | 64 characters |
| Allowed characters | `^[A-Za-z0-9_-]+$` — letters, digits, `_`, `-` |

## Uniqueness

A `clientOrderId` must be **unique per address** — not just within one request, but across every order
you currently have in the system.

The safe rule is to treat each `clientOrderId` as single-use. Generate a fresh value per order.

## Where it shows up

`clientOrderId` is echoed on the **order** grain:

* [`POST /orders-v3`](/api-reference/post-orders-v3) — the `200` body's `orders` array echoes your
  `clientOrderId` on each result when you set one, alongside `orderId`, `status`, and `commandId`.
* [`GET /orders-v3`](/api-reference/get-orders-v3) — each order row carries `clientOrderId` when set.
* [`GET /orders-v3/client/{clientOrderId}`](/api-reference/get-order-v3-by-client-id) — look up one of
  your orders (any status) by the tag you set.
* [`account:orders_v3_#{address}`](/api-reference/channel-orders-v3) — each publication carries it too
  when set.

It is also echoed on the **fill** grain, so you can trace a fill straight back to the order that
produced it:

* [`GET /fills-v3`](/api-reference/get-fills-v3) and
  [`account:fills_v3_#{address}`](/api-reference/channel-fills-v3) — each [fill](/developers/which-grain#fills)
  carries the `clientOrderId` of the order on that side (the maker's tag on maker rows, the taker's on
  taker rows) when that order carried a tag; otherwise the field is omitted.

## Recommended pattern

```javascript JavaScript theme={null}
// Mint a fresh, single-use id and record what you need before you submit.
const clientOrderId = `mm-${crypto.randomUUID()}`;

myOrders.set(clientOrderId, {
  timeInForce: "GTC",       // write-only on the API — record it yourself
  marketHash,
  submittedAt: Date.now(),
});

await fetch(`${API}/orders-v3`, authed({
  method: "POST",
  body: JSON.stringify({ orders: [{ ...order, clientOrderId }] }),
}));

// Later, on account:orders_v3_#{address}, look each update up by its clientOrderId.
// Or poll GET /orders-v3/client/{clientOrderId} when you need the current row.
```

## Related

<CardGroup cols={2}>
  <Card title="Posting orders" icon="paper-plane" href="/developers/posting-orders">
    The submit flow this field rides on.
  </Card>

  <Card title="Time in force" icon="clock" href="/developers/time-in-force">
    The other write-only field worth recording against your tag.
  </Card>

  <Card title="Tracking your orders" icon="magnifying-glass" href="/developers/my-orders">
    Keep a local store keyed by an id you control.
  </Card>

  <Card title="Get an order by client ID" icon="hashtag" href="/api-reference/get-order-v3-by-client-id">
    Look up one of your orders by the tag you set.
  </Card>

  <Card title="EIP-712 order signing" icon="signature" href="/api-reference/eip712-order-signing">
    Why the order id is a digest, not a server-assigned value.
  </Card>

  <Card title="External user id" icon="users" href="/developers/external-user-id">
    A per-person tag when one SX account places orders for many people.
  </Card>
</CardGroup>


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.