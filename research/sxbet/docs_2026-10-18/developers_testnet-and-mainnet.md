> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Testnet & Mainnet

> How to develop on SX Bet testnet and switch to mainnet when you're ready.

## Overview

SX Bet runs a full testnet environment (called **Toronto**) that mirrors mainnet. Use testnet to develop and test your integration without risking real funds, then switch to mainnet by updating a few configuration values.

## Testnet setup

<Steps>
  <Step title="Create a testnet account">
    Sign up at [toronto.sx.bet](https://toronto.sx.bet) and export your private key from the assets page,
    the same way you would on mainnet.
  </Step>

  <Step title="Deploy and fund a proxy wallet">
    Every order settles from a proxy wallet, and a deployed proxy is mandatory before your first order. Then you must transfer USDC to the proxy wallet.
    The simplest way to do this is through the [toronto.sx.bet](https://toronto.sx.bet) UI, which does both for you. You can also do it programmatically with [`POST /user/deploy-proxy`](/api-reference/post-user-deploy-proxy) and [`POST /user/transfer-to-proxy`](/api-reference/post-user-transfer-to-proxy).
  </Step>

  <Step title="Get testnet funds">
    Open a support chat on [sx.bet](https://sx.bet) to receive testnet USDC.
  </Step>

  <Step title="Get your API key">
    Go to your account page on [toronto.sx.bet](https://toronto.sx.bet) and generate an API key from the **API section**. Testnet keys are network-specific and won't work on mainnet. See [API keys](/api-reference/api-key).
  </Step>
</Steps>

## Configuration reference

Everything that differs between testnet and mainnet:

| Setting | Testnet (Toronto) | Mainnet |
| - | - | - |
| **API Base URL** | `https://api.toronto.sx.bet` | `https://api.sx.bet` |
| **App URL** | `https://toronto.sx.bet` | `https://sx.bet` |
| **Chain ID** | `79479957` | `4162` |
| **RPC URL** | `https://rpc-rollup.toronto.sx.technology` | `https://rpc-rollup.sx.technology` |
| **Realtime WebSocket** | `wss://realtime.toronto.sx.bet/connection/websocket` | `wss://realtime.sx.bet/connection/websocket` |
| **Explorer API** | `https://explorerl2.toronto.sx.technology/api` | `https://explorerl2.sx.technology/api` |
| **USDC Address** | `0x1BC6326EA6aF2aB8E4b6Bc83418044B1923b2956` | `0x6629Ce1Cf35Cc1329ebB4F63202F3f197b3F050B` |
| **OBv3 Escrow** | `0x0Db3aBA7de0BCb41FF99D35306f366cFFEEede38` | `0x890482680C3a0116aBB003B17e7D694D13a6c1eB` |
| **Minimum order size** | `1` USDC | `5` USDC |
| **Metadata** | `https://api.toronto.sx.bet/metadata/obv3` | `https://api.sx.bet/metadata/obv3` |

## Switching to mainnet checklist

When you're ready to go live:

* [ ] Update your config to use mainnet values
* [ ] Use your **mainnet** private key and API key — testnet keys won't work on mainnet. See [API keys](/api-reference/api-key)
* [ ] Deploy a mainnet proxy wallet and fund it with USDC. A proxy deployed on Toronto does not exist on mainnet
* [ ] Confirm your order signing domain uses `chainId` and `activeAsset.escrowAddress` from `/metadata/obv3`. See [EIP-712 order signing](/api-reference/eip712-order-signing)
* [ ] Size orders against `limits.orderSizeMinimumBaseUnits`
* [ ] Round odds with `oddsLadderStepSize` from metadata. See [Odds rounding](/developers/odds-rounding)
* [ ] Point your realtime client at the mainnet WebSocket URL. See [Initialization → Connect](/developers/realtime-initialization#connect)
* [ ] Place one minimum-size order and cancel it before scaling up

## Common pitfalls

### Wrong chain ID in signatures

If orders come back with `Invalid OBv3 order EIP-712 signature`, check that your signing domain's `chainId` matches the network you're targeting. Testnet is `79479957`, mainnet is `4162`.

### Wrong Escrow address

`activeAsset.escrowAddress` is the EIP-712 domain's `verifyingContract` and differs per environment. Read it from [`GET /metadata/obv3`](/api-reference/get-metadata-obv3).

### Wrong token addresses

USDC has different contract addresses on each network.

### No proxy on the new network

Proxy wallets are per-environment. A first mainnet order without a deployed proxy returns `PROXY_NOT_DEPLOYED`.

### Testnet API key on mainnet

API keys are network-specific. Generate a separate key for each environment.

## Related

<CardGroup cols={2}>
  <Card title="References →" icon="link" href="/api-reference/references">
    Full list of addresses, URLs, and chain IDs.
  </Card>

  <Card title="Exchange metadata →" icon="gear" href="/api-reference/get-metadata-obv3">
    The runtime config every integration reads at startup.
  </Card>

  <Card title="Funding →" icon="wallet" href="/developers/funding">
    Move USDC into your proxy on either network.
  </Card>

  <Card title="Quickstart →" icon="rocket" href="/developers/quickstart">
    End-to-end guide from setup to first order.
  </Card>
</CardGroup>


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.