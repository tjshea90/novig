> ## Documentation Index
> Fetch the complete documentation index at: https://docs.novig.com/llms.txt
> Use this file to discover all available pages before exploring further.

# WebSocket

> Get RFQs and answer them over one open connection.

The maker WebSocket lets you price RFQs without running a public server.
You keep one connection open. We push each quote round to you, and you answer on the same connection.

A quote round is the auction for one RFQ (request for quote). Every registered pricer can quote on it.

The socket replaces the HTTP server that [webhooks](/api-reference/rfq/webhooks.md) need.
You need no TLS certificate, no public hostname, and no signature checks. You authenticate once, when you connect.

The timing is the same as for webhooks. Each auction lasts 3 seconds.
When you win, you get a 1-second last look: a final chance to confirm or reject the trade.
If you don't answer in time, we reject the trade.

## Connect

| Environment | URL |
| - | - |
| Production | `wss://api.novig.com/rfq/ws` |
| QA | `wss://api-qa.novig.us/rfq/ws` |

Send your OAuth 2.0 access token as a bearer token on the upgrade request.
It's the same market-maker token the REST API uses. See [Authentication](/api-reference/authentication.md).

```http theme={"dark"}
Authorization: Bearer YOUR_ACCESS_TOKEN
```

The token identifies your trader, so your messages carry no pricer ID and no signature.

### Register a pricer before you connect

We refuse the upgrade if your trader has no pricer registration.

Register once with `POST /rfq/pricer` and the body `{}`. A socket-only pricer needs no webhook and gets no shared secret.

The registration outlives every connection, so you don't register again when you reconnect.
See [Registration](/api-reference/rfq/registration.md).

### Why the upgrade fails

We check your token and your registration before the upgrade. A failure comes back as a plain HTTP status.

| Status | Cause | Retry |
| - | - | - |
| `401` | The token is missing or invalid | No. Fix the token |
| `403` | Not a market-maker token, or no pricer registration | No. Register first |
| `404` | The socket is off in this environment | No. Ask us to turn it on |
| `500` | We couldn't read your registration | Yes, with backoff |

## Channels

A channel is a stream of events you subscribe to. There are two.

| Event | Channel | We send it when |
| - | - | - |
| `rfq_created` | `rfq` | A round opens |
| `rfq_closed` | `rfq` | A round's auction closes |
| `rfq_executed` | `rfq` | Any RFQ trade fills |
| `quote_created` | `quotes` | We record your quote |
| `quote_accepted` | `quotes` | You win and must confirm |
| `quote_executed` | `quotes` | Your quote fills |
| `quote_unfilled` | `quotes` | Your confirmed quote won't fill |
| `quote_dropped` | `quotes` | We drop your quote for a reason you can fix |

We send `rfq` events to every pricer. We send `quotes` events only to you.

> [!WARNING]
>
> Subscribe to both channels to trade. Without `quotes` you never see `quote_accepted`, so we reject every trade you win.

## How a round works

**1. We open a round**

`rfq_created` arrives on `rfq` with the legs, the minimum stake, and the deadline.

**2. You answer**

Send `create_quote` before `expires_at`, or send `decline` to pass.

**3. The auction closes**

`rfq_closed` arrives. Stop pricing that round.

**4. You confirm a win**

If you win, `quote_accepted` arrives on `quotes`. Send `confirm` within 1 second.

**5. The trade fills or doesn't**

After you confirm, `quote_executed` or `quote_unfilled` arrives on `quotes`.

## Message format

Every message is a JSON text frame with two keys: `event` names the message, and `data` holds its payload.

We answer every message you send with one ack or one `error`.

Every frame we send also carries `timestamp`, the time we emitted it.
Other times are named fields in `data`. For example, `expires_at` is when the auction closes, and `executed_at` is when the trade filled.

We send decimal amounts like `price`, `max_wager`, and `wager` as JSON strings. Send yours as strings too.

## &#x20;Messages you send

> **`subscribe`**
>
> Start getting a channel's events
>
> `data` is the channel name as a string, not an object. We ack with `subscribed`.
>
> ```json theme={"dark"}
> {
>   "event": "subscribe",
>   "data": "rfq"
> }
> ```
>
> **data** `string` *required*
>
> `"rfq"` or `"quotes"`.

