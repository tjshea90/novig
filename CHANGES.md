# API docs changes since the last snapshot

added 37, changed 260, removed 9

## Added
- kalshi/docs.kalshi.com/api-reference/incentive-programs/get-incentive-schedules.md
- kalshi/docs.kalshi.com/api-reference/markets/create-concert-cancellation-market.md
- kalshi/docs.kalshi.com/api-reference/markets/create-weather-point-market.md
- kalshi/docs.kalshi.com/api-reference/markets/get-concert-cancellation-market-availability.md
- kalshi/docs.kalshi.com/api-reference/markets/get-concerts.md
- kalshi/docs.kalshi.com/api-reference/markets/get-weather-point-market-availability.md
- kalshi/docs.kalshi.com/api-reference/markets/search-concerts.md
- kalshi/docs.kalshi.com/getting_started/concert_rfqs.md
- kalshi/docs.kalshi.com/getting_started/order_errors.md
- kalshi/docs.kalshi.com/getting_started/weather_rfqs.md
- kalshi/docs.kalshi.com/margin/corporate-actions.md
- novig/docs.novig.com/api-reference/exchange/list-the-locked-events-and-markets.md
- novig/docs.novig.com/api-reference/rfq/executions/get-your-collateral.md
- novig/docs.novig.com/api-reference/rfq/executions/list-your-executions.md
- novig/docs.novig.com/api-reference/rfq/maker/get-your-maker.md
- novig/docs.novig.com/api-reference/rfq/maker/register-a-maker.md
- novig/docs.novig.com/api-reference/rfq/streaming/open-the-websocket.md
- novig/docs.novig.com/api/rfq/confirm-rate.md
- novig/docs.novig.com/api/rfq/eligibility.md
- novig/docs.novig.com/api/rfq/executions.md
- novig/docs.novig.com/api/rfq/makers.md
- novig/docs.novig.com/api/rfq/overview.md
- novig/docs.novig.com/api/rfq/settlement.md
- novig/docs.novig.com/api/rfq/socket.md
- oddspapi/docs.oddspapi.io/coverage/sports.md
- polymarket/docs.polymarket.com/nex/connect.md
- polymarket/docs.polymarket.com/nex/get-access.md
- polymarket/docs.polymarket.com/nex/overview.md
- polymarket/docs.polymarket.com/nex/start-trading.md
- polymarket/docs.polymarket.com/nex/websocket-feeds.md
- polymarket/docs.polymarket.com/partners/overview.md
- polymarket/docs.polymarket.com/partners/rfq-trades.md
- polymarket/docs.polymarket.com/perps/learn-about-trading/markets/pre-ipo-anthropic.md
- sxbet/docs.sx.bet/api-reference/get-partner-attestation.md
- sxbet/docs.sx.bet/api-reference/get-partner-users.md
- sxbet/docs.sx.bet/api-reference/post-partner-users.md
- sxbet/docs.sx.bet/developers/partner-api.md

## Removed
- novig/docs.novig.com/api-reference/rfq/confirm-rate.md
- novig/docs.novig.com/api-reference/rfq/eligibility.md
- novig/docs.novig.com/api-reference/rfq/executions.md
- novig/docs.novig.com/api-reference/rfq/overview.md
- novig/docs.novig.com/api-reference/rfq/registration.md
- novig/docs.novig.com/api-reference/rfq/settlement.md
- novig/docs.novig.com/api-reference/rfq/signing.md
- novig/docs.novig.com/api-reference/rfq/webhooks.md
- novig/docs.novig.com/api-reference/rfq/websocket.md

## Changed: data/kalshi-sports-series.json
```diff
-{"series":[{"additional_prohibitions":["Current players, coaches, and staff of the league, association, or organization(s) governing the event. For college leagues/associations specifically, or where otherwise appropriate (as identified by the Exchange), this applies to current players/coaches/staf
+{"series":[{"additional_prohibitions":["Current players, coaches, and staff of the league, association, or organization(s) governing the event. For college leagues/associations specifically, or where otherwise appropriate (as identified by the Exchange), this applies to current players/coaches/staf
```

## Changed: data/novig-live-market-types.json
```diff
-{"ATP":["MONEY","SPREAD","TOTAL","MONEYLINE_3_WAY_WIN","MONEYLINE_3_WAY_DRAW","TO_ADVANCE","TEAM_TOTAL"],"Boxing":["MONEY","SPREAD","TOTAL","MONEYLINE_3_WAY_WIN","MONEYLINE_3_WAY_DRAW","TO_ADVANCE","TEAM_TOTAL","GO_THE_DISTANCE","VICTORY_BY_KO_KTO_OR_DQ","VICTORY_BY_DECISION","DRAW"],"Bundesliga":[
+{"ATP":["MONEY","SPREAD","TOTAL","MONEYLINE_3_WAY_WIN","MONEYLINE_3_WAY_DRAW","TO_ADVANCE","TEAM_TOTAL"],"Boxing":["MONEY","SPREAD","TOTAL","MONEYLINE_3_WAY_WIN","MONEYLINE_3_WAY_DRAW","TO_ADVANCE","TEAM_TOTAL","GO_THE_DISTANCE","VICTORY_BY_KO_KTO_OR_DQ","VICTORY_BY_DECISION","DRAW"],"Bundesliga":[
```

## Changed: data/novig-market-types.json
```diff
-["CUSTOM","MONEY","SPREAD","TOTAL","1X2","MONEYLINE_3_WAY_WIN","MONEYLINE_3_WAY_DRAW","TO_ADVANCE","TEAM_TOTAL","MONEY_1H","SPREAD_1H","TOTAL_1H","TOUCHDOWNS","SAVES","PASSING_TOUCHDOWNS","PASSING_AND_RUSHING_YARDS","KICKING_POINTS","INTERCEPTIONS_THROWN","TACKLES_ASSISTS","FIELD_GOALS_MADE","RUSHI
+["CUSTOM","MONEY","SPREAD","TOTAL","1X2","MONEYLINE_3_WAY_WIN","MONEYLINE_3_WAY_DRAW","TO_ADVANCE","TEAM_TOTAL","MONEY_1H","SPREAD_1H","TOTAL_1H","TOUCHDOWNS","SAVES","PASSING_TOUCHDOWNS","PASSING_AND_RUSHING_YARDS","KICKING_POINTS","INTERCEPTIONS_THROWN","TACKLES_ASSISTS","FIELD_GOALS_MADE","RUSHI
```

## Changed: data/sharpapi-health.json
```diff
-{"newest_odds_age_s":2,"status":"ok"}
+{"newest_odds_age_s":0,"status":"ok"}
```

## Changed: data/sxbet-leagues.json
```diff
-{"status":"success","data":[{"leagueId":1,"label":"NBA","sportId":1,"popularity":4605366.069127,"eventsByType":{"first-half-lines":13,"first-period-lines":8,"game-lines":25,"second-period-lines":8,"third-period-lines":8}},{"leagueId":246,"label":"NCAAF","sportId":8,"popularity":2525420.038426,"even
+{"status":"success","data":[{"leagueId":1,"label":"NBA","sportId":1,"popularity":5254821.852588,"eventsByType":{"first-half-lines":11,"first-period-lines":8,"game-lines":21,"second-period-lines":8,"third-period-lines":8}},{"leagueId":1197,"label":"UEFA Nations League","sportId":5,"popularity":33955
```

