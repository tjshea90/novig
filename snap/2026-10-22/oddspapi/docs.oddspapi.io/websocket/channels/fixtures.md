> ## Documentation Index
> Fetch the complete documentation index at: https://docs.oddspapi.io/llms.txt
> Use this file to discover all available pages before exploring further.

# Fixtures Channel - Match Metadata & Schedules

> Stream realtime fixture updates via WebSocket. Match status, start times, participants, tournaments, scores, and provider ID mappings for Betradar, Pinnacle, Flashscore, OpticOdds.

## What it streams

Metadata for a given `fixtureId` — including match status, start times, participants, and tournament/sport info.
Also includes optional `scores` and mapped provider IDs.

This is the **anchor stream** used to map odds/scores to a real-world fixture.

***

## Routing

* Entity key: `payload.fixtureId`
* Filters: `sportIds`, `tournamentIds`, `fixtureIds`
* Access: live/pregame determined by your `apiKey`

***

## Payload fields

| Field | Type | Description |
| - | - | - |
| `fixtureId` | `string` | Unique ID of the fixture |
| `reissuedFixtureId` | `string \| null` | The fixture that replaced a cancelled one, once that replacement exists. `null` is the normal value — permanent cancellations keep it, and it can flip from `null` to an id later. One hop |
| `previousFixtureId` | `string \| null` | The inverse: the cancelled fixture this one replaced. Sits on a normal scheduled or live fixture, so a fixture carries its own history even if you never saw the cancelled one. `null` is the normal value. One hop, cleared again if a postponement is reverted |
| `status` | `object` | Match status info |
| `status.live` | `boolean` | True if currently live |
| `status.statusId` | `number \| null` | Optional status code |
| `status.statusName` | `string \| null` | Status name (e.g. "Live", "Postponed") |
| `sport` | `object` | Sport metadata |
| `sport.sportId` | `number` | Unique sport ID |
| `sport.sportName` | `string` | Sport name |
| `tournament` | `object` | Tournament metadata |
| `tournament.tournamentId` | `number` | Unique tournament ID |
| `tournament.tournamentName` | `string` | Tournament name |
| `tournament.categoryName` | `string` | Geographic/organizational category |
| `season` | `object` | Season metadata |
| `season.seasonId` | `number \| null` | Season ID (nullable) |
| `season.seasonName` | `string \| null` | Season name |
| `season.seasonRound` | `number \| null` | Round/matchday number within the season |
| `venue` | `object` | Venue metadata |
| `venue.venueId` | `number \| null` | Venue ID |
| `venue.venueName` | `string \| null` | Venue name (translated) |
| `venue.venueLocation` | `string \| null` | Venue location (translated) |
| `startTime` | `number` | Scheduled start (epoch seconds UTC) |
| `trueStartTime` | `string \| null` | Actual start time (ISO 8601) |
| `trueEndTime` | `string \| null` | Actual end time (ISO 8601) |
| `participants` | `object` | Competitor metadata |
| `participants.participant1Id` | `number` | Team/player 1 ID |
| `participants.participant1Name` | `string` | Team/player 1 name |
| `participants.participant1ShortName` | `string \| null` | Short name (e.g. "Hawks") |
| `participants.participant1Abbr` | `string \| null` | Optional abbreviation |
| `participants.participant1RotNr` | `number \| null` | Optional rotation number |
| `participants.participant2Id` | `number` | Team/player 2 ID |
| `participants.participant2Name` | `string` | Team/player 2 name |
| `participants.participant2ShortName` | `string \| null` | Short name (e.g. "Heat") |
| `participants.participant2Abbr` | `string \| null` | Optional abbreviation |
| `participants.participant2RotNr` | `number \| null` | Optional rotation number |
| `scores` | `object` | Optional score object (same format as `scores` stream) |
| `clock` | `object \| null` | Live match clock data |
| `clock.currentPeriod` | `string \| null` | Current period (e.g. "p2") |
| `clock.currentTime` | `string \| null` | Current match time |
| `clock.remainingTime` | `string \| null` | Remaining time in match |
| `clock.remainingTimeInPeriod` | `string \| null` | Remaining time in current period |
| `clock.stopped` | `boolean \| null` | Whether the clock is stopped |
| `expectedPeriods` | `number \| null` | Number of scheduled periods (e.g. halves/sets/quarters) |
| `periodLength` | `number \| null` | Period duration in minutes |
| `externalProviders` | `object` | Mapped provider IDs |
| `externalProviders.betradarId` | `number \| null` | Betradar fixture ID |
| `externalProviders.flashscoreId` | `string \| null` | Flashscore ID |
| `externalProviders.pinnacleId` | `number \| null` | Pinnacle fixture ID |
| `externalProviders.sofascoreId` | `number \| null` | Sofascore ID |
| `externalProviders.oddinId` | `number \| null` | Oddin ID |
| `externalProviders.mollybetId` | `string \| null` | Mollybet ID |
| `externalProviders.opticoddsId` | `string \| null` | Opticodds ID |
| `externalProviders.lsportsId` | `number \| null` | LSports ID |
| `externalProviders.txoddsId` | `number \| null` | TXOdds ID |

The `externalProviders` block is how you join OddsPapi to a feed you already run — see [running OddsPapi as a second feed](/guides/second-feed.md) for the mapping endpoints and cross-validation workflow.

***

## Example message (JSON)

