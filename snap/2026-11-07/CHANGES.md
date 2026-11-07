# API docs changes since the last snapshot

added 3, changed 199, removed 0

## Added
- kalshi/docs.kalshi.com/api-reference/communications/cancel-block-trade-proposal.md
- kalshi/docs.kalshi.com/api-reference/communications/reject-block-trade-proposal.md
- kalshi/docs.kalshi.com/getting_started/mcp.md

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
-{"newest_odds_age_s":4,"status":"ok"}
+{"newest_odds_age_s":16,"status":"ok"}
```

## Changed: data/sxbet-leagues.json
```diff
-{"status":"success","data":[{"leagueId":1,"label":"NBA","sportId":1,"popularity":3912351.568519,"eventsByType":{"first-half-lines":20,"first-period-lines":15,"game-lines":30,"second-period-lines":15,"third-period-lines":15}},{"leagueId":246,"label":"NCAAF","sportId":8,"popularity":2570760.53184,"ev
+{"status":"success","data":[{"leagueId":1,"label":"NBA","sportId":1,"popularity":3924290.149177,"eventsByType":{"first-half-lines":11,"first-period-lines":6,"game-lines":22,"second-period-lines":6,"third-period-lines":6}},{"leagueId":246,"label":"NCAAF","sportId":8,"popularity":2361942.137149,"even
```

## Changed: kalshi/docs.kalshi.com/api-reference/account/get-account-api-limits.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/account/get-account-api-usage-level-volume-progress.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/account/list-non-default-endpoint-costs.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/account/upgrade-account-api-usage-level.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/api-keys/create-api-key.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/api-keys/delete-api-key.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/api-keys/generate-api-key.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/api-keys/get-api-keys.md
```diff
-  version: 3.35.0
+  version: 3.36.0
+        key_type:
+          allOf:
+            - $ref: '#/components/schemas/ApiKeyType'
+          description: >-
+            Signing algorithm of this key, derived from its stored public key.
+            Present for RSA and Ed25519 keys, including existing keys. Omitted
+            when the algorithm is unknown.
+    ApiKeyType:
+      type: string
+      enum:
+        - rsa
+        - ed25519
+      x-enum-varnames:
+        - ApiKeyTypeRsa
+        - ApiKeyTypeEd25519
+      description: >-
+        Signature algorithm of an API key pair. `rsa` - 2048-bit RSA; requests
+        are signed with RSA-PSS SHA-256. `ed25519` - Ed25519 (RFC 8032)
+        signatures over the same pre-sign text, with lower client-side signing
+        cost. Defaults to `rsa` when omitted from a generate request, for
+        compatibility with existing clients.
```

## Changed: kalshi/docs.kalshi.com/api-reference/communications/accept-block-trade-proposal.md
```diff
->  Endpoint for accepting a block trade proposal.
+>  Endpoint for accepting a block trade proposal. The caller accepts as whichever side it is named on, and may book that side onto any of its own subaccounts, including one the proposal did not name. Without a subaccount, the side keeps the one the proposal named. A side that has already accepted m
-  version: 3.35.0
+  version: 3.36.0
-      description: ' Endpoint for accepting a block trade proposal.'
+      description: ' Endpoint for accepting a block trade proposal. The caller accepts as whichever side it is named on, and may book that side onto any of its own subaccounts, including one the proposal did not name. Without a subaccount, the side keeps the one the proposal named. A side that has 
-            Subtrader ID to accept as. Provide either this or subaccount, not
-            both.
+            Subtrader ID to book your side onto. It may differ from the one the
+            proposal named. Omit both this and subaccount to keep the
+            proposal's. Provide either this or subaccount, not both.
-            User-managed subaccount number to accept as (0 for primary, 1-63 for
-            numbered subaccounts). Provide either this or subtrader_id, not
-            both.
+            User-managed subaccount number to book your side onto (0 for
+            primary, 1-63 for numbered subaccounts). It may differ from the one
+            the proposal named. Omit both this and subtrader_id to keep the
+            proposal's. Provide either this or subtrader_id, not both.
```

## Changed: kalshi/docs.kalshi.com/api-reference/communications/accept-quote.md
```diff
-> DEPRECATED: Use PUT /communications/rfqs/{rfq_id}/quotes/{quote_id}/accept instead. Endpoint for accepting a quote. This will require the quoter to confirm.
+> DEPRECATED: Use PUT /communications/rfqs/{rfq_id}/quotes/{quote_id}/accept instead.
-  version: 3.35.0
+  version: 3.36.0
-        /communications/rfqs/{rfq_id}/quotes/{quote_id}/accept instead. Endpoint
-        for accepting a quote. This will require the quoter to confirm.
+        /communications/rfqs/{rfq_id}/quotes/{quote_id}/accept instead.
```

## Changed: kalshi/docs.kalshi.com/api-reference/communications/accept-rfq-quote.md
```diff
->  Endpoint for accepting a quote scoped to its RFQ. This will require the quoter to confirm.
+> Endpoint for accepting a quote scoped to its RFQ. This will require the quoter to confirm. The quote that fills can be another maker's quote at the same price; see [RFQs](/getting_started/rfqs#flow).
-  version: 3.35.0
+  version: 3.36.0
-      description: ' Endpoint for accepting a quote scoped to its RFQ. This will require the quoter to confirm.'
+      description: >-
+        Endpoint for accepting a quote scoped to its RFQ. This will require the
+        quoter to confirm. The quote that fills can be another maker's quote at
+        the same price; see [RFQs](/getting_started/rfqs#flow).
```

## Changed: kalshi/docs.kalshi.com/api-reference/communications/confirm-quote.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/communications/confirm-rfq-quote.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/communications/create-quote.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/communications/create-rfq.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/communications/delete-quote.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/communications/delete-rfq-quote.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/communications/delete-rfq.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/communications/get-block-trade-proposals.md
```diff
-  version: 3.35.0
+  version: 3.36.0
-          description: Current status of the proposal
+          description: >-
+            Current status of the proposal: pending, executed, cancelled (by the
+            proposer, or on expiry) or rejected (by a counterparty)
```

## Changed: kalshi/docs.kalshi.com/api-reference/communications/get-communications-id.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/communications/get-quote.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/communications/get-quotes.md
```diff
-> List quotes. The rfq_id filter requires an RFQ UUID. Pass pagination cursors back unchanged; malformed RFQ IDs or cursors return HTTP 400.
+> List quotes. The rfq_id filter requires an RFQ UUID. With rfq_id, quotes are sorted on the RFQ's main side (no for a target-cost RFQ, yes otherwise): the best price first, then the earliest place in line at that price. Pass pagination cursors back unchanged; malformed RFQ IDs or cursors return HT
-  version: 3.35.0
+  version: 3.36.0
-        List quotes. The rfq_id filter requires an RFQ UUID. Pass pagination
-        cursors back unchanged; malformed RFQ IDs or cursors return HTTP 400.
+        List quotes. The rfq_id filter requires an RFQ UUID. With rfq_id, quotes
+        are sorted on the RFQ's main side (no for a target-cost RFQ, yes
+        otherwise): the best price first, then the earliest place in line at
+        that price. Pass pagination cursors back unchanged; malformed RFQ IDs or
+        cursors return HTTP 400.
```

## Changed: kalshi/docs.kalshi.com/api-reference/communications/get-rfq-quote.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/communications/get-rfq.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/communications/get-rfqs.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/communications/propose-block-trade.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/events/get-event-candlesticks.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/events/get-event-fee-changes.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/events/get-event-forecast-percentile-history.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/events/get-event-metadata.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/events/get-event.md
```diff
-  version: 3.35.0
+  version: 3.36.0
+          x-enumNames:
+            - MarketMarketTypeBinary
+            - MarketMarketTypeScalar
```

## Changed: kalshi/docs.kalshi.com/api-reference/events/get-events.md
```diff
-  version: 3.35.0
+  version: 3.36.0
+          x-enumNames:
+            - MarketMarketTypeBinary
+            - MarketMarketTypeScalar
```

## Changed: kalshi/docs.kalshi.com/api-reference/events/get-multivariate-events.md
```diff
-  version: 3.35.0
+  version: 3.36.0
+          x-enumNames:
+            - MarketMarketTypeBinary
+            - MarketMarketTypeScalar
```

## Changed: kalshi/docs.kalshi.com/api-reference/exchange/get-exchange-schedule.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/exchange/get-exchange-status.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/exchange/get-series-fee-changes.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/exchange/get-user-data-timestamp.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/fcm/create-fcm-position-transfer.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/fcm/create-fcm-subtrader.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/fcm/delete-fcm-subtrader-event-contract-daily-cap.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/fcm/get-fcm-fills.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/fcm/get-fcm-orders.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/fcm/get-fcm-positions.md
```diff
-  version: 3.35.0
+  version: 3.36.0
+        - netting_enabled
-          description: Total spent on this market in dollars
+          description: >-
+            Total spent trading this market in dollars, including past trades;
+            not the remaining position's cost or value.
-            String representation of the number of contracts bought in this
-            market. Negative means NO contracts and positive means YES contracts
+            Signed net contracts as a fixed-point string: positive for YES,
+            negative for NO, zero for no net position.
-          description: Cost of the aggregate market position in dollars
+          description: >-
+            Cost basis of the remaining position in dollars, excluding fees; not
+            current market value or realized loss.
+        netting_enabled:
+          type: boolean
+          description: >-
+            Whether netting applies to this position's event. Set at the
+            subaccount's first order in the event.
```

## Changed: kalshi/docs.kalshi.com/api-reference/fcm/get-fcm-subtrader-blocked-categories.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/fcm/get-fcm-subtrader-event-contract-daily-cap.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/fcm/list-fcm-subtraders.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/fcm/update-fcm-subtrader-blocked-categories.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/fcm/update-fcm-subtrader-event-contract-daily-cap.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/get-margin-reports.md
```diff
+            - trade_audit_fp
+            - position_transfer_audit_fp
+            - position_snapshot_fp
+            - funding_periods_fp
+            - settlement_periods_fp
+            - corporate_action_payments_fp
+            - cash_balance_daily_fp
+            - cash_activity_daily_fp
+            - daily_financial_summary_fp
+            - interest_on_collateral_monthly_fp
+            The `_fp` variants are companion CSV files with the same columns as
+            the original report, selected monetary amounts formatted to 6
+            decimal places, and contract quantities to 2 decimal places. Prices,
+            fees, and margin requirements retain their existing precision.
+            Companion filenames append `_fp` before `.csv`, after the date,
+            timestamp, or `intraday` suffix.
```

## Changed: kalshi/docs.kalshi.com/api-reference/get-obligation-history.md
```diff
+        - name: settlement_start_ts
+          in: query
+          required: false
+          schema:
+            type: string
+            format: date-time
+          description: Inclusive lower bound on settlement time.
+        - name: settlement_end_ts
+          in: query
+          required: false
+          schema:
+            type: string
+            format: date-time
+          description: Inclusive upper bound on settlement time.
```

## Changed: kalshi/docs.kalshi.com/api-reference/get-settlement-balance-history.md
```diff
+        - name: start_ts
+          in: query
+          required: false
+          schema:
+            type: string
+            format: date-time
+          description: Inclusive lower bound on entry creation time.
+        - name: end_ts
+          in: query
+          required: false
+          schema:
+            type: string
+            format: date-time
+          description: Inclusive upper bound on entry creation time.
```

## Changed: kalshi/docs.kalshi.com/api-reference/historical/get-historical-cutoff-timestamps.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/historical/get-historical-fills.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/historical/get-historical-market-candlesticks.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/historical/get-historical-market.md
```diff
-  version: 3.35.0
+  version: 3.36.0
+          x-enumNames:
+            - MarketMarketTypeBinary
+            - MarketMarketTypeScalar
```

## Changed: kalshi/docs.kalshi.com/api-reference/historical/get-historical-markets.md
```diff
-  version: 3.35.0
+  version: 3.36.0
+          x-enumNames:
+            - MarketMarketTypeBinary
+            - MarketMarketTypeScalar
```

## Changed: kalshi/docs.kalshi.com/api-reference/historical/get-historical-orders.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/historical/get-historical-positions.md
```diff
-  version: 3.35.0
+  version: 3.36.0
+        - netting_enabled
-          description: Total spent on this market in dollars
+          description: >-
+            Total spent trading this market in dollars, including past trades;
+            not the remaining position's cost or value.
-            String representation of the number of contracts bought in this
-            market. Negative means NO contracts and positive means YES contracts
+            Signed net contracts as a fixed-point string: positive for YES,
+            negative for NO, zero for no net position.
-          description: Cost of the aggregate market position in dollars
+          description: >-
+            Cost basis of the remaining position in dollars, excluding fees; not
+            current market value or realized loss.
+        netting_enabled:
+          type: boolean
+          description: >-
+            Whether netting applies to this position's event. Set at the
+            subaccount's first order in the event.
```

## Changed: kalshi/docs.kalshi.com/api-reference/historical/get-historical-trades.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/incentive-programs/get-incentives.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/live-data/get-event-live-data.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/live-data/get-game-stats.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/live-data/get-live-data-with-type.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/live-data/get-live-data.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/live-data/get-multiple-live-data.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/live-data/get-weather-index-calibrations.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/live-data/get-weather-index.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/market/batch-get-market-candlesticks.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/market/get-market-candlesticks.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/market/get-market-orderbook.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/market/get-market.md
```diff
-  version: 3.35.0
+  version: 3.36.0
+          x-enumNames:
+            - MarketMarketTypeBinary
+            - MarketMarketTypeScalar
```

## Changed: kalshi/docs.kalshi.com/api-reference/market/get-markets.md
```diff
-  version: 3.35.0
+  version: 3.36.0
+          x-enumNames:
+            - MarketMarketTypeBinary
+            - MarketMarketTypeScalar
```

## Changed: kalshi/docs.kalshi.com/api-reference/market/get-multiple-market-orderbooks.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/market/get-series-list.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/market/get-series.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/market/get-trades.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/milestone/get-milestone.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/milestone/get-milestones.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/multivariate/create-market-in-multivariate-event-collection.md
```diff
-  version: 3.35.0
+  version: 3.36.0
+          x-enumNames:
+            - MarketMarketTypeBinary
+            - MarketMarketTypeScalar
```

## Changed: kalshi/docs.kalshi.com/api-reference/multivariate/get-multivariate-event-collection.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/multivariate/get-multivariate-event-collections.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/order-groups/create-order-group.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/order-groups/delete-order-group.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/order-groups/get-order-group.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/order-groups/get-order-groups.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/order-groups/reset-order-group.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/order-groups/trigger-order-group.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/order-groups/update-order-group-limit.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/orders/amend-order-v2.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/orders/batch-cancel-orders-v2.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/orders/batch-create-orders-v2.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/orders/cancel-all-orders.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/orders/cancel-order-v2.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/orders/create-order-v2.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/orders/decrease-order-v2.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/orders/get-order-queue-position.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/orders/get-order.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/orders/get-orders.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/orders/get-queue-positions-for-orders.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/portfolio/create-subaccount.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/portfolio/get-all-subaccount-balances.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/portfolio/get-balance.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/portfolio/get-credit-history.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/portfolio/get-deposits.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/portfolio/get-fills.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/portfolio/get-intra-account-transfer.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/portfolio/get-intra-account-transfers.md
```diff
-  version: 3.35.0
+  version: 3.36.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/portfolio/get-positions.md
```diff
-> Returns unsettled positions by default. Set `settlement_status=settled` to page through settled positions that are still in the live data set, or `settlement_status=all` for both live states. Positions already archived are available via `GET /historical/positions`.
-Settled and all-state positions are paginated by event ticker to keep large histories bounded. Cursors are specific to the selected settlement status.
-`count_filter` restricts positions to those with a non-zero position or total traded count.
+> Returns unsettled positions by default, including closed markets awaiting determination or settlement. These may retain non-zero quantities and cost basis. Check the market's `status` via `GET /markets/{ticker}` for trading status.
+
+Set `settlement_status=settled` for unarchived settled positions or `all` for both states. These results paginate by event ticker; cursors are specific to settlement status. Archived positions are available via `GET /historical/positions`.
+
+Use `count_filter=position` for non-zero net holdings, including closed, unsettled markets. Without a count filter, results may include fully exited positions with trading history, realized P&L or fees.
+
-  version: 3.35.0
+  version: 3.36.0
-        Returns unsettled positions by default. Set `settlement_status=settled`
-        to page through settled positions that are still in the live data set,
-        or `settlement_status=all` for both live states. Positions already
-        archived are available via `GET /historical/positions`.
-
-        Settled and all-state positions are paginated by event ticker to keep
-        large histories bounded. Cursors are specific to the selected settlement
```
(50 more changed lines)

## Changed: kalshi/docs.kalshi.com/api-reference/portfolio/get-settlements.md
```diff
```
(19 more changed lines)

## Changed: kalshi/docs.kalshi.com/api-reference/portfolio/get-subaccount-netting.md
```diff
```
(2 more changed lines)

## Changed: kalshi/docs.kalshi.com/api-reference/portfolio/get-subaccount-transfers.md
```diff
```
(2 more changed lines)

## Changed: kalshi/docs.kalshi.com/api-reference/portfolio/get-target-balance-allocation.md
```diff
```
(2 more changed lines)

## Changed: kalshi/docs.kalshi.com/api-reference/portfolio/get-total-resting-order-value.md
```diff
```
(2 more changed lines)

## Changed: kalshi/docs.kalshi.com/api-reference/portfolio/get-withdrawals.md
```diff
```
(2 more changed lines)

## Changed: kalshi/docs.kalshi.com/api-reference/portfolio/intra-account-transfer.md
```diff
```
(2 more changed lines)

## Changed: kalshi/docs.kalshi.com/api-reference/portfolio/set-target-balance-allocation.md
```diff
```
(2 more changed lines)

## Changed: kalshi/docs.kalshi.com/api-reference/portfolio/transfer-between-subaccounts.md
```diff
```
(2 more changed lines)

## Changed: kalshi/docs.kalshi.com/api-reference/portfolio/update-subaccount-netting.md
```diff
```
(2 more changed lines)

## Changed: kalshi/docs.kalshi.com/api-reference/search/get-filters-for-sports.md
```diff
```
(2 more changed lines)

## Changed: kalshi/docs.kalshi.com/api-reference/search/get-tags-for-series-categories.md
```diff
```
(2 more changed lines)

## Changed: kalshi/docs.kalshi.com/api-reference/structured-targets/get-structured-target.md
```diff
```
(2 more changed lines)

## Changed: kalshi/docs.kalshi.com/api-reference/structured-targets/get-structured-targets.md
```diff
```
(2 more changed lines)

## Changed: kalshi/docs.kalshi.com/asyncapi.yaml
```diff
```
(5 more changed lines)

## Changed: kalshi/docs.kalshi.com/changelog/index.md
```diff
```
(78 more changed lines)

## Changed: kalshi/docs.kalshi.com/getting_started/rfqs.md
```diff
```
(8 more changed lines)

## Changed: kalshi/docs.kalshi.com/llms-full.txt
```diff
```
(677 more changed lines)

## Changed: kalshi/docs.kalshi.com/llms.txt
```diff
```
(31 more changed lines)

## Changed: kalshi/docs.kalshi.com/margin/price-banding.md
```diff
```
(7 more changed lines)

## Changed: kalshi/docs.kalshi.com/openapi.yaml
```diff
```
(130 more changed lines)

## Changed: kalshi/docs.kalshi.com/websockets/communications.md
```diff
```
(35 more changed lines)

## Changed: kalshi/docs.kalshi.com/welcome/index.md
```diff
```
(4 more changed lines)

## Changed: novig/docs.novig.com/api-reference/changelog.md
```diff
```
(24 more changed lines)

## Changed: novig/docs.novig.com/api-reference/execution/get-your-resting-orders.md
```diff
```
(8 more changed lines)

## Changed: novig/docs.novig.com/api-reference/rfq/overview.md
```diff
```
(1 more changed lines)

## Changed: novig/docs.novig.com/api-reference/rfq/webhooks.md
```diff
```
(3 more changed lines)

## Changed: novig/docs.novig.com/api-reference/rfq/websocket.md
```diff
```
(9 more changed lines)

## Changed: novig/docs.novig.com/api-reference/spec-files/openapi-v3.json
```diff
```
(191 more changed lines)

## Changed: novig/docs.novig.com/api-reference/spec-files/openapi31.json
```diff
```
(14 more changed lines)

## Changed: novig/docs.novig.com/api-reference/throttle/get-your-throttle-schedule.md
```diff
```
(60 more changed lines)

## Changed: novig/docs.novig.com/api/concepts/fees.md
```diff
```
(2 more changed lines)

## Changed: novig/docs.novig.com/api/concepts/participation-protection.md
```diff
```
(15 more changed lines)

## Changed: novig/docs.novig.com/api/errors.md
```diff
```
(1 more changed lines)

## Changed: novig/docs.novig.com/api/streaming/connection.md
```diff
```
(32 more changed lines)

## Changed: novig/docs.novig.com/api/streaming/private.md
```diff
```
(25 more changed lines)

## Changed: novig/docs.novig.com/api/throttling.md
```diff
```
(29 more changed lines)

## Changed: novig/docs.novig.com/llms-full.txt
```diff
```
(991 more changed lines)

## Changed: novig/docs.novig.com/maker-credit-program.md
```diff
```
(2 more changed lines)

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
(10 more changed lines)

## Changed: parlayapi/parlay-api.com/llms.txt
```diff
```
(1 more changed lines)

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
(60 more changed lines)

## Changed: pinnodds/pinnodds.com/llms-full.txt
```diff
```
(8 more changed lines)

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
(6 more changed lines)

## Changed: pinnwire/pinnwire.com/llms.txt
```diff
```
(2 more changed lines)

## Changed: polymarket/docs.polymarket.com/_llms/cn.txt
```diff
```
(2 more changed lines)

## Changed: polymarket/docs.polymarket.com/_llms/cn/api.txt
```diff
```
(5 more changed lines)

## Changed: polymarket/docs.polymarket.com/_llms/en/api-reference.txt
```diff
```
(4 more changed lines)

## Changed: polymarket/docs.polymarket.com/_llms/en/api-reference/perps.txt
```diff
```
(1 more changed lines)

## Changed: polymarket/docs.polymarket.com/_llms/en/api-reference/predictions.txt
```diff
```
(4 more changed lines)

## Changed: polymarket/docs.polymarket.com/changelog/perps.md
```diff
```
(56 more changed lines)

## Changed: polymarket/docs.polymarket.com/changelog/sdks.md
```diff
```
(22 more changed lines)

## Changed: polymarket/docs.polymarket.com/llms-full.txt
```diff
```
(959 more changed lines)

## Changed: polymarket/docs.polymarket.com/llms.txt
```diff
```
(12 more changed lines)

## Changed: polymarket/docs.polymarket.com/market-data/market-details.md
```diff
```
(20 more changed lines)

## Changed: polymarket/docs.polymarket.com/market-data/realtime-data.md
```diff
```
(26 more changed lines)

## Changed: polymarket/docs.polymarket.com/migrate/rtds-to-polybolt.md
```diff
```
(18 more changed lines)

## Changed: polymarket/docs.polymarket.com/perps/errors.md
```diff
```
(24 more changed lines)

## Changed: polymarket/docs.polymarket.com/perps/trading.md
```diff
```
(18 more changed lines)

## Changed: polymarket/docs.polymarket.com/programs/liquidity-rewards.md
```diff
```
(15 more changed lines)

## Changed: polymarket/docs.polymarket.com/resources/error-codes.md
```diff
```
(4 more changed lines)

## Changed: polymarket/docs.polymarket.com/trading/combos/builders.md
```diff
```
(18 more changed lines)

## Changed: polymarket/docs.polymarket.com/trading/combos/requesters.md
```diff
```
(18 more changed lines)

## Changed: polymarket/docs.polymarket.com/trading/positions/combinatorial.md
```diff
```
(19 more changed lines)

## Changed: polymarket/docs.polymarket.com/trading/wallet-activity.md
```diff
```
(10 more changed lines)

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
(49 more changed lines)

## Changed: propline/prop-line.com/llms-full.txt
```diff
```
(40 more changed lines)

## Changed: propline/prop-line.com/llms.txt
```diff
```
(7 more changed lines)

## Changed: propline/prop-line.com/openapi.json
```diff
```
(5 more changed lines)

## Changed: propline/prop-line.com/recipes.html
```diff
```
(6 more changed lines)

## Changed: propline/prop-line.com/sportsbook-accuracy.html
```diff
```
(2 more changed lines)

## Changed: sharpapi/docs.sharpapi.io/llms-full.txt
```diff
```
(13 more changed lines)

## Changed: sharpapi/docs.sharpapi.io/openapi.json
```diff
```
(223 more changed lines)

## Changed: sportsgameodds/sportsgameodds.com/docs.html
```diff
```
(4 more changed lines)

## Changed: sportsgameodds/sportsgameodds.com/docs/llms.txt
```diff
```
(2 more changed lines)

## Changed: sportsgameodds/sportsgameodds.com/llms-full.txt
```diff
```
(6 more changed lines)

## Changed: sportsgameodds/sportsgameodds.com/llms.txt
```diff
```
(2 more changed lines)

## Changed: sxbet/api.docs.sx.bet/index.html
```diff
```
(54 more changed lines)

