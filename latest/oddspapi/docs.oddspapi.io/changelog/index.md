> ## Documentation Index
> Fetch the complete documentation index at: https://docs.oddspapi.io/llms.txt
> Use this file to discover all available pages before exploring further.

# API Changelog - Updates, Features & Breaking Changes

> OddsPapi API changelog. New features, improvements, bug fixes, and breaking changes. Track WebSocket and REST API updates for sports betting data integration.

We continuously improve the Odds API.
This page lists **new features**, **changes**, **fixes**, and **breaking changes**.

> [!NOTE]
>
> All timestamps are UTC.
> If a change affects data shape or behavior, it will be marked as **Breaking**.

***

## 2026-10-26

### ✨ Added

* **`previousFixtureId` on every fixture response** — REST (`GET /fixtures`, `/fixtures/today`, `/fixtures/live`, `/fixtures/odds`, …) and the `fixtures` WebSocket channel. The inverse of `reissuedFixtureId` (added 2026-10-09): where that field points forward from a cancelled fixture to its replacement, this one points back from the replacement to the fixture it replaced.
* **`previousFutureId` on every future response** — same semantics, on `GET /futures`, `GET /futures/odds` and the `futures` WebSocket channel.

Unlike the forward pointer, this one sits on a normal scheduled or live fixture, so you can
see that a fixture you are tracking has a history without having met the cancelled one.
Same contract otherwise: always present, `null` most of the time, a plain id string, one hop
only — a fixture postponed twice gives `A → B → C`, where `B` carries both
`previousFixtureId: A` and `reissuedFixtureId: C`. One predecessor at most, cleared again if
a postponement is reverted. Existing reissue pairs are backfilled, so the back-pointer is
also present on replacements created before this date.

### ⚠️ Breaking

* **Fixture and future response shapes gained a top-level key.** `previousFixtureId` sits next to `fixtureId` and `reissuedFixtureId`; `previousFutureId` next to `futureId` and `reissuedFutureId`. Clients that reject unknown fields when deserializing need to add them; everyone else is unaffected.

***

## 2026-10-09

### ✨ Added

* **`reissuedFixtureId` on every fixture response** — REST (`GET /fixtures`, `/fixtures/today`, `/fixtures/live`, `/fixtures/odds`, …) and the `fixtures` WebSocket channel. When a cancelled fixture is re-listed under a new id — a postponed match moved to a later date, a board re-keyed by the provider — the cancelled fixture points at its replacement.
* **`reissuedFutureId` on every future response** — same semantics, on `GET /futures`, `GET /futures/odds` and the `futures` WebSocket channel.

The field is always present and `null` most of the time — it is only populated on a
cancelled entity (`status.statusId` `3`) that already has a replacement in the feed.
Many cancellations are permanent and stay `null`; others are re-listed later, so the value
can appear after you first saw the entity cancelled. It is a plain id string, one hop only:
if the replacement is cancelled too, read its own `reissuedFixtureId`.

### ⚠️ Breaking

* **Fixture and future response shapes gained a top-level key.** `reissuedFixtureId` sits next to `fixtureId`, `reissuedFutureId` next to `futureId`. Clients that reject unknown fields when deserializing need to add them; everyone else is unaffected.

***

## 2026-10-05

This entry is a **data change only** — no endpoint, field or type changes. It
all surfaces through `GET /markets` (and `GET /fixtures/markets`): refetch the
catalogue once on or after this date and you are current. Odds payloads are
untouched, they just start carrying the new `outcomeId`s.

### ✨ Added

* **64 new esports `marketType` values** and 568 new `marketType` × `period` combinations — objectives, first-objective and multikill markets across Counter-Strike, League of Legends, Dota, Valorant and Rainbow Six. Map-level markets arrive under a new `sportId` **83** (ESport Mobile Legends).
* **195 new `period` keys**, all sub-period grids — `p1r1` … `p5r36` (round within map) and `p1g2` … `p5g6` (tennis). The vocabulary goes from 19 values to 213 and stays append-only.

