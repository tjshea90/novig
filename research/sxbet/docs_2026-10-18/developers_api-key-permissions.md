> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# API key permissions

> Scopes you can grant an API key

Each API key can call only the routes for the scopes you select when you create it.

Routes with no authentication, such as `GET /markets/active`, are not listed here. See
[Authentication by route](/api-reference/auth-matrix).

`GET /user/realtime-token-v3/api-key` accepts any API key. It is not tied to a scope.

## Market data: read

| Endpoint |
| - |
| [`GET` `/orders-v3/odds/best`](/api-reference/get-best-odds-v3) |
| [`GET` `/orders-v3/public`](/api-reference/get-orders-v3-public) |
| [`GET` `/orders-v3/public/user`](/api-reference/get-orders-v3-user-public) |
| [`GET` `/orderbook-v3/snapshot/market`](/api-reference/get-market-orderbook-snapshot) |
| [`GET` `/orderbook-v3/snapshot/event`](/api-reference/get-event-orderbook-snapshot) |
| [`GET` `/positions-v3/public`](/api-reference/get-positions-v3-public) |

## Orders: read

| Endpoint |
| - |
| [`GET` `/orders-v3`](/api-reference/get-orders-v3) |
| [`GET` `/orders-v3/{orderId}`](/api-reference/get-order-v3) |
| [`GET` `/orders-v3/client/{clientOrderId}`](/api-reference/get-order-v3-by-client-id) |

## Orders: create

| Endpoint |
| - |
| [`POST` `/orders-v3`](/api-reference/post-orders-v3) |

## Orders: cancel

| Endpoint |
| - |
| [`DELETE` `/orders-v3`](/api-reference/delete-orders-v3) |
| [`DELETE` `/orders-v3/market`](/api-reference/delete-orders-v3-market) |
| [`DELETE` `/orders-v3/event`](/api-reference/delete-orders-v3-event) |
| [`DELETE` `/orders-v3/all`](/api-reference/delete-orders-v3-all) |
| [`POST` `/heartbeat/v3`](/api-reference/post-heartbeat-v3) |

## Account: read

| Endpoint |
| - |
| [`GET` `/trades-v3`](/api-reference/get-trades-v3) |
| [`GET` `/trades-v3/{tradeId}`](/api-reference/get-trade-v3) |
| [`GET` `/fills-v3`](/api-reference/get-fills-v3) |
| [`GET` `/positions-v3`](/api-reference/get-positions-v3) |
| `GET` `/positions-v3/settled/pnl` |
| [`GET` `/user/balance-v3`](/api-reference/get-user-balance-v3) |
| [`GET` `/user/fees-v3`](/api-reference/get-user-fees-v3) |
| [`GET` `/user/privacy-v3`](/api-reference/get-user-privacy-v3) |
| [`GET` `/ledger-v3`](/api-reference/get-ledger-v3) |

## Account: write

| Endpoint |
| - |
| [`POST` `/user/privacy-v3`](/api-reference/post-user-privacy-v3) |

## Wallet: read

| Endpoint |
| - |
| [`GET` `/user/proxy`](/api-reference/get-user-proxy) |
| [`GET` `/user/pending-deploy-proxy`](/api-reference/get-user-pending-deploy-proxy) |
| [`GET` `/user/transfer-to-proxy/pending`](/api-reference/get-user-transfer-to-proxy-pending) |
| [`GET` `/user/transfer-to-proxy/status`](/api-reference/get-user-transfer-to-proxy-status) |
| [`GET` `/user/multisig/proxy/{proxyAddress}`](/api-reference/get-user-multisig-proxy) |
| [`GET` `/user/multisig/{safeAddress}/info`](/api-reference/get-user-multisig-info) |

## Wallet: deposit

| Endpoint |
| - |
| [`POST` `/user/transfer-to-proxy`](/api-reference/post-user-transfer-to-proxy) |

## Wallet: withdraw

| Endpoint |
| - |
| [`POST` `/user/multisig/withdraw/prepare`](/api-reference/post-user-multisig-withdraw-prepare) |
| [`POST` `/user/multisig/withdraw/execute`](/api-reference/post-user-multisig-withdraw-execute) |

## Wallet: transfer

| Endpoint |
| - |
| [`POST` `/user/multisig/transfer/prepare`](/api-reference/post-user-multisig-transfer-prepare) |
| [`POST` `/user/multisig/transfer/execute`](/api-reference/post-user-multisig-transfer-execute) |

## Wallet: admin

| Endpoint |
| - |
| [`POST` `/user/deploy-proxy`](/api-reference/post-user-deploy-proxy) |
| [`POST` `/user/multisig/add-signer/prepare`](/api-reference/post-user-multisig-add-signer-prepare) |
| [`POST` `/user/multisig/add-signer/execute`](/api-reference/post-user-multisig-add-signer-execute) |
| [`POST` `/user/multisig/remove-signer/prepare`](/api-reference/post-user-multisig-remove-signer-prepare) |
| [`POST` `/user/multisig/remove-signer/execute`](/api-reference/post-user-multisig-remove-signer-execute) |
| [`POST` `/user/multisig/set-threshold/prepare`](/api-reference/post-user-multisig-set-threshold-prepare) |
| [`POST` `/user/multisig/set-threshold/execute`](/api-reference/post-user-multisig-set-threshold-execute) |
| [`POST` `/user/multisig/cancel`](/api-reference/post-user-multisig-cancel) |

## Related

<CardGroup cols={2}>
  <Card title="Rate limits" icon="gauge" href="/developers/rate-limits">
    Request budgets for each route.
  </Card>

  <Card title="API keys" icon="lock" href="/api-reference/api-key">
    Generate a key and send it on a request.
  </Card>
</CardGroup>


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.