## Changed: kalshi/docs.kalshi.com/api-reference/api-keys/create-api-key.md
```diff
->  Endpoint for creating a new API key with a user-provided public key.  This endpoint allows users with Premier or Market Maker API usage levels to create API keys by providing their own RSA or Ed25519 public key in PEM format. The platform will use this public key to verify signatures on API requ
+>  Endpoint for creating a new API key with a user-provided public key.  This endpoint is available on every API usage tier. Provide your own RSA or Ed25519 public key in PEM format. The platform will use this public key to verify signatures on API requests: RSA-PSS with SHA-256 for RSA keys, Ed255
-      description: ' Endpoint for creating a new API key with a user-provided public key.  This endpoint allows users with Premier or Market Maker API usage levels to create API keys by providing their own RSA or Ed25519 public key in PEM format. The platform will use this public key to verify sign
+      description: ' Endpoint for creating a new API key with a user-provided public key.  This endpoint is available on every API usage tier. Provide your own RSA or Ed25519 public key in PEM format. The platform will use this public key to verify signatures on API requests: RSA-PSS with SHA-256 f
-            Forbidden - insufficient API usage level, or fcm_subtrader_id from a
-            non-FCM caller
+            Forbidden - the calling API key lacks the `write` scope, or
+            fcm_subtrader_id from a non-FCM caller
-            Conflict - the bound FCM subtrader is not yet visible in the
-            credential registry; retry after the subtrader create propagates
+            Conflict - the account already holds the maximum number of directly
+            managed API keys, 100 in production (code
+            `MAXIMUM_NUMBER_OF_API_KEYS_REACHED`); or the bound FCM subtrader is
+            not yet visible in the credential registry, in which case retry
+            after the subtrader create propagates
```

## Changed: kalshi/docs.kalshi.com/api-reference/api-keys/generate-api-key.md
```diff
->  Endpoint for generating a new API key with an automatically created key pair.  This endpoint generates a key pair of the requested key_type (RSA when omitted). The public key is stored on the platform, while the private key is returned to the user and must be stored securely. The private key can
+>  Endpoint for generating a new API key with an automatically created key pair.  This endpoint generates a key pair of the requested key_type (RSA when omitted). The public key is stored on the platform, while the private key is returned to the user and must be stored securely. The private key can
-      description: ' Endpoint for generating a new API key with an automatically created key pair.  This endpoint generates a key pair of the requested key_type (RSA when omitted). The public key is stored on the platform, while the private key is returned to the user and must be stored securely. T
+      description: ' Endpoint for generating a new API key with an automatically created key pair.  This endpoint generates a key pair of the requested key_type (RSA when omitted). The public key is stored on the platform, while the private key is returned to the user and must be stored securely. T
-          description: Forbidden - fcm_subtrader_id requires an FCM member caller
+          description: >-
+            Forbidden - the calling API key lacks the `write` scope, or
+            fcm_subtrader_id requires an FCM member caller
-            Conflict - the bound FCM subtrader is not yet visible in the
-            credential registry; retry after the subtrader create propagates
+            Conflict - the account already holds the maximum number of directly
+            managed API keys, 100 in production (code
+            `MAXIMUM_NUMBER_OF_API_KEYS_REACHED`); or the bound FCM subtrader is
+            not yet visible in the credential registry, in which case retry
+            after the subtrader create propagates
```

## Changed: kalshi/docs.kalshi.com/api-reference/communications/create-rfq.md
```diff
-          description: Whether to delete existing RFQs as part of this RFQ's creation
+          description: >-
+            Whether to close your older open RFQs to make room for this one.
+            When true, the oldest open RFQs on the subaccount are closed, on any
+            market, until at most two remain open including this one. An RFQ
+            with a confirmed quote, or with an accepted quote still inside its
+            confirmation window, is never closed, so the RFQ that stays open is
+            not always the newest. If too few RFQs can be closed, the request
+            fails with `expired`. It also skips the one-open-RFQ-per-market
+            check.
```

## Changed: kalshi/docs.kalshi.com/api-reference/estimate-portfolio-maintenance-margin.md
```diff
-        - quantity
-            non-zero.
+            non-zero. Provide `quantity` or `quantity_fp`.
+        quantity_fp:
+          type: string
+          description: >-
+            Signed contract count as a fixed-point string with at most two
+            decimal places (e.g. "0.25", "-1.50"); positive long, negative
+            short. Must be non-zero and at most 1000000 in magnitude, and must
+            equal `quantity` when both are given.
```

## Changed: kalshi/docs.kalshi.com/api-reference/events/get-event.md
```diff
-          description: Time when this market is expected to expire
-        expiration_time:
-          type: string
-          format: date-time
-          deprecated: true
-          x-go-type-skip-optional-pointer: true
+          description: >-
+            Time when this market is expected to expire. Never later than
+            `latest_expiration_time`.
```

## Changed: kalshi/docs.kalshi.com/api-reference/events/get-events.md
```diff
-          description: Time when this market is expected to expire
-        expiration_time:
-          type: string
-          format: date-time
-          deprecated: true
-          x-go-type-skip-optional-pointer: true
+          description: >-
+            Time when this market is expected to expire. Never later than
+            `latest_expiration_time`.
```

## Changed: kalshi/docs.kalshi.com/api-reference/events/get-multivariate-events.md
```diff
-          description: Time when this market is expected to expire
-        expiration_time:
-          type: string
-          format: date-time
-          deprecated: true
-          x-go-type-skip-optional-pointer: true
+          description: >-
+            Time when this market is expected to expire. Never later than
+            `latest_expiration_time`.
```

## Changed: kalshi/docs.kalshi.com/api-reference/exchange/get-exchange-status.md
```diff
->  Endpoint for getting the exchange status.
+> Endpoint for getting the exchange status.
+
+The top-level fields describe the default exchange index (0). Read the flags only from a 200 response. When the status of the default index cannot be read, the response is a 500, 503 or 504; its body can be an `ExchangeStatus` with `exchange_active` and `trading_active` set to `false`, or an error 
+
+In `exchange_index_statuses` on a 200 response, an index other than 0 whose status request failed or timed out is reported with all of its flags `false`. Read the status again before treating a single `false` reading for one index as a pause.
+
-      description: ' Endpoint for getting the exchange status.'
+      description: >
+        Endpoint for getting the exchange status.
+
+
+        The top-level fields describe the default exchange index (0). Read the
+        flags only from a 200 response. When the status of the default index
+        cannot be read, the response is a 500, 503 or 504; its body can be an
+        `ExchangeStatus` with `exchange_active` and `trading_active` set to
+        `false`, or an error object, and in neither case does it mean the
+        exchange is paused.
+
+
+        In `exchange_index_statuses` on a 200 response, an index other than 0
+        whose status request failed or timed out is reported with all of its
+        flags `false`. Read the status again before treating a single `false`
+        reading for one index as a pause.
```

## Changed: kalshi/docs.kalshi.com/api-reference/get-large-trader-positions.md
```diff
-> Positions of the authenticated clearing member that meet the CFTC large-trader reporting levels for a settlement day (17 CFR 15.03(b); each row's reportable_threshold is its market's level), plus exit-day positions that were reportable on the prior settlement day and are now below the threshold. 
+> Positions of the authenticated clearing member that meet the CFTC large-trader reporting levels for a settlement day (17 CFR 15.03(b); each row's reportable_threshold is its market's level), plus exit-day positions that were reportable on the prior settlement day and are now below the threshold. 
-        exit reporting. See the CFTC's Large Trader Reporting Program
-        (https://www.cftc.gov/IndustryOversight/MarketSurveillance/LargeTraderReportingProgram/index.htm)
-        and reporting levels at 17 CFR 15.03
-        (https://www.ecfr.gov/current/title-17/chapter-I/part-15).
+        exit reporting. See the [CFTC's Large Trader Reporting
+        Program](https://www.cftc.gov/IndustryOversight/MarketSurveillance/LargeTraderReportingProgram/index.htm)
+        and reporting levels at [17 CFR
+        15.03](https://www.ecfr.gov/current/title-17/chapter-I/part-15).
```

