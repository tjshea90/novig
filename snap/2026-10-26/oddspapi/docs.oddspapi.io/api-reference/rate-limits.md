> ## Documentation Index
> Fetch the complete documentation index at: https://docs.oddspapi.io/llms.txt
> Use this file to discover all available pages before exploring further.

# API Rate Limits - Request Quotas & Throttling

> OddsPapi API rate limits and quotas. Request limits per endpoint, rate limit headers, and best practices for handling 429 responses.

Rate limits are counted **per `apiKey`** and by endpoint.

## Limits

### Odds endpoints (high-frequency)

These endpoints allow higher throughput:

* `GET /fixtures/odds`
* `GET /fixtures/odds/main`
* `GET /fixtures/odds/parlay`
* `GET /fixtures/odds/sgp`
* `GET /futures/odds`

**Limit:** **10 requests / second**

### All other endpoints

Everything else (metadata, fixtures list, mapping, settlements, historical, media redirects):

**Limit:** **100 requests / minute**

## Rate limit headers

Every authenticated response includes:

* `X-RateLimit-Limit`
* `X-RateLimit-Remaining`
* `X-RateLimit-Reset`

When limited (429), responses also include:

* `Retry-After`

Requests rejected for authentication (`401`) are turned away before the
limiter runs and carry no rate-limit headers.

## What happens when you exceed limits

You’ll receive:

* **429** with `code: "rate_limited"`

Example:

```json theme={null}
{
  "error": 429,
  "message": "rate limit exceeded",
  "code": "rate_limited",
  "retryAfterSec": 1
}
```


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.