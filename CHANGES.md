# API docs changes since the last snapshot

added 1, changed 58, removed 0

## Added
- sxbet/docs.sx.bet/api-reference/get-orders-v3-batch.md

## Changed: data/kalshi-sports-series.json
```diff
-{"series":[{"additional_prohibitions":["Current players, coaches, and staff of the league, association, or organization(s) governing the event. For college leagues/associations specifically, or where otherwise appropriate (as identified by the Exchange), this applies to current players/coaches/staf
+{"series":[{"additional_prohibitions":["Current players, coaches, and staff of the league, association, or organization(s) governing the event. For college leagues/associations specifically, or where otherwise appropriate (as identified by the Exchange), this applies to current players/coaches/staf
```

## Changed: data/polymarket-sports.json
```diff
-[{"id":630,"sport":"ufl","name":"UFL","image":"https://polymarket-upload.s3.us-east-2.amazonaws.com/league-icons/ufl.png","resolution":"https://www.theufl.com/","ordering":"away","tags":"1,100639,1186,105925","primaryTagId":105925,"series":"12553","createdAt":"2026-08-07T21:41:19.879539Z"},{"id":65
+[{"id":630,"sport":"ufl","name":"UFL","image":"https://polymarket-upload.s3.us-east-2.amazonaws.com/league-icons/ufl.png","resolution":"https://www.theufl.com/","ordering":"away","tags":"1,100639,1186,105925","primaryTagId":105925,"series":"12553","createdAt":"2026-08-07T21:41:19.879539Z"},{"id":65
```

## Changed: data/sharpapi-health.json
```diff
-{"newest_odds_age_s":16,"status":"ok"}
+{"newest_odds_age_s":2,"status":"ok"}
```

## Changed: data/sxbet-leagues.json
```diff
-{"status":"success","data":[{"leagueId":1,"label":"NBA","sportId":1,"popularity":3924290.149177,"eventsByType":{"first-half-lines":11,"first-period-lines":6,"game-lines":22,"second-period-lines":6,"third-period-lines":6}},{"leagueId":246,"label":"NCAAF","sportId":8,"popularity":2361942.137149,"even
+{"status":"success","data":[{"leagueId":1,"label":"NBA","sportId":1,"popularity":4605366.069127,"eventsByType":{"first-half-lines":13,"first-period-lines":8,"game-lines":25,"second-period-lines":8,"third-period-lines":8}},{"leagueId":246,"label":"NCAAF","sportId":8,"popularity":2525420.038426,"even
```

## Changed: kalshi/docs.kalshi.com/api-reference/communications/create-rfq.md
```diff
->  Endpoint for creating a new RFQ. You can have a maximum of 100 open RFQs at a time.
+>  Endpoint for creating a new RFQ. You can have a maximum of 100 open RFQs at a time. Creating one more returns 429 with code `open_rfq_limit_exceeded`; unlike request-rate limiting, retrying does not help until some of your RFQs close or expire. An RFQ on a market that is not open returns 400 wit
-      description: ' Endpoint for creating a new RFQ. You can have a maximum of 100 open RFQs at a time.'
+      description: ' Endpoint for creating a new RFQ. You can have a maximum of 100 open RFQs at a time. Creating one more returns 429 with code `open_rfq_limit_exceeded`; unlike request-rate limiting, retrying does not help until some of your RFQs close or expire. An RFQ on a market that is not op
+        '429':
+          $ref: '#/components/responses/RateLimitError'
+    RateLimitError:
+      description: >-
+        Rate limit exceeded. The default cost is 10 tokens per request. Use GET
+        /trade-api/v2/account/endpoint_costs to list non-default endpoint costs.
+      content:
+        application/json:
+          schema:
+            $ref: '#/components/schemas/ErrorResponse'
```

## Changed: kalshi/docs.kalshi.com/api-reference/portfolio/get-all-subaccount-balances.md
```diff
-> Gets balances for all subaccounts including the primary account. For an API key restricted to a subaccount, returns only that subaccount's balances across exchange indices.
+> Returns balances for all subaccounts, including the primary account. Only existing subaccount and exchange index pairs are returned, including pairs with a zero balance. A pair can exist before it is funded. For an API key restricted to a subaccount, returns only that subaccount's balances.
-        Gets balances for all subaccounts including the primary account. For an
-        API key restricted to a subaccount, returns only that subaccount's
-        balances across exchange indices.
+        Returns balances for all subaccounts, including the primary account.
+        Only existing subaccount and exchange index pairs are returned,
+        including pairs with a zero balance. A pair can exist before it is
+        funded. For an API key restricted to a subaccount, returns only that
+        subaccount's balances.
```