## Changed: kalshi/docs.kalshi.com/api-reference/historical/get-historical-market-candlesticks.md
```diff
->  Endpoint for fetching historical candlestick data for markets that have been archived from the live data set. Time period length of each candlestick in minutes. Valid values: 1 (1 minute), 60 (1 hour), 1440 (1 day).
+>  Endpoint for fetching historical candlestick data for markets that have been archived from the live data set. Time period length of each candlestick in minutes. Valid values: 1 (1 minute), 60 (1 hour), 1440 (1 day). One request can span at most 5,000 periods: if the minutes between `start_ts` an
-      description: ' Endpoint for fetching historical candlestick data for markets that have been archived from the live data set. Time period length of each candlestick in minutes. Valid values: 1 (1 minute), 60 (1 hour), 1440 (1 day).'
+      description: ' Endpoint for fetching historical candlestick data for markets that have been archived from the live data set. Time period length of each candlestick in minutes. Valid values: 1 (1 minute), 60 (1 hour), 1440 (1 day). One request can span at most 5,000 periods: if the minutes bet
```

## Changed: kalshi/docs.kalshi.com/api-reference/historical/get-historical-market.md
```diff
-          description: Time when this market is expected to expire
-        expiration_time:
-          type: string
-          format: date-time
-          deprecated: true
-          x-go-type-skip-optional-pointer: true
+          description: >-
+            Time when this market is expected to expire. Never later than
+            `latest_expiration_time`.
```

## Changed: kalshi/docs.kalshi.com/api-reference/historical/get-historical-markets.md
```diff
-          description: Time when this market is expected to expire
-        expiration_time:
-          type: string
-          format: date-time
-          deprecated: true
-          x-go-type-skip-optional-pointer: true
+          description: >-
+            Time when this market is expected to expire. Never later than
+            `latest_expiration_time`.
```

## Changed: kalshi/docs.kalshi.com/api-reference/incentive-programs/get-incentives.md
```diff
+        - name: series_ticker
+          in: query
+          required: false
+          description: Filter to one series' incentives.
+          schema:
+            type: string
+        - name: event_ticker
+          in: query
+          required: false
+          description: >-
+            Filter to one event's incentives. A perpetual's incentives belong to
+            no event.
+          schema:
+            type: string
+        series_ticker:
+          type: string
+          description: The series of the market associated with this incentive program
+        event_ticker:
+          type: string
+          description: >-
+            The event of the market associated with this incentive program;
+            empty for a perpetual
```

## Changed: kalshi/docs.kalshi.com/api-reference/market/batch-get-market-candlesticks.md
```diff
-- Returns up to 10,000 candlesticks total across all markets
+- The request may cover at most 10,000 candlestick periods in total: the minutes between `start_ts` and `end_ts`, divided by `period_interval`, multiplied by the number of tickers. A larger request is rejected with 400 `bad_request` (the `details` field gives the requested and maximum counts) even 
-        - Returns up to 10,000 candlesticks total across all markets
+        - The request may cover at most 10,000 candlestick periods in total: the
+        minutes between `start_ts` and `end_ts`, divided by `period_interval`,
+        multiplied by the number of tickers. A larger request is rejected with
+        400 `bad_request` (the `details` field gives the requested and maximum
+        counts) even if fewer candlesticks exist; the response is never
+        truncated
```

## Changed: kalshi/docs.kalshi.com/api-reference/market/get-market-candlesticks.md
```diff
+
+One request can span at most 5,000 periods. If the minutes between `start_ts` and `end_ts` divided by `period_interval` exceed 5,000, the request is rejected with 400 `bad_request` and the `details` field gives the requested and maximum counts. For example, 1-minute candlesticks are limited to abou
+
+The most recent candlesticks can be a few seconds behind the latest trades and quotes.
+
+
+        One request can span at most 5,000 periods. If the minutes between
+        `start_ts` and `end_ts` divided by `period_interval` exceed 5,000, the
+        request is rejected with 400 `bad_request` and the `details` field gives
+        the requested and maximum counts. For example, 1-minute candlesticks are
+        limited to about 3.5 days per request; split a longer range into several
+        requests. `start_ts` and `end_ts` must be in seconds; millisecond values
+        are rejected with 400 `bad_request`.
+
+
+        The most recent candlesticks can be a few seconds behind the latest
+        trades and quotes.
```

## Changed: kalshi/docs.kalshi.com/api-reference/market/get-market-orderbook.md
```diff
->  Endpoint for getting the current order book for a specific market.  The order book shows all active bid orders for both yes and no sides of a binary market. It returns yes bids and no bids only (no asks are returned). This is because in binary markets, a bid for yes at price X is equivalent to a
+>  Endpoint for getting the current order book for a specific market.  The order book shows all active bid orders for both yes and no sides of a binary market. It returns yes bids and no bids only (no asks are returned). This is because in binary markets, a bid for yes at price X is equivalent to a
-      description: ' Endpoint for getting the current order book for a specific market.  The order book shows all active bid orders for both yes and no sides of a binary market. It returns yes bids and no bids only (no asks are returned). This is because in binary markets, a bid for yes at price X 
+      description: ' Endpoint for getting the current order book for a specific market.  The order book shows all active bid orders for both yes and no sides of a binary market. It returns yes bids and no bids only (no asks are returned). This is because in binary markets, a bid for yes at price X 
-            Depth of the orderbook to retrieve (0 or negative means all levels,
-            1-100 for specific depth)
+            Number of price levels to return per side, counted from the best
+            bid. Omit it or pass 0 for all levels; pass 1-100 for a specific
+            depth. Values below 0 or above 100 are rejected with 400.
```

## Changed: kalshi/docs.kalshi.com/api-reference/market/get-market.md
```diff
->  Endpoint for getting data about a specific market by its ticker. A market represents a specific binary outcome within an event that users can trade on (e.g., "Will candidate X win?"). Markets have yes/no positions, current prices, volume, and settlement rules.
+>  Endpoint for getting data about a specific market by its ticker. A market represents a specific binary outcome within an event that users can trade on (e.g., "Will candidate X win?"). Markets have yes/no positions, current prices, volume, and settlement rules. The response is cached for a short 
-      description: ' Endpoint for getting data about a specific market by its ticker. A market represents a specific binary outcome within an event that users can trade on (e.g., "Will candidate X win?"). Markets have yes/no positions, current prices, volume, and settlement rules.'
+      description: ' Endpoint for getting data about a specific market by its ticker. A market represents a specific binary outcome within an event that users can trade on (e.g., "Will candidate X win?"). Markets have yes/no positions, current prices, volume, and settlement rules. The response is c
-          description: Time when this market is expected to expire
-        expiration_time:
-          type: string
-          format: date-time
-          deprecated: true
-          x-go-type-skip-optional-pointer: true
+          description: >-
+            Time when this market is expected to expire. Never later than
+            `latest_expiration_time`.
```

