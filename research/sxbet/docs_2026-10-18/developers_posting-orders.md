> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Posting orders

> How to construct, sign, and submit orders to the SX Bet orderbook.

Every order — resting quote or immediate bet — goes through
[`POST /orders-v3`](/api-reference/post-orders-v3). The field-by-field reference lives on the
endpoint page.

## Prerequisites

<Warning>
  **A deployed, funded proxy is required before any OBv3 order** — `POST /orders-v3` rejects with
  `400 PROXY_NOT_DEPLOYED` until then. See [Deploy a proxy wallet](/api-reference/post-user-deploy-proxy).
</Warning>

You also need your API key, sent as `x-sx-api-key`. See
[Authentication](/developers/authentication) and [API key](/api-reference/api-key).

## Steps

1. [**`GET /metadata/obv3`**](/api-reference/get-metadata-obv3) — chain id, Escrow address, active token, ladder step, size limits.
2. **Round your odds down onto the ladder** — a multiple of `oddsLadderStepSize × 10^15`. See
   [Odds rounding](/developers/odds-rounding).
3. **Choose a `timeInForce`** — `GTC` to rest, `IOC`/`FOK` to execute now. See
   [Time in force](/developers/time-in-force).
4. Sign the eight-field [EIP-712 `Order` struct](/api-reference/eip712-order-signing).
5. [**`POST /orders-v3`**](/api-reference/post-orders-v3) — expect **`200`** and a per-order result carrying `SUBMITTED`, the server-assigned `commandId`, and your `clientOrderId` / `externalUserId` if you set them.
6. Watch your [`account:orders_v3`](/api-reference/channel-orders-v3) stream for the transition instead of polling. Or set [`waitForOutcome: true`](/api-reference/post-orders-v3) to have the call wait and return each order's terminal `outcome` inline.

<Note>
  **Posting an order does not lock capital**, regardless of `timeInForce`. Funds are only escrowed when
  the order **matches** — see [Risk limits](/developers/risk-limits).
</Note>

## Posting a GTC order end to end