## Changed: kalshi/docs.kalshi.com/api-reference/portfolio/get-balance.md
```diff
+
+For per-subaccount balances, use `GET /portfolio/subaccounts/balances`. It returns existing subaccount and exchange index pairs, so its entry count can differ from `balance_breakdown`. The breakdown uses portfolio valuation snapshots; separate requests may reflect different stages of transfer proce
+        For per-subaccount balances, use `GET /portfolio/subaccounts/balances`.
+        It returns existing subaccount and exchange index pairs, so its entry
+        count can differ from `balance_breakdown`. The breakdown uses portfolio
+        valuation snapshots; separate requests may reflect different stages of
+        transfer processing.
+
+
-            User balance breakdown per exchange instance, omitted only when
-            using a subaccount-restricted API key.
+            Balance for every exchange index, summed across all subaccounts,
+            including zero balances. Unaffected by the `subaccount` and
+            `exchange_index` parameters. Omitted when access is restricted to a
+            subaccount.
```

## Changed: kalshi/docs.kalshi.com/changelog/index.md
```diff
+
+**November 12, 2026**
+
+Clarified how `GET /portfolio/balance` and `GET /portfolio/subaccounts/balances` list exchange indexes and aggregate subaccount balances. Documentation only; API behavior is unchanged.
+
+**November 12, 2026**
+
+`POST /communications/rfqs` returns specific error codes for two refusals that previously used generic ones. HTTP statuses are unchanged.
+
+| Refusal | Before | After |
+| - | - | - |
+| You already have 100 open RFQs | `429` `too_many_requests` | `429` `open_rfq_limit_exceeded` |
+| The RFQ's market is not open | `400` `invalid_parameters` | `400` `rfq_market_not_open` |
+
+`open_rfq_limit_exceeded` is not request-rate limiting: retrying does not help until some of your RFQs close or expire. If you branch on `too_many_requests` or `invalid_parameters` for these cases, handle the new codes as well.
+
+On FIX, the `QuoteRequestReject` (AG) `Text<58>` for the same two refusals changes to `OPEN_RFQ_LIMIT_EXCEEDED` and `RFQ_MARKET_NOT_OPEN`. `QuoteRequestRejectReason<658>` stays `99` (Other).
+
+**November 12, 2026**
+
+When an order is rejected because the account has never been funded on the market's exchange index, the `insufficient_shard_balance` error's `details` now explain that API orders are not funded automatically. They also name the fix: fund the exchange index with [Intra Account Transfer](https://docs
```

