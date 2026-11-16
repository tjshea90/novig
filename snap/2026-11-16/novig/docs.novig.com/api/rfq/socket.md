> ## Documentation Index
> Fetch the complete documentation index at: https://docs.novig.com/llms.txt
> Use this file to discover all available pages before exploring further.

# Socket

> Get RFQs and answer them over one signed connection.

Your maker prices RFQs over one websocket.
You keep one connection open. We push each RFQ to you, and you quote, decline, and confirm on the same connection.

[Overview](https://docs.novig.com/api/rfq/overview.md#how-a-round-works) walks through a round, from `rfq_created` to the last look.

## Connect

| Detail | Value |
| - | - |
| Route | `GET /rfq/v3/ws` |
| Key | `trading` or `trading::read` |
| Reply | 101 |

Sign the upgrade request like any route. [Connection](https://docs.novig.com/api/streaming/connection.md) shows how a signed upgrade works.

The key's subaccount must be a [maker](https://docs.novig.com/api/rfq/makers.md) to open the socket.
A `trading::read` key can connect and read. To quote, decline, or confirm, use a `trading` key.

Open no more than 10 sockets per key. Close one before you open another.

Two upgrade errors are specific to this socket.

| Status | `code` | Cause |
| - | - | - |
| 404 | `MAKER_NOT_FOUND` | The key's subaccount isn't a maker |
| 429 | `TOO_MANY_SOCKETS` | The key already has 10 sockets open |

## Channels

A channel is a stream of events you subscribe to. There are three.

| Event | Channel | We send it when |
| - | - | - |
| `rfq_created` | `rfq` | An RFQ opens |
| `rfq_closed` | `rfq` | An RFQ's auction closes |
| `quote_created` | `quotes` | We record your quote |
| `quote_accepted` | `quotes` | You win and must confirm |
| `quote_executed` | `quotes` | Your quote fills |
| `quote_unfilled` | `quotes` | Your confirmed quote won't fill |
| `quote_dropped` | `quotes` | We pass over your quote for a reason you can fix |
| `executed` | `executions` | Any RFQ trade on Novig fills |

We send `rfq` and `executions` events to every maker, and `quotes` events only to you.

> [!WARNING]
>
> Subscribe to `rfq` and `quotes` to trade. Without `quotes` you never see `quote_accepted`, so we reject every trade you win.

## Message format

Every message is a JSON text frame.
Each message you send must be 64 KiB or smaller.

A message you send has a `nonce` and one method, named as a key beside it.
The `nonce` is a whole number you choose, from 0 up. We echo it on our reply, so use a new one for each message.

We answer every message you send with one ack or one error.

Every event we send carries `type` and `ts`, when the event happened in Unix milliseconds.
`ts` isn't when we sent the frame, so a delayed event carries an older `ts`.
Branch on `type`, because we may add new ones.

We send decimal amounts like `price`, `maxWager`, and `wager` as JSON strings. Send yours as strings too.

## &#x20;Messages you send

> [accordion] `subscribe`
>
> Start getting channels' events
>
> We ack with `subscribed`.
>
> ```json theme={"dark"}
> {
>   "nonce": 1,
>   "subscribe": ["rfq", "quotes"]
> }
> ```
>
> **subscribe** `array of strings` *required*
>
> Any of `"rfq"`, `"quotes"`, and `"executions"`.

> [accordion] `unsubscribe`
>
> Stop getting channels' events
>
> We ack with `unsubscribed`.
>
> ```json theme={"dark"}
> {
>   "nonce": 2,
>   "unsubscribe": ["executions"]
> }
> ```
>
> **unsubscribe** `array of strings` *required*
>
> The channels to drop.

> [accordion] `quote`
>
> Quote an open RFQ
>
> We ack with `quoted`. Needs a `trading` key.
>
> ```json theme={"dark"}
> {
>   "nonce": 3,
>   "quote": {
>     "rfqId": "0193abcd-0000-7000-8000-000000000001",
>     "price": "0.45",
>     "maxWager": "250",
>     "clientId": "0193abcd-0000-7000-8000-0000000000c1"
>   }
> }
> ```
>
> **rfqId** `UUID string` *required*
>
> The RFQ, from `rfq_created`.
>
> **price** `decimal string` *required*
>
> The probability the parlay wins, from `0.001` to `0.999`. `"0.45"` means a \$0.45 stake per \$1.00 payout.
>
> **maxWager** `decimal string` *required*
>
> The largest stake this quote covers, in dollars. It must be above zero.
>
> **clientId** `UUID string`
>
> Your own ID for the quote. We return it on `quote_accepted`. Leave it out if you don't need one.
>
> You get one quote per RFQ, and your first quote stands.
> We ignore a second quote for the same `rfqId` and ack it with `superseded`.

> [accordion] `decline`
>
> Pass on an open RFQ
>
> We ack with `declined`. Needs a `trading` key.
>
> ```json theme={"dark"}
> {
>   "nonce": 4,
>   "decline": {
>     "rfqId": "0193abcd-0000-7000-8000-000000000001"
>   }
> }
> ```
>
> **rfqId** `UUID string` *required*
>
> The RFQ, from `rfq_created`.
>
> Declining is optional. An RFQ you don't answer closes on its own.

> [accordion] `confirm`
>
> Answer the last look on a quote you won
>
> We ack with `confirmed`. Needs a `trading` key.
>
> ```json theme={"dark"}
> {
>   "nonce": 5,
>   "confirm": {
>     "rfqId": "0193abcd-0000-7000-8000-000000000001",
>     "quoteId": "0193abcd-0000-7000-8000-00000000000a",
>     "confirmed": true
>   }
> }
> ```
>
> **rfqId** `UUID string` *required*
>
> The RFQ, from `quote_accepted`.
>
> **quoteId** `UUID string` *required*
>
> Our ID for your quote, from `quote_accepted`. It's not your `clientId`.
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
> Every rejected or missed last look counts against your [confirm rate](https://docs.novig.com/api/rfq/confirm-rate.md).

## &#x20;Events we send

You get a channel's events after you subscribe to it.

> [accordion] `rfq_created`
>
> An RFQ opened (rfq channel)
>
> Send your quote or decline before `expiresTs`.
>
> ```json theme={"dark"}
> {
>   "ts": 1762790400123,
>   "type": "rfq_created",
>   "rfqId": "0193abcd-0000-7000-8000-000000000001",
>   "outcomeIds": [
>     "11111111-1111-1111-1111-111111111111",
>     "22222222-2222-2222-2222-222222222222"
>   ],
>   "expiresTs": 1762790403123,
>   "minWager": "15"
> }
> ```
>
> **rfqId** `UUID string` *required*
>
> The RFQ. Send it on every message about this RFQ.
>
> **outcomeIds** `array of UUID strings` *required*
>
> The legs. See [Universe model](https://docs.novig.com/api/concepts/universe-model.md) to look each one up.
>
> **expiresTs** `integer` *required*
>
> When the auction closes, in Unix milliseconds. It's 3 seconds after the RFQ opened.
>
> **minWager** `decimal string` *required*
>
> The smallest stake a quote must cover: the user's minimum or \$10.00, whichever is higher. We pass over a quote whose `maxWager` is below it.

> [accordion] `rfq_closed`
>
> The auction closed (rfq channel)
>
> Stop pricing the RFQ. A `quote` that arrives after this gets `RFQ_CLOSED`.
>
> ```json theme={"dark"}
> {
>   "ts": 1762790403124,
>   "type": "rfq_closed",
>   "rfqId": "0193abcd-0000-7000-8000-000000000001"
> }
> ```
>
> **rfqId** `UUID string` *required*
>
> The RFQ that closed.

> [accordion] `quote_created`
>
> We recorded your quote (quotes channel)
>
> ```json theme={"dark"}
> {
>   "ts": 1762790400500,
>   "type": "quote_created",
>   "rfqId": "0193abcd-0000-7000-8000-000000000001",
>   "quoteId": "0193abcd-0000-7000-8000-00000000000a"
> }
> ```
>
> **quoteId** `UUID string` *required*
>
> Our ID for your quote. `quote_accepted` and `confirm` use it.

> [accordion] `quote_accepted`
>
> You won and must confirm (quotes channel)
>
> Send `confirm` within 1 second.
>
> ```json theme={"dark"}
> {
>   "ts": 1762790403300,
>   "type": "quote_accepted",
>   "rfqId": "0193abcd-0000-7000-8000-000000000001",
>   "quoteId": "0193abcd-0000-7000-8000-00000000000a",
>   "wager": "50",
>   "price": "0.45",
>   "clientId": "0193abcd-0000-7000-8000-0000000000c1"
> }
> ```
>
> **wager** `decimal string` *required*
>
> The user's actual stake.
>
> **price** `decimal string` *required*
>
> Your quoted price.
>
> **clientId** `UUID string`
>
> The `clientId` you sent on `quote`, or `null`.

> [accordion] `quote_executed`
>
> Your quote filled (quotes channel)
>
> ```json theme={"dark"}
> {
>   "ts": 1762790403500,
>   "type": "quote_executed",
>   "rfqId": "0193abcd-0000-7000-8000-000000000001",
>   "quoteId": "0193abcd-0000-7000-8000-00000000000a",
>   "wager": "50",
>   "price": "0.45",
>   "executedTs": 1762790403480
> }
> ```
>
> **executedTs** `integer` *required*
>
> When the trade filled, in Unix milliseconds.

> [accordion] `quote_unfilled`
>
> Your confirmed quote won't fill (quotes channel)
>
> ```json theme={"dark"}
> {
>   "ts": 1762790403500,
>   "type": "quote_unfilled",
>   "rfqId": "0193abcd-0000-7000-8000-000000000001",
>   "quoteId": "0193abcd-0000-7000-8000-00000000000a",
>   "reason": "maker_insolvent"
> }
> ```
>
> **reason** `string` *required*
>
> `taker_insolvent`: the user couldn't cover the stake. `maker_insolvent`: your subaccount couldn't cover the collateral. `both_insolvent`: neither could.

> [accordion] `quote_dropped`
>
> We passed over your quote (quotes channel)
>
> ```json theme={"dark"}
> {
>   "ts": 1762790400500,
>   "type": "quote_dropped",
>   "rfqId": "0193abcd-0000-7000-8000-000000000001",
>   "quoteId": "0193abcd-0000-7000-8000-00000000000a",
>   "reason": "below_min_wager",
>   "minWager": "15"
> }
> ```
>
> **reason** `string` *required*
>
> `below_min_wager`: `maxWager` was below `minWager`, which we include. `unbacked`: your balance couldn't cover `requiredCollateral`, which we include. `own_rfq`: the RFQ is your own. `declined_recently`: you rejected or missed a last look on these legs in the past 2 minutes.

> [accordion] `executed`
>
> Any RFQ trade filled (executions channel)
>
> ```json theme={"dark"}
> {
>   "ts": 1762790403480,
>   "type": "executed",
>   "offset": 12345,
>   "rfqId": "0193abcd-0000-7000-8000-000000000001",
>   "wager": "50",
>   "price": "0.45"
> }
> ```
>
> **ts** `integer` *required*
>
> When the trade filled, in Unix milliseconds. For your own fills, it matches `ts` on [`GET /rfq/v3/executions`](https://docs.novig.com/api-reference/rfq/executions/list-your-executions).
>
> **offset** `integer` *required*
>
> Goes up by one per execution. A jump, like `41` to `44`, means you missed some. [Executions](https://docs.novig.com/api/rfq/executions.md) shows how to fill a gap.
>
> **wager** `decimal string` *required*
>
> The user's stake.
>
> **price** `decimal string` *required*
>
> The execution price. It never says which maker won.

## &#x20;Acks

An ack confirms that we handled your message. It carries your `nonce` and one key that names what we did.

> [accordion] `subscribed` and `unsubscribed`
>
> Your channels changed
>
> ```json theme={"dark"}
> {
>   "nonce": 1,
>   "subscribed": ["quotes", "rfq"]
> }
> ```
>
> **subscribed** `array of strings`
>
> The channels you added. `unsubscribed` lists the ones you dropped.

> [accordion] `quoted`
>
> Your quote entered the auction
>
> ```json theme={"dark"}
> {
>   "nonce": 3,
>   "quoted": {
>     "rfqId": "0193abcd-0000-7000-8000-000000000001"
>   }
> }
> ```
>
> `quote_created` follows on `quotes` with your `quoteId`.

> [accordion] `superseded`
>
> You already quoted this RFQ
>
> ```json theme={"dark"}
> {
>   "nonce": 6,
>   "superseded": {
>     "rfqId": "0193abcd-0000-7000-8000-000000000001"
>   }
> }
> ```
>
> Your first quote stands, and we ignored this one.

> [accordion] `declined`
>
> We recorded your decline
>
> ```json theme={"dark"}
> {
>   "nonce": 4,
>   "declined": {
>     "rfqId": "0193abcd-0000-7000-8000-000000000001"
>   }
> }
> ```

> [accordion] `confirmed`
>
> We recorded your last-look answer
>
> ```json theme={"dark"}
> {
>   "nonce": 5,
>   "confirmed": {
>     "rfqId": "0193abcd-0000-7000-8000-000000000001",
>     "quoteId": "0193abcd-0000-7000-8000-00000000000a"
>   }
> }
> ```
>
> It means we recorded your answer, not that the trade filled. Watch for `quote_executed` or `quote_unfilled`.

## Errors

We answer a message we can't handle with an error.

> [accordion] error
>
> Your message failed
>
> ```json theme={"dark"}
> {
>   "code": "RFQ_CLOSED",
>   "message": "The RFQ already closed.",
>   "nonce": 3
> }
> ```
>
> **code** `string` *required*
>
> A stable code. Match on this, not on `message`.
>
> **message** `string` *required*
>
> A readable description. It can change.
>
> **nonce** `integer`
>
> The `nonce` of the message that failed. We leave it out when we can't parse your message.

| Code | Cause |
| - | - |
| `INVALID_REQUEST` | Bad JSON, an unknown method or field, or a binary frame |
| `READ_ONLY_KEY` | A `trading::read` key can't quote, decline, or confirm |
| `KYC_REQUIRED` | Your account's KYC or KYB is no longer verified |
| `SELF_EXCLUDED` | Your account has a self-exclusion |
| `MAKER_LOCKED` | You're locked out. `GET /rfq/v3/maker` shows when it ends |
| `MAKER_BARRED` | Your maker can't quote right now. Contact us |
| `RFQ_CLOSED` | The auction already closed, so we dropped the quote |
| `RFQ_NOT_FOUND` | No open RFQ has that `rfqId` |
| `INVALID_PRICE` | `price` isn't between `0.001` and `0.999` |
| `INVALID_MAX_WAGER` | `maxWager` isn't above zero |
| `NO_CONFIRM_WINDOW` | No last look is open for that quote, so we dropped the answer |
| `CONFIRM_ALREADY_DECIDED` | The last look already has an answer, so we dropped this one |
| `INTERNAL_ERROR` | We failed to process the message |

[Makers](https://docs.novig.com/api/rfq/makers.md#your-account-must-stay-eligible) explains `KYC_REQUIRED` and `SELF_EXCLUDED`.

An error never closes the connection. Handle it and keep trading. Don't reconnect because of an error.

## Timing and limits

| Limit | Value | When you pass it |
| - | - | - |
| Auction | 3 seconds | `RFQ_CLOSED`, and we drop the quote |
| Last look | 1 second | We reject the trade |
| Server ping | Every 15 s | A missed Pong closes the connection |
| Outbound queue | 256 frames per connection | We drop event frames |
| Sockets per key | 10 | The upgrade gets `TOO_MANY_SOCKETS` |
| Message you send | 64 KiB | We close the socket with `1009` |

We drop only event frames when your queue is full. We never drop acks or errors.

## Keep the connection alive

We send a websocket Ping every 15 s. Answer with a Pong. Most clients do this for you.

If no Pong arrives before our next Ping, we close the connection.

There's no JSON ping message and no idle timeout. A connection that answers every Ping stays open, unless a [check](#why-the-socket-closes) closes it.

## Why the socket closes

| Code | Reason | Do |
| - | - | - |
| `1008` | `KEY_REVOKED` | Reconnect with a valid key |
| `1008` | `KEY_EXPIRED` | Reconnect with a valid key |
| `1008` | A `451` geolocation `code` | Open the app, then reconnect |
| `1009` | `MESSAGE_TOO_BIG` | Send smaller messages, then reconnect |
| none | — | Answer every Ping |

We check each open socket again about every 15 minutes.
If its key was revoked or has expired, or your account's location is no longer allowed, we close it.

* A geolocation close carries the [`451` code](https://docs.novig.com/api/errors#451-geolocation-unavailable) as its reason, such as `GEOLOCATION_OUT_OF_BOUNDARY`.
* We check geolocation only for accounts that must share their location.
* A geolocation that's only out of date, `GEOLOCATION_EXPIRED`, doesn't close the socket.

## Reconnect

> [!WARNING]
>
> Subscribe again after every reconnect. Subscriptions belong to the connection, so a new one gets no events until you subscribe.

We don't replay events. A new connection starts at the live edge, so you lose anything we sent while you were away.
Get missed executions from [`GET /rfq/v3/executions`](https://docs.novig.com/api/rfq/executions).

Reconnect with backoff, then subscribe again.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.