```json theme={null}
{
  "channel": "fixtures",
  "type": "UPDATE",
  "payload": {
    "fixtureId": "id1100013270505056",
    "reissuedFixtureId": null,
    "previousFixtureId": null,
    "status": {
      "live": true,
      "statusId": 1,
      "statusName": "Live"
    },
    "sport": {
      "sportId": 11,
      "sportName": "Basketball"
    },
    "tournament": {
      "tournamentId": 132,
      "tournamentName": "NBA",
      "categoryName": "USA"
    },
    "season": {
      "seasonId": 131631,
      "seasonName": "NBA 25/26",
      "seasonRound": null
    },
    "venue": {
      "venueId": 6054,
      "venueName": "Madison Square Garden",
      "venueLocation": "New York, NY, USA"
    },
    "startTime": 1776729600,
    "trueStartTime": "2026-04-21T00:09:08+00:00",
    "trueEndTime": null,
    "participants": {
      "participant1Id": 3421,
      "participant1RotNr": 303,
      "participant1Name": "New York Knicks",
      "participant1ShortName": "New York",
      "participant1Abbr": "NYK",
      "participant2Id": 3423,
      "participant2RotNr": 302,
      "participant2Name": "Atlanta Hawks",
      "participant2ShortName": "Atlanta",
      "participant2Abbr": "ATL"
    },
    "scores": {
      "p1": {
        "period": "p1",
        "participant1Score": 32,
        "participant2Score": 23,
        "updatedAt": "2026-04-21T00:59:42.287458+00:00"
      },
      "p2": {
        "period": "p2",
        "participant1Score": 29,
        "participant2Score": 31,
        "updatedAt": "2026-04-21T01:25:05.532295+00:00"
      },
      "result": {
        "period": "result",
        "participant1Score": 61,
        "participant2Score": 54,
        "updatedAt": "2026-04-21T01:24:37.154297+00:00"
      }
    },
    "clock": {
      "currentPeriod": null,
      "currentTime": null,
      "remainingTime": null,
      "remainingTimeInPeriod": null,
      "stopped": null
    },
    "expectedPeriods": 4,
    "periodLength": 12,
    "externalProviders": {
      "betgeniusId": 13808265,
      "betradarId": 70505056,
      "flashscoreId": "nTPkYS3K",
      "mollybetId": "2026-04-21,29093,29096",
      "oddinId": null,
      "opticoddsId": "20260421A1AB4678",
      "pinnacleId": 1628730839,
      "sofascoreId": 15935017,
      "lsportsId": null,
      "txoddsId": null
    },
    "bookmakers": {}
  },
  "ts": 1776729608000,
  "entryId": "1776729608000-4273"
}
```

## Example: fixture update that includes a `scores.result`

Some fixture updates include a `scores` object (for example, a pre-game or result snapshot).

```json theme={null}
{
  "channel": "fixtures",
  "type": "UPDATE",
  "payload": {
    "fixtureId": "id1000070367118324",
    "reissuedFixtureId": null,
    "previousFixtureId": null,
    "status": {
      "live": true,
      "statusId": 1,
      "statusName": "Live"
    },
    "sport": {
      "sportId": 10,
      "sportName": "Soccer"
    },
    "tournament": {
      "tournamentId": 703,
      "tournamentName": "Primera Nacional",
      "categoryName": "Argentina"
    },
    "season": {
      "seasonId": 138218,
      "seasonName": "Primera Nacional 2026",
      "seasonRound": 10
    },
    "venue": {
      "venueId": 12720,
      "venueName": "Estadio Don Leon Kolbovsky",
      "venueLocation": "Buenos Aires, Argentina"
    },
    "startTime": 1776729600,
    "trueStartTime": "2026-04-21T00:06:06.546877+00:00",
    "trueEndTime": null,
    "participants": {
      "participant1Id": 53799,
      "participant1RotNr": 210458,
      "participant1Name": "CA Atlanta",
      "participant1ShortName": "Atlanta",
      "participant1Abbr": "ATL",
      "participant2Id": 3214,
      "participant2RotNr": 210457,
      "participant2Name": "Chacarita Juniors",
      "participant2ShortName": "Chacarita Juniors",
      "participant2Abbr": "CAC"
    },
    "scores": {
      "p1": {
        "period": "p1",
        "participant1Score": 0,
        "participant2Score": 0,
        "updatedAt": "2026-04-21T00:55:10.401457+00:00"
      },
      "result": {
        "period": "result",
        "participant1Score": 1,
        "participant2Score": 0,
        "updatedAt": "2026-04-21T01:13:15.757548+00:00"
      }
    },
    "clock": {
      "currentPeriod": null,
      "currentTime": null,
      "remainingTime": null,
      "remainingTimeInPeriod": null,
      "stopped": null
    },
    "expectedPeriods": 2,
    "periodLength": 45,
    "externalProviders": {
      "betgeniusId": 13767372,
      "betradarId": 67118324,
      "flashscoreId": null,
      "mollybetId": "2026-04-21,10097858,10097878",
      "oddinId": null,
      "opticoddsId": "202604183517C8A7",
      "pinnacleId": 1628328116,
      "sofascoreId": 15275552,
      "lsportsId": null,
      "txoddsId": null
    },
    "bookmakers": {}
  },
  "ts": 1776729666000,
  "entryId": "1776729666000-4278"
}
```


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.