## Changed: kalshi/docs.kalshi.com/llms-full.txt
```diff
- Endpoint for creating a new RFQ. You can have a maximum of 100 open RFQs at a time.
+ Endpoint for creating a new RFQ. You can have a maximum of 100 open RFQs at a time. Creating one more returns 429 with code `open_rfq_limit_exceeded`; unlike request-rate limiting, retrying does not help until some of your RFQs close or expire. An RFQ on a market that is not open returns 400 with 
-Gets balances for all subaccounts including the primary account. For an API key restricted to a subaccount, returns only that subaccount's balances across exchange indices.
+Returns balances for all subaccounts, including the primary account. Only existing subaccount and exchange index pairs are returned, including pairs with a zero balance. A pair can exist before it is funded. For an API key restricted to a subaccount, returns only that subaccount's balances.
+
+For per-subaccount balances, use `GET /portfolio/subaccounts/balances`. It returns existing subaccount and exchange index pairs, so its entry count can differ from `balance_breakdown`. The breakdown uses portfolio valuation snapshots; separate requests may reflect different stages of transfer proce
+
+**November 12, 2026**
+
+Clarified how `GET /portfolio/balance` and `GET /portfolio/subaccounts/balances` list exchange indexes and aggregate subaccount balances. Documentation only; API behavior is unchanged.
+
+**November 12, 2026**
+
+`POST /communications/rfqs` returns specific error codes for two refusals that previously used generic ones. HTTP statuses are unchanged.
+
+| Refusal | Before | After |
+| - | - | - |
+| You already have 100 open RFQs | `429` `too_many_requests` | `429` `open_rfq_limit_exceeded` |
+| The RFQ's market is not open | `400` `invalid_parameters` | `400` `rfq_market_not_open` |
+
+`open_rfq_limit_exceeded` is not request-rate limiting: retrying does not help until some of your RFQs close or expire. If you branch on `too_many_requests` or `invalid_parameters` for these cases, handle the new codes as well.
+
+On FIX, the `QuoteRequestReject` (AG) `Text<58>` for the same two refusals changes to `OPEN_RFQ_LIMIT_EXCEEDED` and `RFQ_MARKET_NOT_OPEN`. `QuoteRequestRejectReason<658>` stays `99` (Other).
+
+**November 12, 2026**
+
+When an order is rejected because the account has never been funded on the market's exchange index, the `insufficient_shard_balance` error's `details` now explain that API orders are not funded automatically. They also name the fix: fund the exchange index with [Intra Account Transfer](/api-referen
+
+# Create Margin FCM Position Transfer
+Source: https://docs.kalshi.com/margin-rest/fcm/create-margin-fcm-position-transfer
+
+/perps_openapi.yaml post /margin/fcm/positions/transfers
+Endpoint for FCM members to move a perps position in one market from one of their subtraders
+into a subtrader under their FCM's error account.
+
+**This endpoint must be enabled for your FCM by Kalshi.** Kalshi enables it by configuring the
+error account that receives transferred positions and gives you that account's user id, which
+prefixes `target_subtrader_id`. Contact Kalshi to enable it; until then, requests return 400.
+Position transfers are also switched on per exchange; while they are off, requests return 409.
+
+- The destination is always your FCM's error account. `target_subtrader_id` picks the subtrader
+  under it, which is created on first use.
+- Each request moves one market for one source subtrader. To move many positions, send one
+  request per subtrader and market.
+- No cash moves when a transfer is booked. Both sides' positions and cost basis change as for a
+  fee-free trade at `price`: the source closes at `price` and the error account opens at
+  `price`. Both settle in the next settlement cycle: the source realizes the difference
+  between `price` and the market's last settlement mark, and the error account's variation
+  margin runs from `price` to the next settlement mark. To make a customer whole, use the
+  price of the fill being reversed.
+- `side` and `count` describe the position being moved: `long` moves a long of `count`
+  contracts, `short` moves a short. `count` must be a whole number of contracts. The source's
+  current position is not checked; moving more than it holds, or the other side, flips or
+  grows its position.
+- `client_transfer_id` makes the request idempotent per source subtrader: once a transfer
+  with that id has been booked from `source_subtrader_id`, a later request with the same id
+  from that subtrader, to any target, books nothing unless the exchange has restarted in
+  between. The exchange checks the id only after it re-checks the transfer switch, trading
+  status and both accounts' current risk, so a repeat returns 409 with error code
+  `transfer_already_applied` only when it passes those checks. Otherwise it gets the error
+  for the check it failed, such as 400 `insufficient_balance` once the earlier transfer has
+  used the error account's margin, or 409 while trading is paused.
+- After a timeout or 5xx, retry with the same id. `transfer_already_applied` confirms that the
+  earlier attempt was booked. Any other error refuses only the retry and leaves the earlier
+  attempt's outcome unknown: check the source subtrader's position before you send the
+  transfer again under a new id.
+- The exchange does not keep transfer ids across a restart, such as a maintenance window, so a
+  retry sent after one can book the transfer a second time. If the exchange may have
+  restarted since the first attempt, check the source subtrader's position before retrying,
+  even with the same id.
+- The market must have traded at least once and trading must not be paused; otherwise the
+  request returns 409. Both accounts must also pass the exchange's risk checks for their
+  post-transfer positions (available margin, FCM initial-margin caps, notional risk limits);
+  a failed risk check returns 400 with the exchange's reason, such as `insufficient_balance`,
+  and a blocked account returns 409.
+- Transfers appear on the daily statement and in the `position_transfer_audit` report. They
+  never appear as fills or in `trade_audit`.
+
+**Example.** A customer subtrader holds 10 long in `KXBTCPERP`, filled at $63,000. Send
+`side` `long`, `count` `10`, `price` `63000.00`: the customer goes from 10 long to flat and the
+error account subtrader from flat to 10 long, both at $63,000, with no cash moving.
+
```

## Changed: kalshi/docs.kalshi.com/llms.txt
```diff
-- [Get All Subaccount Balances](https://docs.kalshi.com/api-reference/portfolio/get-all-subaccount-balances.md): Gets balances for all subaccounts including the primary account. For an API key restricted to a subaccount, returns only that subaccount's balances across exchange indices.
+- [Get All Subaccount Balances](https://docs.kalshi.com/api-reference/portfolio/get-all-subaccount-balances.md): Returns balances for all subaccounts, including the primary account. Only existing subaccount and exchange index pairs are returned, including pairs with a zero balance. A pair can exist
-- [Create RFQ](https://docs.kalshi.com/api-reference/communications/create-rfq.md): Endpoint for creating a new RFQ. You can have a maximum of 100 open RFQs at a time.
+- [Create RFQ](https://docs.kalshi.com/api-reference/communications/create-rfq.md): Endpoint for creating a new RFQ. You can have a maximum of 100 open RFQs at a time. Creating one more returns 429 with code `open_rfq_limit_exceeded`; unlike request-rate limiting, retrying does not help until some 
+- [Create Margin FCM Position Transfer](https://docs.kalshi.com/margin-rest/fcm/create-margin-fcm-position-transfer.md): Endpoint for FCM members to move a perps position in one market from one of their subtraders into a subtrader under their FCM's error account.
```

