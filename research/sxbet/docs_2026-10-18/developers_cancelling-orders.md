> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Cancelling orders

> How to cancel open orders on the SX Bet exchange.

There are four cancel routes. All four return `200` even when nothing was cancelled, so a `200`
alone never confirms an order is off the book — you have to check the response body, and what the
body can tell you differs by route:

| Route | Scope | Does the response body confirm the order is off the book? |
| - | - | - |
| [`DELETE /orders-v3`](/api-reference/delete-orders-v3) | ids you name, up to 100 | **Yes** — waits for the matching engine, reports per order |
| [`DELETE /orders-v3/market`](/api-reference/delete-orders-v3-market) | every open order on one market | **No** — confirms only that a cancel command was accepted |
| [`DELETE /orders-v3/event`](/api-reference/delete-orders-v3-event) | every open order on one event | **No** — confirms only that a cancel command was accepted |
| [`DELETE /orders-v3/all`](/api-reference/delete-orders-v3-all) | every open order you have | **No** — confirms only that a cancel command was accepted |

## Cancelling asynchronously

**Cancel by id is synchronous.** The route waits up to 5 seconds for the matching engine to act
before responding, and reports `cancelled` or `notCancelled` per order — plus a third bucket,
`unconfirmed`, for when the matching engine's answer doesn't arrive within that window.

**Cancel-all, cancel-by-event and cancel-by-market are asynchronous.** All three return as soon as the
matching engine accepts the cancel commands, before your orders are actually off the book. The
response's `cancelledSubmitted` list names which commands were accepted; nothing in the response
confirms the orders are gone.

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

See [Real-time data](/developers/realtime-overview) and [Tracking your orders](/developers/my-orders).

## Cancels from elsewhere

Orders sometime leave the book without you cancelling them, and `inactiveReason` says why. See [the full table](/developers/order-lifecycle#the-inactivereason-values)

## Related

<CardGroup cols={2}>
  <Card title="Cancel orders by id" icon="list-check" href="/api-reference/delete-orders-v3" />

  <Card title="Cancel all orders" icon="broom" href="/api-reference/delete-orders-v3-all" />

  <Card title="Cancel orders by market" icon="chart-line" href="/api-reference/delete-orders-v3-market" />

  <Card title="Cancel orders by event" icon="calendar-xmark" href="/api-reference/delete-orders-v3-event" />

  <Card title="Heartbeat" icon="heart-pulse" href="/developers/heartbeat">
    Arming the switch that cancels for you when your process stops.
  </Card>
</CardGroup>


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.