## Changed: kalshi/docs.kalshi.com/api-reference/market/get-markets.md
```diff
+
+ Paging and ordering:
+ - When `event_ticker` or `tickers` is the only filter (no `status`, `series_ticker`, timestamp filter, `mve_filter` or `cursor`), every matching market is returned in one response, in no guaranteed order. `limit` is not applied and the returned `cursor` is empty.
+ - Otherwise results are sorted by creation time, newest first, with these exceptions. With `min_updated_ts` or `max_updated_ts` they are sorted by last update time, oldest first. When neither `event_ticker` nor `tickers` is supplied, `status=closed`, `min_close_ts` or `max_close_ts` sorts by close
+ - A market whose close time changes while you page through a listing sorted by close time can be skipped or returned twice. Deduplicate by `ticker`.
+ - A `cursor` that cannot be decoded is ignored and the first page is returned, with no error. Pass the cursor exactly as it was returned, and stop when the returned `cursor` is empty.
+
+ Responses are cached for a short time, so prices, sizes and `status` can lag the exchange by several seconds, and filtered listings can lag longer than a read of a single market. Use the [WebSocket API](https://docs.kalshi.com/websockets/websocket-connection) when you need current state.
+
+         Paging and ordering:
+         - When `event_ticker` or `tickers` is the only filter (no `status`, `series_ticker`, timestamp filter, `mve_filter` or `cursor`), every matching market is returned in one response, in no guaranteed order. `limit` is not applied and the returned `cursor` is empty.
+         - Otherwise results are sorted by creation time, newest first, with these exceptions. With `min_updated_ts` or `max_updated_ts` they are sorted by last update time, oldest first. When neither `event_ticker` nor `tickers` is supplied, `status=closed`, `min_close_ts` or `max_close_ts` sorts 
+         - A market whose close time changes while you page through a listing sorted by close time can be skipped or returned twice. Deduplicate by `ticker`.
+         - A `cursor` that cannot be decoded is ignored and the first page is returned, with no error. Pass the cursor exactly as it was returned, and stop when the returned `cursor` is empty.
+
+         Responses are cached for a short time, so prices, sizes and `status` can lag the exchange by several seconds, and filtered listings can lag longer than a read of a single market. Use the [WebSocket API](https://docs.kalshi.com/websockets/websocket-connection) when you need current state.
-          description: Time when this market is expected to expire
-        expiration_time:
-          type: string
-          format: date-time
-          deprecated: true
-          x-go-type-skip-optional-pointer: true
+          description: >-
+            Time when this market is expected to expire. Never later than
+            `latest_expiration_time`.
```

## Changed: kalshi/docs.kalshi.com/api-reference/market/get-multiple-market-orderbooks.md
```diff
-> Endpoint for getting the current order books for multiple markets in a single request. The order book shows all active bid orders for both yes and no sides of a binary market. It returns yes bids and no bids only (no asks are returned). This is because in binary markets, a bid for yes at price X 
+> Endpoint for getting the current order books for multiple markets in a single request. The order book shows all active bid orders for both yes and no sides of a binary market. It returns yes bids and no bids only (no asks are returned). This is because in binary markets, a bid for yes at price X 
-        sizes. Each side shows price levels with their corresponding quantities
-        and order counts, organized from best to worst prices. Returns one
-        orderbook per requested market ticker.
+        sizes. Each side lists price levels as [price, quantity] pairs sorted by
+        price in ascending order, so the best (highest) bid is the last element.
+        Order counts are not returned. Returns one orderbook per requested
+        market ticker, in request order. A ticker that has no order book,
+        including a ticker that does not match any market, is returned with
+        empty arrays.
```

## Changed: kalshi/docs.kalshi.com/api-reference/market/get-trades.md
```diff
+
+A `ticker` that does not match any market returns 200 with an empty `trades` list, not 404. A `cursor` that cannot be decoded is ignored and the first page is returned, so pass the cursor exactly as it was returned. `min_ts` and `max_ts` must be in seconds; millisecond values are rejected with 400 
+
+
+        A `ticker` that does not match any market returns 200 with an empty
+        `trades` list, not 404. A `cursor` that cannot be decoded is ignored and
+        the first page is returned, so pass the cursor exactly as it was
+        returned. `min_ts` and `max_ts` must be in seconds; millisecond values
+        are rejected with 400 `bad_request`.
```

## Changed: kalshi/docs.kalshi.com/api-reference/multivariate/create-market-in-multivariate-event-collection.md
```diff
-> Endpoint for creating an individual market in a multivariate event collection. This endpoint must be hit at least once before trading or looking up a market. Users are limited to 5000 creations per week.
+> Endpoint for creating an individual market in a multivariate event collection. This endpoint must be hit at least once before a combo market can be quoted or traded. If the same legs were created before, it returns the existing market, which does not count toward the limit; the legs are still val
-        collection. This endpoint must be hit at least once before trading or
-        looking up a market. Users are limited to 5000 creations per week.
+        collection. This endpoint must be hit at least once before a combo
+        market can be quoted or traded. If the same legs were created before, it
+        returns the existing market, which does not count toward the limit; the
+        legs are still validated again, so a selection that worked before can
+        later be rejected. On the production API, the standard limit is 5000 new
+        markets per user per rolling 7 days, counted both across all collections
+        and within each collection; over either limit the endpoint returns 429
+        `too_many_requests` until earlier creations pass the 7-day mark.
+        Ordinary request-rate limiting returns the same status and code. A leg
+        selection that cannot be combined returns 400 with `code`
+        `conflicting_leg_outcomes`, `duplicated_legs` or
+        `invalid_market_combination`.
-          description: Time when this market is expected to expire
-        expiration_time:
-          type: string
-          format: date-time
-          deprecated: true
-          x-go-type-skip-optional-pointer: true
+          description: >-
+            Time when this market is expected to expire. Never later than
+            `latest_expiration_time`.
```

## Changed: kalshi/docs.kalshi.com/api-reference/multivariate/get-multivariate-event-collection.md
```diff
-            passed as inputs to the Lookup and Create endpoints.
+            passed as inputs to the Create endpoint.
-            passed into Lookup/Create affects the output. If false, the order
-            does not matter.
+            passed into Create affects the output. If false, the order does not
+            matter.
-            accepts multiple markets from the same event passed into
-            Lookup/Create.
+            accepts multiple markets from the same event passed into Create.
-            The minimum number of markets that must be passed into Lookup/Create
+            The minimum number of markets that must be passed into Create
-            The maximum number of markets that must be passed into Lookup/Create
+            The maximum number of markets that must be passed into Create
```

## Changed: kalshi/docs.kalshi.com/api-reference/multivariate/get-multivariate-event-collections.md
```diff
-            passed as inputs to the Lookup and Create endpoints.
+            passed as inputs to the Create endpoint.
-            passed into Lookup/Create affects the output. If false, the order
-            does not matter.
+            passed into Create affects the output. If false, the order does not
+            matter.
-            accepts multiple markets from the same event passed into
-            Lookup/Create.
+            accepts multiple markets from the same event passed into Create.
-            The minimum number of markets that must be passed into Lookup/Create
+            The minimum number of markets that must be passed into Create
-            The maximum number of markets that must be passed into Lookup/Create
+            The maximum number of markets that must be passed into Create
```

