# API docs changes since the last snapshot

added 0, changed 150, removed 0

## Changed: boltodds/boltodds.com/pricing.html
```diff
-            <button class="plan-button btn-primary join-pricing-plan" id="trial-button">Start 3-day Free Trial <br> <span class="sub-text">No Credit Card Required</span></button> <br>
+            <button class="plan-button btn-primary join-pricing-plan" id="trial-button">Start 3-day Free Trial <br> <span class="sub-text">Card required, you won't be charged</span></button> <br>
```

## Changed: data/kalshi-sports-series.json
```diff
-{"series":[{"additional_prohibitions":["Current players, coaches, and staff of the league, association, or organization(s) governing the event. For college leagues/associations specifically, or where otherwise appropriate (as identified by the Exchange), this applies to current players/coaches/staf
+{"series":[{"additional_prohibitions":["Current players, coaches, and staff of the league, association, or organization(s) governing the event. For college leagues/associations specifically, or where otherwise appropriate (as identified by the Exchange), this applies to current players/coaches/staf
```

## Changed: data/polymarket-sports.json
```diff
-[{"id":630,"sport":"ufl","name":"UFL","image":"https://polymarket-upload.s3.us-east-2.amazonaws.com/league-icons/ufl.png","resolution":"https://www.theufl.com/","ordering":"away","tags":"1,100639,1186,105925","primaryTagId":105925,"series":"12553","createdAt":"2026-08-07T21:41:19.879539Z"},{"id":64
+[{"id":630,"sport":"ufl","name":"UFL","image":"https://polymarket-upload.s3.us-east-2.amazonaws.com/league-icons/ufl.png","resolution":"https://www.theufl.com/","ordering":"away","tags":"1,100639,1186,105925","primaryTagId":105925,"series":"12553","createdAt":"2026-08-07T21:41:19.879539Z"},{"id":64
```

## Changed: data/sharpapi-health.json
```diff
-{"newest_odds_age_s":0,"status":"ok"}
+{"newest_odds_age_s":2,"status":"ok"}
```

## Changed: data/sxbet-leagues.json
```diff
-{"status":"success","data":[{"leagueId":246,"label":"NCAAF","sportId":8,"popularity":2879473.03089,"eventsByType":{"game-lines":37}},{"leagueId":1,"label":"NBA","sportId":1,"popularity":2526968.617068,"eventsByType":{"first-half-lines":17,"first-period-lines":8,"game-lines":33,"second-period-lines"
+{"status":"success","data":[{"leagueId":1,"label":"NBA","sportId":1,"popularity":3128650.046137,"eventsByType":{"first-half-lines":18,"first-period-lines":9,"game-lines":33,"second-period-lines":9,"third-period-lines":9}},{"leagueId":246,"label":"NCAAF","sportId":8,"popularity":2800359.807365,"even
```

