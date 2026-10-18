> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Order signing

> How to sign an order with your private key or an injected wallet.

`POST /orders-v3` requires an EIP-712 signature over the eight-field `Order` struct. Read the domain
from [`GET /metadata/obv3`](/api-reference/get-metadata-obv3). See
[Posting orders](/developers/posting-orders).

## Private key signing

<CodeGroup>
  ```js JavaScript theme={null}
  import { Wallet, randomBytes, hexlify } from "ethers";

  const wallet = new Wallet(process.env.SX_PRIVATE_KEY);

  const { data: meta } = await fetch("https://api.sx.bet/metadata/obv3").then((r) =>
    r.json()
  );
  const domain = meta.domain; // { name, version, chainId, verifyingContract }

  const types = {
    Order: [
      { name: "marketHash", type: "bytes32" },
      { name: "baseToken", type: "address" },
      { name: "totalBetSize", type: "uint256" },
      { name: "percentageOdds", type: "uint256" },
      { name: "salt", type: "uint256" },
      { name: "expiry", type: "uint256" },
      { name: "maker", type: "address" },
      { name: "isMakerBettingOutcomeOne", type: "bool" },
    ],
  };

  const value = {
    marketHash: "0x…", // 32-byte market hash
    baseToken: meta.activeAsset.baseToken,
    totalBetSize: "1000000", // 1 USDC, base units
    percentageOdds: "50000000000000000000", // 50% on the 1e20 scale
    salt: hexlify(randomBytes(32)),
    expiry: Math.floor(Date.now() / 1000) + 3600, // unix seconds; here, 1 hour out
    maker: wallet.address,
    isMakerBettingOutcomeOne: true,
  };

  const orderSignature = await wallet.signTypedData(domain, types, value);
  ```

  ```python Python theme={null}
  import os
  import secrets
  import time
  import requests
  from eth_account import Account
  from eth_account.messages import encode_typed_data

  account = Account.from_key(os.environ["SX_PRIVATE_KEY"])
  meta = requests.get("https://api.sx.bet/metadata/obv3").json()["data"]

  structured_data = {
      "types": {
          "EIP712Domain": [
              {"name": "name", "type": "string"},
              {"name": "version", "type": "string"},
              {"name": "chainId", "type": "uint256"},
              {"name": "verifyingContract", "type": "address"},
          ],
          "Order": [
              {"name": "marketHash", "type": "bytes32"},
              {"name": "baseToken", "type": "address"},
              {"name": "totalBetSize", "type": "uint256"},
              {"name": "percentageOdds", "type": "uint256"},
              {"name": "salt", "type": "uint256"},
              {"name": "expiry", "type": "uint256"},
              {"name": "maker", "type": "address"},
              {"name": "isMakerBettingOutcomeOne", "type": "bool"},
          ],
      },
      "primaryType": "Order",
      "domain": meta["domain"],
      "message": {
          "marketHash": "0x…",
          "baseToken": meta["activeAsset"]["baseToken"],
          "totalBetSize": 1000000,
          "percentageOdds": 50000000000000000000,
          "salt": int("0x" + secrets.token_hex(32), 16),
          "expiry": int(time.time()) + 3600,
          "maker": account.address,
          "isMakerBettingOutcomeOne": True,
      },
  }

  signed = Account.sign_message(
      encode_typed_data(full_message=structured_data),
      private_key=os.environ["SX_PRIVATE_KEY"],
  )
  order_signature = signed.signature.to_0x_hex()
  ```
</CodeGroup>

Using a private key is the most straightforward approach for bots and scripts.

***

## Injected provider signing

```js theme={null}
import { BrowserProvider, randomBytes, hexlify } from "ethers";

const provider = new BrowserProvider(window.ethereum);
const signer = await provider.getSigner();

const { data: meta } = await fetch("https://api.sx.bet/metadata/obv3").then((r) =>
  r.json()
);
const domain = meta.domain;

const types = {
  Order: [
    { name: "marketHash", type: "bytes32" },
    { name: "baseToken", type: "address" },
    { name: "totalBetSize", type: "uint256" },
    { name: "percentageOdds", type: "uint256" },
    { name: "salt", type: "uint256" },
    { name: "expiry", type: "uint256" },
    { name: "maker", type: "address" },
    { name: "isMakerBettingOutcomeOne", type: "bool" },
  ],
};

const value = {
  marketHash: "0x…",
  baseToken: meta.activeAsset.baseToken,
  totalBetSize: "1000000",
  percentageOdds: "50000000000000000000",
  salt: hexlify(randomBytes(32)),
  expiry: Math.floor(Date.now() / 1000) + 3600,
  maker: await signer.getAddress(),
  isMakerBettingOutcomeOne: true,
};

const orderSignature = await signer.signTypedData(domain, types, value);
```

Any injected provider that exposes `eth_signTypedData_v4` (e.g. MetaMask) works.

> This signing scheme uses the [EIP-712 typed data standard](https://eips.ethereum.org/EIPS/eip-712).


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.