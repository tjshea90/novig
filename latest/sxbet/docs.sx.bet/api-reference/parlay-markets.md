> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Parlay markets

> How parlay (multi-leg) markets work on SX Bet

Bettors can request a custom parlay on [SX.Bet](https://sx.bet) by selecting multiple markets. When they submit a parlay request, a message is sent via Websocket (See [this link](/api-reference/channel-parlay-requests.md) for more details on the request). The parlay market is created with a 60-second expiry, and makers can submit orders to it until then.

Market makers can use the payload data from the Parlay Request to submit an [order](/api-reference/post-orders-v3.md).

Bettors can choose which order to take, and take it by posting a `FOK` or `IOC` order like any other non-parlay order.

Market makers can [cancel](/api-reference/delete-orders-v3.md) orders like any other non-parlay order.

> [!NOTE]
>
> Parlay markets expire 60 seconds after creation. Set each order's `expiry` to a positive Unix timestamp in seconds no later than the parlay market's expiry. Otherwise, the order is rejected with `PARLAY_ORDER_EXPIRY_BEYOND_PARLAY_EXPIRY`. New orders submitted after the parlay expires are rejected with `INVALID_MARKET`.

> [!NOTE]
>
> Parlay Markets act as regular markets, but with additional fields to indicate the underlying legs that make up the Parlay.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.