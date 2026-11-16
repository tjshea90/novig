> ## Documentation Index
> Fetch the complete documentation index at: https://docs.novig.com/llms.txt
> Use this file to discover all available pages before exploring further.

# Parlay Pricing

> Show Novig's price for a parlay inside your UI

## Overview

You can show your users what a parlay would pay on Novig before they click through.
You send the outcome IDs of the legs, and we answer with one price for the whole parlay.

It's a price a user could get on Novig right now.
You don't need a key, and you don't sign the request.

Every example reads the host from `NOVIG_HOST`. On Production that is `https://api.novig.com`.

```bash theme={"dark"}
export NOVIG_HOST=https://api.novig.com
```

## Get a price

`POST https://api.novig.com/rfq/v2/quote/unauthenticated`

Send `outcomeIds`, a list of 2 to 20 outcome IDs, one per leg.

> [tab] Request
>
> ```bash theme={"dark"}
> curl -s -X POST "$NOVIG_HOST/rfq/v2/quote/unauthenticated" \
>   -H "Content-Type: application/json" \
>   -d '{
>     "outcomeIds": [
>       "abc123-outcome-uuid",
>       "xyz789-outcome-uuid"
>     ]
>   }'
> ```

> [tab] 200
>
> ```json theme={"dark"}
> {
>   "price": "0.215",
>   "expiresAt": 1793500000000,
>   "serverNow": 1793499970000
> }
> ```

| Field | Meaning |
| - | - |
| `price` | The parlay's implied probability, as a decimal string |
| `expiresAt` | When the price expires, in Unix milliseconds |
| `serverNow` | Our clock when we answered, in Unix milliseconds |

Reuse the price until `expiresAt` instead of asking again.
If your clock drifts, measure the time left as `expiresAt` minus `serverNow`.

The first request for a new set of legs can take a few seconds.
Repeat requests for the same legs answer right away until the price expires.

## Get outcome IDs

Each leg is an outcome ID from our catalog.

* [Odds Screens](https://docs.novig.com/affiliates/odds-screens.md) shows how to list a league's markets and their outcome IDs.
* [Deeplinking](https://docs.novig.com/affiliates/deeplinking.md) shows the outcome IDs inside a Novig link.

## Errors

| Status | Meaning |
| - | - |
| `400` | We can't combine these legs |
| `404` | We have no price for these legs right now |
| `429` | You're over the rate limit |

A `400` body names the reason, for example a repeated leg, an unknown outcome ID, or a closed event.
Fix the request before you send it again.

A `404` isn't a failure on your side.
Hide the Novig price for that parlay, and try again later.

## Rate limits

Each IP address can send a burst of 10 requests, then 1 request per second.
Unused capacity builds back up to 10.

Over the limit, you get `429` with a `Retry-After` header.
Wait that many seconds before you send another request.

A repeat request for the same legs counts against your limit, even when we answer it from our cache.
Reuse each price until `expiresAt` to keep your request count low.

If your traffic needs a higher limit, contact us.

## Best practices

* **Label the source.** When you show Novig next to other prices, say which one is ours.
* **Hide what we can't price.** Treat a `404` as "no price right now", not as an error to show your users.
* **Respect `expiresAt`.** A price past its expiry may no longer be available on Novig.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.