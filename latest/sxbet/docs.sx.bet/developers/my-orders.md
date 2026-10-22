> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Tracking your orders

> How to track your open orders

## Keeping an up-to-date view of your orders

**Seed your view from REST, keep it live off the account channel, and de-duplicate with `messageId`.**
The snapshot gives you what is resting right now; the channel gives you every change after.

1. **Subscribe to [`account:orders_v3_#{address}`](/api-reference/channel-orders-v3) first** — before
   you seed and before you post anything. Subscribing first means no transition slips through the gap between your snapshot and your feed.

2. **Seed from [`GET /orders-v3`](/api-reference/get-orders-v3).** Load the currently-resting orders
   into a store keyed by `order.id` — an id you can compute locally before you submit. Optionally tag each entry
   with a `clientOrderId` when you post; the API echoes it back, so it is a cheap way to recognize your
   own order later.

3. **Key your store by `order.id`, and de-duplicate the channel by `messageId`.** Drop any
   publication whose `messageId` (in `ctx.tags`) you have already applied; otherwise write the row
   into your store under its `order.id`.

## Looking up one order

[`GET /orders-v3/{orderId}`](/api-reference/get-order-v3) returns a single order you own in **any**
status.

If you tagged the order on submit, [`GET /orders-v3/client/{clientOrderId}`](/api-reference/get-order-v3-by-client-id)
looks it up by that tag instead.

## Related

> **[Get your orders](/api-reference/get-orders-v3.md)**
>
> Parameters, the fifteen-field row, and the available filters.

> **[Get an order by ID](/api-reference/get-order-v3.md)**
>
> One order, any status.

> **[Get an order by client ID](/api-reference/get-order-v3-by-client-id.md)**
>
> One order, looked up by the tag you set.

> **[account:orders\_v3](/api-reference/channel-orders-v3.md)**
>
> Where terminal state actually comes from.

> **[Order lifecycle](/developers/order-lifecycle.md)**
>
> The three statuses, all ten terminal reasons, and the correct way to test for a fill.

> **[Which grain?](/developers/which-grain.md)**
>
> Bets, fills and positions — picking between them.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.