## Changed: kalshi/docs.kalshi.com/api-reference/orders/amend-order-v2.md
```diff
-> Endpoint for amending the price, max fillable count, and/or expiration time of an existing event-market order. The request `count` is the updated total/max fillable count, equal to already filled count plus desired resting remaining count. This behavior matches the v1 amend endpoints; only the re
+> Endpoint for amending the price, max fillable count, and/or expiration time of an existing event-market order. The request `count` is the updated total/max fillable count, equal to already filled count plus desired resting remaining count. An amend that changes nothing returns `400` with code `AM
-        desired resting remaining count. This behavior matches the v1 amend
-        endpoints; only the request/response shape differs.
+        desired resting remaining count. An amend that changes nothing returns
+        `400` with code `AMEND_ORDER_NO_OP`. See [Order
+        Errors](/getting_started/order_errors).
```

## Changed: kalshi/docs.kalshi.com/api-reference/orders/batch-cancel-orders-v2.md
```diff
-> Endpoint for cancelling a batch of event-market orders using the V2 response shape. To auto-route a cancellation, provide its `market_ticker` and omit `exchange_index` or set it to `-1`. The maximum batch size scales with your tier's write budget — see [Rate Limits and Tiers](/getting_started/rat
+> Endpoint for cancelling a batch of event-market orders using the V2 response shape. To auto-route a cancellation, provide its `market_ticker` and omit `exchange_index` or set it to `-1`. The maximum batch size scales with your tier's write budget — see [Rate Limits and Tiers](/getting_started/rat
-        Tiers](/getting_started/rate_limits).
+        Tiers](/getting_started/rate_limits). The batch is not atomic: the `200`
+        response carries one entry per order, and a cancellation that failed has
+        an `error` object and `reduced_by` of `0.00`. An entry error with a
+        timeout or unavailable code does not prove the order was not cancelled.
+        Some checks reject the whole batch before any cancellation is sent. See
+        [Order Errors](/getting_started/order_errors#batch-requests).
```

## Changed: kalshi/docs.kalshi.com/api-reference/orders/batch-create-orders-v2.md
```diff
-> Endpoint for submitting a batch of event-market orders using the V2 request/response shape. The maximum batch size scales with your tier's write budget — see [Rate Limits and Tiers](/getting_started/rate_limits).
+> Endpoint for submitting a batch of event-market orders using the V2 request/response shape. The maximum batch size scales with your tier's write budget — see [Rate Limits and Tiers](/getting_started/rate_limits). Orders are processed one at a time in request order and the batch is not atomic: a `
-        Tiers](/getting_started/rate_limits).
+        Tiers](/getting_started/rate_limits). Orders are processed one at a time
+        in request order and the batch is not atomic: a `201` response carries
+        one entry per order, and an order that failed has an `error` object
+        (`code`, `message`, `details`) in place of `order_id`. An entry error
+        with a timeout or unavailable code does not prove the order was not
+        placed. Some checks reject the whole batch before any order is sent. See
+        [Order Errors](/getting_started/order_errors#batch-requests) for those
+        cases and for the error body.
+          description: >-
+            Optional client-assigned ID, up to 64 characters from letters,
+            digits and `_ - : + = /`. It must be unique per subaccount on the
+            market's exchange shard while the order is open. See [Order
+            Errors](/getting_started/order_errors#client-order-ids).
```

## Changed: kalshi/docs.kalshi.com/api-reference/orders/cancel-order-v2.md
```diff
-> Endpoint for cancelling event-market orders using the V2 response shape. To auto-route the cancellation, provide `market_ticker` and omit `exchange_index` or set it to `-1`. Returns `{order_id, client_order_id, reduced_by}` rather than a full order object.
+> Endpoint for cancelling event-market orders using the V2 response shape. To auto-route the cancellation, provide `market_ticker` and omit `exchange_index` or set it to `-1`. Returns `{order_id, client_order_id, reduced_by}` rather than a full order object. A cancellation sent to an exchange shard
-        reduced_by}` rather than a full order object.
+        reduced_by}` rather than a full order object. A cancellation sent to an
+        exchange shard other than the one the order rests on returns `404`, even
+        though the order is still resting; see [Order
+        Errors](/getting_started/order_errors#requests-sent-to-the-wrong-exchange-shard).
```

## Changed: kalshi/docs.kalshi.com/api-reference/orders/create-order-v2.md
```diff
-> Endpoint for submitting event-market orders using the V2 request/response shape (single-book `bid`/`ask` side and fixed-point dollar prices). The legacy `/portfolio/orders` endpoint will be deprecated no earlier than May 6, 2026 — clients should migrate to this path.
+> Endpoint for submitting event-market orders using the V2 request/response shape (single-book `bid`/`ask` side and fixed-point dollar prices). The legacy `/portfolio/orders` write endpoints (create, cancel, amend, decrease and their batch forms) have been removed and now return `410 Gone` with cod
-        dollar prices). The legacy `/portfolio/orders` endpoint will be
-        deprecated no earlier than May 6, 2026 — clients should migrate to this
-        path.
+        dollar prices). The legacy `/portfolio/orders` write endpoints (create,
+        cancel, amend, decrease and their batch forms) have been removed and now
+        return `410 Gone` with code `deprecated_v1_order_endpoint`; use this
+        path instead. A `201` means the order was accepted, not that it is
+        resting: check `remaining_count`. See [Order
+        Errors](/getting_started/order_errors) for the error codes this endpoint
+        returns.
+          description: >-
+            Optional client-assigned ID, up to 64 characters from letters,
+            digits and `_ - : + = /`. It must be unique per subaccount on the
+            market's exchange shard while the order is open. See [Order
+            Errors](/getting_started/order_errors#client-order-ids).
```

## Changed: kalshi/docs.kalshi.com/api-reference/orders/get-order.md
```diff
->  Endpoint for getting a single order.
+>  Endpoint for getting a single order. Order reads are updated asynchronously after the matching engine processes a write, so an order created, amended or cancelled moments ago can briefly return `404 not_found` or its previous state. The response of the write itself is the only immediate result; 
-      description: ' Endpoint for getting a single order.'
+      description: ' Endpoint for getting a single order. Order reads are updated asynchronously after the matching engine processes a write, so an order created, amended or cancelled moments ago can briefly return `404 not_found` or its previous state. The response of the write itself is the only 
```

## Changed: kalshi/docs.kalshi.com/api-reference/portfolio/create-subaccount.md
```diff
-> Creates a new subaccount for the authenticated user. This endpoint is available to all users on the Advanced API tier and above. Subaccounts are numbered sequentially starting from 1. Maximum 63 numbered subaccounts per user (64 including the primary account).
+> Creates a new subaccount for the authenticated user. This endpoint is available to all users on the Advanced API tier and above; on the Basic tier it returns 403 with code `subaccount_creation_requires_advanced_api_usage_level`. The demo environment does not enforce the tier requirement. Subaccou
-        available to all users on the Advanced API tier and above. Subaccounts
-        are numbered sequentially starting from 1. Maximum 63 numbered
-        subaccounts per user (64 including the primary account).
+        available to all users on the Advanced API tier and above; on the Basic
+        tier it returns 403 with code
+        `subaccount_creation_requires_advanced_api_usage_level`. The demo
+        environment does not enforce the tier requirement. Subaccounts are
+        numbered sequentially starting from 1. Maximum 63 numbered subaccounts
+        per user (64 including the primary account).
```

## Changed: kalshi/docs.kalshi.com/api-reference/portfolio/get-fills.md
```diff
+Available to direct member accounts. FCM member accounts receive 403 with code `permission_denied` here and use `GET /fcm/fills` instead.
+
+        Available to direct member accounts. FCM member accounts receive 403
+        with code `permission_denied` here and use `GET /fcm/fills` instead.
```

## Changed: kalshi/docs.kalshi.com/api-reference/portfolio/get-settlements.md
```diff
+Available to direct member accounts. FCM member accounts receive 403 with code `permission_denied`.
+
+        Available to direct member accounts. FCM member accounts receive 403
+        with code `permission_denied`.
```

## Changed: kalshi/docs.kalshi.com/api-reference/portfolio/intra-account-transfer.md
```diff
+
+Naming a numbered subaccount requires the Advanced API tier or above; on the Basic tier the request returns 403 with code `subaccount_creation_requires_advanced_api_usage_level`.
+
+**Rate limit:** this endpoint has its own bucket, separate from the Read and Write budgets and the same on every tier. It refills at 600 tokens per minute and holds 600 tokens; at the default cost of 10 tokens that is 60 transfers per minute. A request over the limit returns 429.
+
+
+        Naming a numbered subaccount requires the Advanced API tier or above; on
+        the Basic tier the request returns 403 with code
+        `subaccount_creation_requires_advanced_api_usage_level`.
+
+
+        **Rate limit:** this endpoint has its own bucket, separate from the Read
```
(3 more changed lines)

## Changed: kalshi/docs.kalshi.com/asyncapi.yaml
```diff
```
(270 more changed lines)

## Changed: kalshi/docs.kalshi.com/changelog/index.md
```diff
```
(145 more changed lines)

## Changed: kalshi/docs.kalshi.com/fix-margin/authentication.md
```diff
```
(16 more changed lines)

## Changed: kalshi/docs.kalshi.com/fix-margin/error-handling.md
```diff
```
(15 more changed lines)

## Changed: kalshi/docs.kalshi.com/fix-margin/order-entry.md
```diff
```
(36 more changed lines)

## Changed: kalshi/docs.kalshi.com/getting_started/api_environments.md
```diff
```
(10 more changed lines)

## Changed: kalshi/docs.kalshi.com/getting_started/api_keys.md
```diff
```
(72 more changed lines)

## Changed: kalshi/docs.kalshi.com/getting_started/exchange_sharding.md
```diff
```
(5 more changed lines)

## Changed: kalshi/docs.kalshi.com/getting_started/maintenance_and_pauses.md
```diff
```
(10 more changed lines)

## Changed: kalshi/docs.kalshi.com/getting_started/market_lifecycle.md
```diff
```
(1 more changed lines)

## Changed: kalshi/docs.kalshi.com/getting_started/order_groups.md
```diff
```
(10 more changed lines)

## Changed: kalshi/docs.kalshi.com/getting_started/orderbook_responses.md
```diff
```
(8 more changed lines)

## Changed: kalshi/docs.kalshi.com/getting_started/pagination.md
```diff
```
(27 more changed lines)

## Changed: kalshi/docs.kalshi.com/getting_started/quick_start_authenticated_requests.md
```diff
```
(3 more changed lines)

## Changed: kalshi/docs.kalshi.com/getting_started/quick_start_create_order.md
```diff
```
(13 more changed lines)

## Changed: kalshi/docs.kalshi.com/getting_started/quick_start_market_data.md
```diff
```
(12 more changed lines)

## Changed: kalshi/docs.kalshi.com/getting_started/quick_start_websockets.md
```diff
```
(38 more changed lines)

## Changed: kalshi/docs.kalshi.com/getting_started/rate_limits.md
```diff
```
(44 more changed lines)

## Changed: kalshi/docs.kalshi.com/getting_started/rfqs.md
```diff
```
(46 more changed lines)

## Changed: kalshi/docs.kalshi.com/getting_started/subaccounts.md
```diff
```
(27 more changed lines)

## Changed: kalshi/docs.kalshi.com/llms-full.txt
```diff
```
(1815 more changed lines)

## Changed: kalshi/docs.kalshi.com/llms.txt
```diff
```
(41 more changed lines)

## Changed: kalshi/docs.kalshi.com/margin.md
```diff
```
(67 more changed lines)

## Changed: kalshi/docs.kalshi.com/margin/price-banding.md
```diff
```
(28 more changed lines)

## Changed: kalshi/docs.kalshi.com/openapi.yaml
```diff
```
(986 more changed lines)

## Changed: kalshi/docs.kalshi.com/websockets/communications.md
```diff
```
(362 more changed lines)

## Changed: kalshi/docs.kalshi.com/websockets/connection-keep-alive.md
```diff
```
(34 more changed lines)

## Changed: kalshi/docs.kalshi.com/websockets/market-and-event-lifecycle.md
```diff
```
(104 more changed lines)

## Changed: kalshi/docs.kalshi.com/websockets/market-positions.md
```diff
```
(25 more changed lines)

## Changed: kalshi/docs.kalshi.com/websockets/market-ticker.md
```diff
```
(50 more changed lines)

## Changed: kalshi/docs.kalshi.com/websockets/multivariate-market-and-event-lifecycle.md
```diff
```
(68 more changed lines)

## Changed: kalshi/docs.kalshi.com/websockets/order-group-updates.md
```diff
```
(12 more changed lines)

## Changed: kalshi/docs.kalshi.com/websockets/orderbook-updates.md
```diff
```
(50 more changed lines)

## Changed: kalshi/docs.kalshi.com/websockets/public-trades.md
```diff
```
(23 more changed lines)

## Changed: kalshi/docs.kalshi.com/websockets/user-fills.md
```diff
```
(28 more changed lines)

## Changed: kalshi/docs.kalshi.com/websockets/user-orders.md
```diff
```
(50 more changed lines)

## Changed: kalshi/docs.kalshi.com/websockets/websocket-connection.md
```diff
```
(461 more changed lines)

## Changed: novig/docs.novig.com/api-reference/accounts/fund-a-subaccount.md
```diff
```
(8 more changed lines)

## Changed: novig/docs.novig.com/api-reference/accounts/get-a-balance.md
```diff
```
(8 more changed lines)

## Changed: novig/docs.novig.com/api-reference/accounts/get-a-transfer.md
```diff
```
(8 more changed lines)

## Changed: novig/docs.novig.com/api-reference/accounts/get-an-account-transfer.md
```diff
```
(8 more changed lines)

## Changed: novig/docs.novig.com/api-reference/accounts/get-the-account-balance.md
```diff
```
(8 more changed lines)

## Changed: novig/docs.novig.com/api-reference/accounts/get-the-geolocation-status.md
```diff
```
(8 more changed lines)

## Changed: novig/docs.novig.com/api-reference/accounts/issue-a-subaccount-key.md
```diff
```
(8 more changed lines)

## Changed: novig/docs.novig.com/api-reference/accounts/label-a-subaccount.md
```diff
```
(8 more changed lines)

## Changed: novig/docs.novig.com/api-reference/accounts/list-account-transactions.md
```diff
```
(8 more changed lines)

## Changed: novig/docs.novig.com/api-reference/accounts/list-account-transfers.md
```diff
```
(8 more changed lines)

## Changed: novig/docs.novig.com/api-reference/accounts/list-subaccounts.md
```diff
```
(8 more changed lines)

## Changed: novig/docs.novig.com/api-reference/accounts/list-transactions.md
```diff
```
(8 more changed lines)

## Changed: novig/docs.novig.com/api-reference/accounts/open-a-subaccount.md
```diff
```
(8 more changed lines)

## Changed: novig/docs.novig.com/api-reference/authentication/create-a-key.md
```diff
```
(8 more changed lines)

## Changed: novig/docs.novig.com/api-reference/authentication/echo.md
```diff
```
(8 more changed lines)

## Changed: novig/docs.novig.com/api-reference/authentication/get-a-key.md
```diff
```
(8 more changed lines)

## Changed: novig/docs.novig.com/api-reference/authentication/list-keys.md
```diff
```
(8 more changed lines)

## Changed: novig/docs.novig.com/api-reference/authentication/revoke-a-key.md
```diff
```
(8 more changed lines)

## Changed: novig/docs.novig.com/api-reference/catalog/get-a-market.md
```diff
```
(19 more changed lines)

## Changed: novig/docs.novig.com/api-reference/catalog/get-an-event.md
```diff
```
(8 more changed lines)

## Changed: novig/docs.novig.com/api-reference/catalog/get-the-order-book.md
```diff
```
(8 more changed lines)

## Changed: novig/docs.novig.com/api-reference/catalog/list-consensus-markets.md
```diff
```
(8 more changed lines)

## Changed: novig/docs.novig.com/api-reference/catalog/list-event-participants.md
```diff
```
(14 more changed lines)

## Changed: novig/docs.novig.com/api-reference/catalog/list-event-statuses.md
```diff
```
(8 more changed lines)

## Changed: novig/docs.novig.com/api-reference/catalog/list-events.md
```diff
```
(8 more changed lines)

## Changed: novig/docs.novig.com/api-reference/catalog/list-leagues.md
```diff
```
(8 more changed lines)

## Changed: novig/docs.novig.com/api-reference/catalog/list-live-market-types.md
```diff
```
(9 more changed lines)

## Changed: novig/docs.novig.com/api-reference/catalog/list-market-types.md
```diff
```
(8 more changed lines)

## Changed: novig/docs.novig.com/api-reference/catalog/list-markets.md
```diff
```
(19 more changed lines)

## Changed: novig/docs.novig.com/api-reference/catalog/list-sports.md
```diff
```
(8 more changed lines)

## Changed: novig/docs.novig.com/api-reference/catalog/list-trades.md
```diff
```
(8 more changed lines)

## Changed: novig/docs.novig.com/api-reference/changelog.md
```diff
```
(40 more changed lines)

## Changed: novig/docs.novig.com/api-reference/exchange/get-the-exchange-status.md
```diff
```
(8 more changed lines)

## Changed: novig/docs.novig.com/api-reference/exchange/get-the-server-time.md
```diff
```
(8 more changed lines)

## Changed: novig/docs.novig.com/api-reference/execution/batch-cancel-orders.md
```diff
```
(8 more changed lines)

## Changed: novig/docs.novig.com/api-reference/execution/batch-place-orders.md
```diff
```
(8 more changed lines)

## Changed: novig/docs.novig.com/api-reference/execution/cancel-all-orders.md
```diff
```
(8 more changed lines)

## Changed: novig/docs.novig.com/api-reference/execution/cancel-an-order.md
```diff
```
(8 more changed lines)

## Changed: novig/docs.novig.com/api-reference/execution/get-an-order.md
```diff
```
(8 more changed lines)

## Changed: novig/docs.novig.com/api-reference/execution/get-your-positions.md
```diff
```
(8 more changed lines)

## Changed: novig/docs.novig.com/api-reference/execution/get-your-resting-orders.md
```diff
```
(8 more changed lines)

## Changed: novig/docs.novig.com/api-reference/execution/get-your-unrealized-pnl.md
```diff
```
(8 more changed lines)

## Changed: novig/docs.novig.com/api-reference/execution/list-fills.md
```diff
```
(8 more changed lines)

## Changed: novig/docs.novig.com/api-reference/execution/list-orders.md
```diff
```
(8 more changed lines)

## Changed: novig/docs.novig.com/api-reference/execution/list-positions.md
```diff
```
(8 more changed lines)

## Changed: novig/docs.novig.com/api-reference/execution/place-an-order.md
```diff
```
(8 more changed lines)

## Changed: novig/docs.novig.com/api-reference/history/get-a-market-from-history.md
```diff
```
(19 more changed lines)

## Changed: novig/docs.novig.com/api-reference/history/get-a-position-from-history.md
```diff
```
(8 more changed lines)

## Changed: novig/docs.novig.com/api-reference/history/get-an-event-from-history.md
```diff
```
(8 more changed lines)

## Changed: novig/docs.novig.com/api-reference/history/list-a-markets-candles.md
```diff
```
(8 more changed lines)

## Changed: novig/docs.novig.com/api-reference/history/list-event-history.md
```diff
```
(8 more changed lines)

## Changed: novig/docs.novig.com/api-reference/history/list-market-history.md
```diff
```
(19 more changed lines)

## Changed: novig/docs.novig.com/api-reference/history/list-position-history.md
```diff
```
(8 more changed lines)

## Changed: novig/docs.novig.com/api-reference/history/list-trade-history.md
```diff
```
(8 more changed lines)

## Changed: novig/docs.novig.com/api-reference/spec-files/openapi-v3.json
```diff
```
(852 more changed lines)

## Changed: novig/docs.novig.com/api-reference/streaming/open-the-websocket.md
```diff
```
(8 more changed lines)

## Changed: novig/docs.novig.com/api-reference/throttle/get-your-throttle-schedule.md
```diff
```
(8 more changed lines)

## Changed: novig/docs.novig.com/api/concepts/participation-protection.md
```diff
```
(52 more changed lines)

## Changed: novig/docs.novig.com/api/environments.md
```diff
```
(12 more changed lines)

## Changed: novig/docs.novig.com/api/errors.md
```diff
```
(27 more changed lines)

## Changed: novig/docs.novig.com/api/streaming/connection.md
```diff
```
(61 more changed lines)

## Changed: novig/docs.novig.com/api/streaming/private.md
```diff
```
(16 more changed lines)

## Changed: novig/docs.novig.com/api/throttling.md
```diff
```
(61 more changed lines)

## Changed: novig/docs.novig.com/llms-full.txt
```diff
```
(5905 more changed lines)

## Changed: novig/docs.novig.com/llms.txt
```diff
```
(40 more changed lines)

## Changed: oddspapi/docs.oddspapi.io/coverage.md
```diff
```
(125 more changed lines)

## Changed: oddspapi/docs.oddspapi.io/coverage/bookmakers.md
```diff
```
(234 more changed lines)

## Changed: oddspapi/docs.oddspapi.io/coverage/markets.md
```diff
```
(1703 more changed lines)

## Changed: oddspapi/docs.oddspapi.io/llms-full.txt
```diff
```
(2257 more changed lines)

## Changed: oddspapi/docs.oddspapi.io/llms.txt
```diff
```
(9 more changed lines)

## Changed: oddspapi/docs.oddspapi.io/websocket/console.md
```diff
```
(29 more changed lines)

## Changed: oddspapi/oddspapi.io/us/docs.html
```diff
```
(4 more changed lines)

## Changed: parlayapi/parlay-api.com/changelog.html
```diff
```
(2 more changed lines)

## Changed: parlayapi/parlay-api.com/docs.html
```diff
```
(2 more changed lines)

## Changed: parlayapi/parlay-api.com/llms-full.txt
```diff
```
(4 more changed lines)

## Changed: parlayapi/parlay-api.com/openapi-agent.json
```diff
```
(2 more changed lines)

## Changed: parlayapi/parlay-api.com/openapi.json
```diff
```
(2 more changed lines)

## Changed: pinnodds/pinnodds.com/docs.html
```diff
```
(333 more changed lines)

## Changed: pinnodds/pinnodds.com/llms-full.txt
```diff
```
(258 more changed lines)

## Changed: pinnodds/pinnodds.com/llms.txt
```diff
```
(4 more changed lines)

## Changed: pinnwire/pinnwire.com/docs.html
```diff
```
(2 more changed lines)

## Changed: polymarket/docs.polymarket.com/_llms/cn.txt
```diff
```
(4 more changed lines)

## Changed: polymarket/docs.polymarket.com/_llms/cn/api.txt
```diff
```
(19 more changed lines)

## Changed: polymarket/docs.polymarket.com/_llms/en/api-reference.txt
```diff
```
(21 more changed lines)

## Changed: polymarket/docs.polymarket.com/_llms/en/api-reference/predictions.txt
```diff
```
(2 more changed lines)

## Changed: polymarket/docs.polymarket.com/changelog/perps.md
```diff
```
(67 more changed lines)

## Changed: polymarket/docs.polymarket.com/changelog/predictions.md
```diff
```
(55 more changed lines)

## Changed: polymarket/docs.polymarket.com/changelog/sdks.md
```diff
```
(30 more changed lines)

## Changed: polymarket/docs.polymarket.com/llms-full.txt
```diff
```
(3832 more changed lines)

## Changed: polymarket/docs.polymarket.com/llms.txt
```diff
```
(40 more changed lines)

## Changed: polymarket/docs.polymarket.com/market-data/realtime-data.md
```diff
```
(70 more changed lines)

## Changed: polymarket/docs.polymarket.com/migrate/clob-sdk-to-unified-sdk.md
```diff
```
(9 more changed lines)

## Changed: polymarket/docs.polymarket.com/migrate/polymarket-v2/overview.md
```diff
```
(20 more changed lines)

## Changed: polymarket/docs.polymarket.com/migrate/rtds-to-polybolt.md
```diff
```
(50 more changed lines)

## Changed: polymarket/docs.polymarket.com/perps/errors.md
```diff
```
(16 more changed lines)

## Changed: polymarket/docs.polymarket.com/perps/learn-about-trading/markets.md
```diff
```
(10 more changed lines)

## Changed: polymarket/docs.polymarket.com/perps/learn-about-trading/overview.md
```diff
```
(2 more changed lines)

## Changed: polymarket/docs.polymarket.com/perps/trading-competitions.md
```diff
```
(53 more changed lines)

## Changed: polymarket/docs.polymarket.com/trading/manage-orders.md
```diff
```
(41 more changed lines)

## Changed: polymarket/docs.polymarket.com/trading/realtime-order-updates.md
```diff
```
(15 more changed lines)

## Changed: polymarket/docs.polymarket.com/trading/wallets-auth.md
```diff
```
(5 more changed lines)

## Changed: propline/api.prop-line.com/openapi.json
```diff
```
(2 more changed lines)

## Changed: propline/prop-line.com/api-versioning.html
```diff
```
(2 more changed lines)

## Changed: propline/prop-line.com/docs.html
```diff
```
(79 more changed lines)

## Changed: propline/prop-line.com/llms-full.txt
```diff
```
(109 more changed lines)

## Changed: propline/prop-line.com/llms.txt
```diff
```
(18 more changed lines)

## Changed: propline/prop-line.com/openapi.json
```diff
```
(440 more changed lines)

## Changed: propline/prop-line.com/recipes.html
```diff
```
(42 more changed lines)

## Changed: propline/prop-line.com/sportsbook-accuracy.html
```diff
```
(2 more changed lines)

## Changed: sharpapi/docs.sharpapi.io/llms-full.txt
```diff
```
(64 more changed lines)

## Changed: sharpapi/docs.sharpapi.io/openapi.json
```diff
```
(467 more changed lines)

## Changed: sportsgameodds/sportsgameodds.com/docs.html
```diff
```
(4 more changed lines)

## Changed: sportsgameodds/sportsgameodds.com/docs/llms-full.txt
```diff
```
(35 more changed lines)

## Changed: sxbet/api.docs.sx.bet/index.html
```diff
```
(67 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/auth-matrix.md
```diff
```
(10 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/channel-markets.md
```diff
```
(2 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/delete-orders-v3-all.md
```diff
```
(2 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/delete-orders-v3-event.md
```diff
```
(2 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/delete-orders-v3-market.md
```diff
```
(2 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/delete-orders-v3.md
```diff
```
(2 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/get-best-odds-v3.md
```diff
```
(2 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/get-event-orderbook-snapshot.md
```diff
```
(2 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/get-fills-v3.md
```diff
```
(2 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/get-fixture-active.md
```diff
```
(2 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/get-fixture-status.md
```diff
```
(2 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/get-leagues-active.md
```diff
```
(2 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/get-leagues.md
```diff
```
(2 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/get-ledger-v3.md
```diff
```
(2 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/get-live-scores.md
```diff
```
(2 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/get-market-orderbook-snapshot.md
```diff
```
(2 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/get-markets-active.md
```diff
```
(8 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/get-markets-find.md
```diff
```
(8 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/get-markets-popular.md
```diff
```
(8 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/get-metadata-obv3.md
```diff
```
(2 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/get-order-v3-by-client-id.md
```diff
```
(2 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/get-order-v3.md
```diff
```
(2 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/get-orders-v3-batch.md
```diff
```
(2 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/get-orders-v3-public.md
```diff
```
(2 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/get-orders-v3-user-public.md
```diff
```
(2 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/get-orders-v3.md
```diff
```
(2 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/get-positions-v3-public.md
```diff
```
(8 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/get-positions-v3.md
```diff
```
(8 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/get-sports.md
```diff
```
(2 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/get-teams.md
```diff
```
(2 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/get-trade-v3.md
```diff
```
(2 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/get-trades-v3-public.md
```diff
```
(2 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/get-trades-v3.md
```diff
```
(2 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/get-user-balance-v3.md
```diff
```
(2 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/get-user-fees-v3.md
```diff
```
(2 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/get-user-multisig-info.md
```diff
```
(2 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/get-user-multisig-proxy.md
```diff
```
(2 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/get-user-pending-deploy-proxy.md
```diff
```
(2 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/get-user-privacy-v3.md
```diff
```
(2 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/get-user-proxy.md
```diff
```
(2 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/get-user-transfer-to-proxy-pending.md
```diff
```
(2 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/get-user-transfer-to-proxy-status.md
```diff
```
(2 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/post-heartbeat-v3.md
```diff
```
(2 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/post-orders-v3.md
```diff
```
(20 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/post-user-deploy-proxy.md
```diff
```
(2 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/post-user-multisig-add-signer-execute.md
```diff
```
(2 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/post-user-multisig-add-signer-prepare.md
```diff
```
(2 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/post-user-multisig-cancel.md
```diff
```
(2 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/post-user-multisig-remove-signer-execute.md
```diff
```
(2 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/post-user-multisig-remove-signer-prepare.md
```diff
```
(2 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/post-user-multisig-set-threshold-execute.md
```diff
```
(2 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/post-user-multisig-set-threshold-prepare.md
```diff
```
(2 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/post-user-multisig-transfer-execute.md
```diff
```
(2 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/post-user-multisig-transfer-prepare.md
```diff
```
(2 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/post-user-multisig-withdraw-execute.md
```diff
```
(2 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/post-user-multisig-withdraw-prepare.md
```diff
```
(2 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/post-user-privacy-v3.md
```diff
```
(2 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/post-user-transfer-to-proxy.md
```diff
```
(2 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/search.md
```diff
```
(2 more changed lines)

## Changed: sxbet/docs.sx.bet/developers/account-models.md
```diff
```
(10 more changed lines)

## Changed: sxbet/docs.sx.bet/developers/api-key-permissions.md
```diff
```
(18 more changed lines)

## Changed: sxbet/docs.sx.bet/developers/markets-and-events.md
```diff
```
(2 more changed lines)

## Changed: sxbet/docs.sx.bet/developers/posting-orders.md
```diff
```
(2 more changed lines)

## Changed: sxbet/docs.sx.bet/developers/rate-limits.md
```diff
```
(15 more changed lines)

## Changed: sxbet/docs.sx.bet/llms-full.txt
```diff
```
(343 more changed lines)

## Changed: sxbet/docs.sx.bet/llms.txt
```diff
```
(6 more changed lines)

## Changed: theoddsapi/the-odds-api.com/liveapi/guides/v4.html
```diff
```
(3302 more changed lines)

## Changed: theoddsapi/the-odds-api.com/llms-full.txt
```diff
```
(34 more changed lines)

## Changed: theoddsapi/the-odds-api.com/llms.txt
```diff
```
(57 more changed lines)

## Changed: theoddsapi/the-odds-api.com/sports-odds-data/betting-markets.html
```diff
```
(77 more changed lines)

## Changed: theoddsapi/the-odds-api.com/sports-odds-data/bookmaker-apis.html
```diff
```
(193 more changed lines)

