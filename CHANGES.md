# API docs changes since the last snapshot

added 5, changed 296, removed 0

## Added
- kalshi/docs.kalshi.com/api-reference/portfolio/get-credit-history.md
- novig/docs.novig.com/api-reference/accounts/get-the-geolocation-status.md
- novig/docs.novig.com/api/concepts/participation-protection.md
- oddspapi/docs.oddspapi.io/guides/latency-methodology.md
- polymarket/docs.polymarket.com/perps/trading-competitions.md

## Changed: boltodds/boltodds.com/docs.html
```diff
-    <script src="docs.js"></script>
+    <script src="docs.js?v=2"></script>
```

## Changed: boltodds/boltodds.com/pricing.html
```diff
-    <script src="pricing.js"></script>
+    <script src="pricing.js?v=2"></script>
```

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

## Changed: data/polymarket-sports.json
```diff
-[{"id":630,"sport":"ufl","name":"UFL","image":"https://polymarket-upload.s3.us-east-2.amazonaws.com/league-icons/ufl.png","resolution":"https://www.theufl.com/","ordering":"away","tags":"1,100639,1186,105925","primaryTagId":105925,"series":"12553","createdAt":"2026-08-07T21:41:19.879539Z"},{"id":64
+[{"id":630,"sport":"ufl","name":"UFL","image":"https://polymarket-upload.s3.us-east-2.amazonaws.com/league-icons/ufl.png","resolution":"https://www.theufl.com/","ordering":"away","tags":"1,100639,1186,105925","primaryTagId":105925,"series":"12553","createdAt":"2026-08-07T21:41:19.879539Z"},{"id":65
```

## Changed: data/sharpapi-health.json
```diff
-{"newest_odds_age_s":2,"status":"ok"}
+{"newest_odds_age_s":4,"status":"ok"}
```

## Changed: data/sxbet-leagues.json
```diff
-{"status":"success","data":[{"leagueId":1,"label":"NBA","sportId":1,"popularity":3128650.046137,"eventsByType":{"first-half-lines":18,"first-period-lines":9,"game-lines":33,"second-period-lines":9,"third-period-lines":9}},{"leagueId":246,"label":"NCAAF","sportId":8,"popularity":2800359.807365,"even
+{"status":"success","data":[{"leagueId":1,"label":"NBA","sportId":1,"popularity":3912351.568519,"eventsByType":{"first-half-lines":20,"first-period-lines":15,"game-lines":30,"second-period-lines":15,"third-period-lines":15}},{"leagueId":246,"label":"NCAAF","sportId":8,"popularity":2570760.53184,"ev
```

## Changed: kalshi/docs.kalshi.com/api-reference/communications/get-rfq.md
```diff
+        rfq_batch_id:
+          type: string
+          description: >-
+            Identifier of the RFQ batch this RFQ is a child of. Omitted for an
+            RFQ outside a batch. A child can be quoted like a single RFQ, with
+            the differences described in [Request for
+            Quote](/getting_started/rfqs#rfq-batch-children).
+          x-go-type-skip-optional-pointer: true
```

## Changed: kalshi/docs.kalshi.com/api-reference/communications/get-rfqs.md
```diff
+        rfq_batch_id:
+          type: string
+          description: >-
+            Identifier of the RFQ batch this RFQ is a child of. Omitted for an
+            RFQ outside a batch. A child can be quoted like a single RFQ, with
+            the differences described in [Request for
+            Quote](/getting_started/rfqs#rfq-batch-children).
+          x-go-type-skip-optional-pointer: true
```

## Changed: kalshi/docs.kalshi.com/api-reference/events/get-event.md
```diff
-          description: Shortened title for the no side of this market
+          deprecated: true
+          description: >
+            Deprecated. Use `yes_sub_title` instead. Equal to `yes_sub_title` on
+            every market created since September 2024.
+          x-go-type: json.RawMessage
+          x-go-type-skip-optional-pointer: true
```

## Changed: kalshi/docs.kalshi.com/api-reference/events/get-events.md
```diff
+          x-go-type: json.RawMessage
-          description: Shortened title for the no side of this market
+          deprecated: true
+          description: >
+            Deprecated. Use `yes_sub_title` instead. Equal to `yes_sub_title` on
+            every market created since September 2024.
+          x-go-type: json.RawMessage
+          x-go-type-skip-optional-pointer: true
```

## Changed: kalshi/docs.kalshi.com/api-reference/events/get-multivariate-events.md
```diff
-          description: Shortened title for the no side of this market
+          deprecated: true
+          description: >
+            Deprecated. Use `yes_sub_title` instead. Equal to `yes_sub_title` on
+            every market created since September 2024.
+          x-go-type: json.RawMessage
+          x-go-type-skip-optional-pointer: true
```

## Changed: kalshi/docs.kalshi.com/api-reference/get-active-margin-obligations.md
```diff
+        - amount_fp
+        - pnl_fp
-            centicents). All monetary fields on this API are returned in
-            centicents. Total the clearing member owes or is owed, inclusive
-            of PnL, fees, and maintenance margin changes. Negative means the
-            clearing member pays Kalshi Klear; positive means Kalshi Klear
-            pays the clearing member.
+            centicents). Monetary fields ending in `_centicents` are integer
+            centicents; where an `_fp` counterpart exists, it carries the same
+            amount as a fixed-point dollar string with 6 decimal places. Total
+            the clearing member owes or is owed, inclusive of PnL, fees, and
+            maintenance margin changes. Negative means the clearing member pays
+            Kalshi Klear; positive means Kalshi Klear pays the clearing member.
+        amount_fp:
+          type: string
+          description: >-
+            amount_centicents as a fixed-point dollar string with 6 decimal
+            places (e.g. "10005.000000").
+        pnl_fp:
+          type: string
+          description: pnl_centicents as a fixed-point dollar string with 6 decimal places.
+        - amount_fp
+        amount_fp:
+          type: string
+          description: >-
+            amount_centicents as a fixed-point dollar string with 6 decimal
+            places.
+        - pnl_fp
+        - total_amount_fp
+        pnl_fp:
+          type: string
+          description: pnl_centicents as a fixed-point dollar string with 6 decimal places.
+        total_amount_fp:
+          type: string
+          description: >-
+            total_amount_centicents as a fixed-point dollar string with 6
+            decimal places.
+        - notional_value_fp
+        - funding_amount_fp
+        notional_value_fp:
+          type: string
+          description: >-
+            notional_value_centicents as a fixed-point dollar string with 6
+            decimal places.
+        funding_amount_fp:
+          type: string
+          description: >-
+            funding_amount_centicents as a fixed-point dollar string with 6
+            decimal places.
+        - payment_amount_fp
+        payment_amount_fp:
+          type: string
+          description: >-
+            payment_amount_centicents as a fixed-point dollar string with 6
+            decimal places.
```

