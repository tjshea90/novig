> ## Documentation Index
> Fetch the complete documentation index at: https://docs.oddspapi.io/llms.txt
> Use this file to discover all available pages before exploring further.

# Parlays - Price Multi-Leg Bets

> Price accumulators and correlated same-game parlays from OddsPapi oddsIds. Compare bookmakers side by side, read the correlation margin, and handle suspended or missing legs.

Two endpoints turn a list of `oddsIds` into a multi-leg price:

| Endpoint | Priced by | Legs may span | Correlation |
| - | - | - | - |
| `GET /fixtures/odds/parlay` | OddsPapi, locally | several fixtures & sports | none — a plain product |
| `GET /fixtures/odds/sgp` | the bookmaker itself | exactly one fixture | yes, quoted by the book |

Both take the same `oddsIds` parameter and return the same fixture view you
already know — they only **add** a section.

For the workflow around these endpoints — choosing between them, keeping a live
slip priced, degrading when a leg suspends — see [Building a
betslip](/guides/parlays.md).

***

## Selecting legs

A leg is identified by its `oddsId`:

```
{fixtureId}:{bookmaker}:{outcomeId}:{playerId}
```

which is exactly the key you already get back from `/fixtures/odds`. Pass them
comma- or space-separated:

```bash theme={null}
curl -H 'X-API-Key: YOUR_KEY' \
  'https://v5.oddspapi.io/en/fixtures/odds/parlay?oddsIds=id1400003160574217:bet365:141:0,id1400003160574217:bet365:146:0'
```

* Duplicates are removed and the list sorted, so the same selection always
  produces the same request.
* **Max 20 legs** per request.
* Legs are grouped **by bookmaker** — request the same selections at several
  books to price them side by side.

`parlayId`, the key inside the `parlay` / `sgp` section, is that sorted list
joined by `,`. It is built the same way on both endpoints, so a `/parlay` and an
`/sgp` entry for the same selections are directly comparable.

***

## What comes back

Both endpoints return a normal fixture view, with one section added.

| Section | Content |
| - | - |
| fixture meta | flattened at top level (`status`, `sport`, `tournament`, …) |
| `odds` | the requested legs, **inactive ones included** |
| `bookmakers` | `BookmakerFixtureMeta` for the requested bookmakers |
| `parlay` / `sgp` | the multi-leg price, keyed by bookmaker then `parlayId` |
| `missingOddsIds` | legs that could not be found — only when something is missing |

