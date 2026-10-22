> ## Documentation Index
> Fetch the complete documentation index at: https://docs.polymarket.com/llms.txt
> Use this file to discover all available pages before exploring further.

# Matching Engine Restarts

> Maintenance windows, restart handling, and post-restart post-only mode

The Polymarket matching engine periodically restarts for maintenance and upgrades. Prepare your order workflow to pause safely, honor server-provided delays, and resume under the temporary post-only rules that follow a restart.

***

## Announcements

Matching engine changes, including planned restarts, updates, and maintenance windows, are announced **before they happen** in these channels:

> **[Telegram](https://t.me/polytradingapis)**
>
> Join the Polymarket Trading APIs channel for real-time announcements.

> **[Discord](https://discord.com/channels/710897173927297116/1473553279421255803)**
>
> Join the #trading-api-announcements channel in the Polymarket Discord.

Announcements typically include **what's changing**, the **scheduled time**, and the **expected downtime window**. The goal is about two days' notice when possible.

***

## Handle Matching Engine Restarts

During a restart, order-related actions reject new work temporarily. After the engine returns, it enters post-only mode for two minutes: cancels remain available, but new orders must be eligible maker orders submitted as post-only.

Your integration controls whether and when to retry. Use a delay supplied by the service when one is available; otherwise, fall back to exponential backoff.

### Recommended Retry Strategy

**1. Detect the Restart**

Treat the restart signal as a temporary condition. Do not retry unrelated
request failures under the same policy.

**2. Wait Before Retrying**

Honor the server-provided delay when present. Otherwise, start with a
one-to-two-second delay and increase it after each failed attempt.

**3. Resume in Post-Only Mode**

Once restart rejections stop, continue canceling as needed and submit only
eligible maker orders as post-only for the next two minutes.

### Retry Order Placement

Retry an eligible post-only limit order only when the rejection identifies a matching engine restart:

**TypeScript**

Given a `SecureClient` and a selected `assetId`, catch `RequestRejectedError` and check its
`restriction` field. The SDK does not retry automatically.

Don't have the TypeScript SDK installed? Start with the [TypeScript SDK
guide](/getting-started/typescript.md), then come back.

```typescript TypeScript theme={null} theme={null}
import {
  OrderSide,
  RequestRejectedError,
  TradingRestriction,
} from "@polymarket/client";

async function placeWithRestartRetry(assetId: string) {
  const MAX_RETRIES = 10;
  let fallbackDelayMs = 1000;

  for (let attempt = 0; attempt < MAX_RETRIES; attempt++) {
    try {
      return await client.placeLimitOrder({
        assetId,
        side: OrderSide.BUY,
        price: "0.52",
        size: "10",
        postOnly: true,
      });
    } catch (error) {
      if (
        !(error instanceof RequestRejectedError) ||
        error.restriction !== TradingRestriction.RESTARTING
      ) {
        throw error;
      }

      const delayMs =
        error.retryAfter === undefined
          ? fallbackDelayMs
          : error.retryAfter * 1000;

      await new Promise((resolve) => setTimeout(resolve, delayMs));

      if (error.retryAfter === undefined) {
        fallbackDelayMs = Math.min(fallbackDelayMs * 2, 30000);
      }
    }
  }

  throw new Error("Engine restart exceeded maximum retry attempts");
}

const response = await placeWithRestartRetry(assetId);
// response: OrderResponse
```

**Python**

Given an `AsyncSecureClient` and a selected `asset_id`, catch `RequestRejectedError` and check its
`restriction` field. The synchronous `SecureClient` raises the same error,
and neither client retries automatically.

Don't have the Python SDK installed? Start with the [Python SDK
guide](/getting-started/python.md), then come back.

```python Python theme={null} theme={null}
import asyncio

from polymarket import RequestRejectedError


async def place_with_restart_retry(asset_id: str):
    max_retries = 10
    fallback_delay = 1

    for _ in range(max_retries):
        try:
            return await client.place_limit_order(
                asset_id=asset_id,
                side="BUY",
                price="0.52",
                size="10",
                post_only=True,
            )
        except RequestRejectedError as error:
            if error.restriction != "restarting":
                raise

            delay = (
                fallback_delay
                if error.retry_after is None
                else error.retry_after
            )
            await asyncio.sleep(delay)

            if error.retry_after is None:
                fallback_delay = min(fallback_delay * 2, 30)

    raise RuntimeError("Engine restart exceeded maximum retry attempts")


response = await place_with_restart_retry(asset_id)
# response: OrderResponse
```

**API**

An order-related request returns HTTP `425` while the matching engine is
restarting. Honor `Retry-After` when the response includes it; otherwise,
apply bounded exponential backoff before resubmitting the signed request.

```http Response theme={null} theme={null}
HTTP/1.1 425 Too Early
Retry-After: 1
```

***

## Handle Restricted Trading Modes

Restricted modes change which new orders are accepted. Cancels remain available unless trading is fully disabled. A cancel-only restriction blocks all new orders; a post-only restriction allows eligible maker orders submitted as post-only.

### Handle Request-Level Restrictions

