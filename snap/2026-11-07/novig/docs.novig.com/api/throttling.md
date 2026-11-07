> ## Documentation Index
> Fetch the complete documentation index at: https://docs.novig.com/llms.txt
> Use this file to discover all available pages before exploring further.

# Throttling

> How we limit your request rate, and how to pace your requests.

We limit how fast you can send requests, in two places.
Our servers throttle each subaccount and each management key, and an edge filter in front of them limits each IP address.

## Limits on our servers

Each throttle is a bucket of tokens.
A request spends tokens from its bucket.
The bucket holds up to its capacity, which is your burst, and refills at its refill rate, in tokens per second.
These are the limits at the Base [tier](#tiers).

Throttle
Burst
Capacity
Refill

`place`

{PLACE_CAPACITY.toLocaleString("en-US")}

8 Hz

`cancel`

{CANCEL_CAPACITY.toLocaleString("en-US")}

16 Hz

`read`

{READ_CAPACITY.toLocaleString("en-US")}

16 Hz

`account`

{ACCOUNT_CAPACITY.toLocaleString("en-US")}

8 Hz

`stream`

{STREAM_CAPACITY.toLocaleString("en-US")}

32 Hz

`history`

{HISTORY_CAPACITY.toLocaleString("en-US")}

4 Hz

The `history` bucket meters the record reads: fills, transactions, settled orders, and the history routes. Each such read costs a base plus one token per page of rows, so a larger page spends more. `GET /v3/limits` reports your numbers for every bucket.

`GET /v3/limits`, `GET /v3/time`, and `GET /v3/exchange/status` spend no tokens.

### Keys that share throttles

Each subaccount has one set of throttles, and every key on it spends from that set.
A management key has a set of its own.

| Key | Spends the throttles of |
| - | - |
| `trading` | Its subaccount |
| `trading::read` | Its subaccount |
| `management` | The key |
| `management::read` | The key |

A second `trading::read` key on a subaccount doesn't raise its limits.

### Tiers

Your account has one tier, and every subaccount you hold runs at it.
A higher tier raises your limits: it multiplies both the capacity and the refill rate of every throttle.
Each subaccount keeps its own throttles, so each one gets the full limits of your tier.
A tier also sets how many orders each subaccount may keep open at once.
An order that would take you over that cap answers `OPEN_ORDER_LIMIT_EXCEEDED`.
It also sets how many markets each websocket connection may watch. See [Websocket subscriptions](#websocket-subscriptions).

<table>
  <thead>
    <tr><th>Tier</th><th>Monthly volume</th><th>Multiplier</th><th>Open orders</th><th>Watched markets</th></tr>
  </thead>

  <tbody>
    {RATE_LIMIT_TIERS.map((tier) => <tr key={tier.name}><td>{tier.name}</td><td>${tier.monthlyVolume.toLocaleString("en-US")}</td><td>{tier.multiplier}×</td><td>{tier.maxOpenOrders.toLocaleString("en-US")}</td><td>{tier.maxWatchedMarkets.toLocaleString("en-US")}</td></tr>)}
  </tbody>
</table>

Your account earns its tier from the volume of all your subaccounts over the last calendar month, in Eastern time.
Early each month, we move your account to the tier that volume reaches, so a tier can go down as well as up.

Volume is the full payout of every contract your subaccounts traded, so 100 contracts count as \$1.
A wash, where your order trades against another of yours, doesn't count, and neither does a voided trade.

A new subaccount runs at your account's tier from its first request.
A management key is always at Base.

`GET /v3/limits` reports your account's tier as `accountTier`, whichever key you call it with.
It reports the limits of the key you call it with, and their tier as `tier`.
With a trading key, `tier` is your account's tier. With a management key, it's `BASE`.
`accountMaxOpenOrders` and `accountMaxWatchedMarkets` report the caps of your account's tier, whichever key you call it with.

## Websocket subscriptions

The `stream` bucket meters how fast you subscribe. A second limit caps how much you hold: a connection may watch up to  markets at once at Base, and more at a higher tier. An event counts as the markets it contains. A subscribe that would take you over that answers `SUBSCRIPTION_LIMIT_EXCEEDED`, and it subscribes to nothing. A `snapshot` that names more markets than the cap answers the same. Such a request also costs more than the `stream` bucket holds, so you get `RATE_LIMIT_EXCEEDED` until the bucket is full. `GET /v3/limits` reports your cap as `maxWatchedMarkets`.

The key routes and the subaccount routes spend the `account` bucket, one token a request. A transfer spends from it too.

## Limits at the edge

The edge is a filter in front of our servers.
It checks every request on every route before the request reaches them.

| Check | Limit | Counted per |
| - | - | - |
| Request rate | {EDGE_REQUESTS_PER_MINUTE.toLocaleString("en-US")} a minute | IP address |
| Body size | 64 KiB | Request |

> [!WARNING]
>
> The edge refuses a request with a `403` and an HTML body, with no `code` and no `message`. The request never reaches our servers.

## Pace your requests

A throttle refills over time, up to its capacity:

$$
\text{tokens}(t + \Delta t) = \min\bigl(C,\; \text{tokens}(t) + r\,\Delta t\bigr)
$$

Here $C$ is the capacity, $r$ is the refill rate, and $\Delta t$ is the time that has passed.

1. Model each throttle in your client.
2. When you send a request, subtract its cost from your model.
3. While your model holds too few tokens, queue the request instead of sending it.

## Error response

When you go over a limit, we answer `429` with a `Retry-After` header:

```http theme={"dark"}
HTTP/1.1 429 Too Many Requests
Retry-After: 3

{
  "code": "RATE_LIMIT_EXCEEDED",
  "message": "Rate limit exceeded. Please wait before retrying."
}
```

* Wait `Retry-After` seconds before you retry. Retrying sooner hits the same empty throttle.
* Don't create keys to raise a limit. Every key on a subaccount shares its throttles.
* Handle a `429` even if you model the throttles. Our servers share token counts with each other gradually, so your model is never exact.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.