## Changed: kalshi/docs.kalshi.com/api-reference/get-funding-estimate-by-asset-class.md
```diff
+        - funding_amount_fp
+        funding_amount_fp:
+          type: string
+          description: >-
+            funding_amount_centicents as a fixed-point dollar string with 6
+            decimal places.
+        - funding_amount_fp
+        funding_amount_fp:
+          type: string
+          description: >-
+            funding_amount_centicents as a fixed-point dollar string with 6
+            decimal places.
```

## Changed: kalshi/docs.kalshi.com/api-reference/get-member-funding-payments.md
```diff
+        - notional_value_fp
+        - funding_amount_fp
+        notional_value_fp:
+          type: string
+          description: >-
+            notional_value_centicents as a fixed-point dollar string with 6
+            decimal places.
+        funding_amount_fp:
+          type: string
+          description: >-
+            funding_amount_centicents as a fixed-point dollar string with 6
+            decimal places.
```

## Changed: kalshi/docs.kalshi.com/api-reference/get-obligation-corporate-action-payments.md
```diff
+        - payment_amount_fp
+        payment_amount_fp:
+          type: string
+          description: >-
+            payment_amount_centicents as a fixed-point dollar string with 6
+            decimal places.
```

## Changed: kalshi/docs.kalshi.com/api-reference/get-obligation-funding-payments.md
```diff
+        - notional_value_fp
+        - funding_amount_fp
+        notional_value_fp:
+          type: string
+          description: >-
+            notional_value_centicents as a fixed-point dollar string with 6
+            decimal places.
+        funding_amount_fp:
+          type: string
+          description: >-
+            funding_amount_centicents as a fixed-point dollar string with 6
+            decimal places.
```

## Changed: kalshi/docs.kalshi.com/api-reference/get-obligation-history.md
```diff
+        - amount_fp
+        - pnl_fp
-            centicents). All monetary fields on this API are returned in
-            centicents. Total the clearing member owes or is owed, inclusive
-            of PnL, fees, and maintenance margin changes. Negative means the
-            clearing member pays Kalshi Klear; positive means Kalshi Klear
-            pays the clearing member.
+            centicents). Monetary fields ending in `_centicents` are integer
+            centicents; where an `_fp` counterpart exists, it carries the same
+            amount as a fixed-point dollar string with 6 decimal places. Total
+            the clearing member owes or is owed, inclusive of PnL, fees, and
+            maintenance margin changes. Negative means the clearing member pays
+            Kalshi Klear; positive means Kalshi Klear pays the clearing member.
+        amount_fp:
+          type: string
+          description: >-
+            amount_centicents as a fixed-point dollar string with 6 decimal
+            places (e.g. "10005.000000").
+        pnl_fp:
+          type: string
+          description: pnl_centicents as a fixed-point dollar string with 6 decimal places.
+        - amount_fp
+        amount_fp:
+          type: string
+          description: >-
+            amount_centicents as a fixed-point dollar string with 6 decimal
+            places.
+        - pnl_fp
+        - total_amount_fp
+        pnl_fp:
+          type: string
+          description: pnl_centicents as a fixed-point dollar string with 6 decimal places.
+        total_amount_fp:
+          type: string
+          description: >-
+            total_amount_centicents as a fixed-point dollar string with 6
+            decimal places.
+        - notional_value_fp
+        - funding_amount_fp
+        notional_value_fp:
+          type: string
+          description: >-
+            notional_value_centicents as a fixed-point dollar string with 6
+            decimal places.
+        funding_amount_fp:
+          type: string
+          description: >-
+            funding_amount_centicents as a fixed-point dollar string with 6
+            decimal places.
+        - payment_amount_fp
+        payment_amount_fp:
+          type: string
+          description: >-
+            payment_amount_centicents as a fixed-point dollar string with 6
+            decimal places.
```

## Changed: kalshi/docs.kalshi.com/api-reference/get-obligation-settlement-details.md
```diff
+        - pnl_fp
+        - total_amount_fp
+        pnl_fp:
+          type: string
+          description: pnl_centicents as a fixed-point dollar string with 6 decimal places.
+        total_amount_fp:
+          type: string
+          description: >-
+            total_amount_centicents as a fixed-point dollar string with 6
+            decimal places.
```

## Changed: kalshi/docs.kalshi.com/api-reference/get-settlement-balance-history.md
```diff
+        - balance_delta_fp
+        - locked_balance_delta_fp
+        balance_delta_fp:
+          type: string
+          description: >-
+            balance_delta_centicents as a fixed-point dollar string with 6
+            decimal places.
+        locked_balance_delta_fp:
+          type: string
+          description: >-
+            locked_balance_delta_centicents as a fixed-point dollar string with
+            6 decimal places.
```

## Changed: kalshi/docs.kalshi.com/api-reference/get-settlement-balance.md
```diff
+        - balance_available_fp
+        balance_available_fp:
+          type: string
+          description: >-
+            balance_available_centicents as a fixed-point dollar string with 6
+            decimal places.
+        locked_balance_fp:
+          type: string
+          description: >-
+            locked_balance_centicents as a fixed-point dollar string with 6
+            decimal places.
```

## Changed: kalshi/docs.kalshi.com/api-reference/get-settlement-estimate-by-asset-class.md
```diff
+        - settlement_balance_fp
+        settlement_balance_fp:
+          type: string
+          description: >-
+            settlement_balance_centicents as a fixed-point dollar string with 6
+            decimal places.
+        - variation_margin_fp
+        - total_amount_fp
+        variation_margin_fp:
+          type: string
+          description: >-
+            variation_margin_centicents as a fixed-point dollar string with 6
+            decimal places.
+        total_amount_fp:
+          type: string
+          description: >-
+            total_amount_centicents as a fixed-point dollar string with 6
+            decimal places.
+        - variation_margin_fp
+        - notional_value_fp
+        variation_margin_fp:
+          type: string
+          description: >-
+            variation_margin_centicents as a fixed-point dollar string with 6
+            decimal places.
+        notional_value_fp:
+          type: string
+          description: >-
+            notional_value_centicents as a fixed-point dollar string with 6
+            decimal places.
```

## Changed: kalshi/docs.kalshi.com/api-reference/historical/get-historical-market.md
```diff
-          description: Shortened title for the no side of this market
+          deprecated: true
+          description: >
+            Deprecated. Use `yes_sub_title` instead. Equal to `yes_sub_title` on
+            every market created since September 2024.
+          x-go-type: json.RawMessage
+          x-go-type-skip-optional-pointer: true
```