> **All 64 new marketType values**
>
> **Totals / spreads / team totals on esports objectives** — `totals-kills`, `totals-towers`, `totals-turrets`, `totals-dragons`, `totals-barons`, `totals-roshans`, `totals-inhibitors`, `totals-barracks`, `totals-rounds`, `totals-headshots`, `spreads-kills`, `spreads-towers`, `spreads-turrets`, `spreads-dragons`, `spreads-roshans`, `spreads-inhibitors`, `spreads-barracks`, `spreads-rounds`, `teamtotals-kills-team1`/`-team2`, `teamtotals-towers-team1`/`-team2`, `teamtotals-turrets-team1`/`-team2`, `teamtotals-dragons-team1`/`-team2`, `teamtotals-barons-team1`/`-team2`, `teamtotals-roshans-team1`/`-team2`, `teamtotals-inhibitors-team1`/`-team2`, `teamtotals-barracks-team1`/`-team2`, `teamtotals-rounds-team1`/`-team2`
>
> **First objective** — `firstblood`, `firsttower`, `firstbaron`, `firstroshan`, `firstinhibitor`, `firstbarracks`
>
> **Multikills** — `triplekill`, `quadrakill`, `pentakill`
>
> **Other** — `1x2-kills`, `drawnobet-kills`, `oddeven-kills`, `oddeven-rounds`, `racetokills`, `winningmargin-rounds`, `playertotals-kills`, `playertotals-headshots`, `toqualify`
>
> **Rename targets that did not exist before** — `spreads-bookings`, `players-commitpenalty`, `players-goalieshutouts`, `players-goalsagainst`, `players-nextgoalscorer`, `players-powerplaypoints`, `players-secondgoalscorer`, `players-shorthandedgoals`, `players-shorthandedgoalscorer`, `players-thirdgoalscorer`. These are new only because the market moved to them — see the rename table below.

### 🔁 Changed

* **24 `marketType` values are renamed for cross-sport consistency**, affecting **222** markets in Ice Hockey, Soccer and Cricket. The singular prefixes `player-`, `spread-` and `total-` become the plural `players-`, `spreads-` and `totals-` already used everywhere else.
* **`period` changes on 74 markets.** **28 move from `fulltime` to `result`** across 26 sports that have no scoring phase after regulation, so the two values were names for one number — see [Periods per sport](/guides/periods.md). The other **46 had a blank `period` and are now populated**. `marketId`s are unchanged throughout.
* **No market has an empty `period` any more.** If you special-cased the blank, drop that branch.

> **All 74 period changes**
>
> **`fulltime` → `result` — the `1x2` market in 26 sports:** MMA `203`, Boxing `213`, Beach Volley `303`, Squash `343`, Basketball 3x3 `353`, Curling `393`, Padel `403`, Soccer Specials `443`, Beach Handball `473`, Athletics `483`, Badminton `493`, Bowls `503`, Cross-Country `513`, ESport Call of Duty `563`, ESport Overwatch `573`, ESport Rainbow Six `583`, ESport Rocket League `593`, ESport StarCraft `603`, ESport Valorant `613`, ESport Arena of Valor `623`, ESport King of Glory `633`, Judo `643`, ESport Honor of Kings `653`, Speedway `663`, Golf `673`, Cycling `683`.
>
> Boxing also moves two `Total Rounds` lines (`216`, `218`) — its last `fulltime` markets, in a sport whose other Total Rounds lines were already on `result`.
>
> **Blank → populated, 46 markets:**
>
> | New `period` | Markets | Which |
> | - | - | - |
> | `fulltime` | 13 | Soccer both-halves / highest-scoring-half, Will There Be Overtime, Will There Be An Extra Inning |
> | `result` | 10 | Basketball player firsts, Volleyball exact-sets |
> | `p1+p2`, `p3+p4`, `p3+p4+overtime` | 8 | Basketball and American Football half splits |
> | `p1g2` … `p5g6` | 15 | Tennis `correctscore` "after *N* games" |

