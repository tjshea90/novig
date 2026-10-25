> ## Documentation Index
> Fetch the complete documentation index at: https://docs.oddspapi.io/llms.txt
> Use this file to discover all available pages before exploring further.

# Scores Channel - Live Score Updates

> Stream realtime live scores via WebSocket. Period-by-period score updates for all sports including result, halftime, quarters, sets, and periods.

## What it streams

Realtime score updates scoped to a specific `fixtureId`.
Each `payload.scores` object is keyed by the period (`result`, `p1`, etc.).

***

## Routing

* Entity key: `payload.fixtureId`
* Filters: `sportIds`, `tournamentIds`, `fixtureIds`
* Access: live/pregame access is determined by your `apiKey`

***

## Payload structure

| Field | Type | Description |
| - | - | - |
| `fixtureId` | `string` | The fixture this score applies to |
| `scores` | `object` | Scores per period |
| `scores[period]` | `object` | Each period's score and metadata |
| `updatedAt` | `string (ISO)` | Last known update time for this period |

***

## Example: result update

```json theme={null}
{
  "channel": "scores",
  "type": "UPDATE",
  "payload": {
    "fixtureId": "id1500025662664057",
    "scores": {
      "result": {
        "period": "result",
        "participant1Score": 2,
        "participant2Score": 1,
        "updatedAt": "2025-12-28T16:24:11.426852+00:00"
      }
    }
  },
  "ts": 1766939805321,
  "entryId": "1766939805321-3077"
}
```

## Example: multiple period updates

```json theme={null}
{
  "channel": "scores",
  "type": "UPDATE",
  "payload": {
    "fixtureId": "id2503637767171366",
    "scores": {
      "p1": {
        "period": "p1",
        "participant1Score": 7,
        "participant2Score": 6,
        "updatedAt": "2025-12-28T16:34:02.865934+00:00"
      },
      "result": {
        "period": "result",
        "participant1Score": 0,
        "participant2Score": 0,
        "updatedAt": "2025-12-28T16:32:18.616082+00:00"
      }
    }
  },
  "ts": 1766939642953,
  "entryId": "1766939642953-1905"
}
```

***

## Notes

* Periods may include `result`, `p1`, `1stHalf`, etc.
* Use `updatedAt` to detect stale scores
* This stream **does not** include match status (see `fixtures`)


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.