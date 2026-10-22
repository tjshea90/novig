> ## Documentation Index
> Fetch the complete documentation index at: https://docs.oddspapi.io/llms.txt
> Use this file to discover all available pages before exploring further.

# WebSocket API Overview - Realtime Sports Data Streaming

> Connect to OddsPapi WebSocket gateway for low-latency realtime sports betting data. Stream live odds, scores, fixtures, and events with JSON or MessagePack encoding.

## Endpoint

Production gateway:

```
wss://v5.oddspapi.io/ws
```

***

## Recommended Integration Flow (Production)

Connect the WebSocket **before** fetching the snapshot — the overlap is what closes the gap between the two:

1. Connect to the WebSocket and send `login` with filters
2. After `login_ok`, start applying updates to your store, keyed per row (e.g. `oddsId`)
3. Fetch a snapshot via REST (e.g. `/fixtures`, `/fixtures/odds`) and merge it: per key, keep whichever side has the newer `changedAt`
4. Persist `serverEpoch` and per-channel `lastSeenId` (from `entryId`)
5. On reconnect, send `serverEpoch` + `lastSeenId` to resume
6. If you receive `snapshot_required`, re-fetch the snapshot via REST — same merge rule

Ordered this way there is no window to bridge: anything that changes while the snapshot request is in flight is already arriving on the socket, and anything older is in the snapshot. There is no REST-side cursor to hand to the WebSocket — none is needed, because updates are full latest-state per row and both sides carry `changedAt`. Cursors (`serverEpoch` + `entryId`) come from the stream itself and are only for resuming it.

***

## Login Mode

WebSocket supports **login-only subscriptions**. To change filters or channels, reconnect with a new `login`.

***

## Message Envelope

All updates share a common envelope:

```json theme={null}
{
  "channel": "scores",
  "type": "UPDATE",
  "payload": { "...": "..." },
  "ts": 1765497902846,
  "entryId": "1765497902846-3221"
}
```

* `channel` – stream name (e.g. `odds`, `fixtures`)
* `type` – message type (currently always `UPDATE`)
* `payload` – channel-specific data
* `ts` – UTC timestamp (milliseconds)
* `entryId` – cursor for replay/resume

> `entryId` is not guaranteed to be contiguous. See [Resume & Replay](/websocket/resume-replay.md) for full explanation.

***

## Encoding: JSON, Binary, or zstd

Control at login using `receiveType`.

* `"json"` (default) — all messages arrive as UTF-8 JSON
* `"binary"` — data frames use MessagePack; control frames remain JSON
* `"zstd"` — dictless zstd-compressed JSON (\~5–6× smaller on `odds`); decode in one line, no dictionary handling. Control frames remain JSON.
* `"zstd-dict"` — zstd with trained per-channel dictionaries (\~7–9× on `odds`); the server pushes the dictionaries at connect. Self-describing per-frame, no per-channel logic. See [Compression](/websocket/compression.md).

**Tip for clients:**

```js theme={null}
import msgpack from "@msgpack/msgpack";

const obj = typeof raw === "string"
  ? JSON.parse(raw)
  : msgpack.decode(new Uint8Array(raw));
```

> Control messages like `login_ok`, `snapshot_required`, and `resume_complete` are always JSON, even in binary mode.

***

## Channel Types

* **Fixture-scoped**: `fixtures`, `scores`, `odds`, `bookmakers`. — payloads include `fixtureId`
* **Future-scoped**: `futures`, `bookmakersFutures`, `oddsFutures` — include `futureId`
* **Global**: `currencies` — no ID

> See `/websocket/channels/*` for per-channel schemas.

***

## Throughput Guidance

* Prefer `receiveType: "zstd"` (or `"zstd-dict"` for the best ratio) on high-volume channels like `odds` — far smaller frames than `binary`
* Use filters (`sportIds`, `bookmakers`) to reduce noise
* `odds` should be treated as **latest state**, not a tick ledger

> Need full price movement or closing line value? Use the REST [Historical Odds & CLV](/api-reference/concepts.md#historical-odds-and-clv) endpoints.

***

## WebSocket Limits

| Limit Type | Description |
| - | - |
| Concurrent connections | Enforced per `apiKey` group (max: 5). See error `4003`. |
| Backpressure | If your client can’t keep up, connection is closed (`4002`). |
| Replay window | See `resumeWindowMs` in [Resume & Replay](/websocket/resume-replay.md) |
| Message rate | Not explicitly limited, but filters are recommended for performance |

***

## 💬 Ask an AI Assistant

Want to explore or ask questions about this page using your favorite AI?

Click one of the links below — each one opens this page in the selected tool with a pre-filled prompt:

* [Ask ChatGPT](https://chatgpt.com/?prompt=Read+from+https%3A%2F%2Fdocs.oddspapi.io%2Fllms-full.txt+and+help+me+with+this+API.)
* [Ask Claude](https://claude.ai/?prompt=Please+read+https%3A%2F%2Fdocs.oddspapi.io%2Fllms-full.txt+and+help+me+use+this+API.)
* [Ask Perplexity](https://www.perplexity.ai/search?q=Read+from+https%3A%2F%2Fdocs.oddspapi.io%2Fllms-full.txt)
* [Ask Gemini](https://gemini.google.com/app?query=Read+from+https%3A%2F%2Fdocs.oddspapi.io%2Fllms-full.txt+and+help+me+use+this+API.)


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.