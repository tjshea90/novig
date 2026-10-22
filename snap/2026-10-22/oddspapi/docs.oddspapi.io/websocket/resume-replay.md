> ## Documentation Index
> Fetch the complete documentation index at: https://docs.oddspapi.io/llms.txt
> Use this file to discover all available pages before exploring further.

# WebSocket Resume & Replay - Connection Recovery

> Handle WebSocket disconnections gracefully. Resume data streams without gaps using serverEpoch and entryId cursors. Automatic replay for missed updates.

## Why this is safe for trading

The replay window (`resumeWindowMs`, e.g. `60000`) is **not** a fragile time limit you risk losing data past — it's the buffer for **zero-loss fast resume**. Recovery is a two-tier, designed failover:

1. **Within the window** — reconnect and replay missed updates from your cursor with **no gaps**. This covers the overwhelming majority of real-world disconnects (network blips, redeploys), which resolve in seconds.
2. **Outside the window** — the gateway emits `snapshot_required` and you restore full, current state with a **single fast REST snapshot**. This is an explicit, deterministic path, not an error.

The window is intentionally short because for realtime trading the right recovery for a long gap is a **fresh snapshot of the current market**, not a slow replay of stale prices. Worst case is one REST call — bounded and predictable, never an open-ended outage. See [Reliability & Operations](/api-reference/reliability.md).

***

## What “resume” means

After a successful login, the server returns a `resume` block in `login_ok`:

```json theme={null}
{
  "resume": {
    "serverEpoch": "0804ab61513c4681a3afd8afc1fb2f75",
    "resumeWindowMs": 60000,
    "replayChannels": ["fixtures", "scores"],
    "serverEntryIds": {
      "fixtures": "1766414833582-2542",
      "scores": "1766418736962-198"
    }
  }
}
```

This tells you:

* `serverEpoch` — identifies the current gateway session (changes on restart)
* `resumeWindowMs` — how long replayable data is buffered
* `replayChannels` — channels eligible for replay
* `serverEntryIds` — server’s latest cursor per channel

***

## Server-initiated reconnect on release

When we deploy a new gateway version or rebalance a node, the server does **not** drop you
silently. It first pushes a control frame to every connected client:

```json theme={null}
{ "type": "reconnect", "reason": "server_upgrade" }
```

This is a **JSON text frame** (control), sent on all `receiveType` modes. After sending it the
server keeps streaming for a short grace window (a few seconds), then closes the socket.

**What your client should do:**

1. **React to the message, not the socket error.** As soon as you see `type: "reconnect"`,
   reconnect to the same endpoint. Don't wait for the eventual close (`1006`/`4002`) — acting on the
   hint minimizes your gap during a release.
2. The replacement replica has a **new `serverEpoch`**. So resuming with your stored `serverEpoch`
   will return `snapshot_required` with reason `server_restarted` (see below). This is expected on a
   release — re-fetch a REST snapshot for the affected channels and continue.

> Treat any `reconnect` frame as "reconnect now", regardless of `reason`. New reasons may be added
> over time; the action is always the same.

***

## What clients must persist

To resume safely, persist:

1. `serverEpoch`
2. The most recent `entryId` you processed **per channel**

```json theme={null}
{
  "scores": "1766418736962-198",
  "fixtures": "1766414833582-2542"
}
```

***

## entryId format

Each streamed message includes an `entryId`:

```
<ts_ms>-<seq>
```

* `ts_ms` — server timestamp (UTC, milliseconds)
* `seq` — monotonic per-channel sequence

Important:

* `entryId` is a **cursor**, not a delivery guarantee
* Gaps are normal (upstream behavior, coalescing, backpressure)

***

## Resume login example

On reconnect, send the same `serverEpoch` and your stored cursors:

```json theme={null}
{
  "type": "login",
  "apiKey": "YOUR_API_KEY",
  "channels": ["fixtures", "scores", "odds"],
  "serverEpoch": "0804ab61513c4681a3afd8afc1fb2f75",
  "lastSeenId": {
    "scores": "1766418736962-198"
  }
}
```

If replay succeeds, the server sends:

