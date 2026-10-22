> ## Documentation Index
> Fetch the complete documentation index at: https://docs.oddspapi.io/llms.txt
> Use this file to discover all available pages before exploring further.

# API Authentication - How to Authenticate Requests

> Learn how to authenticate OddsPapi API requests using your API key, as an X-API-Key header or an apiKey query parameter.

All HTTP endpoints require an API key.
You will only receive:

1. what you are requesting, filtered by
2. what you have access for

## How to authenticate

Pass your key either as the `X-API-Key` **header** or as the `apiKey` **query
parameter**. Both are accepted on every endpoint — pick one. Prefer the header:
query strings land in proxy, CDN and browser history logs. The query parameter
stays supported indefinitely.

**Header (recommended)**

```bash theme={null}
curl -H 'X-API-Key: YOUR_KEY' 'https://v5.oddspapi.io/en/bookmakers'
```

**Query parameter**

```bash theme={null}
curl 'https://v5.oddspapi.io/en/bookmakers?apiKey=YOUR_KEY'
```

> [!WARNING]
>
> **Send one, not both.** If the header and the query parameter are both
> present and carry *different* values, the request is rejected with `401`
> `invalid_api_key` — whichever of the two is valid. Sending the same key in
> both places is harmless.

## Errors

| Status | `code` | Cause |
| - | - | - |
| `401` | `missing_api_key` | neither the header nor the query parameter was sent |
| `401` | `invalid_api_key` | the key is unknown, expired or disabled |
| `403` | `channel_not_allowed` | valid key, but it is not entitled to that endpoint |

```json theme={null}
{ "error": 401, "message": "missing apiKey", "code": "missing_api_key" }
```


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.