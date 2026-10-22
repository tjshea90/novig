> ## Documentation Index
> Fetch the complete documentation index at: https://docs.oddspapi.io/llms.txt
> Use this file to discover all available pages before exploring further.

# WebSocket Compression (zstd)

> Cut WebSocket bandwidth with zstd. Two opt-in modes: receiveType: zstd (dictless, decode in one line) or receiveType: zstd-dict (trained per-channel dictionaries, best ratio).

> [!NOTE]
>
> **Beta — rolling out per region.** zstd is being enabled gradually. If a gateway has it
> disabled, a `receiveType: "zstd"` or `"zstd-dict"` connection is **gracefully downgraded to JSON** —
> the server tells you the negotiated mode in `login_ok.receiveType`. **Always trust that echo** to
> choose your decoder; never assume the mode you requested.

## Two modes

Both compress every data frame with zstd; control frames stay JSON text. Pick based on how much
client work you want to do:

| `receiveType` | Ratio on `odds` | Client work | `dict` frames |
| - | - | - | - |
| `"zstd"` | \~5–6× | **One line:** `zstd.decompress(frame)` → JSON | none |
| `"zstd-dict"` | \~7–9× | Cache the dictionaries the server pushes, decode each frame by its embedded `dictId` | sent at connect |

**Start with `"zstd"`.** It needs no dictionary handling at all — decompress and parse, done. Move to
`"zstd-dict"` when you want the extra \~30–40% and can keep a small in-memory dictionary store.

> There is intentionally **no** compressed-MessagePack mode — compressing JSON beats compressing
> MessagePack at every level.

***

## Mode 1 — `zstd` (dictless)

The soft-adoption path. Every data frame is a standalone, dictless zstd frame (`dictId = 0`); the
server sends **no** `dict` control frames. Your client only decompresses and parses.

### Login

```json theme={null}
{
  "type": "login",
  "apiKey": "YOUR_API_KEY",
  "channels": ["odds", "fixtures"],
  "receiveType": "zstd"
}
```

### Decode

```
on text frame   → JSON.parse → control (login_ok, error, …)
on binary frame → JSON.parse(zstd.decompress(frame))   // no dictionary needed
                  route by msg.channel
```

That's the whole protocol for this mode.

***

## Mode 2 — `zstd-dict` (trained dictionaries)

The maximum-ratio path. `odds`, `fixtures`, and `bookmakers` have trained \~32 KB dictionaries that
push the ratio to \~7–9×. The server delivers them as control frames at connect; each data frame
embeds the `dictId` it was compressed with, so decoding is **self-describing and never branches on
channel**.

### Login

```json theme={null}
{
  "type": "login",
  "apiKey": "YOUR_API_KEY",
  "channels": ["odds", "fixtures"],
  "receiveType": "zstd-dict"
}
```

### Dictionary delivery (server → client)

Right after `login_ok` (and before any data frame), for each subscribed channel that has a trained
dictionary the server pushes one control frame:

```json theme={null}
{
  "type": "dict",
  "channel": "odds",
  "dictVersion": "odds-v1",
  "dictId": 740826216,
  "encoding": "base64",
  "data": "<base64 of the ~32 KB zstd dictionary>"
}
```

1. Base64-decode `data` to the raw dictionary bytes.
2. Build a reusable zstd decoder from it, stored **keyed by `dictId`**.

> [!NOTE]
>
> Decoding is driven by the **`dictId` embedded in each data frame**, not by channel. The
> `channel`/`dictVersion` fields on the `dict` frame are informational.

The dictionaries are small (\~32 KB) and re-sent on **every** connection — there is no client-side
version cache to maintain and no `dicts` field to send at login. Channels without a trained
dictionary (e.g. `scores`, `clocks`) send no `dict` frame; their data frames carry `dictId = 0` and
decode dictless.

### Decode