## Changed: kalshi/docs.kalshi.com/api-reference/historical/get-historical-markets.md
```diff
-          description: Shortened title for the no side of this market
+          deprecated: true
+          description: >
+            Deprecated. Use `yes_sub_title` instead. Equal to `yes_sub_title` on
+            every market created since September 2024.
+          x-go-type: json.RawMessage
+          x-go-type-skip-optional-pointer: true
```

## Changed: kalshi/docs.kalshi.com/api-reference/live-data/get-event-live-data.md
```diff
+          x-go-type: json.RawMessage
```

## Changed: kalshi/docs.kalshi.com/api-reference/live-data/get-live-data-with-type.md
```diff
+          x-go-type: json.RawMessage
```

## Changed: kalshi/docs.kalshi.com/api-reference/live-data/get-live-data.md
```diff
+          x-go-type: json.RawMessage
```

## Changed: kalshi/docs.kalshi.com/api-reference/live-data/get-multiple-live-data.md
```diff
+          x-go-type: json.RawMessage
```

## Changed: kalshi/docs.kalshi.com/api-reference/market/get-market.md
```diff
-          description: Shortened title for the no side of this market
+          deprecated: true
+          description: >
+            Deprecated. Use `yes_sub_title` instead. Equal to `yes_sub_title` on
+            every market created since September 2024.
+          x-go-type: json.RawMessage
+          x-go-type-skip-optional-pointer: true
```

## Changed: kalshi/docs.kalshi.com/api-reference/market/get-markets.md
```diff
-          description: Shortened title for the no side of this market
+          deprecated: true
+          description: >
+            Deprecated. Use `yes_sub_title` instead. Equal to `yes_sub_title` on
+            every market created since September 2024.
+          x-go-type: json.RawMessage
+          x-go-type-skip-optional-pointer: true
```

## Changed: kalshi/docs.kalshi.com/api-reference/milestone/get-milestone.md
```diff
+          x-go-type: json.RawMessage
```

## Changed: kalshi/docs.kalshi.com/api-reference/milestone/get-milestones.md
```diff
+          x-go-type: json.RawMessage
```

## Changed: kalshi/docs.kalshi.com/api-reference/multivariate/create-market-in-multivariate-event-collection.md
```diff
-          description: Shortened title for the no side of this market
+          deprecated: true
+          description: >
+            Deprecated. Use `yes_sub_title` instead. Equal to `yes_sub_title` on
+            every market created since September 2024.
+          x-go-type: json.RawMessage
+          x-go-type-skip-optional-pointer: true
```

## Changed: kalshi/docs.kalshi.com/api-reference/withdraw-settlement-balance.md
```diff
-````yaml /perps_scm_openapi.yaml post /margin/withdraw_settlement_balance
+````yaml /perps_scm_openapi.yaml post /margin/settlement_balance_withdrawal
-  /margin/withdraw_settlement_balance:
+  /margin/settlement_balance_withdrawal:
+        - mfa_code
-            Must be positive.
+            Must be positive and in whole cents.
+        mfa_code:
+          type: string
+          description: Code from the requesting user's multi-factor authentication method.
```

