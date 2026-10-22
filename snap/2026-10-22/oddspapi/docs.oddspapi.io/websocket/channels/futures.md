> ## Documentation Index
> Fetch the complete documentation index at: https://docs.oddspapi.io/llms.txt
> Use this file to discover all available pages before exploring further.

# Futures Channel - Season & Outright Markets

> Stream futures and outright betting markets via WebSocket. Season-long markets, tournament winners, championship odds with timing windows and provider mappings.

## What it streams

Metadata about long-term or season-based betting markets (called “futures”), scoped to a `futureId`.

Includes tournament/season mapping, market metadata, timing window, and external provider IDs.

***

## Routing

* Entity key: `payload.futureId`
* Filters: `sportIds`, `tournamentIds`, `futureIds`
* Access: live vs pregame determined by your `apiKey`

***

## Payload fields

| Field | Type | Description |
| - | - | - |
| `futureId` | `string` | Unique identifier for the future |
| `reissuedFutureId` | `string \| null` | The future that replaced a cancelled one, once that replacement exists. `null` is the normal value — permanent cancellations keep it, and it can flip from `null` to an id later. One hop |
| `previousFutureId` | `string \| null` | The inverse: the cancelled future this one replaced. Sits on a normal open future, so a future carries its own history even if you never saw the cancelled one. `null` is the normal value. One hop, cleared again if a cancellation is reverted |
| `status` | `object` | Live/pregame status |
| `status.live` | `boolean` | Whether the future is currently live |
| `status.statusId` | `number \| null` | Optional status code |
| `status.statusName` | `string \| null` | Optional status label (e.g. `"Pre-Game"`, `"Live"`) |
| `sport` | `object` | Sport metadata |
| `sport.sportId` | `number` | Unique sport ID |
| `sport.sportName` | `string` | Human-readable sport name |
| `tournament` | `object` | Tournament metadata |
| `tournament.tournamentId` | `number` | Unique tournament ID |
| `tournament.tournamentName` | `string` | Tournament name |
| `tournament.categoryName` | `string` | Geographic/organizational grouping |
| `season` | `object` | Season metadata |
| `season.seasonId` | `number \| null` | Season ID (nullable) |
| `season.seasonName` | `string \| null` | Season name |
| `startTime` | `number` | Start of the betting window (epoch seconds UTC) |
| `endTime` | `number` | End of the betting window (epoch seconds UTC) |
| `market` | `object \| null` | Market metadata (nullable) |
| `market.marketId` | `number \| null` | Optional market ID |
| `market.marketName` | `string \| null` | Optional market name (e.g. `"Outright Winner"`) |
| `market.marketType` | `string \| null` | Optional market type code |
| `participants` | `array` | List of participants for this future (may be empty) |
| `externalProviders` | `object` | Mapped external provider IDs |
| `externalProviders.betradarId` | `number \| null` | Betradar ID |
| `externalProviders.flashscoreId` | `string \| null` | Flashscore ID |
| `externalProviders.opticoddsId` | `string \| null` | OpticOdds ID |
| `externalProviders.polymarketId` | `string \| null` | Polymarket ID |
| `externalProviders.kalshiId` | `string \| null` | Kalshi ID |
| `externalProviders.sofascoreId` | `number \| null` | Sofascore ID |
| `bookmakers` | `object` | Odds availability status (see `bookmakersFutures`) |

***

## Example: future metadata

```json theme={null}
{
  "channel": "futures",
  "type": "UPDATE",
  "payload": {
    "futureId": "pm6980037088158379224",
    "reissuedFutureId": null,
    "previousFutureId": null,
    "status": {
      "live": true,
      "statusId": 1,
      "statusName": "Live"
    },
    "sport": {
      "sportId": 69,
      "sportName": "Politics"
    },
    "tournament": {
      "tournamentId": 800370,
      "tournamentName": "2025 Predictions",
      "categoryName": "Politics"
    },
    "season": {
      "seasonId": 8815837922,
      "seasonName": "Macron out by...?"
    },
    "startTime": 1735932904,
    "endTime": 1782820800,
    "market": {
      "marketId": 4,
      "marketName": null,
      "marketType": null
    },
    "participants": [],
    "externalProviders": {
      "betradarId": null,
      "flashscoreId": null,
      "opticoddsId": null,
      "polymarketId": "16263",
      "kalshiId": null,
      "sofascoreId": null
    },
    "bookmakers": {}
  },
  "ts": 1776939800000,
  "entryId": "1776939800000-999"
}
```

## Example: sports future

```json theme={null}
{
  "channel": "futures",
  "type": "UPDATE",
  "payload": {
    "futureId": "id100000281190131",
    "reissuedFutureId": null,
    "previousFutureId": null,
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
      "tournamentId": 28,
      "tournamentName": "AFC Asian Cup QF",
      "categoryName": "International"
    },
    "season": {
      "seasonId": 119013,
      "seasonName": "AFC Asian Cup QF 2026"
    },
    "startTime": 1725494400,
    "endTime": 1780617599,
    "market": {
      "marketId": 1,
      "marketName": null,
      "marketType": null
    },
    "participants": [],
    "externalProviders": {
      "betradarId": 119013,
      "flashscoreId": null,
      "opticoddsId": null,
      "polymarketId": null,
      "kalshiId": null,
      "sofascoreId": null
    },
    "bookmakers": {}
  },
  "ts": 1776939800100,
  "entryId": "1776939800100-1000"
}
```

***

## Notes

* Odds are streamed separately via the `oddsFutures` channel
* Use `futureId` to join with:
  * `oddsFutures` (for prices)
  * `bookmakersFutures` (for status per bookmaker)
* Most `market` fields may be `null` for legacy or non-structured markets


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.