```json theme={null}
{
  "type": "resume_complete",
  "serverEpoch": "0804ab61513c4681a3afd8afc1fb2f75"
}
```

***

## snapshot\_required (when replay is not possible)

Sometimes replay cannot be done safely. In that case the server sends:

```json theme={null}
{
  "type": "snapshot_required",
  "reason": "resume_window_exceeded",
  "channels": ["scores"],
  "serverEpoch": "0804ab61513c4681a3afd8afc1fb2f75",
  "resumeWindowMs": 60000,
  "serverEntryIds": {
    "scores": "1766418738000-220"
  }
}
```

### Possible reasons

* `server_restarted` — gateway restarted, cursors invalid
* `resume_window_exceeded` — your cursor is older than the replay buffer
* `client_backpressure` — your client couldn’t consume replay fast enough

### Important nuance

Replay eligibility depends on **cursor age**, not disconnect duration:

```
(now_ms - last_seen_entry_ts_ms) > resumeWindowMs
⇒ snapshot_required is likely
```

A very short disconnect can still exceed the window if your last processed message is already old.

***

## How clients should handle snapshot\_required

When you receive `snapshot_required`:

1. Fetch a fresh snapshot via REST for the listed channels
   (e.g. `/fixtures`, `/fixtures/odds`, `/futures`)
2. Clear `lastSeenId` for those channels
3. Continue processing live updates

> The gateway continues streaming after `snapshot_required`.
> This message is your signal that **local state must be rebuilt**.

***

## Python reconnect example (FULL TEMPLATE)

This example:

* Persists `serverEpoch` and per-channel `lastSeenId`
* Sends cursors **only for replayable channels**
* Handles `snapshot_required`
* Automatically reconnects on failure

```python theme={null}
import asyncio
import json
import time
import websockets

WS_URL = "wss://v5.oddspapi.io/ws"
API_KEY = "YOUR_API_KEY"

server_epoch = None
replay_channels = None
last_seen = {}

async def run_once():
    global server_epoch, replay_channels, last_seen

    async with websockets.connect(
        WS_URL,
        ping_interval=20,
        ping_timeout=20,
        max_size=4194304
    ) as ws:
        login = {
            "type": "login",
            "apiKey": API_KEY,
            "channels": ["fixtures", "scores", "odds"],
            "receiveType": "json",
        }

        # Resume mode
        if server_epoch:
            login["serverEpoch"] = server_epoch

        # Only send cursors for replayable channels
        if replay_channels:
            cursors = {
                ch: eid
                for ch, eid in last_seen.items()
                if ch in replay_channels
            }
        else:
            cursors = dict(last_seen)

        if cursors:
            login["lastSeenId"] = cursors

        await ws.send(json.dumps(login))

        async for raw in ws:
            if isinstance(raw, (bytes, bytearray)):
                raw = raw.decode("utf-8", errors="replace")

            msg = json.loads(raw)
            msg_type = msg.get("type")

            if msg_type == "login_ok":
                resume = msg.get("resume") or {}
                server_epoch = resume.get("serverEpoch") or server_epoch

                rc = resume.get("replayChannels")
                if isinstance(rc, list):
                    replay_channels = set(map(str, rc))

                continue

            if msg_type == "reconnect":
                # Release/maintenance: reconnect now (new replica = new serverEpoch,
                # so the next resume will return snapshot_required: server_restarted).
                print("Server asked us to reconnect:", msg.get("reason"))
                return

            if msg_type == "snapshot_required":
                channels = msg.get("channels") or []
                for ch in channels:
                    last_seen.pop(ch, None)

                # Trigger REST snapshot refresh here
                print("Snapshot required for:", channels)
                continue

            if msg_type == "resume_complete":
                print("Resume complete")
                continue

            # Data message
            channel = msg.get("channel")
            entry_id = msg.get("entryId")

            if isinstance(channel, str) and isinstance(entry_id, str):
                last_seen[channel] = entry_id

async def main():
    while True:
        try:
            await run_once()
        except Exception as e:
            print("Disconnected:", e)
            await asyncio.sleep(1)

asyncio.run(main())
```


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.