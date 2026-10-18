> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Fetching markets

> Query active sports markets from the SX Bet API.

All active markets are available from [`GET /markets/active`](/api-reference/get-markets-active). You can filter by sport, league, event, market type, and more. To look up a specific market by hash, use [`GET /markets/find`](/api-reference/get-markets-find).

***

## Fetch markets by sport

Pass one or more `sportIds` to scope results to a particular sport.

```bash theme={null}
# NFL and NBA together
curl "https://api.sx.bet/markets/active?sportIds=1,4"
```

Use [`GET /sports`](/api-reference/get-sports) to get a full list of sport IDs and their labels.

***

## Fetch markets by league

Pass a `leagueId` to get all active markets in a specific league.

```bash theme={null}
# English Premier League (leagueId=29)
curl "https://api.sx.bet/markets/active?leagueId=29"
```

Use [`GET /leagues/active`](/api-reference/get-leagues-active) to get a full list of active league IDs.

***

## Fetch markets by event

If you already have a `sportXeventId` (returned on any market object), you can fetch all markets for that specific fixture.

```bash theme={null}
curl "https://api.sx.bet/markets/active?eventId=L16068923"
```

This returns every market type available for that single game — moneylines, spreads, totals, and any period or prop markets.

***

## Find fixtures by team name

If you do not already have an `eventId`, use [`GET /search`](/api-reference/search) to look up active fixtures by team name. Pass a `query` between 3 and 100 characters. The match is case-insensitive, and it supports partial names. Results are limited to 8 active fixtures, ordered by `gameTime`.

```bash theme={null}
curl "https://api.sx.bet/search?query=mia"
```

Each result includes the `eventId`, scheduled `gameTime`, both team names, and a `type` array of available market type IDs for that fixture. Pass the `eventId` to [`GET /markets/active`](/api-reference/get-markets-active) to fetch markets for that fixture.

***

## Fetch a specific market by marketHash

If you have a `marketHash`, use [`GET /markets/find`](/api-reference/get-markets-find) to retrieve it directly. You can pass up to 30 hashes in a single request.

```bash theme={null}
curl "https://api.sx.bet/markets/find?marketHashes=0x024902...dd7667b,0x1c8f12...bcbc83"
```

[`GET /markets/find`](/api-reference/get-markets-find) also returns settled markets, so it is useful for looking up historical results.

***

## Filters

### Main lines only

For spread and total markets, multiple lines are usually available at once (for example over 1.5, 2.5, 3.5 goals). Set `onlyMainLine=true` to return only the primary line for each market type — the line where both sides are closest to 50/50.

```bash theme={null}
curl "https://api.sx.bet/markets/active?leagueId=29&onlyMainLine=true"
```

### Live markets only

Set `liveOnly=true` to return only markets currently available for in-play betting.

```bash theme={null}
curl "https://api.sx.bet/markets/active?liveOnly=true"
```

### Filter by market type

Pass one or more market type IDs using the `type` parameter to narrow results to a specific bet type.

```bash theme={null}
# Moneyline and Asian Handicap
curl "https://api.sx.bet/markets/active?type=52,3"
```

Common market types:

| `type` | Name |
| - | - |
| `1` | 1X2 (win / draw / no win) |
| `52` | 12 (moneyline, no draw) |
| `226` | 12 Including Overtime |
| `3` | Asian Handicap (spread) |
| `2` | Under/Over (totals) |

See the full list on the [Market Types](/api-reference/market-types) page.

***

## Pagination

[`GET /markets/active`](/api-reference/get-markets-active) uses cursor-based pagination. Each response includes a `nextKey`. Pass it as `paginationKey` in your next request. The maximum `pageSize` is 100, which is also the default.

<Warning>
  **Stop when `nextKey` is absent, never when a page comes back short.** A short page can still
  carry a `nextKey`, with the next page empty.
</Warning>

The page-size parameter is `pageSize` on this route, not `perPage`. A wrong name is silently
ignored and you get a default-sized page, never an error.

***

## Real-time market updates

Rather than poll [`GET /markets/active`](/api-reference/get-markets-active), subscribe to a channel and receive market changes as they happen. Three channels are relevant to markets:

| Channel | What you receive |
| - | - |
| [`markets:global`](/api-reference/channel-markets) | A market's metadata changing — created, or moved between `ACTIVE` and `INACTIVE` |
| [`main_line:global`](/api-reference/channel-line-changes) | Main line shifts on markets whose type carries a line |
| [`fixtures:live_scores`](/api-reference/channel-fixtures) | Live scores for **every** event — there is no per-event filter |

<Note>
  **None of the three keeps history**, so a change you miss while disconnected is gone. Seed from REST
  on connect. Also, `markets:global` publishes an **array**, while `fixtures` publishes a bare object.
  See [Real-time data](/developers/realtime-overview).
</Note>


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.