## Changed: kalshi/docs.kalshi.com/asyncapi.yaml
```diff
-    | 11 | Invalid parameter | Malformed parameter value |
+    | 11 | Invalid parameter | Malformed parameter value, or update_interval_ms differs from the existing orderbook subscription |
+    | 29 | update_interval_ms must be 0, 100 or 1000 | Invalid update_interval_ms on orderbook_delta |
-      - `get_snapshot` returns an `orderbook_snapshot` for the requested `market_tickers` without modifying the subscription
-
-      **Matching updates across connections:**
+      - `get_snapshot` returns an `orderbook_snapshot` for the requested `market_tickers` without changing the subscribed markets. Apply every returned snapshot as a replacement for that market's local book
+      - Each connection has one `orderbook_delta` subscription. A repeated `subscribe` adds new markets to its existing `sid`; it does not resend snapshots for markets already subscribed
+      - A repeated `subscribe` must use the existing `update_interval_ms` (omitting it means `0`). A different valid interval returns error code 11 without adding markets. Use `update_subscription` with `add_markets` to add markets without respecifying the interval. To change the interval, `unsubsc
+
+      **Matching real-time updates across connections:**
+      These guarantees apply to subscriptions created in real-time mode (the initial `update_interval_ms` is omitted or `0`). Conflated subscriptions have the separate clock and recovery rules below.
+
+
+      **Conflated updates (opt-in):**
+      - Set `update_interval_ms` to `100` or `1000` on the initial `subscribe` to receive conflated updates instead of one `orderbook_delta` per change. Omit it or set it to `0` for real-time updates, the default
+      - Each window produces one `orderbook_delta` per changed price level, carrying the net held change at that level. Levels whose held changes cancel out are skipped. Apply every delta in sequence to account for those changes exactly
+      - Each window's flush is scheduled one interval after that window's first held change; processing and transport latency are additional. `clock`, `ts` and `ts_ms` are those of the latest change included in each delta
+      - A conflated delta's `clock` describes that price level's latest included change, not the completeness of the whole book. Clocks can decrease between messages, including when an immediate own-order update overtakes a held public update. Do not discard a delta because its clock is lower than 
+      - Updates to your own orders bypass the interval, with the usual `client_order_id` and `subaccount` annotations when available. For a subaccount-restricted key, this applies only to orders within that restriction. Changes to other participants' orders, including liquidity your trades consume,
+      - Snapshots bypass the interval and replace any unsent held changes for their market. Always replace your local book with a received snapshot before applying subsequent deltas for that market
+      - Between flushes, public levels can lag your own orders, and the book can appear locked or crossed. A flush contains separate delta messages, not an atomic full-book snapshot; there is no end-of-flush message
+      - `seq` stays contiguous; the interval is fixed for the life of the subscription
+      - Apply every delta in `seq` order within its subscription. If you detect a gap, request `get_snapshot` for all subscribed markets, or `unsubscribe`, wait for `unsubscribed`, then `subscribe` again. A repeated `subscribe` alone does not resynchronize existing markets
+      - Conflation windows are independent across subscriptions, so the same clock can accompany different net deltas on different connections. Do not merge or deduplicate conflated streams by clock. When switching connections, rebuild from that connection's snapshot and follow its own `seq`
-      - RFQ batch events (RFQBatchCreated, RFQBatchDeleted) follow the RFQ event rules: sent for all users by default, or with `user_filter: "self"` only for RFQ batches you created. A batch's child RFQs are announced only inside RFQBatchCreated; each child trade still sends QuoteExecuted, with `rf
+      - RFQ batch events (RFQBatchCreated, RFQBatchDeleted) follow the RFQ event rules: sent for all users by default, or with `user_filter: "self"` only for RFQ batches you created. Each child RFQ of a batch also arrives as an RFQCreated with `rfq_batch_id` set, right after its batch's RFQBatchCre
+      - A child RFQ of a batch can be quoted like a single RFQ. Such a quote competes with batch quotes for that child, with the differences listed on `rfq_batch_id` in RFQCreated. Its QuoteCreated and QuoteAccepted go only to the maker, never to the batch creator, with `rfq_creator_id` set to the 
+        - name: subscribeToConflatedOrderbook
+          summary: Subscribe to orderbook_delta with 100 ms conflated updates
+          payload:
+            id: 16
+            cmd: subscribe
+            params:
+              channels: ["orderbook_delta"]
+              market_tickers: ["FED-23DEC-T3.00", "CORIVER-2024-T1030"]
+              update_interval_ms: 100
-        | 11 | Invalid parameter | Malformed parameter value |
+        | 11 | Invalid parameter | Malformed parameter value, or update_interval_ms differs from the existing orderbook subscription |
+        | 29 | update_interval_ms must be 0, 100 or 1000 | Invalid update_interval_ms on orderbook_delta |
-                no_sub_title: "NO 1"
+                no_sub_title: "YES 1"
-        Quote it with `POST /communications/rfq_batches/{rfq_batch_id}/quotes`, pricing every child. Each child is filled at the best bid across makers. Its child RFQs are not announced separately.
+        Quote it with `POST /communications/rfq_batches/{rfq_batch_id}/quotes`, pricing every child. Each child is filled at the best bid across makers. Each child RFQ is also announced as an `rfq_created` with `rfq_batch_id` set, and can be quoted like a single RFQ.
+            update_interval_ms:
+              type: integer
+              enum: [0, 100, 1000]
+              default: 0
+              description: |
+                Orderbook channel only. Opts in to conflated `orderbook_delta` updates: one net delta per
+                changed price level in each 100 or 1000 millisecond window. A window starts with its first
+                held change; processing and transport can delay delivery. Your own orders within any subaccount restriction and snapshots bypass the interval;
+                changes to other participants' orders, including liquidity you consume, remain held.
+                Omit or set to 0 for real-time updates, the default. Use JSON integer literals: other signed
+                64-bit integer literals return error code 29. Values that fail integer decoding, including
+                floating-point notation and strings, return code 1 for the whole command.
+                A repeated subscribe adds markets to the existing sid and must specify the same interval
+                (omission means 0); a mismatch returns error code 11. Change intervals by unsubscribing first.
+                Follow seq within this subscription; conflated clocks can decrease and cannot match or
+                deduplicate updates across connections. Recover gaps with fresh snapshots for all subscribed
+                markets or unsubscribe then subscribe again; a repeated subscribe alone does not resynchronize.
-                - 11: Invalid parameter - Malformed parameter value
+                - 11: Invalid parameter - Malformed parameter value, or update_interval_ms differs from the existing orderbook subscription
+                - 29: update_interval_ms must be 0, 100 or 1000 - Invalid update_interval_ms on orderbook_delta
-              enum: [1, 2, 3, 4, 5, 7, 8, 9, 10, 11, 12, 13, 14, 15, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28]
+              enum: [1, 2, 3, 4, 5, 7, 8, 9, 10, 11, 12, 13, 14, 15, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29]
-                Exchange clock of the exchange action that made this change. Every connection sends the same clock for the same change.
-                Several deltas can share a clock when one action makes several changes.
+                Exchange clock of the latest change included in this delta.
+                In real-time mode, every connection sends the same clock for the same change;
+                several deltas can share a clock.
+                In conflated mode, the latest exchange clock included in this price level's net delta.
+                It can decrease between messages and is not a whole-book watermark or a cross-connection
+                deduplication key. Apply deltas using this subscription's seq order.
+          deprecated: true
+          description: Deprecated. Use `yes_sub_title` instead. Equal to `yes_sub_title` on every market created since September 2024.
+            rfq_batch_id:
+              type: string
+              description: >-
+                Identifier of the RFQ batch this RFQ is a child of. Omitted for an
```
(22 more changed lines)

## Changed: kalshi/docs.kalshi.com/changelog/index.md
```diff
```
(97 more changed lines)

## Changed: kalshi/docs.kalshi.com/fix-margin/connectivity.md
```diff
```
(48 more changed lines)

## Changed: kalshi/docs.kalshi.com/fix-margin/drop-copy.md
```diff
```
(12 more changed lines)

## Changed: kalshi/docs.kalshi.com/fix-margin/order-entry.md
```diff
```
(16 more changed lines)

## Changed: kalshi/docs.kalshi.com/getting_started/api_environments.md
```diff
```
(11 more changed lines)

## Changed: kalshi/docs.kalshi.com/getting_started/exchange_sharding.md
```diff
```
(2 more changed lines)

## Changed: kalshi/docs.kalshi.com/getting_started/fee_rounding.md
```diff
```
(4 more changed lines)

## Changed: kalshi/docs.kalshi.com/getting_started/fixed_point_migration.md
```diff
```
(57 more changed lines)

## Changed: kalshi/docs.kalshi.com/getting_started/market_lifecycle.md
```diff
```
(6 more changed lines)

## Changed: kalshi/docs.kalshi.com/getting_started/quick_start_websockets.md
```diff
```
(8 more changed lines)

## Changed: kalshi/docs.kalshi.com/getting_started/rate_limits.md
```diff
```
(26 more changed lines)

## Changed: kalshi/docs.kalshi.com/getting_started/rfqs.md
```diff
```
(25 more changed lines)

## Changed: kalshi/docs.kalshi.com/llms-full.txt
```diff
```
(526 more changed lines)

## Changed: kalshi/docs.kalshi.com/llms.txt
```diff
```
(4 more changed lines)

## Changed: kalshi/docs.kalshi.com/margin.md
```diff
```
(4 more changed lines)

## Changed: kalshi/docs.kalshi.com/openapi.yaml
```diff
```
(598 more changed lines)

## Changed: kalshi/docs.kalshi.com/sdks/overview.md
```diff
```
(6 more changed lines)

## Changed: kalshi/docs.kalshi.com/websockets.md
```diff
```
(1 more changed lines)

## Changed: kalshi/docs.kalshi.com/websockets/communications.md
```diff
```
(437 more changed lines)

## Changed: kalshi/docs.kalshi.com/websockets/connection-keep-alive.md
```diff
```
(16 more changed lines)

## Changed: kalshi/docs.kalshi.com/websockets/market-and-event-lifecycle.md
```diff
```
(114 more changed lines)

## Changed: kalshi/docs.kalshi.com/websockets/market-positions.md
```diff
```
(20 more changed lines)

## Changed: kalshi/docs.kalshi.com/websockets/market-ticker.md
```diff
```
(30 more changed lines)

## Changed: kalshi/docs.kalshi.com/websockets/multivariate-market-and-event-lifecycle.md
```diff
```
(80 more changed lines)

## Changed: kalshi/docs.kalshi.com/websockets/order-group-updates.md
```diff
```
(12 more changed lines)

## Changed: kalshi/docs.kalshi.com/websockets/orderbook-updates.md
```diff
```
(172 more changed lines)

## Changed: kalshi/docs.kalshi.com/websockets/public-trades.md
```diff
```
(18 more changed lines)

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
(223 more changed lines)

## Changed: kalshi/docs.kalshi.com/welcome/index.md
```diff
```
(26 more changed lines)

## Changed: novig/docs.novig.com/affiliates/deeplinking.md
```diff
```
(4 more changed lines)

## Changed: novig/docs.novig.com/affiliates/futures.md
```diff
```
(92 more changed lines)

## Changed: novig/docs.novig.com/affiliates/odds-screens.md
```diff
```
(162 more changed lines)

## Changed: novig/docs.novig.com/affiliates/overview.md
```diff
```
(20 more changed lines)

## Changed: novig/docs.novig.com/affiliates/parlay-pricing.md
```diff
```
(111 more changed lines)

## Changed: novig/docs.novig.com/api-reference/catalog/list-live-market-types.md
```diff
```
(4 more changed lines)

## Changed: novig/docs.novig.com/api-reference/changelog.md
```diff
```
(54 more changed lines)

## Changed: novig/docs.novig.com/api-reference/rfq/webhooks.md
```diff
```
(8 more changed lines)

## Changed: novig/docs.novig.com/api-reference/rfq/websocket.md
```diff
```
(40 more changed lines)

## Changed: novig/docs.novig.com/api-reference/spec-files/openapi-v3.json
```diff
```
(149 more changed lines)

## Changed: novig/docs.novig.com/api-reference/throttle/get-your-throttle-schedule.md
```diff
```
(11 more changed lines)

## Changed: novig/docs.novig.com/api/errors.md
```diff
```
(169 more changed lines)

## Changed: novig/docs.novig.com/api/signing.md
```diff
```
(2 more changed lines)

## Changed: novig/docs.novig.com/api/streaming/book.md
```diff
```
(3 more changed lines)

## Changed: novig/docs.novig.com/api/streaming/connection.md
```diff
```
(4 more changed lines)

## Changed: novig/docs.novig.com/api/streaming/lifecycle.md
```diff
```
(26 more changed lines)

## Changed: novig/docs.novig.com/api/streaming/private.md
```diff
```
(156 more changed lines)

## Changed: novig/docs.novig.com/api/subaccounts/funding.md
```diff
```
(46 more changed lines)

## Changed: novig/docs.novig.com/api/subaccounts/manage.md
```diff
```
(36 more changed lines)

## Changed: novig/docs.novig.com/api/throttling.md
```diff
```
(33 more changed lines)

## Changed: novig/docs.novig.com/fees.md
```diff
```
(4 more changed lines)

## Changed: novig/docs.novig.com/index.md
```diff
```
(12 more changed lines)

## Changed: novig/docs.novig.com/llms-full.txt
```diff
```
(745 more changed lines)

## Changed: novig/docs.novig.com/llms.txt
```diff
```
(7 more changed lines)

## Changed: oddspapi/docs.oddspapi.io/_llms/zh.txt
```diff
```
(7 more changed lines)

## Changed: oddspapi/docs.oddspapi.io/api-reference/authentication.md
```diff
```
(20 more changed lines)

## Changed: oddspapi/docs.oddspapi.io/api-reference/concepts.md
```diff
```
(16 more changed lines)

## Changed: oddspapi/docs.oddspapi.io/api-reference/reliability.md
```diff
```
(8 more changed lines)

## Changed: oddspapi/docs.oddspapi.io/changelog/index.md
```diff
```
(8 more changed lines)

## Changed: oddspapi/docs.oddspapi.io/coverage.md
```diff
```
(8 more changed lines)

## Changed: oddspapi/docs.oddspapi.io/coverage/bookmakers.md
```diff
```
(4 more changed lines)

## Changed: oddspapi/docs.oddspapi.io/guides/evaluation.md
```diff
```
(11 more changed lines)

## Changed: oddspapi/docs.oddspapi.io/guides/parlays.md
```diff
```
(70 more changed lines)

## Changed: oddspapi/docs.oddspapi.io/guides/periods.md
```diff
```
(14 more changed lines)

## Changed: oddspapi/docs.oddspapi.io/guides/prediction-markets.md
```diff
```
(2 more changed lines)

## Changed: oddspapi/docs.oddspapi.io/guides/sharp-line-trading.md
```diff
```
(4 more changed lines)

## Changed: oddspapi/docs.oddspapi.io/index.md
```diff
```
(44 more changed lines)

## Changed: oddspapi/docs.oddspapi.io/llms-full.txt
```diff
```
(128 more changed lines)

## Changed: oddspapi/docs.oddspapi.io/llms.txt
```diff
```
(9 more changed lines)

## Changed: oddspapi/docs.oddspapi.io/quickstart.md
```diff
```
(84 more changed lines)

## Changed: oddspapi/docs.oddspapi.io/websocket/channels/odds.md
```diff
```
(4 more changed lines)

## Changed: oddspapi/docs.oddspapi.io/websocket/compression.md
```diff
```
(130 more changed lines)

## Changed: oddspapi/oddspapi.io/llms.txt
```diff
```
(18 more changed lines)

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

## Changed: pinnapi/pinnapi.com/docs.html
```diff
```
(15 more changed lines)

## Changed: pinnapi/pinnapi.com/llms-full.txt
```diff
```
(19 more changed lines)

## Changed: pinnodds/pinnodds.com/docs.html
```diff
```
(824 more changed lines)

## Changed: pinnodds/pinnodds.com/llms-full.txt
```diff
```
(7 more changed lines)

## Changed: pinnodds/pinnodds.com/llms.txt
```diff
```
(2 more changed lines)

## Changed: pinnwire/pinnwire.com/docs.html
```diff
```
(6 more changed lines)

## Changed: pinnwire/pinnwire.com/llms-full.txt
```diff
```
(4 more changed lines)

## Changed: polymarket/docs.polymarket.com/_llms/cn.txt
```diff
```
(5 more changed lines)

## Changed: polymarket/docs.polymarket.com/_llms/cn/api.txt
```diff
```
(20 more changed lines)

## Changed: polymarket/docs.polymarket.com/_llms/en/api-reference.txt
```diff
```
(3 more changed lines)

## Changed: polymarket/docs.polymarket.com/_llms/en/api-reference/predictions.txt
```diff
```
(20 more changed lines)

## Changed: polymarket/docs.polymarket.com/changelog/perps.md
```diff
```
(171 more changed lines)

## Changed: polymarket/docs.polymarket.com/changelog/predictions.md
```diff
```
(7 more changed lines)

## Changed: polymarket/docs.polymarket.com/changelog/sdks.md
```diff
```
(2074 more changed lines)

## Changed: polymarket/docs.polymarket.com/concepts/markets-events.md
```diff
```
(4 more changed lines)

## Changed: polymarket/docs.polymarket.com/concepts/negative-risk.md
```diff
```
(4 more changed lines)

## Changed: polymarket/docs.polymarket.com/concepts/order-lifecycle.md
```diff
```
(4 more changed lines)

## Changed: polymarket/docs.polymarket.com/concepts/prices-orderbook.md
```diff
```
(4 more changed lines)

## Changed: polymarket/docs.polymarket.com/concepts/pusd.md
```diff
```
(4 more changed lines)

## Changed: polymarket/docs.polymarket.com/getting-started/api.md
```diff
```
(114 more changed lines)

## Changed: polymarket/docs.polymarket.com/getting-started/python.md
```diff
```
(12 more changed lines)

## Changed: polymarket/docs.polymarket.com/getting-started/sdks-apis.md
```diff
```
(6 more changed lines)

## Changed: polymarket/docs.polymarket.com/getting-started/typescript.md
```diff
```
(250 more changed lines)

## Changed: polymarket/docs.polymarket.com/index.md
```diff
```
(6 more changed lines)

## Changed: polymarket/docs.polymarket.com/llms-full.txt
```diff
```
(78360 more changed lines)

## Changed: polymarket/docs.polymarket.com/llms.txt
```diff
```
(14 more changed lines)

## Changed: polymarket/docs.polymarket.com/market-data/discover-markets.md
```diff
```
(3998 more changed lines)

## Changed: polymarket/docs.polymarket.com/market-data/market-details.md
```diff
```
(1822 more changed lines)

## Changed: polymarket/docs.polymarket.com/market-data/overview.md
```diff
```
(12 more changed lines)

## Changed: polymarket/docs.polymarket.com/market-data/prices-order-books.md
```diff
```
(2278 more changed lines)

## Changed: polymarket/docs.polymarket.com/market-data/public-analytics.md
```diff
```
(2832 more changed lines)

## Changed: polymarket/docs.polymarket.com/market-data/realtime-data.md
```diff
```
(3732 more changed lines)

## Changed: polymarket/docs.polymarket.com/migrate/clob-sdk-to-unified-sdk.md
```diff
```
(4066 more changed lines)

## Changed: polymarket/docs.polymarket.com/migrate/data-api-v1-to-v2.md
```diff
```
(17 more changed lines)

## Changed: polymarket/docs.polymarket.com/migrate/polymarket-v2/sdk-integrations.md
```diff
```
(361 more changed lines)

## Changed: polymarket/docs.polymarket.com/migrate/rtds-to-polybolt.md
```diff
```
(1362 more changed lines)

## Changed: polymarket/docs.polymarket.com/perps/account-management.md
```diff
```
(2910 more changed lines)

## Changed: polymarket/docs.polymarket.com/perps/authenticated-sessions.md
```diff
```
(1326 more changed lines)

## Changed: polymarket/docs.polymarket.com/perps/builder-codes.md
```diff
```
(5498 more changed lines)

## Changed: polymarket/docs.polymarket.com/perps/errors.md
```diff
```
(13 more changed lines)

## Changed: polymarket/docs.polymarket.com/perps/faq.md
```diff
```
(66 more changed lines)

## Changed: polymarket/docs.polymarket.com/perps/fund-your-account.md
```diff
```
(2114 more changed lines)

## Changed: polymarket/docs.polymarket.com/perps/learn-about-trading/margin.md
```diff
```
(2 more changed lines)

## Changed: polymarket/docs.polymarket.com/perps/learn-about-trading/overview.md
```diff
```
(20 more changed lines)

## Changed: polymarket/docs.polymarket.com/perps/market-data.md
```diff
```
(1898 more changed lines)

## Changed: polymarket/docs.polymarket.com/perps/notifications.md
```diff
```
(1658 more changed lines)

## Changed: polymarket/docs.polymarket.com/perps/overview.md
```diff
```
(14 more changed lines)

## Changed: polymarket/docs.polymarket.com/perps/place-your-first-trade.md
```diff
```
(430 more changed lines)

## Changed: polymarket/docs.polymarket.com/perps/realtime-updates.md
```diff
```
(2058 more changed lines)

## Changed: polymarket/docs.polymarket.com/perps/trading.md
```diff
```
(6048 more changed lines)

## Changed: polymarket/docs.polymarket.com/polymarket-101.md
```diff
```
(4 more changed lines)

## Changed: polymarket/docs.polymarket.com/programs/builders/fees.md
```diff
```
(6 more changed lines)

## Changed: polymarket/docs.polymarket.com/programs/builders/overview.md
```diff
```
(12 more changed lines)

## Changed: polymarket/docs.polymarket.com/programs/builders/tiers.md
```diff
```
(14 more changed lines)

## Changed: polymarket/docs.polymarket.com/programs/liquidity-rewards.md
```diff
```
(422 more changed lines)

## Changed: polymarket/docs.polymarket.com/programs/maker-rebates.md
```diff
```
(166 more changed lines)

## Changed: polymarket/docs.polymarket.com/programs/referral-program.md
```diff
```
(14 more changed lines)

## Changed: polymarket/docs.polymarket.com/programs/taker-rebates.md
```diff
```
(152 more changed lines)

## Changed: polymarket/docs.polymarket.com/resources/blockchain-data.md
```diff
```
(14 more changed lines)

## Changed: polymarket/docs.polymarket.com/resources/contracts.md
```diff
```
(4 more changed lines)

## Changed: polymarket/docs.polymarket.com/trading/bridge/deposit.md
```diff
```
(4 more changed lines)

## Changed: polymarket/docs.polymarket.com/trading/bridge/quote.md
```diff
```
(4 more changed lines)

## Changed: polymarket/docs.polymarket.com/trading/bridge/status.md
```diff
```
(4 more changed lines)

## Changed: polymarket/docs.polymarket.com/trading/bridge/supported-assets.md
```diff
```
(4 more changed lines)

## Changed: polymarket/docs.polymarket.com/trading/bridge/withdraw.md
```diff
```
(4 more changed lines)

## Changed: polymarket/docs.polymarket.com/trading/combos/builders.md
```diff
```
(1186 more changed lines)

## Changed: polymarket/docs.polymarket.com/trading/combos/collateral-return.md
```diff
```
(2268 more changed lines)

## Changed: polymarket/docs.polymarket.com/trading/combos/market-makers.md
```diff
```
(7256 more changed lines)

## Changed: polymarket/docs.polymarket.com/trading/combos/overview.md
```diff
```
(4 more changed lines)

## Changed: polymarket/docs.polymarket.com/trading/combos/requesters.md
```diff
```
(1020 more changed lines)

## Changed: polymarket/docs.polymarket.com/trading/fees.md
```diff
```
(216 more changed lines)

## Changed: polymarket/docs.polymarket.com/trading/manage-orders.md
```diff
```
(3286 more changed lines)

## Changed: polymarket/docs.polymarket.com/trading/market-making.md
```diff
```
(4 more changed lines)

## Changed: polymarket/docs.polymarket.com/trading/matching-engine.md
```diff
```
(554 more changed lines)

## Changed: polymarket/docs.polymarket.com/trading/overview.md
```diff
```
(8 more changed lines)

## Changed: polymarket/docs.polymarket.com/trading/place-orders.md
```diff
```
(3101 more changed lines)

## Changed: polymarket/docs.polymarket.com/trading/positions/combinatorial.md
```diff
```
(6 more changed lines)

## Changed: polymarket/docs.polymarket.com/trading/positions/how-positions-work.md
```diff
```
(6 more changed lines)

## Changed: polymarket/docs.polymarket.com/trading/positions/manage.md
```diff
```
(1308 more changed lines)

## Changed: polymarket/docs.polymarket.com/trading/quickstart.md
```diff
```
(316 more changed lines)

## Changed: polymarket/docs.polymarket.com/trading/realtime-order-updates.md
```diff
```
(862 more changed lines)

## Changed: polymarket/docs.polymarket.com/trading/session-keys.md
```diff
```
(2304 more changed lines)

## Changed: polymarket/docs.polymarket.com/trading/wallet-activity.md
```diff
```
(2280 more changed lines)

## Changed: polymarket/docs.polymarket.com/trading/wallets-auth.md
```diff
```
(2642 more changed lines)

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
(117 more changed lines)

## Changed: propline/prop-line.com/llms-full.txt
```diff
```
(130 more changed lines)

## Changed: propline/prop-line.com/llms.txt
```diff
```
(24 more changed lines)

## Changed: propline/prop-line.com/openapi.json
```diff
```
(1175 more changed lines)

## Changed: propline/prop-line.com/recipes.html
```diff
```
(12 more changed lines)

## Changed: propline/prop-line.com/sportsbook-accuracy.html
```diff
```
(2 more changed lines)

## Changed: sharpapi/docs.sharpapi.io/llms-full.txt
```diff
```
(42 more changed lines)

## Changed: sharpapi/docs.sharpapi.io/llms.txt
```diff
```
(2 more changed lines)

## Changed: sharpapi/docs.sharpapi.io/openapi.json
```diff
```
(1012 more changed lines)

## Changed: sportsgameodds/sportsgameodds.com/docs.html
```diff
```
(4 more changed lines)

## Changed: sportsgameodds/sportsgameodds.com/docs/llms-full.txt
```diff
```
(53 more changed lines)

## Changed: sxbet/api.docs.sx.bet/index.html
```diff
```
(14 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/channel-fixtures.md
```diff
```
(6 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/channel-orderbook-v3-event.md
```diff
```
(4 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/channel-orderbook-v3.md
```diff
```
(3 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/delete-orders-v3-all.md
```diff
```
(10 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/delete-orders-v3-event.md
```diff
```
(11 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/delete-orders-v3-market.md
```diff
```
(10 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/get-best-odds-v3.md
```diff
```
(6 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/get-event-orderbook-snapshot.md
```diff
```
(8 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/get-live-scores.md
```diff
```
(5 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/get-market-orderbook-snapshot.md
```diff
```
(9 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/introduction.md
```diff
```
(8 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/market-types.md
```diff
```
(5 more changed lines)

## Changed: sxbet/docs.sx.bet/api-reference/testnet.md
```diff
```
(8 more changed lines)

## Changed: sxbet/docs.sx.bet/developers/account-models.md
```diff
```
(6 more changed lines)

## Changed: sxbet/docs.sx.bet/developers/accounts.md
```diff
```
(10 more changed lines)

## Changed: sxbet/docs.sx.bet/developers/api-key-permissions.md
```diff
```
(4 more changed lines)

## Changed: sxbet/docs.sx.bet/developers/balances-and-ledger.md
```diff
```
(4 more changed lines)

## Changed: sxbet/docs.sx.bet/developers/bet-lifecycle.md
```diff
```
(4 more changed lines)

## Changed: sxbet/docs.sx.bet/developers/book-versioning.md
```diff
```
(6 more changed lines)

## Changed: sxbet/docs.sx.bet/developers/cancelling-orders.md
```diff
```
(10 more changed lines)

## Changed: sxbet/docs.sx.bet/developers/capital-efficiency.md
```diff
```
(8 more changed lines)

## Changed: sxbet/docs.sx.bet/developers/client-order-id.md
```diff
```
(12 more changed lines)

## Changed: sxbet/docs.sx.bet/developers/exchange-model.md
```diff
```
(6 more changed lines)

## Changed: sxbet/docs.sx.bet/developers/external-user-id.md
```diff
```
(12 more changed lines)

## Changed: sxbet/docs.sx.bet/developers/fees.md
```diff
```
(6 more changed lines)

## Changed: sxbet/docs.sx.bet/developers/fetch-markets.md
```diff
```
(2 more changed lines)

## Changed: sxbet/docs.sx.bet/developers/funding.md
```diff
```
(6 more changed lines)

## Changed: sxbet/docs.sx.bet/developers/heartbeat.md
```diff
```
(6 more changed lines)

## Changed: sxbet/docs.sx.bet/developers/introduction.md
```diff
```
(20 more changed lines)

## Changed: sxbet/docs.sx.bet/developers/maintenance.md
```diff
```
(4 more changed lines)

## Changed: sxbet/docs.sx.bet/developers/market-making-parlays.md
```diff
```
(16 more changed lines)

## Changed: sxbet/docs.sx.bet/developers/market-making.md
```diff
```
(10 more changed lines)

## Changed: sxbet/docs.sx.bet/developers/markets-and-events.md
```diff
```
(8 more changed lines)

## Changed: sxbet/docs.sx.bet/developers/markets-overview.md
```diff
```
(4 more changed lines)

## Changed: sxbet/docs.sx.bet/developers/migrate-to-v3.md
```diff
```
(6 more changed lines)

## Changed: sxbet/docs.sx.bet/developers/my-orders.md
```diff
```
(12 more changed lines)

## Changed: sxbet/docs.sx.bet/developers/new-in-v3.md
```diff
```
(4 more changed lines)

## Changed: sxbet/docs.sx.bet/developers/odds-rounding.md
```diff
```
(6 more changed lines)

## Changed: sxbet/docs.sx.bet/developers/odds.md
```diff
```
(8 more changed lines)

## Changed: sxbet/docs.sx.bet/developers/order-book.md
```diff
```
(36 more changed lines)

## Changed: sxbet/docs.sx.bet/developers/order-lifecycle.md
```diff
```
(6 more changed lines)

## Changed: sxbet/docs.sx.bet/developers/parlays.md
```diff
```
(8 more changed lines)

## Changed: sxbet/docs.sx.bet/developers/posting-orders.md
```diff
```
(12 more changed lines)

## Changed: sxbet/docs.sx.bet/developers/public-trades.md
```diff
```
(8 more changed lines)

## Changed: sxbet/docs.sx.bet/developers/quarter-line-markets.md
```diff
```
(2 more changed lines)

## Changed: sxbet/docs.sx.bet/developers/quickstart.md
```diff
```
(4 more changed lines)

## Changed: sxbet/docs.sx.bet/developers/rate-limits.md
```diff
```
(4 more changed lines)

## Changed: sxbet/docs.sx.bet/developers/realtime-overview.md
```diff
```
(10 more changed lines)

## Changed: sxbet/docs.sx.bet/developers/realtime-reliability.md
```diff
```
(2 more changed lines)

## Changed: sxbet/docs.sx.bet/developers/risk-limits.md
```diff
```
(8 more changed lines)

## Changed: sxbet/docs.sx.bet/developers/taking-liquidity.md
```diff
```
(14 more changed lines)

## Changed: sxbet/docs.sx.bet/developers/testnet-and-mainnet.md
```diff
```
(8 more changed lines)

## Changed: sxbet/docs.sx.bet/developers/time-in-force.md
```diff
```
(8 more changed lines)

## Changed: sxbet/docs.sx.bet/developers/what-can-you-build.md
```diff
```
(16 more changed lines)

## Changed: sxbet/docs.sx.bet/developers/what-is-sx-bet.md
```diff
```
(8 more changed lines)

## Changed: sxbet/docs.sx.bet/developers/which-grain.md
```diff
```
(8 more changed lines)

## Changed: sxbet/docs.sx.bet/developers/work-with-us.md
```diff
```
(4 more changed lines)

## Changed: sxbet/docs.sx.bet/llms-full.txt
```diff
```
(581 more changed lines)

## Changed: sxbet/docs.sx.bet/user-guides/deposit-withdraw/deposit-from-coinbase.md
```diff
```
(4 more changed lines)

## Changed: sxbet/docs.sx.bet/user-guides/deposit-withdraw/deposit-from-wallet.md
```diff
```
(4 more changed lines)

## Changed: sxbet/docs.sx.bet/user-guides/deposit-withdraw/interac.md
```diff
```
(4 more changed lines)

## Changed: sxbet/docs.sx.bet/user-guides/deposit-withdraw/large-withdrawals.md
```diff
```
(4 more changed lines)

## Changed: sxbet/docs.sx.bet/user-guides/deposit-withdraw/transfer-crypto.md
```diff
```
(12 more changed lines)

## Changed: sxbet/docs.sx.bet/user-guides/deposit-withdraw/withdraw-interac.md
```diff
```
(4 more changed lines)

## Changed: sxbet/docs.sx.bet/user-guides/deposit-withdraw/withdraw-to-exchange.md
```diff
```
(4 more changed lines)

## Changed: sxbet/docs.sx.bet/user-guides/faq/export-wallet-metamask.md
```diff
```
(4 more changed lines)

## Changed: sxbet/docs.sx.bet/user-guides/getting-started/how-it-works.md
```diff
```
(8 more changed lines)

## Changed: sxbet/docs.sx.bet/user-guides/getting-started/overview.md
```diff
```
(10 more changed lines)

## Changed: sxbet/docs.sx.bet/user-guides/getting-started/quickstart.md
```diff
```
(10 more changed lines)

## Changed: sxbet/docs.sx.bet/user-guides/markets/finding-markets.md
```diff
```
(4 more changed lines)

## Changed: sxbet/docs.sx.bet/user-guides/more/betting-rules.md
```diff
```
(24 more changed lines)

## Changed: sxbet/docs.sx.bet/user-guides/rewards/bet-credits.md
```diff
```
(4 more changed lines)

## Changed: sxbet/docs.sx.bet/user-guides/rewards/maker-rewards.md
```diff
```
(20 more changed lines)

## Changed: sxbet/docs.sx.bet/user-guides/rewards/tournaments.md
```diff
```
(4 more changed lines)

## Changed: sxbet/docs.sx.bet/user-guides/trading/capital-efficiency.md
```diff
```
(8 more changed lines)

## Changed: sxbet/docs.sx.bet/user-guides/trading/fees.md
```diff
```
(4 more changed lines)

## Changed: sxbet/docs.sx.bet/user-guides/trading/limit-orders.md
```diff
```
(2 more changed lines)

## Changed: sxbet/docs.sx.bet/user-guides/trading/market-orders.md
```diff
```
(4 more changed lines)

## Changed: sxbet/docs.sx.bet/user-guides/trading/order-book.md
```diff
```
(6 more changed lines)

## Changed: sxbet/docs.sx.bet/user-guides/trading/parlays.md
```diff
```
(4 more changed lines)

## Changed: theoddsapi/the-odds-api.com/liveapi/guides/v4.html
```diff
```
(4 more changed lines)

## Changed: theoddsapi/the-odds-api.com/llms-full.txt
```diff
```
(4 more changed lines)

## Changed: theoddsapi/the-odds-api.com/llms.txt
```diff
```
(4 more changed lines)

## Changed: theoddsapi/the-odds-api.com/sports-odds-data/betting-markets.html
```diff
```
(4 more changed lines)

## Changed: theoddsapi/the-odds-api.com/sports-odds-data/bookmaker-apis.html
```diff
```
(4 more changed lines)

