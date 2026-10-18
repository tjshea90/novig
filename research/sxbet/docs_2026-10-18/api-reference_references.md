> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# References

> Endpoints, chain IDs, contract addresses, and network details for mainnet and testnet.

## Toronto (testnet)

| Reference | Value |
| - | - |
| API base URL | `https://api.toronto.sx.bet` |
| App | `https://toronto.sx.bet` |
| Realtime WebSocket | `wss://realtime.toronto.sx.bet/connection/websocket` |
| Chain id | `79479957` |
| USDC | `0x1BC6326EA6aF2aB8E4b6Bc83418044B1923b2956` |
| Escrow Contract | `0x0Db3aBA7de0BCb41FF99D35306f366cFFEEede38` |

## Production (mainnet)

| Reference | Value |
| - | - |
| API base URL | `https://api.sx.bet` |
| App | `https://sx.bet` |
| Realtime WebSocket | `wss://realtime.sx.bet/connection/websocket` |
| Chain id | `4162` |
| USDC | `0x6629Ce1Cf35Cc1329ebB4F63202F3f197b3F050B` |
| Escrow Contract | `0x890482680C3a0116aBB003B17e7D694D13a6c1eB` |

## B. Runtime values

`GET /metadata/obv3` has additional environment configuration

| Value | Where it is used |
| - | - |
| `domain` (`name`, `version`, `chainId`, `verifyingContract`) | the EIP-712 domain — pass `meta.domain` to `signTypedData` |
| `activeAsset.baseToken` | the `baseToken` field of every order |
| `activeAsset.escrowAddress` | equals `domain.verifyingContract` |
| `activeAsset.decimals` | converting whole tokens to base units |
| `transferToProxyExecutorAddress` | EIP-2612 permit `spender` for [transfer-to-proxy](/api-reference/post-user-transfer-to-proxy) |
| `oddsLadderStepSize` | rounding `percentageOdds` onto the ladder |
| `limits.orderSizeMinimumBaseUnits` | the floor for `totalBetSize` |
| `limits.minRestingOrderSizeBaseUnits` | dust floor — a partial-fill remainder below this is discarded and the order closes `FILLED` |
| `limits.maxCreateOrders`, `limits.maxCancelOrders` | batch sizes for create and cancel |
| `bettingDelay` | resolve the per-order delay — field-by-field on [Betting delays](/developers/betting-delays) |

Field-by-field detail, the mixed string/number types, and the values that differ by environment are on
[Exchange metadata](/api-reference/get-metadata-obv3).

## Versions of dependencies

These are the latest versions of js dependencies that are referenced in documentation:

* `ethers`: 6.16.0


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.