## Changed: kalshi/docs.kalshi.com/openapi.yaml
```diff
+        For per-subaccount balances, use `GET /portfolio/subaccounts/balances`. It returns existing subaccount and exchange index pairs, so its entry count can differ from `balance_breakdown`. The breakdown uses portfolio valuation snapshots; separate requests may reflect different stages of transf
+
-      description: "Gets balances for all subaccounts including the primary account. For an API key restricted to a subaccount, returns only that subaccount's balances across exchange indices."
+      description: "Returns balances for all subaccounts, including the primary account. Only existing subaccount and exchange index pairs are returned, including pairs with a zero balance. A pair can exist before it is funded. For an API key restricted to a subaccount, returns only that subaccount
-      description: ' Endpoint for creating a new RFQ. You can have a maximum of 100 open RFQs at a time.'
+      description: ' Endpoint for creating a new RFQ. You can have a maximum of 100 open RFQs at a time. Creating one more returns 429 with code `open_rfq_limit_exceeded`; unlike request-rate limiting, retrying does not help until some of your RFQs close or expire. An RFQ on a market that is not op
+        '429':
+          $ref: '#/components/responses/RateLimitError'
-          description: 'User balance breakdown per exchange instance, omitted only when using a subaccount-restricted API key.'
+          description: 'Balance for every exchange index, summed across all subaccounts, including zero balances. Unaffected by the `subaccount` and `exchange_index` parameters. Omitted when access is restricted to a subaccount.'
```