<CodeGroup>
  ```js post_gtc_order.mjs theme={null}
  import { Wallet, TypedDataEncoder, getAddress, zeroPadValue, randomBytes, hexlify } from "ethers";

  const API = "https://api.sx.bet"; // Mainnet — use https://api.toronto.sx.bet for testnet
  const wallet = new Wallet(process.env.SX_PRIVATE_KEY);
  const authed = (init = {}) => ({
    ...init,
    headers: {
      "Content-Type": "application/json",
      "x-sx-api-key": process.env.SX_API_KEY,
      ...init.headers,
    },
  });

  // 1. Fetch metadata and build the signing domain.
  const { data: meta } = await fetch(`${API}/metadata/obv3`).then((r) => r.json());
  const domain = meta.domain;                         // complete EIP-712 domain

  // 2. Round the odds DOWN onto the ladder, before signing.
  const step = BigInt(meta.oddsLadderStepSize) * 10n ** 15n;
  const wanted = 40000000000000000000n;               // 40.000% implied
  const percentageOdds = ((wanted / step) * step).toString();

  // 3. Build the order. `expiry` is a NUMBER; the three amounts are STRINGS.
  const order = {
    marketHash: process.env.SX_MARKET_HASH,
    maker: wallet.address,
    totalBetSize: "1000000",                          // 1 USDC, 6 dp
    percentageOdds,
    salt: hexlify(randomBytes(32)),
    expiry: Math.floor(Date.now() / 1000) + 3600,     // unix seconds; here, 1 hour out
    baseToken: meta.activeAsset.baseToken,
    isMakerBettingOutcomeOne: true,                   // the outcome YOU want to bet
    timeInForce: "GTC",
  };

  // 4. Sign the eight-field struct. Order matters: salt precedes expiry.
  const TYPES = {
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
  const message = {
    marketHash: zeroPadValue(order.marketHash, 32),
    baseToken: getAddress(order.baseToken),
    totalBetSize: BigInt(order.totalBetSize),
    percentageOdds: BigInt(order.percentageOdds),
    salt: BigInt(order.salt),
    expiry: BigInt(order.expiry),
    maker: getAddress(order.maker),
    isMakerBettingOutcomeOne: order.isMakerBettingOutcomeOne,
  };
  const orderSignature = await wallet.signTypedData(domain, TYPES, message);

  // The order id IS the EIP-712 digest, lowercased. Compute it before submitting.
  const orderId = TypedDataEncoder.hash(domain, TYPES, message).toLowerCase();

  // 5. Submit.
  const res = await fetch(`${API}/orders-v3`, authed({
    method: "POST",
    body: JSON.stringify({ orders: [{ ...order, orderSignature }] }),
  }));
  const body = await res.json();

  console.log(`HTTP ${res.status}`);
  console.log(JSON.stringify(body, null, 2));

  // 6. PENDING is not live. Poll until it appears, or watch the account channel.
  for (let i = 0; i < 10; i++) {
    await new Promise((r) => setTimeout(r, 1000));
    const { data } = await fetch(`${API}/orders-v3?marketHash=${order.marketHash}`, authed())
      .then((r) => r.json());
    const row = data.orders.find((o) => o.id === orderId);
    if (row) {
      console.log(`ACTIVE after ${i + 1}s:`);
      console.log(JSON.stringify(row, null, 2));
      break;
    }
  }
  ```

  ```python post_gtc_order.py theme={null}
  import json, os, secrets, time, requests
  from eth_account import Account
  from eth_account.messages import encode_typed_data
  from eth_utils import keccak, to_checksum_address

  API = "https://api.sx.bet"  # Mainnet — use https://api.toronto.sx.bet for testnet
  account = Account.from_key(os.environ["SX_PRIVATE_KEY"])
  headers = {"Content-Type": "application/json", "x-sx-api-key": os.environ["SX_API_KEY"]}

  # 1. Fetch metadata and build the signing domain.
  meta = requests.get(f"{API}/metadata/obv3").json()["data"]
  domain = {
      "name": meta["domain"]["name"],                   # "OBv3 Escrow"
      "version": meta["domain"]["version"],             # "1"
      "chainId": meta["chainId"],                       # from metadata, never a constant
      "verifyingContract": to_checksum_address(meta["activeAsset"]["escrowAddress"]),
  }

  # 2. Round the odds DOWN onto the ladder, before signing.
  step = int(meta["oddsLadderStepSize"]) * 10**15
  wanted = 40_000_000_000_000_000_000                   # 40.000% implied
  percentage_odds = (wanted // step) * step

  # 3. Build the order. `expiry` is a NUMBER; the three amounts are STRINGS.
  order = {
      "marketHash": os.environ["SX_MARKET_HASH"],
      "maker": account.address,
      "totalBetSize": "1000000",                        # 1 USDC, 6 dp
      "percentageOdds": str(percentage_odds),
      "salt": str(int.from_bytes(secrets.token_bytes(32), "big")),
      "expiry": int(time.time()) + 3600,                # unix seconds; here, 1 hour out
      "baseToken": meta["activeAsset"]["baseToken"],
      "isMakerBettingOutcomeOne": True,                 # the outcome YOU want to bet
      "timeInForce": "GTC",
  }

  # 4. Sign the eight-field struct. Order matters: salt precedes expiry.
  TYPES = {"Order": [
      {"name": "marketHash", "type": "bytes32"},
      {"name": "baseToken", "type": "address"},
      {"name": "totalBetSize", "type": "uint256"},
      {"name": "percentageOdds", "type": "uint256"},
      {"name": "salt", "type": "uint256"},
      {"name": "expiry", "type": "uint256"},
      {"name": "maker", "type": "address"},
      {"name": "isMakerBettingOutcomeOne", "type": "bool"},
  ]}
  message = {
      **order,
      "baseToken": to_checksum_address(order["baseToken"]),
      "totalBetSize": int(order["totalBetSize"]),
      "percentageOdds": int(order["percentageOdds"]),
      "salt": int(order["salt"]),
      "maker": to_checksum_address(order["maker"]),
  }
  del message["timeInForce"]                            # not part of the signed struct
  signable = encode_typed_data(domain_data=domain, message_types=TYPES, message_data=message)
  order_signature = account.sign_message(signable).signature.to_0x_hex()

  # The order id IS the EIP-712 digest, lowercased. Compute it before submitting.
  order_id = "0x" + keccak(b"\x19" + signable.version + signable.header + signable.body).hex()

  # 5. Submit.
  res = requests.post(f"{API}/orders-v3", headers=headers,
                      json={"orders": [{**order, "orderSignature": order_signature}]})
  body = res.json()

  print(f"HTTP {res.status_code}")
  print(json.dumps(body, indent=2))
  returned = body["data"]["orders"][0]["orderId"]
  print(f"local digest  : {order_id}")
  print(f"returned id   : {returned}")
  print(f"match         : {returned.lower() == order_id}")

  # 6. PENDING is not live. Poll until it appears, or watch the account channel.
  for i in range(10):
      time.sleep(1)
      data = requests.get(f"{API}/orders-v3", headers=headers,
                          params={"marketHash": order["marketHash"]}).json()["data"]
      row = next((o for o in data["orders"] if o["id"].lower() == order_id), None)
      if row:
          print(f"ACTIVE after {i + 1}s:")
          print(json.dumps(row, indent=2))
          break
  ```