> **`unsubscribe`**
>
> Stop getting a channel's events
>
> We ack with `unsubscribed`.
>
> ```json theme={"dark"}
> {
>   "event": "unsubscribe",
>   "data": "quotes"
> }
> ```
>
> **data** `string` *required*
>
> `"rfq"` or `"quotes"`.

> **`create_quote`**
>
> Quote an open round
>
> We ack with `quote_submitted`.
>
> ```json theme={"dark"}
> {
>   "event": "create_quote",
>   "data": {
>     "rfq_id": "0193abcd-0000-7000-8000-000000000001",
>     "price": "0.45",
>     "max_wager": "250",
>     "quote_id": "mm-1"
>   }
> }
> ```
>
> **rfq\_id** `UUID string` *required*
>
> The round, from `rfq_created`.
>
> **price** `decimal string` *required*
>
> A decimal probability between 0 and 1. `"0.45"` means a \$0.45 stake per \$1.00 payout. We round it to the nearest 0.001.
>
> **max\_wager** `decimal string` *required*
>
> The largest taker stake this quote covers, in Novig Cash. It must be above zero.
>
> **quote\_id** `string`
>
> Your own reference for the quote, up to 256 bytes. We return it to you as `external_flag`. Leave it out if you don't need one.
>
> You get one quote per round, and your first quote stands.
> We drop a second quote for the same `rfq_id` and ack it with `quote_superseded`.
> To change your price, wait for the next round.

> **`decline`**
>
> Pass on an open round
>
> We ack with `quote_declined`.
>
> ```json theme={"dark"}
> {
>   "event": "decline",
>   "data": {
>     "rfq_id": "0193abcd-0000-7000-8000-000000000001"
>   }
> }
> ```
>
> **rfq\_id** `UUID string` *required*
>
> The round, from `rfq_created`.
>
> Declining is optional. A round you don't answer closes on its own.
>
> A decline tells us you saw the round and passed. Silence looks the same as a pricer that is down or lagging.

> **`confirm`**
>
> Answer the last look on a quote you won
>
> We ack with `confirm_recorded`.
>
> ```json theme={"dark"}
> {
>   "event": "confirm",
>   "data": {
>     "rfq_id": "0193abcd-0000-7000-8000-000000000001",
>     "quote_id": "0193abcd-0000-7000-8000-00000000000a",
>     "confirmed": true
>   }
> }
> ```
>
> **rfq\_id** `UUID string` *required*
>
> The round, from `quote_accepted`.
>
> **quote\_id** `UUID string` *required*
>
> Our ID for your quote, from `quote_accepted`. It's not the `quote_id` you sent on `create_quote`.
>
> **confirmed** `boolean` *required*
>
> `true` fills the trade at your quoted price. `false` rejects it.
>
> > [!WARNING]
> >
> > You have 1 second, and silence rejects the trade. A late answer, or a dropped connection before you answer, rejects it just as `false` does.
>
> Your first answer counts. A second answer gets `CONFIRM_ALREADY_DECIDED`, or `NO_CONFIRM_WINDOW` once the window has closed.

## &#x20;Events we send

You get a channel's events after you subscribe to it.

