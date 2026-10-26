> ## Documentation Index
> Fetch the complete documentation index at: https://docs.oddspapi.io/llms.txt
> Use this file to discover all available pages before exploring further.

# Clocks Channel - Live Match Clock Updates

> Stream realtime match clock updates via WebSocket. Period, remaining time, and stopped state for live fixtures.

## What it streams

Realtime clock updates scoped to a specific `fixtureId`.
Each message contains the current clock state for a live match — period, elapsed/remaining time, and whether the clock is stopped.

***

## Routing

* Entity key: `payload.fixtureId`
* Filters: `sportIds`, `tournamentIds`, `fixtureIds`
* Access: live/pregame determined by your `apiKey`

***

## Payload fields

| Field | Type | Description |
| - | - | - |
| `fixtureId` | `string` | The fixture this clock applies to |
| `clock` | `object` | Clock state |
| `clock.currentPeriod` | `string \| null` | Current period (e.g. `"p1"`, `"p2"`) |
| `clock.currentTime` | `string \| null` | Current match time |
| `clock.remainingTime` | `string \| null` | Remaining time in the match |
| `clock.remainingTimeInPeriod` | `string \| null` | Remaining time in the current period |
| `clock.stopped` | `boolean \| null` | Whether the clock is currently stopped |

***

## Example message (JSON)

```json theme={null}
{
  "channel": "clocks",
  "type": "UPDATE",
  "payload": {
    "fixtureId": "id1500023462078980",
    "clock": {
      "currentPeriod": "p2",
      "currentTime": null,
      "remainingTime": "26:42",
      "remainingTimeInPeriod": "2:42",
      "stopped": false
    }
  },
  "ts": 1775170900000,
  "entryId": "1775170900000-42"
}
```

***

## Notes

* Clock updates are delivered independently from `scores` and `fixtures`
* The `clock` object is also included in the `fixtures` channel payload (as part of the fixture snapshot)
* When no clock data is available, all fields will be `null`
* Use this channel for high-frequency clock state without receiving the full fixture payload


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.