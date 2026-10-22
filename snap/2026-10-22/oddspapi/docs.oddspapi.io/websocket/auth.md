> ## Documentation Index
> Fetch the complete documentation index at: https://docs.oddspapi.io/llms.txt
> Use this file to discover all available pages before exploring further.

# WebSocket Authentication & Channel Filters

> Authenticate to OddsPapi WebSocket gateway and configure channel subscriptions. Filter by sports, tournaments, fixtures, and bookmakers for targeted data streaming.

## Login Message

Connect and send a `login` message immediately:

```json theme={null}
{
  "type": "login",
  "apiKey": "YOUR_API_KEY"
}
```

**Rules:**

* Must be the first message
* Send within 10 seconds
* Defines all subscriptions and filters

***

## Login with Channels and Filters

```json theme={null}
{
  "type": "login",
  "apiKey": "YOUR_API_KEY",
  "receiveType": "binary",
  "channels": ["fixtures", "scores", "odds"],
  "sportIds": [10, 11, 12, 13],
  "tournamentIds": [35430, 39351],
  "fixtureIds": ["id1103543066138356", "id1103935163991375"],
  "bookmakers": ["stake"]
}
```

***

## Filter Mode

| Field | Type | Description |
| - | - | - |
| `channels` | `string[]` | Streams you want to receive. |
| `sportIds` | `number[]` | Restrict to these sports. |
| `tournamentIds` | `number[]` | Restrict to these tournaments. |
| `fixtureIds` | `string[]` | Exact fixtures (fixture-scoped channels only). |
| `futureIds` | `string[]` | Specific futures (future-scoped channels). |
| `bookmakers` | `string[]` | Only receive these bookmakers (bookmaker-gated). |
| `lang` | `string` | Translations (`en`, `de`, `fr`, etc.). |
| `receiveType` | `string` | `"json"` (default) \| `"binary"` (MessagePack) \| `"zstd"` (dictless compressed JSON) \| `"zstd-dict"` (compressed JSON with trained dictionaries). See [Compression](/websocket/compression.md). |
| `clientName` | `string` | Optional debug/metrics tag. |
| `serverEpoch` | `string` | For resume. |
| `lastSeenId` | `object` | `{ "<channel>": "<entryId>" }` for resume. |

> IDs like `fixtureId` and `futureId` are structured but should be treated as opaque in your logic. See [Concepts](/api-reference/concepts.md).

***

## Access: Live vs Pregame

After login, the server tells you what you're allowed to receive:

```json theme={null}
{
  "access": { "live": true, "pregame": false }
}
```

These are determined by your `apiKey`, not client filters.

***

## Bookmaker-Gated Channels

These channels require explicit bookmaker access:

* `odds`, `bookmakers`
* `oddsFutures`, `bookmakersFutures`

You can restrict which bookmakers you receive:

```json theme={null}
{
  "type": "login",
  "apiKey": "YOUR_API_KEY",
  "channels": ["odds", "bookmakers"],
  "receiveType": "binary",
  "bookmakers": ["stake", "pinnacle"]
}
```

***

## 🐍 Python Example

```python theme={null}
import asyncio, json, websockets, msgpack

WS_URL = "wss://v5.oddspapi.io/ws"
API_KEY = "your-api-key"

LOGIN = {
    "type": "login",
    "apiKey": API_KEY,
    "channels": ["fixtures", "scores", "odds"],
    "receiveType": "binary",
    "sportIds": [10, 11],
    "bookmakers": ["stake"],
}

async def main():
    async with websockets.connect(WS_URL) as ws:
        await ws.send(json.dumps(LOGIN))

        async for raw in ws:
            if isinstance(raw, str):
                print("CONTROL:", json.loads(raw))
            else:
                msg = msgpack.unpackb(raw, raw=False)
                print("DATA:", msg.get("channel"), msg.get("entryId"))

asyncio.run(main())
```


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.