> **Full marketType rename table**
>
> | Old `marketType` | New `marketType` | Sport | Markets | `marketId` |
> | - | - | - | - | - |
> | `moneyline` | `toqualify` | Soccer | 1 | `10728` |
> | `player-anytimegoalscorer` | `players-anytimegoalscorer` | Ice Hockey | 4 | `152401`, `152671`, `152709`, `152747` |
> | `player-assists` | `players-assists` | Ice Hockey | 1 | `152376` |
> | `player-blocks` | `players-blocks` | Ice Hockey | 1 | `152468` |
> | `player-commitpenalty` | `players-commitpenalty` | Ice Hockey | 1 | `152670` |
> | `player-firstgoalscorer` | `players-firstgoalscorer` | Ice Hockey | 4 | `152402`, `152672`, `152710`, `152748` |
> | `player-goalieshutouts` | `players-goalieshutouts` | Ice Hockey | 1 | `152654` |
> | `player-goals` | `players-goals` | Ice Hockey | 4 | `152381`, `152679`, `152717`, `152755` |
> | `player-goalsagainst` | `players-goalsagainst` | Ice Hockey | 1 | `152644` |
> | `player-lastgoalscorer` | `players-lastgoalscorer` | Ice Hockey | 1 | `152406` |
> | `player-nextgoalscorer` | `players-nextgoalscorer` | Ice Hockey | 1 | `152405` |
> | `player-points` | `players-points` | Ice Hockey | 4 | `152386`, `152689`, `152727`, `152765` |
> | `player-powerplaypoints` | `players-powerplaypoints` | Ice Hockey | 1 | `152443` |
> | `player-saves` | `players-saves` | Ice Hockey | 1 | `152579` |
> | `player-secondgoalscorer` | `players-secondgoalscorer` | Ice Hockey | 1 | `152403` |
> | `player-shorthandedgoals` | `players-shorthandedgoals` | Ice Hockey | 1 | `152664` |
> | `player-shorthandedgoalscorer` | `players-shorthandedgoalscorer` | Ice Hockey | 1 | `152669` |
> | `player-shots` | `players-shots` | Ice Hockey | 1 | `152425` |
> | `player-shotsongoal` | `players-shotsongoal` | Ice Hockey | 4 | `152391`, `152703`, `152741`, `152779` |
> | `player-thirdgoalscorer` | `players-thirdgoalscorer` | Ice Hockey | 1 | `152404` |
> | `spread-bookings` | `spreads-bookings` | Soccer | 59 | `10972`–`101332` (59 ids) |
> | `spread-corners` | `spreads-corners` | Soccer | 54 | `10845`–`101635` (54 ids) |
> | `total-fours` | `totals-fours` | Cricket | 42 | `2712`–`27126` (42 ids) |
> | `total-sixes` | `totals-sixes` | Cricket | 32 | `2754`–`27158` (32 ids) |
>
> **13 of these renames merge into a `marketType` that already existed** —
> `players-anytimegoalscorer`, `players-assists`, `players-blocks`,
> `players-firstgoalscorer`, `players-goals`, `players-lastgoalscorer`,
> `players-points`, `players-saves`, `players-shots`, `players-shotsongoal`,
> `spreads-corners`, `totals-fours` and `totals-sixes` were already in the
> registry alongside their singular twin. If you group by `marketType`, those
> groups get **larger** rather than disappearing — the duplicate spelling for
> the same concept is gone.
>
> The `moneyline` → `toqualify` row is the one rename that is not cosmetic: a single Soccer market
> typed `moneyline` becomes `toqualify`, which is what it always meant. It is also the only market
> whose `marketName` changes in this release — `Winner (incl. overtime)` becomes `To Qualify`.

> [!WARNING]
>
> **If you key markets on `marketType` × `period`, re-pull the registry and
> regenerate those groupings.** 296 markets change their `marketType` or
> `period` — a hard-coded or cached mapping will silently stop matching them.
>
> **If you key on `marketId`, nothing breaks.** No `marketId` was removed, and
> no market gained or lost an `outcomeId`; every one of the 32,950 existing ids
> still resolves, and the 13,891 additions are purely new.

***

## 2026-09-28

### ✨ Added

* **`GET /{lang}/fixtures/odds/parlay`** — price an accumulator from a list of `oddsIds`, across fixtures and sports. See [Parlays & SGP](/api-reference/parlays.md).
* **`GET /{lang}/fixtures/odds/sgp`** — price a **correlated** same-game parlay, quoted by the bookmaker. `price` carries the correlation, `singlesPrice` the naive product.
* **`missingOddsIds`** on both new endpoints — names the requested legs that could not be found, so an empty section is never ambiguous.
* **`X-API-Key` header authentication** on every endpoint, alongside the `apiKey` query parameter. Send one or the other. See [Authentication](/api-reference/authentication.md).

