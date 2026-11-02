> ## Documentation Index
> Fetch the complete documentation index at: https://docs.oddspapi.io/llms.txt
> Use this file to discover all available pages before exploring further.

# API Error Codes - HTTP Status Codes & Error Handling

> Complete guide to OddsPapi API error responses. HTTP status codes, error formats, rate limit headers, and troubleshooting common error causes.

## Error Format

Most error responses return a JSON object like:

```json theme={null}
{
  "error": 401,
  "message": "invalid apiKey",
  "code": "invalid_api_key"
}
```

| Field | Type | Description |
| - | - | - |
| `error` | integer | HTTP status code |
| `message` | string | Human-readable description |
| `code` | string | Machine-readable reason code |

***

## Rate Limit Headers

All endpoints protected by the rate limiter return these headers:

| Header | Description |
| - | - |
| `X-RateLimit-Limit` | Requests allowed per window (per apiKey) |
| `X-RateLimit-Remaining` | Requests left in the current window |
| `X-RateLimit-Reset` | Unix timestamp when the window resets |

If you hit the limit (`429`), you also receive:

| Header | Description |
| - | - |
| `Retry-After` | Seconds to wait before retrying |

***

## Common Error Responses

### 400 — Bad Request

```json theme={null}
{
  "error": 400,
  "message": "Invalid filters.",
  "code": "invalid_filters"
}
```

Used for syntactically valid requests with invalid parameters.

***

### 401 — Unauthorized

**Missing or invalid `apiKey`.**

```json theme={null}
{
  "error": 401,
  "message": "missing apiKey",
  "code": "missing_api_key"
}
```

```json theme={null}
{
  "error": 401,
  "message": "invalid apiKey",
  "code": "invalid_api_key"
}
```

***

### 403 — Forbidden

**Valid API key, but no access to this channel or feature.**

```json theme={null}
{
  "error": 403,
  "message": "Access denied: apiKey is not allowed to access this endpoint.",
  "code": "channel_not_allowed"
}
```

***

### 422 — Validation Error

Usually returned by FastAPI’s internal validation.

```json theme={null}
{
  "error": 422,
  "message": "Validation error",
  "code": "validation_error",
  "details": [
    {
      "loc": ["query", "fixtureId"],
      "msg": "Field required",
      "type": "missing"
    }
  ]
}
```

***

### 429 — Rate Limit Exceeded

Triggered when request volume exceeds limits per apiKey.

```json theme={null}
{
  "error": 429,
  "message": "rate limit exceeded",
  "code": "rate_limited",
  "limit": 30,
  "windowSec": 1,
  "retryAfterSec": 1,
  "endpoint": "/fixtures/odds",
  "method": "GET"
}
```

Headers:

* `Retry-After: 1`
* `X-RateLimit-Limit: 30`
* `X-RateLimit-Remaining: 0`
* `X-RateLimit-Reset: 1700000000`

***

### 503 — Service Unavailable

If internal systems (e.g. rate limiter) are unavailable:

```json theme={null}
{
  "error": 503,
  "message": "rate limiter unavailable",
  "code": "rate_limiter_error"
}
```

## Notes

* `code` is stable and suitable for programmatic handling.
* `message` may change (but is designed to help developers).
* Most endpoints return `429` if rate-limited — check your headers.
* FastAPI validation errors are always returned as `422`.

***


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.