Pause new submissions when trading is unavailable. Switch to eligible post-only orders only when the rejection identifies post-only mode:

**TypeScript**

Given a `SecureClient` and a selected `assetId`, handle rejections from `placeLimitOrder()`:

```typescript TypeScript theme={null} theme={null}
import {
  OrderSide,
  RequestRejectedError,
  TradingRestriction,
} from "@polymarket/client";

try {
  const response = await client.placeLimitOrder({
    assetId,
    side: OrderSide.BUY,
    price: "0.52",
    size: "10",
  });
  // response: OrderResponse
} catch (error) {
  if (!(error instanceof RequestRejectedError)) {
    throw error;
  }

  if (error.restriction === TradingRestriction.POST_ONLY) {
    if (error.retryAfter !== undefined) {
      const delayMs = error.retryAfter * 1000;
      await new Promise((resolve) => setTimeout(resolve, delayMs));
    }
    // Submit only eligible maker orders with postOnly: true.
  } else {
    throw error;
  }
}
```

`restriction` identifies post-only rejections, and `retryAfter` contains
the server-provided delay in seconds when supplied. An unclassified HTTP
`503` does not distinguish cancel-only mode from fully disabled trading.
Pause new submissions and propagate the error; do not infer that cancels
are available from this response alone.

**Python**

Given an `AsyncSecureClient` and a selected `asset_id`, handle rejections from
`place_limit_order()`. The synchronous `SecureClient` raises the same error.

```python Python theme={null} theme={null}
import asyncio

from polymarket import RequestRejectedError

try:
    response = await client.place_limit_order(
        asset_id=asset_id,
        side="BUY",
        price="0.52",
        size="10",
    )
    # response: OrderResponse
except RequestRejectedError as error:
    if error.restriction == "post_only":
        if error.retry_after is not None:
            await asyncio.sleep(error.retry_after)
        # Submit only eligible maker orders with post_only=True.
    else:
        raise
```

`restriction` identifies post-only rejections, and `retry_after` contains
the server-provided delay in seconds when supplied. An unclassified HTTP
`503` does not distinguish cancel-only mode from fully disabled trading.
Pause new submissions and propagate the error; do not infer that cancels
are available from this response alone.

**API**

Inspect the response when an order submission is rejected:

```http Trading Unavailable Response theme={null}
HTTP/1.1 503 Service Unavailable
Content-Type: application/json

{
  "error": "trading is disabled"
}
```

```http Post-Only Response theme={null}
HTTP/1.1 503 Service Unavailable
Content-Type: application/json
Retry-After: 79

{
  "error": "post-only mode: only post-only orders and cancels are allowed",
  "code": "post_only_mode",
  "retry_after_seconds": 79
}
```

`POST /order` and `POST /orders` return the same trading-disabled response
in cancel-only and fully disabled modes. Pause new submissions; this
response does not establish whether cancels are available.

A non-post-only order submitted to `POST /order` during post-only mode
returns `code: "post_only_mode"`. Honor the delay in `Retry-After` or
`retry_after_seconds` before retrying. Batch behavior appears below.

### Handle Batch Post-Only Rejections

A batch can be accepted at the request level while individual non-post-only orders are rejected. Check every result before treating the batch as successful:

**TypeScript**

`postOrders()` returns an `OrderResponse` for each submitted order. Compare
rejected results with `OrderResponseErrorCode.POST_ONLY_MODE`.

```typescript TypeScript theme={null} theme={null}
import { OrderResponseErrorCode } from "@polymarket/client";

const responses = await client.postOrders(signedOrders);
const postOnlyRejections = responses.filter(
  (response) =>
    !response.ok && response.code === OrderResponseErrorCode.POST_ONLY_MODE,
);
```

**Python**

`post_orders()` returns an `OrderResponse` for each submitted order. The
`code` on a post-only rejection is `"post_only_mode"`.

```python Python theme={null} theme={null}
responses = await client.post_orders(signed_orders)
post_only_rejections = [
    response
    for response in responses
    if not response.ok and response.code == "post_only_mode"
]
```

**API**

`POST /orders` returns per-order errors in its successful response array:

```json Response theme={null} theme={null}
[
  {
    "errorMsg": "post-only mode: only post-only orders and cancels are allowed",
    "orderID": "",
    "takingAmount": "",
    "makingAmount": "",
    "status": "",
    "success": true
  },
  {
    "errorMsg": "post-only mode: only post-only orders and cancels are allowed",
    "orderID": "",
    "takingAmount": "",
    "makingAmount": "",
    "status": "",
    "success": true
  }
]
```

Do not retry the same non-post-only order unchanged. Pause new submissions in cancel-only mode, wait for an indicated delay when appropriate, or submit an eligible maker order as post-only.

***

## Best Practices

* **Subscribe to announcement channels** — prepare your order workflow before a planned restart.
* **Retry only restart rejections** — do not apply restart retry logic to unrelated failures.
* **Honor server delays** — use the supplied delay when present and bounded exponential backoff otherwise.
* **Adapt to restricted modes** — pause new orders in cancel-only mode and submit only eligible maker orders in post-only mode.
* **Log restriction changes** — correlate observed transitions with announced maintenance windows.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.