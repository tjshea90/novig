> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Parlay markets

> How parlay (multi-leg) markets work on SX Bet

Bettors can request a custom parlay on [SX.Bet](https://sx.bet) by selecting multiple markets. When they submit a parlay request, a message is sent via Websocket (See [this link](/api-reference/channel-parlay-requests) for more details on the request). Once this message is sent, makers will be able to submit orders to this market for upto three seconds.

Market makers can use the payload data from the Parlay Request to submit an [order](/api-reference/post-orders-v3). Market makers have a three second window to post orders. After this point, bettors will be shown all available orders at the same time and no other orders will be viewable by the bettor.

Bettors can choose which order to take, and take it by posting a `FOK` order like any other non-parlay order. An `IOC` order on a parlay market is rejected with `PARLAY_ORDER_MUST_NOT_BE_IOC`.

Market makers can [cancel](/api-reference/delete-orders-v3) orders like any other non-parlay order.

<Info>The parlay order-book that is displayed to a bettor will automatically close after one minute. Your orders may still be active even though the order-book window has closed (the user will not be able to view your order after the window closes).</Info>

<Info>Parlay Orders will expire just as normal orders do, so please set the `expiry` on your orders accordingly. It must not outlive the parlay's own expiry, or the order is rejected with `PARLAY_ORDER_EXPIRY_BEYOND_PARLAY_EXPIRY`.</Info>

<Info>Parlay Markets act as regular markets, but with additional fields to indicate the underlying legs that make up the Parlay.</Info>


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.