An empty `parlay` / `sgp` section is normal: see [suspended vs missing
legs](#suspended-vs-missing-legs).

`/fixtures/odds/parlay` returns an **array** — one object per fixture a leg
belongs to, ordered by `fixtureId`. `/fixtures/odds/sgp` returns a **single
object**, because all its legs share one fixture.

***

## Parlay: priced locally

The added section, for two legs at 3.8 and 2.1:

```json theme={null}
"parlay": {
  "bet365": {
    "id1400003160574217:bet365:141:0,id1400003160574217:bet365:146:0": {
      "bookmaker": "bet365",
      "active": true,
      "price": 7.98,
      "priceFractional": "349/50",
      "priceAmerican": 698,
      "singlesPrice": 7.98,
      "changedAt": 1766667441097
    }
  }
}
```

`price` is `3.8 × 2.1`, and `singlesPrice` equals it while the parlay is active.
A cross-fixture parlay comes back on **every** involved fixture with the same
entry, so read it off whichever one you were already looking at.

***

## SGP: priced by the bookmaker

All legs must belong to one fixture. Requesting legs from two fixtures is a
`400`:

```json theme={null}
{ "error": 400, "message": "oddsIds must share one fixture.", "code": "invalid_filters" }
```

The `sgp` section carries the bookmaker's own quote:

```json theme={null}
"sgp": {
  "bet365": {
    "id1400003160574217:bet365:141:0,id1400003160574217:bet365:146:0": {
      "bookmaker": "bet365",
      "active": true,
      "price": 6.5,
      "priceFractional": "11/2",
      "priceAmerican": 550,
      "limit": 500.0,
      "singlesPrice": 7.98,
      "changedAt": 1766667441097,
      "bookmakerParlayId": "B365-2493810567",
      "bookmakerChangedAt": 1766667440813,
      "betslip": "https://www.bet365.com/dl/sportsbookredirect?bs=..."
    }
  }
}
```

> [!NOTE]
>
> **The correlation margin.** `singlesPrice` (7.98) is what the naive product would pay; `price` (6.5) is what the
> bookmaker actually offers once it accounts for the legs being correlated. The gap between the two is the margin the
> book charges for the correlation — the number most clients are after.

SGP-only fields:

* `limit` — max stake at this price. Optional; absent means no stated cap, not zero.
* `bookmakerParlayId` — the book's own id for the parlay.
* `bookmakerChangedAt` — the book's quote timestamp (epoch ms). Optional; falls back to `changedAt`.
* `betslip` — deep link that opens the wager with every leg pre-loaded.
* `meta.offers` — exchanges only: the price-ladder rungs below the best one, as `[{ price, limit }, ...]`.

### Which bookmakers offer SGP

`/fixtures/odds/parlay` prices **every** bookmaker we carry. `/fixtures/odds/sgp`
only works with these 55. Any other slug comes back with `sgp_unsupported`.

`atg.se`, `ballybet`, `betboo.bet.br`, `betcity.nl`, `betmgm`, `betmgm.co.uk`,
`betonline.ag`, `betparx`, `betplay`, `betrivers`, `betuk`, `bingoal.be`,
`borgata`, `bwin`, `bwin.de`, `bwin.dk`, `bwin.es`, `casumo`, `draftkings`,
`expekt.se`, `fanduel`, `fourwinds`, `grosvenor`, `jacks.nl`, `kto`, `leovegas`,
`leovegas.es`, `leovegas.it`, `novig.us`, `oddset`, `paddypower`, `paf`,
`paf.es`, `partycasino`, `partypoker`, `pmu`, `prolineplus`, `prophetx`,
`rushbet.co`, `scooore.be`, `sportingbet`, `sportingbet.bet.br`,
`sportsinteraction`, `stake`, `stake.bet.br`, `svenskaspel`, `tabtouch`,
`unibet`, `unibet.be`, `unibet.co.uk`, `unibet.com.au`, `unibet.dk`,
`unibet.ie`, `unibet.nl`, `unibet.se`

Being on this list is not a promise for a given slip — the book still has to
carry that fixture and be willing to combine those particular legs, otherwise
you get `not_combinable`.

***

## Suspended vs missing legs

This is the distinction to build against, and it is the same on both endpoints.

**A suspended leg still exists**, so it comes back in `odds` with
`active: false` and the parlay keeps an inactive entry carrying the reason:

```json theme={null}
"id1400003160574217:bet365:141:0,id1400003160574217:bet365:146:0": {
  "active": false,
  "price": 1,
  "singlesPrice": 7.98,
  "meta": { "inactiveReason": "market_closed" }
}
```

`price: 1` means stake back; `singlesPrice` still shows what it would have paid,
so you can keep the selection on screen.

**A leg that cannot be found** — unknown bookmaker, unknown or expired `oddsId` —
gets that bookmaker no entry at all, and is named in `missingOddsIds`.

> [!WARNING]
>
> **`sgp: {}` without `missingOddsIds`.** On `/fixtures/odds/sgp` the bookmaker is also dropped when *we* hold every leg
> but the bookmaker's own pricing service could not resolve the parlay — it does not know the fixture, it rate limited
> us, or it errored. Your legs are all present in `odds`, there is no `missingOddsIds`, and `sgp` is simply `{}`. Treat
> an empty `sgp` as "no correlated price available right now" rather than "your selection was wrong", and fall back to
> `/fixtures/odds/parlay` for an uncorrelated price.

### `meta.inactiveReason` values

| Reason | Endpoint | Meaning |
| - | - | - |
| `leg_suspended` | both | one of the legs is currently suspended |
| `market_closed` | both | the leg's market is closed |
| `bookmaker_suspended` | both | the bookmaker is suspended for an involved fixture |
| `not_combinable` | `/sgp` | the bookmaker offers SGPs but will not combine *these particular* legs — by far the most common reason in practice |
| `sgp_unsupported` | `/sgp` | the bookmaker does not offer an SGP on these legs |
| `sgp_fetch_failed` | `/sgp` | the bookmaker could not be quoted in time |
| `fixture_ended` | `/sgp` | the fixture is over |

***

## Rate limits

| Endpoint | Limit |
| - | - |
| `GET /fixtures/odds/parlay` | **10 requests / second** |
| `GET /fixtures/odds/sgp` | **10 requests / second** |

Both sit in the odds bucket with the rest of the odds surface. See [Rate
Limits](/api-reference/rate-limits.md).

***

## Errors

| Status | When |
| - | - |
| `400` | empty or invalid `oddsIds`, more than 20 legs, or (on `/sgp`) legs spanning several fixtures |
| `422` | the `oddsIds` parameter was omitted entirely (`validation_error`) — sending it empty gives `400` instead |
| `401` | missing or invalid API key |
| `403` | your key is not entitled to the endpoint (`channel_not_allowed`), or a fixture, sport, tournament or bookmaker in the request is not allowed for it |
| `404` | (`/sgp`) the pricing service explicitly reported the fixture as unknown |
| `429` | rate limit exceeded |
| `501` | (`/sgp`) SGP pricing is not enabled for your endpoint |
| `502` | (`/sgp`) the pricing service was unreachable or failed — in practice a fabricated `fixtureId` surfaces here rather than as `404` |

A leg you are not entitled to makes the **whole request** fail with `403` — it
is not silently dropped, so filter selections against your key's access before
requesting a price.

Both endpoints are opt-in per API key. If yours is not enabled you get `403`
`channel_not_allowed` — contact us to have them added.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.