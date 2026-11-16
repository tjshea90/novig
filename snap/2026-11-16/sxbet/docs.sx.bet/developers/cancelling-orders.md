> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Cancelling orders

> How to cancel open orders on the SX Bet exchange.

There are four cancel routes. All four return `200` even when nothing was cancelled, so a `200`
alone never confirms an order is off the book — you have to check the response body, and what the
body can tell you differs by route:

| Route | Scope | What the response tells you |
| - | - | - |
| [`DELETE /orders-v3`](https://docs.sx.bet/api-reference/delete-orders-v3) | ids you name, up to 100 | **A per-order result** — waits up to 5 seconds for the matching engine |
| [`DELETE /orders-v3/market`](https://docs.sx.bet/api-reference/delete-orders-v3-market) | every open order on one market | **Which orders are being cancelled** — returns without waiting for cancellation to complete |
| [`DELETE /orders-v3/event`](https://docs.sx.bet/api-reference/delete-orders-v3-event) | every open order on one event | **Which orders are being cancelled** — returns without waiting for cancellation to complete |
| [`DELETE /orders-v3/all`](https://docs.sx.bet/api-reference/delete-orders-v3-all) | every open order you have | **Which orders are being cancelled** — returns without waiting for cancellation to complete |

## Cancelling asynchronously

**Cancel by id is synchronous.** The route waits up to 5 seconds for the matching engine to act
before responding, and reports `cancelled` or `notCancelled` per order — plus a third bucket,
`unconfirmed`, for when the matching engine's answer doesn't arrive within that window.

**Cancel-all, cancel-by-event and cancel-by-market are asynchronous.** They return without waiting
for cancellation to complete. The unfilled amount of each order in the response's
`cancelledSubmitted` list will be cancelled.

## Once a cancel is sent

A cancel is queued in the matching engine and processed in sequence with the market's other
commands. Until it takes effect, the order can still fill: for example, against an order the
matching engine received before the cancel. An order still in its
[betting delay](https://docs.sx.bet/developers/betting-delays.md) matches when the delay elapses. Any unfilled amount is
then cancelled.

After a `200` response, you do not need to send another cancel for:

* **Cancel-all, cancel-by-event and cancel-by-market:** every order in `cancelledSubmitted`.
* **Cancel by id:** orders in `unconfirmed`, and orders in `notCancelled` with `reason`
  `CANCEL_PENDING` or `CANCEL_AFTER_MATCH`. For `unconfirmed`, the cancel was accepted, but
  confirmation did not arrive within the timeout.

A bulk cancel does not cover orders submitted after it completes. Orders submitted while it is
running may or may not be included. Check `cancelledSubmitted` to see which orders it covers.

## Choosing a route

**Use cancel by id** when you know what you want gone: repricing a level, pulling one side, reacting
to a fill.

**Use cancel by market** to clear one market and leave the rest of the event alone.

**Use cancel by event** for reacting to event-level changes

**Use cancel-all** as a circuit breaker in case everything needs to be immediately cancelled.

## Watching cancels land

For the asynchronous endpoints, the direct signal is the `account:orders_v3_#{address}` channel: when the
matching engine removes your order, you get an update with `status: "INACTIVE"` and
`inactiveReason: "USER_REQUESTED"`.

See [Real-time data](https://docs.sx.bet/developers/realtime-overview.md) and [Tracking your orders](https://docs.sx.bet/developers/my-orders.md).

## Cancels from elsewhere

Orders sometime leave the book without you cancelling them, and `inactiveReason` says why. See [the full table](https://docs.sx.bet/developers/order-lifecycle.md#the-inactivereason-values)

## Related

> [card] [Cancel orders by id](https://docs.sx.bet/api-reference/delete-orders-v3.md)

> [card] [Cancel all orders](https://docs.sx.bet/api-reference/delete-orders-v3-all.md)

> [card] [Cancel orders by market](https://docs.sx.bet/api-reference/delete-orders-v3-market.md)

> [card] [Cancel orders by event](https://docs.sx.bet/api-reference/delete-orders-v3-event.md)

> [card] [Heartbeat](https://docs.sx.bet/developers/heartbeat.md)
>
> Arming the switch that cancels for you when your process stops.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.