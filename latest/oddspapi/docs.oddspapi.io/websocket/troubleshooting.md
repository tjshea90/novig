> ## Documentation Index
> Fetch the complete documentation index at: https://docs.oddspapi.io/llms.txt
> Use this file to discover all available pages before exploring further.

# WebSocket Troubleshooting - Common Issues & Solutions

> Debug OddsPapi WebSocket connection issues. Solutions for login errors, empty responses, binary decoding, backpressure disconnects, close codes, and resume failures.

## 1) Login Errors

### “first message must be login”

Send a valid `login` as your first frame.

### `login_failed`

* Missing/invalid `apiKey`
* No channels allowed or requested
* Bookmakers not allowed

### `too_many_connections` (4003)

* Max connections reached for your key group (max: 5)
* Shard or reduce concurrent connects
* Contact support to raise the limit

***

## 2) Connected but Receiving Nothing

* Not subscribed to expected channels? Check `login_ok.channels`
* Filters too narrow? e.g. empty `sportIds` or invalid `bookmakers`
* Bookmaker-gated filters:
  * If the upstream message has no matching bookmaker keys, it's filtered out

***

## 3) Binary Decoding Problems

With `receiveType: "binary"`:

* Data frames: MessagePack
* Control frames: JSON

You must decode both.

***

## 3b) zstd Decoding Problems

With `receiveType: "zstd"` or `"zstd-dict"` (see [Compression](/websocket/compression.md)):

* **Decompression fails / garbage output** — on `"zstd-dict"` you decompressed without the right
  dictionary. Read the `dictId` from each binary frame and use the matching dictionary; `dictId = 0`
  means dictless. On `"zstd"` every frame is dictless — decompress with no dictionary.
* **Frames look like plain JSON, not zstd** — the gateway downgraded you. Check
  `login_ok.receiveType`: if it's `"json"`, zstd is disabled on this gateway (it's rolling out) and
  you must decode text JSON.
* **Missing dictionary** (`"zstd-dict"` only) — `dict` control frames arrive right after `login_ok`,
  before data. Store them keyed by `dictId` before processing binary frames. They are re-sent on
  every connection, so there is no cache to prime.

***

## 4) `snapshot_required` During Resume

Means server could not safely replay. Possible reasons:

* `server_restarted`
* `resume_window_exceeded`
* `client_backpressure`

> Even short disconnects can exceed buffer if your `lastSeenId` is too old.

Recovery:

* Re-fetch snapshot via REST
* Reset your `lastSeenId`
* Continue streaming

***

## 5) Disconnects Under Load (Backpressure)

Symptoms:

* Close code `4002`
* Skipped `odds` updates

Fixes:

* Use `receiveType: "zstd"` (or `"zstd-dict"`) — smaller frames on the wire mean less backpressure
* Push parsing to async queue
* Filter by `sportIds`, `bookmakers`

***

## 6) Gaps in `entryId`

`entryId` is a cursor — not a delivery ledger. Gaps expected due to:

* Upstream skips
* Gateway coalescing
* Reconnect without replay

***

## 7) Server Asked You to Reconnect

You received a control frame:

```json theme={null}
{ "type": "reconnect", "reason": "server_upgrade" }
```

This is normal during a release or node rebalance — **not** an error.

* Reconnect immediately on this message; don't wait for the socket to close.
* Expect `snapshot_required` with reason `server_restarted` on the next resume (new replica = new
  `serverEpoch`). Re-fetch a REST snapshot and continue.
* See [Resume & Replay](/websocket/resume-replay.md#server-initiated-reconnect-on-release).

***

## 8) Close Codes Explained

Codes in the `4000` range are sent deliberately by the gateway and always carry a reason string.
Everything else comes from the transport or your own client library.

| Code | Sent by | Meaning |
| - | - | - |
| `4000` | gateway | Login problem: no `login` frame within 10 s, or first frame wasn't a valid login |
| `4001` | gateway | `api_key_revoked` — your key was disabled; reconnecting won't help, contact support |
| `4002` | gateway | Backpressure — your client isn't keeping up (see §5) |
| `4003` | gateway | Connection limit for your key group reached (see §1) |
| `1006` | your library | Connection dropped without a close handshake (network path died) |
| `1009` | your library | A frame exceeded your library's message-size limit |
| `1011` | network edge | Connection to the gateway was torn down mid-flight |

### `1011` / `1006` — transient infrastructure closes

The gateway never sends these. They surface when the connection is interrupted between you and the
gateway — most commonly during a **release or node restart**, right after the `reconnect` control
frame from §7 (expect them more often on staging, which deploys frequently).

* Treat both as transient: reconnect with a short backoff and resubscribe.
* Handle the §7 `reconnect` frame proactively and you'll usually be gone before the close arrives.
* Persistent 1011/1006 loops (every reconnect fails) are a different problem — check your network
  path, then contact support.

### `1009` — frame too big for *your* client

The gateway does not limit outbound frame size; uncompressed `odds` frames can exceed 1 MiB. Raise
your library's limit (e.g. `max_size` in python-websockets) or switch to `receiveType: "zstd"` /
`"zstd-dict"` — compressed frames are a fraction of the size.

***


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.