</CodeGroup>

Output:

```
HTTP 200
{
  "status": "success",
  "data": {
    "orders": [
      {
        "orderId": "0xb4ade904cd8fcdd2c68cf11dbf90a63a9c5d5c762c5a619d74b21b1a62899065",
        "status": "SUBMITTED",
        "commandId": "550e8400-e29b-41d4-a716-446655440000"
      }
    ]
  }
}
ACTIVE after 1s:
{
  "id": "0xb4ade904cd8fcdd2c68cf11dbf90a63a9c5d5c762c5a619d74b21b1a62899065",
  "marketHash": "0x81cfc23d0a02403f32d29b5a7c5686acd5eaedcdaa461cf253a1488e4cac0fcf",
  "userAddress": "0xbcc6D643e4159A75ED1dB4e13330230B82F2AEe5",
  "wallet": "0x4361123dbdc1D812fdf7D27045aF358C9C8AA70A",
  "isBettingOutcomeOne": true,
  "percentageOdds": "40000000000000000000",
  "totalBetSize": "1000000",
  "remainingSize": "1000000",
  "expiry": "2026-07-31T19:52:31.000Z",
  "status": "ACTIVE",
  "inactiveReason": null,
  "eventId": "L12952568",
  "createdAt": "2026-07-31T18:52:31.482Z",
  "updatedAt": "2026-07-31T18:52:31.497Z"
}
```

<Note>
  `expiry` is required and must be a unix epoch timestamp in **seconds** (not milliseconds) that is in
  the future. An `expiry` that falls inside the market's betting delay plus two seconds is rejected.
</Note>

## Post-only

`postOnly: true` on a `GTC` order makes it add liquidity only — if it would cross the book on arrival
it is cancelled with `inactiveReason: "POST_ONLY_WOULD_CROSS"` instead of matching. See
[Time in force](/developers/time-in-force#post-only).

```javascript JavaScript theme={null}
await fetch(`${API}/orders-v3`, authed({
  method: "POST",
  body: JSON.stringify({ orders: [{ ...order, postOnly: true, orderSignature }] }),
}));
```

## Batching

`POST /orders-v3` takes an array of up to `limits.maxCreateOrders` orders (currently **10**), one
shared `maker` per batch — the full field rules are on [Create orders](/api-reference/post-orders-v3).

* **One maker per request.** Every order in the batch must carry the same `maker`. Mixed makers are
  a `400`.
* **Batches are not atomic.** A partial outcome is normal. Always read the status of every entry in the array.

## Dust remainders

<Note>
  A partial fill that would leave a remainder below `limits.minRestingOrderSizeBaseUnits` does not rest that remainder.
  The order then goes terminal: `status: "INACTIVE"` with `inactiveReason: "FILLED"`.
</Note>

## Re-quoting

There is no amend. To move a price: cancel, then post a new order.

## Related

<CardGroup cols={2}>
  <Card title="Create orders" icon="paper-plane" href="/api-reference/post-orders-v3">
    Every field, every validation rule.
  </Card>

  <Card title="Taking liquidity" icon="hand-pointer" href="/developers/taking-liquidity">
    The same signing and submission path, from the taker's side.
  </Card>

  <Card title="Time in force" icon="clock" href="/developers/time-in-force">
    Choosing between GTC, IOC and FOK.
  </Card>

  <Card title="Tracking your orders" icon="list-check" href="/developers/my-orders">
    Reading your own orders back, and what the API will not tell you.
  </Card>

  <Card title="Market making" icon="chart-line" href="/developers/market-making">
    Quoting, exposure and re-quote loops.
  </Card>

  <Card title="External user id" icon="users" href="/developers/external-user-id">
    Tag orders when one SX account places bets for many people.
  </Card>
</CardGroup>


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.