```
on text frame   → JSON.parse → control (login_ok, dict, error, …)
on binary frame → id   = zstd.getDictID(frame)        // 0 ⇒ no dict
                  dict = store[id]                     // undefined ⇒ dictless
                  msg  = JSON.parse(zstd.decompress(frame, dict))
                  route by msg.channel
```

***

## Frame rules (both modes)

* Every data frame is a WebSocket **Binary** frame containing one standalone zstd frame
  (magic `28 B5 2F FD`, with an embedded `dictId`).
* Control frames (`login_ok`, `dict`, `error`, `snapshot_required`, `resume_complete`) stay **JSON
  text frames**, even on a zstd connection. The rule is fixed:

> **Text frame → control (JSON). Binary frame → data (zstd).**

`login_ok.receiveType` is the **negotiated** mode (`"zstd"`, `"zstd-dict"`, or — if the gateway has
zstd disabled — `"json"`). If it comes back `"json"`, decode plain JSON text frames; do **not** try
to decompress.

***

## 🐍 Python examples

Requires `pip install zstandard`.

**zstd (dictless)**

```python theme={null}
import asyncio, json, websockets
import zstandard as zstd

WS_URL, API_KEY = "wss://v5.oddspapi.io/ws", "YOUR_API_KEY"
dctx = zstd.ZstdDecompressor()
MAX_OUT = 64 * 1024 * 1024  # frames carry no content size — pass an upper bound

async def main():
    async with websockets.connect(WS_URL, max_size=4194304) as ws:
        await ws.send(json.dumps({
            "type": "login", "apiKey": API_KEY,
            "channels": ["odds", "fixtures"], "receiveType": "zstd",
        }))
        async for raw in ws:
            if isinstance(raw, str):                       # control (JSON)
                msg = json.loads(raw)
                if msg.get("type") == "login_ok" and msg.get("receiveType") != "zstd":
                    print("zstd disabled here, negotiated:", msg.get("receiveType"))
                continue
            data = json.loads(dctx.decompress(raw, max_output_size=MAX_OUT))
            print("DATA:", data.get("channel"), data.get("entryId"))

asyncio.run(main())
```

**zstd-dict (dictionaries)**

```python theme={null}
import asyncio, json, base64, websockets
import zstandard as zstd

WS_URL, API_KEY = "wss://v5.oddspapi.io/ws", "YOUR_API_KEY"
decoders: dict[int, zstd.ZstdDecompressor] = {}   # dictId -> decoder
dictless = zstd.ZstdDecompressor()
MAX_OUT = 64 * 1024 * 1024

def decode_frame(frame: bytes) -> dict:
    dict_id = zstd.get_frame_parameters(frame).dict_id  # 0 if no dictionary
    dctx = decoders.get(dict_id, dictless)
    return json.loads(dctx.decompress(frame, max_output_size=MAX_OUT))

async def main():
    async with websockets.connect(WS_URL, max_size=4194304) as ws:
        await ws.send(json.dumps({
            "type": "login", "apiKey": API_KEY,
            "channels": ["odds", "fixtures"], "receiveType": "zstd-dict",
        }))
        async for raw in ws:
            if isinstance(raw, str):                       # control (JSON)
                msg = json.loads(raw)
                t = msg.get("type")
                if t == "login_ok" and msg.get("receiveType") != "zstd-dict":
                    print("downgraded, negotiated:", msg.get("receiveType"))
                elif t == "dict":
                    d = zstd.ZstdCompressionDict(base64.b64decode(msg["data"]))
                    decoders[msg["dictId"]] = zstd.ZstdDecompressor(dict_data=d)
                continue
            data = decode_frame(raw)                        # data (zstd)
            print("DATA:", data.get("channel"), data.get("entryId"))

asyncio.run(main())
```

***

## See also

* [Auth & Filters](/websocket/auth.md) — full `login` field reference
* [Resume & Replay](/websocket/resume-replay.md) — reconnecting and recovering missed data
* [Troubleshooting](/websocket/troubleshooting.md) — zstd decoding issues


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.