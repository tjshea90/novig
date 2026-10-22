> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Authentication by route

> Which SX Bet API routes require authentication.

## Public routes

| Route | What it gives you | Link |
| - | - | - |
| `GET /metadata/obv3` | Exchange metadata | [Docs →](/api-reference/get-metadata-obv3.md) |
| `GET /trades-v3/public` | The anonymised recent-trade tape | [Docs →](/api-reference/get-trades-v3-public.md) |

## Authenticated routes

Send `x-sx-api-key` as a header. Every response is scoped to the address that owns the key.

| Route | What it does | Link |
| - | - | - |
| `GET /orders-v3/odds/best` | Best available odds per market | [Docs →](/api-reference/get-best-odds-v3.md) |
| `GET /orderbook-v3/snapshot/market` | The whole book for one market | [Docs →](/api-reference/get-market-orderbook-snapshot.md) |
| `GET /orderbook-v3/snapshot/event` | The book for markets on one event | [Docs →](/api-reference/get-event-orderbook-snapshot.md) |
| `GET /orders-v3` | Your active orders | [Docs →](/api-reference/get-orders-v3.md) |
| `GET /orders-v3/{orderId}` | One of your orders by id (any status) | [Docs →](/api-reference/get-order-v3.md) |
| `POST /orders-v3` | Places one signed order | [Docs →](/api-reference/post-orders-v3.md) |
| `DELETE /orders-v3` | Cancels named orders by id | [Docs →](/api-reference/delete-orders-v3.md) |
| `DELETE /orders-v3/all` | Cancels every open order you have | [Docs →](/api-reference/delete-orders-v3-all.md) |
| `DELETE /orders-v3/event` | Cancels your orders on one event | [Docs →](/api-reference/delete-orders-v3-event.md) |
| `DELETE /orders-v3/market` | Cancels your orders on one market | [Docs →](/api-reference/delete-orders-v3-market.md) |
| `GET /trades-v3` | Your bet history | [Docs →](/api-reference/get-trades-v3.md) |
| `GET /trades-v3/{tradeId}` | One of your bets by tradeId | [Docs →](/api-reference/get-trade-v3.md) |
| `GET /fills-v3` | Your fill history | [Docs →](/api-reference/get-fills-v3.md) |
| `GET /positions-v3` | Your positions | [Docs →](/api-reference/get-positions-v3.md) |
| `GET /user/balance-v3` | Your current balance | [Docs →](/api-reference/get-user-balance-v3.md) |
| `GET /ledger-v3` | Your balance ledger | [Docs →](/api-reference/get-ledger-v3.md) |
| `GET /user/fees-v3` | The fee rates set on your account | [Docs →](/api-reference/get-user-fees-v3.md) |
| `GET /user/pending-deploy-proxy` | Whether a proxy deploy is still pending | [Docs →](/api-reference/get-user-pending-deploy-proxy.md) |
| `POST /user/deploy-proxy` | Deploys your proxy wallet | [Docs →](/api-reference/post-user-deploy-proxy.md) |
| `GET /user/proxy` | Your proxy wallet address and deploy status | [Docs →](/api-reference/get-user-proxy.md) |
| `POST /user/transfer-to-proxy` | Moves tokens into your proxy | [Docs →](/api-reference/post-user-transfer-to-proxy.md) |
| `GET /user/transfer-to-proxy/pending` | Open deposit `sessionIds` (newest first) | [Docs →](/api-reference/get-user-transfer-to-proxy-pending.md) |
| `GET /user/transfer-to-proxy/status` | Outcome of one deposit by `sessionId` | [Docs →](/api-reference/get-user-transfer-to-proxy-status.md) |
| `GET /user/multisig/{safeAddress}/info` | Signers, threshold, and the pending Safe transaction | [Docs →](/api-reference/get-user-multisig-info.md) |
| `GET /user/multisig/proxy/{proxyAddress}` | Whether a proxy address is registered and deployed | [Docs →](/api-reference/get-user-multisig-proxy.md) |
| `POST /user/multisig/withdraw/prepare` | Builds a withdrawal for you to sign | [Docs →](/api-reference/post-user-multisig-withdraw-prepare.md) |
| `POST /user/multisig/withdraw/execute` | Submits your signature for a withdrawal | [Docs →](/api-reference/post-user-multisig-withdraw-execute.md) |
| `POST /user/multisig/transfer/prepare` | Builds a transfer to another proxy | [Docs →](/api-reference/post-user-multisig-transfer-prepare.md) |
| `POST /user/multisig/transfer/execute` | Submits your signature for a transfer | [Docs →](/api-reference/post-user-multisig-transfer-execute.md) |
| `POST /user/multisig/add-signer/prepare` | Builds a transaction that adds a signer | [Docs →](/api-reference/post-user-multisig-add-signer-prepare.md) |
| `POST /user/multisig/add-signer/execute` | Submits your signature to add a signer | [Docs →](/api-reference/post-user-multisig-add-signer-execute.md) |
| `POST /user/multisig/remove-signer/prepare` | Builds a transaction that removes a signer | [Docs →](/api-reference/post-user-multisig-remove-signer-prepare.md) |
| `POST /user/multisig/remove-signer/execute` | Submits your signature to remove a signer | [Docs →](/api-reference/post-user-multisig-remove-signer-execute.md) |
| `POST /user/multisig/set-threshold/prepare` | Builds a threshold change | [Docs →](/api-reference/post-user-multisig-set-threshold-prepare.md) |
| `POST /user/multisig/set-threshold/execute` | Submits your signature for a threshold change | [Docs →](/api-reference/post-user-multisig-set-threshold-execute.md) |
| `POST /user/multisig/cancel` | Deletes a pending Safe transaction | [Docs →](/api-reference/post-user-multisig-cancel.md) |


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.