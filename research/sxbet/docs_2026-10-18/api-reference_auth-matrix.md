> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Authentication by route

> Which SX Bet API routes require authentication.

## Public routes

| Route | What it gives you | Link |
| - | - | - |
| `GET /metadata/obv3` | Exchange metadata | [Docs →](/api-reference/get-metadata-obv3) |
| `GET /trades-v3/public` | The anonymised recent-trade tape | [Docs →](/api-reference/get-trades-v3-public) |

## Authenticated routes

Send `x-sx-api-key` as a header. Every response is scoped to the address that owns the key.

| Route | What it does | Link |
| - | - | - |
| `GET /orders-v3/odds/best` | Best available odds per market | [Docs →](/api-reference/get-best-odds-v3) |
| `GET /orderbook-v3/snapshot/market` | The whole book for one market | [Docs →](/api-reference/get-market-orderbook-snapshot) |
| `GET /orderbook-v3/snapshot/event` | The book for markets on one event | [Docs →](/api-reference/get-event-orderbook-snapshot) |
| `GET /orders-v3` | Your active orders | [Docs →](/api-reference/get-orders-v3) |
| `GET /orders-v3/{orderId}` | One of your orders by id (any status) | [Docs →](/api-reference/get-order-v3) |
| `POST /orders-v3` | Places one signed order | [Docs →](/api-reference/post-orders-v3) |
| `DELETE /orders-v3` | Cancels named orders by id | [Docs →](/api-reference/delete-orders-v3) |
| `DELETE /orders-v3/all` | Cancels every open order you have | [Docs →](/api-reference/delete-orders-v3-all) |
| `DELETE /orders-v3/event` | Cancels your orders on one event | [Docs →](/api-reference/delete-orders-v3-event) |
| `DELETE /orders-v3/market` | Cancels your orders on one market | [Docs →](/api-reference/delete-orders-v3-market) |
| `GET /trades-v3` | Your bet history | [Docs →](/api-reference/get-trades-v3) |
| `GET /trades-v3/{tradeId}` | One of your bets by tradeId | [Docs →](/api-reference/get-trade-v3) |
| `GET /fills-v3` | Your fill history | [Docs →](/api-reference/get-fills-v3) |
| `GET /positions-v3` | Your positions | [Docs →](/api-reference/get-positions-v3) |
| `GET /user/balance-v3` | Your current balance | [Docs →](/api-reference/get-user-balance-v3) |
| `GET /ledger-v3` | Your balance ledger | [Docs →](/api-reference/get-ledger-v3) |
| `GET /user/fees-v3` | The fee rates set on your account | [Docs →](/api-reference/get-user-fees-v3) |
| `GET /user/pending-deploy-proxy` | Whether a proxy deploy is still pending | [Docs →](/api-reference/get-user-pending-deploy-proxy) |
| `POST /user/deploy-proxy` | Deploys your proxy wallet | [Docs →](/api-reference/post-user-deploy-proxy) |
| `GET /user/proxy` | Your proxy wallet address and deploy status | [Docs →](/api-reference/get-user-proxy) |
| `POST /user/transfer-to-proxy` | Moves tokens into your proxy | [Docs →](/api-reference/post-user-transfer-to-proxy) |
| `GET /user/transfer-to-proxy/pending` | Open deposit `sessionIds` (newest first) | [Docs →](/api-reference/get-user-transfer-to-proxy-pending) |
| `GET /user/transfer-to-proxy/status` | Outcome of one deposit by `sessionId` | [Docs →](/api-reference/get-user-transfer-to-proxy-status) |
| `GET /user/multisig/{safeAddress}/info` | Signers, threshold, and the pending Safe transaction | [Docs →](/api-reference/get-user-multisig-info) |
| `GET /user/multisig/proxy/{proxyAddress}` | Whether a proxy address is registered and deployed | [Docs →](/api-reference/get-user-multisig-proxy) |
| `POST /user/multisig/withdraw/prepare` | Builds a withdrawal for you to sign | [Docs →](/api-reference/post-user-multisig-withdraw-prepare) |
| `POST /user/multisig/withdraw/execute` | Submits your signature for a withdrawal | [Docs →](/api-reference/post-user-multisig-withdraw-execute) |
| `POST /user/multisig/transfer/prepare` | Builds a transfer to another proxy | [Docs →](/api-reference/post-user-multisig-transfer-prepare) |
| `POST /user/multisig/transfer/execute` | Submits your signature for a transfer | [Docs →](/api-reference/post-user-multisig-transfer-execute) |
| `POST /user/multisig/add-signer/prepare` | Builds a transaction that adds a signer | [Docs →](/api-reference/post-user-multisig-add-signer-prepare) |
| `POST /user/multisig/add-signer/execute` | Submits your signature to add a signer | [Docs →](/api-reference/post-user-multisig-add-signer-execute) |
| `POST /user/multisig/remove-signer/prepare` | Builds a transaction that removes a signer | [Docs →](/api-reference/post-user-multisig-remove-signer-prepare) |
| `POST /user/multisig/remove-signer/execute` | Submits your signature to remove a signer | [Docs →](/api-reference/post-user-multisig-remove-signer-execute) |
| `POST /user/multisig/set-threshold/prepare` | Builds a threshold change | [Docs →](/api-reference/post-user-multisig-set-threshold-prepare) |
| `POST /user/multisig/set-threshold/execute` | Submits your signature for a threshold change | [Docs →](/api-reference/post-user-multisig-set-threshold-execute) |
| `POST /user/multisig/cancel` | Deletes a pending Safe transaction | [Docs →](/api-reference/post-user-multisig-cancel) |


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.