## Changed: kalshi/docs.kalshi.com/api-reference/account/get-account-api-limits.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/account/get-account-api-usage-level-volume-progress.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/account/list-non-default-endpoint-costs.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/account/upgrade-account-api-usage-level.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/api-keys/create-api-key.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/api-keys/delete-api-key.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/api-keys/generate-api-key.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/api-keys/get-api-keys.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/communications/accept-block-trade-proposal.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/communications/accept-quote.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/communications/accept-rfq-quote.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/communications/confirm-quote.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/communications/confirm-rfq-quote.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/communications/create-quote.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/communications/create-rfq.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/communications/delete-quote.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/communications/delete-rfq-quote.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/communications/delete-rfq.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/communications/get-block-trade-proposals.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/communications/get-communications-id.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/communications/get-quote.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/communications/get-quotes.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/communications/get-rfq-quote.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/communications/get-rfq.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/communications/get-rfqs.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/communications/propose-block-trade.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/events/get-event-candlesticks.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/events/get-event-fee-changes.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/events/get-event-forecast-percentile-history.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/events/get-event-metadata.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/events/get-event.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/events/get-events.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/events/get-multivariate-events.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/exchange/get-exchange-schedule.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/exchange/get-exchange-status.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/exchange/get-series-fee-changes.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/exchange/get-user-data-timestamp.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/fcm/create-fcm-position-transfer.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/fcm/create-fcm-subtrader.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/fcm/delete-fcm-subtrader-event-contract-daily-cap.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/fcm/get-fcm-fills.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/fcm/get-fcm-orders.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/fcm/get-fcm-positions.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/fcm/get-fcm-subtrader-blocked-categories.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/fcm/get-fcm-subtrader-event-contract-daily-cap.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/fcm/list-fcm-subtraders.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/fcm/update-fcm-subtrader-blocked-categories.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/fcm/update-fcm-subtrader-event-contract-daily-cap.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/historical/get-historical-cutoff-timestamps.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/historical/get-historical-fills.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/historical/get-historical-market-candlesticks.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/historical/get-historical-market.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/historical/get-historical-markets.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/historical/get-historical-orders.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/historical/get-historical-positions.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/historical/get-historical-trades.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/incentive-programs/get-incentives.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/live-data/get-event-live-data.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/live-data/get-game-stats.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/live-data/get-live-data-with-type.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/live-data/get-live-data.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/live-data/get-multiple-live-data.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/live-data/get-weather-index-calibrations.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/live-data/get-weather-index.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/market/batch-get-market-candlesticks.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/market/get-market-candlesticks.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/market/get-market-orderbook.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/market/get-market.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/market/get-markets.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/market/get-multiple-market-orderbooks.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/market/get-series-list.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/market/get-series.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/market/get-trades.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/milestone/get-milestone.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/milestone/get-milestones.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/multivariate/create-market-in-multivariate-event-collection.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/multivariate/get-multivariate-event-collection.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/multivariate/get-multivariate-event-collections.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/order-groups/create-order-group.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/order-groups/delete-order-group.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/order-groups/get-order-group.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/order-groups/get-order-groups.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/order-groups/reset-order-group.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/order-groups/trigger-order-group.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/order-groups/update-order-group-limit.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/orders/amend-order-v2.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/orders/batch-cancel-orders-v2.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/orders/batch-create-orders-v2.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/orders/cancel-all-orders.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/orders/cancel-order-v2.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/orders/create-order-v2.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/orders/decrease-order-v2.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/orders/get-order-queue-position.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/orders/get-order.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/orders/get-orders.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/orders/get-queue-positions-for-orders.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/portfolio/create-subaccount.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/portfolio/get-all-subaccount-balances.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/portfolio/get-balance.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/portfolio/get-deposits.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/portfolio/get-fills.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/portfolio/get-intra-account-transfer.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/portfolio/get-intra-account-transfers.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/portfolio/get-positions.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/portfolio/get-settlements.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/portfolio/get-subaccount-netting.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/portfolio/get-subaccount-transfers.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/portfolio/get-target-balance-allocation.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/portfolio/get-total-resting-order-value.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/portfolio/get-withdrawals.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/portfolio/intra-account-transfer.md
```diff
+
+Subaccount-restricted API keys can only transfer between `event_contract` exchange indices, with `source_subaccount` and `destination_subaccount` both set to the key's subaccount.
-  version: 3.34.0
+  version: 3.35.0
+        Subaccount-restricted API keys can only transfer between
+        `event_contract` exchange indices, with `source_subaccount` and
+        `destination_subaccount` both set to the key's subaccount.
+
+
```

## Changed: kalshi/docs.kalshi.com/api-reference/portfolio/set-target-balance-allocation.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/portfolio/transfer-between-subaccounts.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/portfolio/update-subaccount-netting.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/search/get-filters-for-sports.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/search/get-tags-for-series-categories.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/structured-targets/get-structured-target.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/api-reference/structured-targets/get-structured-targets.md
```diff
-  version: 3.34.0
+  version: 3.35.0
```

