> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Authentication

> Different operations on SX Bet require different levels of authentication. Here's what you need and why.

## Authentication by scope

| Operation | Auth required |
| - | - |
| Fetch markets and the anonymized public tape | None |
| Fetch the order book or best odds; read your own orders, positions, trades, fills, or balances; subscribe to WebSocket channels; register a heartbeat | API key |
| Place an order and account actions | API key + signature |

Send the key as the `x-sx-api-key` header. Generate one from the API section of your account page on [sx.bet](https://sx.bet). See [API keys](/api-reference/api-key).

## Auth by route

| Method | Route | Auth | Signature |
| - | - | - | - |
| `GET` | `/metadata/obv3` | None | — |
| `GET` | `/sports` | None | — |
| `GET` | `/leagues` | None | — |
| `GET` | `/leagues/active` | None | — |
| `GET` | `/teams` | None | — |
| `GET` | `/fixture/active` | None | — |
| `GET` | `/fixture/status` | None | — |
| `GET` | `/live-scores` | None | — |
| `GET` | `/search` | None | — |
| `GET` | `/markets/active` | None | — |
| `GET` | `/markets/find` | None | — |
| `GET` | `/markets/popular` | None | — |
| `GET` | `/trades-v3/public` | None | — |
| `GET` | `/orders-v3/odds/best` | API key | — |
| `GET` | `/orderbook-v3/snapshot/market` | API key | — |
| `GET` | `/orderbook-v3/snapshot/event` | API key | — |
| `GET` | `/orders-v3` | API key | — |
| `POST` | `/orders-v3` | API key | EIP-712 order |
| `DELETE` | `/orders-v3` | API key | — |
| `DELETE` | `/orders-v3/all` | API key | — |
| `DELETE` | `/orders-v3/event` | API key | — |
| `POST` | `/heartbeat/v3` | API key | — |
| `GET` | `/trades-v3` | API key | — |
| `GET` | `/fills-v3` | API key | — |
| `GET` | `/positions-v3` | API key | — |
| `POST` | `/user/deploy-proxy` | API key | — |
| `GET` | `/user/proxy` | API key | — |
| `POST` | `/user/transfer-to-proxy` | API key | EIP-2612 permit |
| `GET` | `/user/transfer-to-proxy/pending` | API key | — |
| `GET` | `/user/transfer-to-proxy/status` | API key | — |
| `GET` | `/user/pending-deploy-proxy` | API key | — |
| `GET` | `/user/balance-v3` | API key | — |
| `GET` | `/user/fees-v3` | API key | — |
| `GET` | `/user/multisig/{safeAddress}/info` | API key | — |
| `GET` | `/user/multisig/proxy/{proxyAddress}` | API key | — |
| `POST` | `/user/multisig/withdraw/prepare` | API key | — |
| `POST` | `/user/multisig/withdraw/execute` | API key | EIP-712 Safe transaction |
| `POST` | `/user/multisig/transfer/prepare` | API key | — |
| `POST` | `/user/multisig/transfer/execute` | API key | EIP-712 Safe transaction |
| `POST` | `/user/multisig/add-signer/prepare` | API key | — |
| `POST` | `/user/multisig/add-signer/execute` | API key | EIP-712 Safe transaction |
| `POST` | `/user/multisig/remove-signer/prepare` | API key | — |
| `POST` | `/user/multisig/remove-signer/execute` | API key | EIP-712 Safe transaction |
| `POST` | `/user/multisig/set-threshold/prepare` | API key | — |
| `POST` | `/user/multisig/set-threshold/execute` | API key | EIP-712 Safe transaction |
| `POST` | `/user/multisig/cancel` | API key | EIP-712 delete request |
| `GET` | `/user/realtime-token-v3` | API key | — |


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.