## Changed: oddspapi/docs.oddspapi.io/api-reference/concepts.md
```diff
-### sportId
-
-* Integer.
-* The **first two digits** embedded into several downstream IDs.
-* Discover via `/sports`.
-
-### tournamentId
-
-* Integer.
-* Belongs to exactly one sport.
-* Discover via `/tournaments`.
-
-### seasonId
-
-* Integer.
-* Belongs to exactly one tournament.
-* Discover via `/seasons`.
+All three are integers, each belonging to exactly one parent:
+
+| ID | Discover via | Note |
+| - | - | - |
+| `sportId` | `/sports` | its **first two digits** are embedded into several downstream IDs |
+| `tournamentId` | `/tournaments` | belongs to one sport |
+| `seasonId` | `/seasons` | belongs to one tournament |
-> **TL;DR** — Every selection follows a fixed hierarchy: `marketType → period → handicap → side`, all baked into the `outcomeId` — so the same bet always resolves to the same `marketId` / `outcomeId`, addressed by coordinates rather than bookmaker name strings. The one part *not* in `outcomeId` is 
-
-For which market types, lines, periods, and player props are actually carried, see [market coverage](https://docs.oddspapi.io/coverage/markets.md).
-
-### How markets decompose (the deterministic hierarchy)
-
-Every `outcomeId` comes from a single, deterministic decomposition — OddsPapi splits each market into a fixed hierarchy:
-
-```
-marketType → period → handicap (line value / specifier) → side (outcome)   [+ optional player]
-```
-
-So "total goals over 2.5, full time" always lands on the same `marketId` / `outcomeId` across bookmakers and fixtures.
-
-| Level | Field | Meaning |
-| - | - | - |
-| 1 | `marketType` | The kind of market (`1x2`, `totals`, `spreads`, `spreads-european`, `bothteamsscore`, `drawnobet`, `oddeven`, …). |
-| 2 | `period` | The segment the market applies to (`fulltime`, `p1`, `p2`). |
-| 3 | `handicap` | The line value / specifier (e.g. `2.5`, `-0.25`). **Each distinct line value is its own `marketId`.** `0.0` for lineless markets like `1x2`. |
-| 4 | `outcomeName` | The **side** of the market (`Over` / `Under`, `1` / `X` / `2`, `Yes` / `No`, …). |
-
-> **Key rule:** because every line value gets its **own** `marketId`, "Over 2.5" and "Over 3.5" are *different* markets — not two outcomes of one market. Within a single `marketId`, the outcomes are only the **sides** of that exact line (e.g. `Over 2.5` + `Under 2.5`).
+> **TL;DR** — A market is `sportId × marketType × period × handicap`; add the side and you have the `outcomeId`. Everything is baked into the id, so the same bet always resolves to the same `marketId` / `outcomeId` — addressed by coordinates, never by bookmaker name strings. The one part *not* in t
+
+For which market types, lines, periods and player props are actually carried, see [market coverage](https://docs.oddspapi.io/coverage/markets.md).
+
+### The identity of a market (the strict rule)
+
+A market is identified by exactly four cells, and an outcome adds a fifth:
+
+```
+market   sportId × marketType × period × handicap
+outcome  sportId × marketType × period × handicap × outcomeName
+```
+
+| Cell | Meaning |
+| - | - |
+| `marketType` | the kind of question (`1x2`, `totals`, `spreads`, `bothteamsscore`, `oddeven`, …) |
+| `period` | the scope it applies to (`fulltime`, `result`, `p1`, `p1o6`, …) |
+| `handicap` | the line / specifier (`2.5`, `-0.25`); `0.0` for a lineless market |
+| `outcomeName` | the side (`Over`/`Under`, `1`/`X`/`2`, `Yes`/`No`, …) |
+
+Nothing else distinguishes a market — in particular **not its name**. Two consequences, both audited against the published catalogue on every release:
+
+**Each line is its own market.** "Over 2.5" and "Over 3.5" are *different* `marketId`s, not two outcomes of one market. Within one `marketId` the outcomes are only the sides of that exact line (`Over 2.5` + `Under 2.5`).
+
+**One structure per `marketType` and `period`, with a complete side set.** The same type at the same scope always offers the same sides, and those sides are the whole probability space:
+
+| if the market has | it also has |
+| - | - |
+| `Yes` | `No` |
+| `Over` | `Under` at that same `handicap` |
+| `Odd` | `Even` |
+| `1` | `2`, plus `X` wherever the market is three-way and a draw is possible |
+| `1X` | `12` and `2X` |
+| a `-team1` marketType | its `-team2` sibling at the same `period` and `handicap` |
+
+Two notes on applying this. Completeness is **per line** — an Over/Under ladder is many markets, so check the pair at a line, not across the ladder. And the band families are complete *without* an opposite, because their shape is a partition rather than a pair: `correctscore` (`a:b` cells), `winnin
+
+This same key is what composes the `oddsId` — see [Odds identifiers](#odds-identifiers).
-> A representative slice of the real soccer market map. Each `marketId` equals its first `outcomeId`, and each line value is its own market:
+> Each `marketId` equals its first `outcomeId`, and each line value is its own market:
-> | 10122 | European Handicap | `spreads-european` | fulltime | 6.0 | 10122 | 1 |
-> | 10122 | European Handicap | `spreads-european` | fulltime | 6.0 | 10123 | X |
-> | 10122 | European Handicap | `spreads-european` | fulltime | 6.0 | 10124 | 2 |
-> | 10208 | First Half Result | `1x2` | p1 | 0.0 | 10209 | X |
-> | 10208 | First Half Result | `1x2` | p1 | 0.0 | 10210 | 2 |
-> | 10214 | Draw No Bet | `drawnobet` | fulltime | 0.0 | 10214 | 1 |
-> | 10214 | Draw No Bet | `drawnobet` | fulltime | 0.0 | 10215 | 2 |
-> | 10222 | Odd Even Full Time | `oddeven` | fulltime | 0.0 | 10222 | Odd |
-> | 10222 | Odd Even Full Time | `oddeven` | fulltime | 0.0 | 10223 | Even |
-> Reading the first row: `marketType=1x2`, `period=fulltime`, `handicap=0.0`, `outcomeName=1` — the home side of the full-time match-result market.
+> Reading the first row: `marketType=1x2`, `period=fulltime`, `handicap=0.0`, `outcomeName=1` — the home side of the full-time match-result market. Note `106` and `1010` are the same question at two lines, hence two markets.
-`marketLength` is the **number of outcomes under a `marketId`** — the count of sides that can be bet on, which together form the complete probability space (their implied probabilities sum to ≈ 1 before margin removal). Examples: `moneyline` → 2, `1x2` → 3, `winningmargin` → the number of margin ba
+The size of **one complete probability space** where that size is fixed — the sides whose implied probabilities sum to ≈ 1 before margin removal (`moneyline` → 2, `1x2` → 3). **`0` means variable-length** (`correctscore`, `winningmargin`).
+
+It is **not** a count of the outcome rows you received. The field is frozen metadata, so a market whose missing side was appended after publication still reports its original value — 26 markets today. **To count sides, read the outcome list.**
-### participantId
-
-* Integer.
-* Represents teams or competitors in fixtures.
-* Discover via `/participants`.
-
-### playerId
-
-* Integer.
-* Used for player proposition markets.
-* `playerId = 0` typically represents a non-player market.
-* Discover via `/players`.
+Both integers: `participantId` identifies teams or competitors in fixtures (discover via `/participants`); `playerId` ties odds to a player for player prop markets, with `0` meaning a non-player market (discover via `/players`).
+### Why the `oddsId` is static (and what that saves you)
+
+The key is the market identity rule plus one extra coordinate:
+
+```
+{fixtureId} : {bookmaker} : {outcomeId} : {playerId}
+```
+
+| Segment | What it encodes |
+| - | - |
+| `outcomeId` | sportId × marketType × period × handicap × side |
+| `playerId` | the player (`0` for non-player markets) |
+
+Because all of that is *in the id*, a price is self-describing the moment it arrives:
+
+* **No joins on the hot path.** `outcomeId` `1010` *is* "soccer, total goals, full time, line 2.5, Over" — nothing is resolved at read time.
+* **The whole dictionary fits in memory** — 156,757 outcome rows, \~17.8 MB as CSV. Load once, resolve any id with a hash lookup.
+* **Integer keys, minimal payload.** No market name, period label, or line string repeated per message; cross-book comparison is `GROUP BY fixtureId, outcomeId` and nothing else.
+* **Arbitrage spaces are explicit.** Each line is its own `marketId` and 97.8% of markets are 2-sided — sum the implied probabilities under one `marketId` and the space is complete.
+* **Stable and cacheable.** Ids are append-only and never renumbered — safe to embed in code or a schema. The 2026-11-16 release re-filed 33,792 outcomes onto new `marketType` / `period` values with **zero** id changes.
+* **Player props don't inflate the id space.** The player is a coordinate of the price, not the taxonomy — one ladder serves every roster, and a transfer never churns an id.
+
+**The one cost:** a line a bookmaker quotes *outside* our published ladder has no id to land on and is dropped. That is why ladders are generated mechanically per family (floor `0.5`, ceiling = measured maximum + `2.5`) rather than decided per market.
+
-### Recommendation
-
-* Store timestamps as integers.
-* Rule of thumb: **schedule times are seconds, odds-update times are milliseconds.**
-* Normalize internally only if needed.
+Store timestamps as integers. Rule of thumb: **schedule times are seconds, odds-update times are milliseconds.**
-* **marketLength** — Number of outcomes under a `marketId`: the sides that can be bet on, together summing to a probability of ≈ 1.
+* **marketLength** — The size of one complete probability space where that size is fixed (`0` = variable-length). Frozen metadata, **not** a count of the outcome rows you received — read the outcome list for that.
```

## Changed: oddspapi/docs.oddspapi.io/api-reference/enumerations.md
```diff
+| `4` | `removed` | no | Wrongly issued; withdrawn from the schedule. |
-* Status only ever moves **forward** (`0 → 1 → 2`, or any state → `3`); it never goes backwards.
+* Status only ever moves **forward** (`0 → 1 → 2`, any state → `3`, any state → `4`); it never goes backwards.
+* **`3` vs `4`.** `cancelled` is a *real* event that will not be played — called off, abandoned, postponed indefinitely. `removed` means the entity **should never have existed**: a provider-side duplicate, a test entry, an event that was not real. Nothing was called off.
+* **`4` outranks `3`.** Any state can move to `4`, including `3`, and `4` is terminal — once an entity is `removed` it stays removed. Treat it as final and stop polling it. Scores and settlement are never published for a `4`.
+| `p1o1+p1o2` … `p1o1+…+p1o20` | **First *N* overs** of an innings — cricket, innings 1–2, *N* up to 20. Spelled out as the sum of its member overs, like every other combined segment. |
-Sub-period keys follow one shape: `p{X}{axis}{Y}` — unit *Y* within period *X*. Two axes exist today.
+Sub-period keys follow one shape: `p{X}{axis}{Y}` — unit *Y* within period *X*. Three axes exist today, and one of them nests a second level.
+| `p1o1`, `p2o1` | **`o` = over.** Over *Y* within innings *X* — cricket. `p1` remains the whole innings. |
+| `p1o1b1` … `p2o20b6` | **`b` = ball**, nested inside an over: delivery *d* of over *n* of innings *i* (`p{i}o{n}b{d}`). Innings 1–2, overs 1–20, balls 1–6. The only two-level sub-period key — `p{X}{axis}{Y}` alone does not describe it. |
-> The period vocabulary is **append-only**: new sports may introduce additional keys, and new axes may join `g` and `r`
-> (darts legs are a likely next one). Never hard-code an exhaustive list — handle unknown period keys gracefully.
-> Parsing \`p{X}
->
-> {axis}
->
-> {Y}\` structurally is safer than matching a fixed set of strings.
+> The period vocabulary is **append-only**: new sports may introduce additional keys, and new axes may join `g`, `r`
+> and `o` (darts legs are a likely next one). Never hard-code an exhaustive list — handle unknown period keys
+> gracefully. Parsing structurally is safer than matching a fixed set of strings — but note that an axis may nest a
+> second level, as cricket's `b` does inside `o`, so match the axes you know rather than assuming a single
+> `p{X}{axis}{Y}` shape.
```

## Changed: oddspapi/docs.oddspapi.io/api-reference/rate-limits.md
```diff
-**Limit:** **100 requests / minute**
+**Limit:** **200 requests / minute** (default)
```

## Changed: oddspapi/docs.oddspapi.io/api-reference/reliability.md
```diff
-| Pinnacle | 0.38 s | 0.38 s | measured |
+| Pinnacle | 0.28 s | 0.28 s | measured |
```

## Changed: oddspapi/docs.oddspapi.io/changelog/index.md
```diff
+
+***
+
+## 2026-11-16
+
+Market catalogue update. **Data only** — no endpoint, field or type changes. Refetch
+`GET /markets` once on or after this date and you are current.
+
+### ✨ Added
+
+* **7,882 `outcomeId`s across 3,733 new markets** and **33 new `marketType` values** —
+  almost all soccer: shots, saves, yellow and red cards, substitutions, own goals,
+  penalties awarded and missed (as totals / team totals / 1X2 / race-to), plus
+  `firstcorner`, `lastcorner`, `penaltyawarded`, `1up`, `players-firstcard`.
+* **280 new `period` keys**, all cricket over scopes: `p{i}o{n}` (after over *n* of
+  innings *i*), `p{i}o{n}b{d}` (delivery *d*), `p{i}o1+…+p{i}oN` (first *N* overs).
+  The vocabulary in use goes 280 → 560 and stays append-only; `p1` is still the
+  whole innings.
+
+### ⚠️ Breaking
+
+* **300 cricket `marketType` values retired; their markets re-filed onto the new over
+  periods.** The over count was in the type (`12-overs-3-delivery-team1`) and is now a
+  `period`, so those markets carry a plain `totals` / `teamtotals-team1` /
+  `teamtotals-team2`. **33,792 outcomes change `marketType` and `period` together**;
+  `outcomeId`, `marketId`, `handicap`, both names and `marketLength` are untouched, so
+  nothing is lost and no price needs re-mapping.
+
+  | before | after |
+  | - | - |
+  | `6-overs` @ `p1` | `totals` @ `p1o6` |
+  | `12-overs-team1` @ `p2` | `teamtotals-team1` @ `p2o12` |
+  | `12-overs-3-delivery-team2` @ `p1` | `teamtotals-team2` @ `p1o12b3` |
+
+  If you key cricket over markets on `marketType`, switch to `period`. This is the rule
+  the rest of the catalogue follows: **a market's scope lives in `period`, never in its
+  type or name.**
+
+* **60 ice-hockey player markets move from `fulltime+overtime` to `result`.** `Over Under
+  Player Saves (incl. overtime)` (50 markets) and `Over Under Player Blocks (incl.
+  overtime)` (10) were published at the `fulltime+overtime` scope; both now carry
+  `result`. **120 outcomes change `period` only** — `outcomeId`, `marketId`, `handicap`,
+  both names and `marketLength` are untouched, so no price needs re-mapping. If you key
+  those two on `period`, switch to `result`.
+
+* **46 markets gain their missing side** — `Yes`-only markets now offer `Yes` and `No`,
+  the `No` appended under the same `marketId`. Re-read the outcome list rather than a
+  cached count. `marketLength` is deliberately not updated: it is frozen metadata for a
+  market's intended probability space, not the length of the array you received — see
+  [marketLength](https://docs.oddspapi.io/api-reference/concepts.md#marketlength).
+
+### 🔁 Changed
+
+* **Market uniqueness is now strict and enforced.** A market is
+  `sportId × marketType × period × handicap`; an outcome adds `outcomeName` — the same
+  coordinates that compose the `oddsId`. So one `marketType` at one `period` has one
+  structure, and its sides are always the complete space (`Yes`⇒`No`, `Over`⇒`Under` at
+  that line, `-team1`⇒`-team2`, three-way carries its draw). Audited every release. See
+  [Markets and outcomes](https://docs.oddspapi.io/api-reference/concepts.md#the-identity-of-a-market-the-strict-rule).
+
+***
+
+## 2026-11-12
```
(24 more changed lines)

## Changed: oddspapi/docs.oddspapi.io/llms-full.txt
```diff
```
(705 more changed lines)

## Changed: oddspapi/docs.oddspapi.io/websocket/overview.md
```diff
```
(2 more changed lines)

## Changed: oddspapi/docs.oddspapi.io/websocket/troubleshooting.md
```diff
```
(2 more changed lines)

## Changed: oddspapi/oddspapi.io/us/docs.html
```diff
```
(4 more changed lines)

## Changed: parlayapi/parlay-api.com/agents.json
```diff
```
(2 more changed lines)

## Changed: parlayapi/parlay-api.com/changelog.html
```diff
```
(2 more changed lines)

## Changed: parlayapi/parlay-api.com/docs.html
```diff
```
(80 more changed lines)

## Changed: parlayapi/parlay-api.com/integrations.html
```diff
```
(2 more changed lines)

## Changed: parlayapi/parlay-api.com/llms-full.txt
```diff
```
(141 more changed lines)

## Changed: parlayapi/parlay-api.com/llms.txt
```diff
```
(4 more changed lines)

## Changed: parlayapi/parlay-api.com/openapi.json
```diff
```
(2 more changed lines)

## Changed: pinnodds/pinnodds.com/docs.html
```diff
```
(21 more changed lines)

## Changed: pinnodds/pinnodds.com/llms-full.txt
```diff
```
(55 more changed lines)

## Changed: pinnodds/pinnodds.com/llms.txt
```diff
```
(57 more changed lines)

## Changed: pinnwire/pinnwire.com/docs.html
```diff
```
(2 more changed lines)

## Changed: polymarket/docs.polymarket.com/changelog/sdks.md
```diff
```
(12 more changed lines)

## Changed: polymarket/docs.polymarket.com/llms-full.txt
```diff
```
(581 more changed lines)

## Changed: polymarket/docs.polymarket.com/trading/session-keys.md
```diff
```
(569 more changed lines)

## Changed: propline/api.prop-line.com/openapi.json
```diff
```
(2 more changed lines)

## Changed: propline/prop-line.com/api-versioning.html
```diff
```
(4 more changed lines)

## Changed: propline/prop-line.com/docs.html
```diff
```
(12 more changed lines)

## Changed: propline/prop-line.com/llms-full.txt
```diff
```
(3 more changed lines)

## Changed: propline/prop-line.com/llms.txt
```diff
```
(6 more changed lines)

## Changed: propline/prop-line.com/openapi.json
```diff
```
(2 more changed lines)

## Changed: propline/prop-line.com/recipes.html
```diff
```
(4 more changed lines)

## Changed: propline/prop-line.com/sportsbook-accuracy.html
```diff
```
(2 more changed lines)

## Changed: sharpapi/docs.sharpapi.io/openapi.json
```diff
```
(42 more changed lines)

## Changed: sxbet/api.docs.sx.bet/index.html
```diff
```
(40 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/auth-matrix.md
```diff
```
(1 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/delete-orders-v3-all.md
```diff
```
(25 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/delete-orders-v3-event.md
```diff
```
(25 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/delete-orders-v3-market.md
```diff
```
(25 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/delete-orders-v3.md
```diff
```
(38 more changed lines)

## Changed: sxbet/docs.sx.bet/developers/api-key-permissions.md
```diff
```
(1 more changed lines)

## Changed: sxbet/docs.sx.bet/developers/betting-delays.md
```diff
```
(7 more changed lines)

## Changed: sxbet/docs.sx.bet/developers/cancelling-orders.md
```diff
```
(35 more changed lines)

## Changed: sxbet/docs.sx.bet/developers/heartbeat.md
```diff
```
(4 more changed lines)

## Changed: sxbet/docs.sx.bet/developers/migrate-to-v3.md
```diff
```
(3 more changed lines)

## Changed: sxbet/docs.sx.bet/developers/my-orders.md
```diff
```
(9 more changed lines)

## Changed: sxbet/docs.sx.bet/developers/rate-limits.md
```diff
```
(1 more changed lines)

## Changed: sxbet/docs.sx.bet/llms-full.txt
```diff
```
(125 more changed lines)

## Changed: sxbet/docs.sx.bet/llms.txt
```diff
```
(1 more changed lines)