> **`rfq_created`**
>
> A round opened (rfq channel)
>
> Send your quote or decline before `expires_at`.
>
> ```json theme={"dark"}
> {
>   "event": "rfq_created",
>   "data": {
>     "rfq_id": "0193abcd-0000-7000-8000-000000000001",
>     "outcome_ids": [
>       "11111111-1111-1111-1111-111111111111",
>       "22222222-2222-2222-2222-222222222222"
>     ],
>     "expires_at": "2026-01-01T00:00:03Z",
>     "min_wager": "15"
>   },
>   "timestamp": "2026-01-01T00:00:00.123Z"
> }
> ```
>
> **event** `string` *required*
>
> `"rfq_created"`.
>
> **timestamp** `ISO-8601 string` *required*
>
> When we emitted the frame.
>
> **data.rfq\_id** `UUID string` *required*
>
> The round. Send it on every message about this round.
>
> **data.outcome\_ids** `array of UUID strings` *required*
>
> The legs, in the order the taker asked for them. `GET /rfq/executions` sorts the same legs by outcome ID. See [Universe model](/api/concepts/universe-model.md#outcome).
>
> **data.expires\_at** `ISO-8601 string` *required*
>
> When the auction closes, 3 seconds after we opened the round.
>
> **data.min\_wager** `decimal string` *required*
>
> The smallest stake a quote must cover: the taker's minimum or our minimum of \$10.00, whichever is higher. We drop a quote whose `max_wager` is below it, with reason `below_min_wager`.

> **`rfq_closed`**
>
> The auction closed (rfq channel)
>
> Stop pricing the round. We answer a `create_quote` that arrives after this with `RFQ_CLOSED`.
>
> ```json theme={"dark"}
> {
>   "event": "rfq_closed",
>   "data": {
>     "rfq_id": "0193abcd-0000-7000-8000-000000000001"
>   },
>   "timestamp": "2026-01-01T00:00:03.001Z"
> }
> ```
>
> **event** `string` *required*
>
> `"rfq_closed"`.
>
> **timestamp** `ISO-8601 string` *required*
>
> When we emitted the frame.
>
> **data.rfq\_id** `UUID string` *required*
>
> The round that closed.

> **`rfq_executed`**
>
> A trade filled, with no counterparty named (rfq channel)
>
> Every pricer sees this event, so it names no one.
>
> ```json theme={"dark"}
> {
>   "event": "rfq_executed",
>   "data": {
>     "rfq_id": "0193abcd-0000-7000-8000-000000000001",
>     "wager": "50",
>     "price": "0.50",
>     "executed_at": "2023-11-14T22:13:20Z"
>   },
>   "timestamp": "2023-11-14T22:13:20.050Z"
> }
> ```
>
> **event** `string` *required*
>
> `"rfq_executed"`.
>
> **timestamp** `ISO-8601 string` *required*
>
> When we emitted the frame.
>
> **data.rfq\_id** `UUID string` *required*
>
> The round.
>
> **data.wager** `decimal string` *required*
>
> The amount staked.
>
> **data.price** `decimal string` *required*
>
> The execution price.
>
> **data.executed\_at** `ISO-8601 string` *required*
>
> When the trade filled.

> **`quote_created`**
>
> We recorded your quote (quotes channel)
>
> We recorded your quote when the auction closed.
>
> ```json theme={"dark"}
> {
>   "event": "quote_created",
>   "data": {
>     "quote_id": "0193abcd-0000-7000-8000-00000000000a",
>     "rfq_id": "0193abcd-0000-7000-8000-000000000001",
>     "pricer_trader_id": "0193abcd-0000-7000-8000-0000000000ff"
>   },
>   "timestamp": "2026-01-01T00:00:03.001Z"
> }
> ```
>
> **event** `string` *required*
>
> `"quote_created"`.
>
> **timestamp** `ISO-8601 string` *required*
>
> When we emitted the frame.
>
> **data.quote\_id** `UUID string` *required*
>
> Our ID for your quote.
>
> **data.rfq\_id** `UUID string` *required*
>
> The round.
>
> **data.pricer\_trader\_id** `UUID string` *required*
>
> Your pricer.

> **`quote_accepted`**
>
> You won. Confirm within 1 second (quotes channel)
>
> The taker is trading against your quote. Answer with `confirm`.
>
> ```json theme={"dark"}
> {
>   "event": "quote_accepted",
>   "data": {
>     "quote_id": "0193abcd-0000-7000-8000-00000000000a",
>     "rfq_id": "0193abcd-0000-7000-8000-000000000001",
>     "pricer_trader_id": "0193abcd-0000-7000-8000-0000000000ff",
>     "wager": "50",
>     "price": "0.55",
>     "external_flag": "mm-1"
>   },
>   "timestamp": "2026-01-01T00:00:05.250Z"
> }
> ```
>
> **event** `string` *required*
>
> `"quote_accepted"`.
>
> **timestamp** `ISO-8601 string` *required*
>
> When we emitted the frame.
>
> **data.quote\_id** `UUID string` *required*
>
> Our ID for your quote. Send this one back on `confirm`.
>
> **data.rfq\_id** `UUID string` *required*
>
> The round.
>
> **data.pricer\_trader\_id** `UUID string` *required*
>
> Your pricer.
>
> **data.wager** `decimal string` *required*
>
> The taker's stake. It's never above the `max_wager` you quoted.
>
> **data.price** `decimal string` *required*
>
> The price you quoted.
>
> **data.external\_flag** `string or null` *required*
>
> The `quote_id` you sent on `create_quote`, or `null` if you sent none.

> **`quote_executed`**
>
> Your quote filled (quotes channel)
>
> This event names your pricer, which `rfq_executed` leaves out.
>
> ```json theme={"dark"}
> {
>   "event": "quote_executed",
>   "data": {
>     "quote_id": "0193abcd-0000-7000-8000-00000000000a",
>     "rfq_id": "0193abcd-0000-7000-8000-000000000001",
>     "pricer_trader_id": "0193abcd-0000-7000-8000-0000000000ff",
>     "wager": "50",
>     "price": "0.55",
>     "executed_at": "2023-11-14T22:13:20Z"
>   },
>   "timestamp": "2023-11-14T22:13:20.050Z"
> }
> ```
>
> **event** `string` *required*
>
> `"quote_executed"`.
>
> **timestamp** `ISO-8601 string` *required*
>
> When we emitted the frame.
>
> **data.quote\_id** `UUID string` *required*
>
> Our ID for your quote.
>
> **data.rfq\_id** `UUID string` *required*
>
> The round.
>
> **data.pricer\_trader\_id** `UUID string` *required*
>
> Your pricer.
>
> **data.wager** `decimal string` *required*
>
> The amount staked.
>
> **data.price** `decimal string` *required*
>
> The execution price.
>
> **data.executed\_at** `ISO-8601 string` *required*
>
> When the trade filled.

> **`quote_unfilled`**
>
> Your confirmed quote won't fill (quotes channel)
>
> You confirmed, but the trade didn't fill. Release anything you reserved for it.
>
> A quote you confirm ends in either `quote_executed` or `quote_unfilled`.
>
> ```json theme={"dark"}
> {
>   "event": "quote_unfilled",
>   "data": {
>     "quote_id": "0193abcd-0000-7000-8000-00000000000a",
>     "rfq_id": "0193abcd-0000-7000-8000-000000000001",
>     "pricer_trader_id": "0193abcd-0000-7000-8000-0000000000ff",
>     "reason": "taker_insolvent"
>   },
>   "timestamp": "2023-11-14T22:13:20.050Z"
> }
> ```
>
> **event** `string` *required*
>
> `"quote_unfilled"`.
>
> **timestamp** `ISO-8601 string` *required*
>
> When we emitted the frame.
>
> **data.quote\_id** `UUID string` *required*
>
> Our ID for your quote.
>
> **data.rfq\_id** `UUID string` *required*
>
> The round.
>
> **data.pricer\_trader\_id** `UUID string` *required*
>
> Your pricer.
>
> **data.reason** `string` *required*
>
> Why the trade didn't fill.
>
> | `reason` | Means |
> | - | - |
> | `taker_insolvent` | The taker's balance didn't cover the stake and fee |
> | `pricer_insolvent` | Your balance didn't cover the collateral and fee |
> | `both_insolvent` | Neither balance covered its side |

> **`quote_dropped`**
>
> We dropped your quote for a reason you can fix (quotes channel)
>
> We send this only when you can fix the cause. We don't tell you when you lose on price.
>
> ```json theme={"dark"}
> {
>   "event": "quote_dropped",
>   "data": {
>     "quote_id": "0193abcd-0000-7000-8000-00000000000a",
>     "rfq_id": "0193abcd-0000-7000-8000-000000000001",
>     "pricer_trader_id": "0193abcd-0000-7000-8000-0000000000ff",
>     "reason": "below_min_wager",
>     "min_wager": "15"
>   },
>   "timestamp": "2026-01-01T00:00:03.001Z"
> }
> ```
>
> **event** `string` *required*
>
> `"quote_dropped"`.
>
> **timestamp** `ISO-8601 string` *required*
>
> When we emitted the frame.
>
> **data.quote\_id** `UUID string` *required*
>
> Our ID for your quote.
>
> **data.rfq\_id** `UUID string` *required*
>
> The round.
>
> **data.pricer\_trader\_id** `UUID string` *required*
>
> Your pricer.
>
> **data.reason** `string` *required*
>
> `below_min_wager` or `unbacked`. Each reason adds one field with the number you must beat.
>
> | `reason` | Extra field | Means |
> | - | - | - |
> | `below_min_wager` | `min_wager` | Your `max_wager` is below the round's minimum stake |
> | `unbacked` | `required_collateral` | Your free balance doesn't cover the collateral |
>
> The round's minimum stake is the taker's minimum or ours, whichever is higher.

## &#x20;Acks

An ack confirms that we handled your message.

> **`subscribed`**
>
> The subscription is active
>
> Answers your `subscribe`.
>
> ```json theme={"dark"}
> {
>   "event": "subscribed",
>   "data": {
>     "channel": "rfq"
>   },
>   "timestamp": "2026-01-01T00:00:00.123Z"
> }
> ```
>
> **event** `string` *required*
>
> `"subscribed"`.
>
> **timestamp** `ISO-8601 string` *required*
>
> When we emitted the frame.
>
> **data.channel** `string` *required*
>
> `"rfq"` or `"quotes"`.

> **`unsubscribed`**
>
> The subscription is removed
>
> Answers your `unsubscribe`.
>
> ```json theme={"dark"}
> {
>   "event": "unsubscribed",
>   "data": {
>     "channel": "quotes"
>   },
>   "timestamp": "2026-01-01T00:00:00.123Z"
> }
> ```
>
> **event** `string` *required*
>
> `"unsubscribed"`.
>
> **timestamp** `ISO-8601 string` *required*
>
> When we emitted the frame.
>
> **data.channel** `string` *required*
>
> `"rfq"` or `"quotes"`.

> **`quote_submitted`**
>
> Your quote entered the auction
>
> Answers your `create_quote`.
>
> ```json theme={"dark"}
> {
>   "event": "quote_submitted",
>   "data": {
>     "rfq_id": "0193abcd-0000-7000-8000-000000000001"
>   },
>   "timestamp": "2026-01-01T00:00:01.500Z"
> }
> ```
>
> **event** `string` *required*
>
> `"quote_submitted"`.
>
> **timestamp** `ISO-8601 string` *required*
>
> When we emitted the frame.
>
> **data.rfq\_id** `UUID string` *required*
>
> The round your message named.

> **`quote_superseded`**
>
> Your first quote stands. We dropped this one
>
> Answers your `create_quote`.
>
> ```json theme={"dark"}
> {
>   "event": "quote_superseded",
>   "data": {
>     "rfq_id": "0193abcd-0000-7000-8000-000000000001"
>   },
>   "timestamp": "2026-01-01T00:00:01.800Z"
> }
> ```
>
> **event** `string` *required*
>
> `"quote_superseded"`.
>
> **timestamp** `ISO-8601 string` *required*
>
> When we emitted the frame.
>
> **data.rfq\_id** `UUID string` *required*
>
> The round your message named.

> **`quote_declined`**
>
> We recorded your pass
>
> Answers your `decline`.
>
> ```json theme={"dark"}
> {
>   "event": "quote_declined",
>   "data": {
>     "rfq_id": "0193abcd-0000-7000-8000-000000000001"
>   },
>   "timestamp": "2026-01-01T00:00:01.500Z"
> }
> ```
>
> **event** `string` *required*
>
> `"quote_declined"`.
>
> **timestamp** `ISO-8601 string` *required*
>
> When we emitted the frame.
>
> **data.rfq\_id** `UUID string` *required*
>
> The round your message named.

> **`confirm_recorded`**
>
> We recorded your last-look answer
>
> Answers your `confirm`.
>
> ```json theme={"dark"}
> {
>   "event": "confirm_recorded",
>   "data": {
>     "rfq_id": "0193abcd-0000-7000-8000-000000000001",
>     "quote_id": "0193abcd-0000-7000-8000-00000000000a"
>   },
>   "timestamp": "2026-01-01T00:00:05.400Z"
> }
> ```
>
> **event** `string` *required*
>
> `"confirm_recorded"`.
>
> **timestamp** `ISO-8601 string` *required*
>
> When we emitted the frame.
>
> **data.rfq\_id** `UUID string` *required*
>
> The round your message named.
>
> **data.quote\_id** `UUID string` *required*
>
> The quote your answer decided.

## Errors

We answer a message we can't handle with an `error` frame.

> **`error`**
>
> Your message failed
>
> ```json theme={"dark"}
> {
>   "event": "error",
>   "data": {
>     "code": "RFQ_CLOSED",
>     "message": "The quote round has already closed.",
>     "rfq_id": "0193abcd-0000-7000-8000-000000000001"
>   },
>   "timestamp": "2026-01-01T00:00:03.100Z"
> }
> ```
>
> **event** `string` *required*
>
> `"error"`.
>
> **timestamp** `ISO-8601 string` *required*
>
> When we emitted the frame.
>
> **data.code** `string` *required*
>
> A stable code. Match on this, not on `message`.
>
> **data.message** `string` *required*
>
> A human-readable description.
>
> **data.channel** `string`
>
> Reserved for the channel a failed message named. We don't send it today.
>
> **data.rfq\_id** `UUID string`
>
> The round the failed message named. We leave it out when we can't parse your message.

| Code | Cause |
| - | - |
| `MALFORMED_REQUEST` | Bad JSON, an unknown `event`, a wrong `data` shape, or a binary frame |
| `INVALID_QUOTE` | A quote term is out of range. `message` names the term |
| `RFQ_CLOSED` | The auction already closed, so we dropped the quote |
| `RFQ_UNKNOWN` | We hold no round for that `rfq_id`, so we dropped the quote. Either the round never opened, or it closed more than a minute ago. A quote that arrives within a minute of the close gets `RFQ_CLOSED` instead |
| `FORBIDDEN` | You have no active pricer registration, or you're restricted or [locked out](/api-reference/rfq/confirm-rate.md) |
| `RATE_LIMITED` | Too many actions. Retry later |
| `NO_CONFIRM_WINDOW` | No last look is open for that quote, so we dropped the answer |
| `CONFIRM_ALREADY_DECIDED` | The last look already has an answer, so we dropped this one |
| `INTERNAL` | We failed to process the message |

An error never closes the connection. Handle it and keep trading. Don't reconnect because of an error.

We check that you may quote on every action, not only when you connect.
If we remove or restrict your pricer, you keep the connection. Your next `create_quote`, `decline`, or `confirm` gets `FORBIDDEN`.

## Timing and limits

| Limit | Value | When you pass it |
| - | - | - |
| Auction | 3 seconds | `RFQ_CLOSED`, and we drop the quote |
| Last look | 1 second | We reject the trade |
| Server ping | Every 15 s | A missed Pong closes the connection |
| Actions per trader | 6000 per 60 seconds | `RATE_LIMITED` |
| Outbound queue | 256 frames per connection | We drop event frames |
| `price` | 0.001 to 0.999, after rounding | `INVALID_QUOTE` |
| `max_wager` | Above zero | `INVALID_QUOTE` |
| `quote_id` | 256 bytes | `INVALID_QUOTE` |

Actions are `create_quote` and `decline`. `confirm` is exempt from the rate limit.

We drop only event frames when your queue is full. We never drop acks or errors.

## Keep the connection alive

We send a WebSocket Ping every 15 s. Answer with a Pong. Most clients do this for you.

If no Pong arrives before our next Ping, we close the connection.

There's no JSON ping message and no idle timeout. A connection that answers every Ping stays open.

## Reconnect

> [!WARNING]
>
> Subscribe again after every reconnect. Subscriptions belong to the connection, so a new one gets no events until you subscribe.

We don't replay or backfill. A new connection starts at the live edge, so you lose anything we sent while you were away.
Get missed executions from the [executions listing](/api-reference/rfq/executions.md).

We close a connection without a status code, so don't read meaning into the close frame. Reconnect with backoff, then subscribe again.

If you read too slowly, we drop event frames instead of waiting for you. Read promptly, or you'll miss quote rounds.

## Use webhooks and the socket together

A webhook and the socket are independent. You can register a `webhookUrl`, connect the socket, or do both.

| Setup | Rounds arrive on | Confirms arrive on |
| - | - | - |
| Webhook only | `{webhookUrl}/quote` | `{webhookUrl}/confirm` |
| Socket only | The socket | The socket |
| Both | Both | The leg that carried the winning quote |

With both, each round reaches you twice. Your first quote wins, and we drop the other.
If the dropped quote came over the socket, we ack it with `quote_superseded`.

A round your webhook won is confirmed over HTTP. A round your socket won arrives as `quote_accepted`.
Handle confirms on both legs, or run only one.

To make the socket your only path, leave out `webhookUrl` on your first registration.
On an existing registration, leaving it out keeps your webhook. See [Registration](/api-reference/rfq/registration.md).


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.