***

## 2026-05-25

### ✨ Added

* **WebSocket `receiveType: "zstd"` — dictless compression.** Opt-in zstd egress with **no dictionary handling**: every data frame is a standalone dictless zstd frame (`dictId 0`), decoded in one line (`zstd.decompress(frame)` → JSON). \~5–6× smaller than JSON on `odds` — the simplest way to cut bandwidth. See [Compression](/websocket/compression.md).
* **WebSocket `receiveType: "zstd-dict"` — trained dictionaries.** Maximum ratio (\~7–9× on `odds`) using per-channel dictionaries the server pushes as `dict` control frames at connect.

### 🔁 Changed

* **`receiveType: "zstd"` now means dictless.** During the zstd beta, `"zstd"` previously delivered trained dictionaries; that behavior moved to the new `"zstd-dict"`. If you were decoding dictionary frames under `"zstd"`, switch to `"zstd-dict"`.
* **Dictionary version cache removed.** The `dicts` login field is gone; the server now re-sends the (\~32 KB) dictionaries on every `zstd-dict` connection, so clients no longer persist or version dictionaries across reconnects. A `dicts` field, if still sent, is ignored.

***

## 2026-04-09

### ✨ Added

* **Fixture view: `venue` section** — new nested object on all fixture responses with `venueId`, `venueName`, and `venueLocation`. Translated per language.
* **Fixture view: `clock` section** — new nested object on all fixture responses with `currentPeriod`, `currentTime`, `remainingTime`, `remainingTimeInPeriod`, and `stopped`. All keys present with `null` values until clock data is populated for a fixture.
* **Fixture view: `participant1ShortName` / `participant2ShortName`** — new fields in the `participants` section of fixture responses, sourced from translated participant data.
* **Fixture view: `seasonRound`** — new field in the `season` section of fixture responses.
* **Futures view: `marketId`** — the `market` section in future responses now includes the `marketId` from the futures table. Name fields (`marketName`, `marketType`, `playerMarket`, `participantMarket`) remain `null` for now.
* **Participants endpoint: `participantShortName`** — the `GET /{lang}/participants` response now includes `participantShortName` for each participant.
* New REST endpoint: **`GET /{lang}/venues?venueIds=...`** — returns venue data with translated `venueName` and `venueLocation`.
* New WebSocket channel: **`clocks`** — delivers live clock updates per fixture (same routing as `scores`: filtered by `fixtureId`, sport, tournament). Supports resume/replay.

### ⚠️ Breaking

* **Fixture response shape changed** — all fixture endpoints (REST and WebSocket) now include three new top-level keys: `venue` (object), `clock` (object), and updated `participants` / `season` sections. Clients parsing fixture responses strictly should update their models.
  * `season` now includes `seasonRound: integer | null`
  * `participants` now includes `participant1ShortName: string | null` and `participant2ShortName: string | null`
  * `venue: { venueId, venueName, venueLocation }` added after `season`
  * `clock: { currentPeriod, currentTime, remainingTime, remainingTimeInPeriod, stopped }` added after `scores`
* **Future response shape changed** — `market.marketId` is now populated (integer or null) instead of always `null`.

***

## 2025-12-12

### ✨ Added

* WebSocket **resume & replay** support using `entryId` cursors
* New WebSocket channels:
  * `injuries`
  * `lineups`
  * `stats`
* AsyncAPI 3.0 reference for WebSocket gateway

### 🔧 Improved

* Reduced WebSocket latency for `odds` and `scores`
* Better filtering for `sportIds`, `tournamentIds`, and `bookmakers`

### 🐛 Fixed

* Fixed an issue where some `odds` updates were delivered without bookmaker gating
* Fixed incorrect `live` flag on some fixtures during transitions

***

## 2025-11-28

### ⚠️ Breaking

* `odds.payload.odds` keys are now **always bookmaker-scoped**
  * Old clients expecting a flat structure must update

### ✨ Added

* Support for `receiveType: "binary"` (MessagePack)
* Added `entryId` to all update messages

***

## 2025-11-10

### ✨ Added

* Initial WebSocket gateway
* Channels:
  * `fixtures`
  * `scores`
  * `odds`
  * `bookmakers`


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.