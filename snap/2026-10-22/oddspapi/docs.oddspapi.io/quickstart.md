> ## Documentation Index
> Fetch the complete documentation index at: https://docs.oddspapi.io/llms.txt
> Use this file to discover all available pages before exploring further.

# Quickstart Guide - Connect to Odds API in Minutes

> Get started with OddsPapi in minutes. Connect to the WebSocket gateway, authenticate with your API key, subscribe to channels, and stream realtime sports odds data.

## 1) Connect

Gateway:

* **ws**: `wss://v5.oddspapi.io/ws`

**JavaScript (Node)**

```js theme={null}
import WebSocket from "ws";

const ws = new WebSocket("wss://v5.oddspapi.io/ws");

ws.on("open", () => {
  ws.send(JSON.stringify({ type: "login", apiKey: process.env.ODDS_API_KEY }));
});

ws.on("message", (data) => {
  const msg = JSON.parse(data.toString());
  console.log(msg.type ?? msg.channel, msg);
});
```

**Python**

```py theme={null}
import json
import os
import websocket

def on_open(ws):
    ws.send(json.dumps({
        "type": "login",
        "apiKey": os.environ["ODDS_API_KEY"],
    }))

def on_message(ws, message):
    msg = json.loads(message)
    print(msg.get("type") or msg.get("channel"), msg)

ws = websocket.WebSocketApp(
    "wss://v5.oddspapi.io/ws",
    on_open=on_open,
    on_message=on_message,
)
ws.run_forever()
```

## 2) Minimal login

```json theme={null}
{
  "type": "login",
  "apiKey": "YOUR_API_KEY"
}
```

## 3) Understand the data model

Everything hangs off one tree — Sport → Tournament → Season → **Fixture** (single match) or **Future** (outright / prediction market), with participants, per-period scores, and players underneath:

> **[How Fixtures Work](/api-reference/concepts.md#how-fixtures-work-sports)**
>
> A fixture is one match between two participants — schedule, status, per-period scores, and the odds keyed on it.

> **[How Futures Work](/api-reference/concepts.md#how-futures-work-prediction-markets)**
>
> A future is an outright market on a whole season or event — league winners, elections, prediction markets (sportId 69+).

## 4) Pick your build

End-to-end guides for the four things clients most often build: [prediction markets & exchanges](/guides/prediction-markets.md), [sharp-line trading & CLV](/guides/sharp-line-trading.md), [running OddsPapi as a second feed](/guides/second-feed.md), and [powering a sportsbook](/guides/sportsbook.md).

Checking whether the feed carries what you need? See [bookmaker coverage](/coverage/bookmakers.md) and [market coverage](/coverage/markets.md).


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.