## Changed: kalshi/docs.kalshi.com/asyncapi.yaml
```diff
-
-      **Use case:** Tracking RFQs you create and quotes on your RFQs, or quotes you create on others' RFQs. Use QuoteExecuted to correlate fill messages with quotes via client_order_id.
+      - RFQ batch events (RFQBatchCreated, RFQBatchDeleted) follow the RFQ event rules: sent for all users by default, or with `user_filter: "self"` only for RFQ batches you created. A batch's child RFQs are announced only inside RFQBatchCreated; each child trade still sends QuoteExecuted, with `rf
+      - Each child of an RFQ batch fills at its best quote across makers, and the children execute together as one fill-or-kill: nothing trades unless every child can fill in full. In the rare case that filling one child cancels a maker order another child needed, the batch closes partially execute
+      - RFQBatchBestQuotes and RFQBatchExecuted are only sent to the RFQ batch creator. RFQBatchBestQuotes carries the best bids across makers without identifying them; the batch creator never receives a maker's individual batch quote.
+      - Batch quote events (BatchQuoteCreated, BatchQuoteAccepted, BatchQuoteExecuted) are only sent to the maker that created the batch quote. Each winning maker confirms the children it won, and nothing executes until every winning maker has confirmed.
+
+      **Use case:** Tracking RFQs and RFQ batches you create and the quotes on them, or quotes and batch quotes you create on others'. Use QuoteExecuted to correlate fill messages with quotes via client_order_id.
-
-  batch_communications:
-    address: batch_communications
-    title: Batch Communications
-    description: |
-      Real-time RFQ batch and batch quote notifications. Requires authentication.
-
-      **Requirements:**
-      - Authentication required
-      - Market specification ignored
-      - Optional sharding for fanout control:
-        - `shard_factor` (1-100) and `shard_key` (`0 <= key < shard_factor`)
-        - Every event is partitioned by `rfq_batch_id`
-      - RFQ batch events (RFQBatchCreated, RFQBatchDeleted) are sent for all users by default. Set `user_filter: "self"` to receive only RFQ batches you created. Omitted or empty `user_filter` keeps the default.
-      - A batch's child RFQs are announced only inside RFQBatchCreated. Each child trade still sends QuoteExecuted, with `rfq_batch_id` set, on the `communications` channel. The two channels are separate subscriptions, so a child's QuoteExecuted is not ordered with this channel's messages.
-      - Each child of an RFQ batch fills at its best quote across makers, and the children execute together: every child trades, or none does. Every maker's batch quote prices every child, and each child goes to the maker bidding best on the accepted side, so one batch can have several winning make
-      - RFQBatchBestQuotes and RFQBatchExecuted are only sent to the RFQ batch creator. RFQBatchBestQuotes carries the best bids across makers without identifying them; the batch creator never receives a maker's individual batch quote.
-      - Batch quote events (BatchQuoteCreated, BatchQuoteAccepted, BatchQuoteExecuted) are only sent to the maker that created the batch quote. Each winning maker confirms the children it won, and nothing executes until every winning maker has confirmed.
-
-      **Use case:** Tracking RFQ batches you create and the best quotes on them, or batch quotes you create on others' RFQ batches.
-    messages:
-      $ref: '#/channels/batch_communications'
-    messages:
-      - $ref: '#/channels/batch_communications/messages/rfqBatchCreated'
+      $ref: '#/channels/communications'
+    messages:
+      - $ref: '#/channels/communications/messages/rfqBatchCreated'
-      $ref: '#/channels/batch_communications'
-    messages:
-      - $ref: '#/channels/batch_communications/messages/rfqBatchDeleted'
+      $ref: '#/channels/communications'
+    messages:
+      - $ref: '#/channels/communications/messages/rfqBatchDeleted'
-      $ref: '#/channels/batch_communications'
-    messages:
-      - $ref: '#/channels/batch_communications/messages/batchQuoteCreated'
+      $ref: '#/channels/communications'
+    messages:
+      - $ref: '#/channels/communications/messages/batchQuoteCreated'
-      $ref: '#/channels/batch_communications'
-    messages:
-      - $ref: '#/channels/batch_communications/messages/rfqBatchBestQuotes'
+      $ref: '#/channels/communications'
+    messages:
+      - $ref: '#/channels/communications/messages/rfqBatchBestQuotes'
-      $ref: '#/channels/batch_communications'
-    messages:
-      - $ref: '#/channels/batch_communications/messages/batchQuoteAccepted'
+      $ref: '#/channels/communications'
+    messages:
+      - $ref: '#/channels/communications/messages/batchQuoteAccepted'
-      $ref: '#/channels/batch_communications'
-    messages:
-      - $ref: '#/channels/batch_communications/messages/rfqBatchExecuted'
+      $ref: '#/channels/communications'
+    messages:
+      - $ref: '#/channels/communications/messages/rfqBatchExecuted'
-      $ref: '#/channels/batch_communications'
-    messages:
-      - $ref: '#/channels/batch_communications/messages/batchQuoteExecuted'
+      $ref: '#/channels/communications'
+    messages:
+      - $ref: '#/channels/communications/messages/batchQuoteExecuted'
-        Sent only to the RFQ batch creator, once per batch, when the batch has executed or failed. Each child trade also sends `quote_executed` on the `communications` channel, a separate subscription, so it is not ordered with this message.
+        Sent only to the RFQ batch creator, once per batch, after the per-child `quote_executed` messages. A sharded subscription can receive a child's `quote_executed` on another shard, so across shards the two are not ordered.
-        Sent to each winning maker once the batch has executed or failed. It covers only your children. Each child trade also sends `quote_executed` on the `communications` channel, a separate subscription, so it is not ordered with this message.
+        Sent to each winning maker once the batch has executed or failed, after the per-child `quote_executed` messages. It covers only your children. A sharded subscription can receive a child's `quote_executed` on another shard, so across shards the two are not ordered.
-              description: 'Communications channel only. Set to "self" to receive RFQ created/deleted events only for RFQs you created. Omitted or empty receives RFQs from all users. Quote notifications are unchanged.'
+              description: 'Communications channel only. Set to "self" to receive RFQ and RFQ batch created/deleted events only for those you created. Omitted or empty receives them from all users. Quote and batch quote notifications are unchanged.'
```

## Changed: kalshi/docs.kalshi.com/changelog/index.md
```diff
+
+**October 29, 2026**
+
+The Margin Web Sockets API now supports `market_lifecycle_v2` for market deactivation and activation.
+
+**October 29, 2026**
+
+Direct members can now use subaccount-restricted API keys to move funds within
+that subaccount between exchange indices with `POST /portfolio/intra_exchange_instance_transfer`.
+
+**TBD**
+
+The `communications` WebSocket channel adds RFQ batch messages:
+`rfq_batch_created` and `rfq_batch_deleted` for all subscribers (only your
+own with `user_filter: "self"`), `rfq_batch_best_quotes` and
+`rfq_batch_executed` for the batch's requester, and `batch_quote_created`,
+`batch_quote_accepted` and `batch_quote_executed` for each maker.
+`quote_executed` carries `rfq_batch_id` for a batch child's trade. If your
+client doesn't handle these messages, ignore them; see
+[Forward compatibility](https://docs.kalshi.com/websockets.md#forward-compatibility).
+
+**October 29, 2026**
+
+Margin market responses now carry `halt_sources`, listing why a market is
+halted and empty when it is not. An inactive market that has never opened
+carries `initial_setup`, while one halted for a corporate action carries
+`corporate_action`, so a halted market can be told apart from one that never
+launched.
```

## Changed: kalshi/docs.kalshi.com/getting_started/rate_limits.md
```diff
-> Token costs, tier budgets, and burst capacity for the Kalshi API
+> Token costs, tier budgets, burst capacity, and WebSocket connection limits for the Kalshi API
+
+## WebSocket connection limits
+
+Predictions and Perps (Margin) WebSocket connections share a **per-user limit on concurrent open connections**.
+
+| Tier | Maximum concurrent connections |
+| - | - |
+| Basic | 200 |
+| Advanced | 200 |
+| Expert | 200 |
+| Premier | 500 |
+| Paragon | 750 |
+| Prime | 1,000 |
+| Prestige | 1,000 |
+
+When you reach the connection limit, new WebSocket handshakes return `429 Too Many Requests`.
```

## Changed: kalshi/docs.kalshi.com/getting_started/sub_users_vs_subaccounts.md
```diff
-it can read and trade only that subaccount and can't transfer funds. Only the
+it can read and trade only that subaccount and can't transfer funds between subaccounts. Only the
```

## Changed: kalshi/docs.kalshi.com/getting_started/subaccounts.md
```diff
-restricted key can only read and trade on that subaccount: order placement and
+restricted key can read and trade on that subaccount: order placement and
+To move funds between exchange indices, call
+`POST /portfolio/intra_exchange_instance_transfer` with `source_subaccount` and
+`destination_subaccount` both set to the key's subaccount. Transfers to or from
+margin are not allowed.
-A restricted key cannot transfer funds, manage subaccounts or API keys, or
+A restricted key cannot transfer funds between subaccounts, manage subaccounts or API keys, or
```

## Changed: kalshi/docs.kalshi.com/llms-full.txt
```diff
+Subaccount-restricted API keys can only transfer between `event_contract` exchange indices, with `source_subaccount` and `destination_subaccount` both set to the key's subaccount.
+
+
+**October 29, 2026**
+
+Direct members can now use subaccount-restricted API keys to move funds within
+that subaccount between exchange indices with `POST /portfolio/intra_exchange_instance_transfer`.
+
+**TBD**
+
+The `communications` WebSocket channel adds RFQ batch messages:
+`rfq_batch_created` and `rfq_batch_deleted` for all subscribers (only your
+own with `user_filter: "self"`), `rfq_batch_best_quotes` and
+`rfq_batch_executed` for the batch's requester, and `batch_quote_created`,
```
(52 more changed lines)

## Changed: kalshi/docs.kalshi.com/llms.txt
```diff
```
(3 more changed lines)

## Changed: kalshi/docs.kalshi.com/margin.md
```diff
```
(4 more changed lines)

## Changed: kalshi/docs.kalshi.com/openapi.yaml
```diff
```
(32 more changed lines)

## Changed: kalshi/docs.kalshi.com/websockets.md
```diff
```
(4 more changed lines)

## Changed: kalshi/docs.kalshi.com/websockets/communications.md
```diff
```
(1846 more changed lines)

## Changed: kalshi/docs.kalshi.com/websockets/websocket-connection.md
```diff
```
(12 more changed lines)

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

## Changed: pinnodds/pinnodds.com/docs.html
```diff
```
(14 more changed lines)

## Changed: pinnwire/pinnwire.com/docs.html
```diff
```
(14 more changed lines)

## Changed: pinnwire/pinnwire.com/llms-full.txt
```diff
```
(8 more changed lines)

## Changed: pinnwire/pinnwire.com/llms.txt
```diff
```
(4 more changed lines)

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
(8 more changed lines)

## Changed: propline/prop-line.com/llms-full.txt
```diff
```
(13 more changed lines)

## Changed: propline/prop-line.com/llms.txt
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

## Changed: sxbet/api.docs.sx.bet/index.html
```diff
```
(12 more changed lines)

