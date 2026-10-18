> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Taking liquidity

> How to fill existing orders on the SX Bet orderbook as a taker.

Taking liquidity is the same request as [posting an order](/developers/posting-orders). There is no separate endpoint. Submit a signed limit order to
[`POST /orders-v3`](/api-reference/post-orders-v3) with `timeInForce: "IOC"` or `"FOK"`, and the order
executes against the book immediately instead of resting on it.

## Prerequisites

Same as posting: your proxy must be deployed and funded, and auth is an API key plus your signature.
See [Prerequisites](/developers/posting-orders#prerequisites) on Posting orders.

## The price you submit

`percentageOdds` is the highest implied probability you're willing to pay — you match at that price or
better, never worse.

`percentageOdds` is an implied probability, so a lower number is a bigger payout: submitting
`"48000000000000000000"` (48%) matches you at 46% but never at 50%.

## Steps

1. **Read the book in your frame** — `GET /orderbook-v3/snapshot/market` with `showTakerPerspective=true`, optionally, to avoid doing the conversion yourself.
2. **Pick the best level** for the outcome you want.
3. **Sign and submit an `IOC` or `FOK`** at that level's price. It matches at that price or better, or
   not at all.
4. **Read the result from your trades and fills** — an `IOC`/`FOK` never rests.
5. **Or listen on your account streams** — subscribe to
   [`account:fills_v3_#{address}`](/api-reference/channel-fills-v3),
   [`account:trades_v3_#{address}`](/api-reference/channel-trades-v3), or
   [`account:orders_v3_#{address}`](/api-reference/channel-orders-v3) to have the same updates pushed to
   you instead of polling.

<Warning>
  **The engine never matches you against your own resting orders.**
</Warning>

## Taking an IOC end to end

<CodeGroup>
  ```js take_now.mjs theme={null}
  import { Wallet, TypedDataEncoder, getAddress, zeroPadValue, randomBytes, hexlify } from "ethers";

  const API = "https://api.sx.bet"; // Mainnet — use https://api.toronto.sx.bet for testnet
  const MARKET = process.env.SX_MARKET_HASH;
  const wallet = new Wallet(process.env.SX_PRIVATE_KEY);
  const authed = (init = {}) => ({
    ...init,
    headers: { "Content-Type": "application/json", "x-sx-api-key": process.env.SX_API_KEY, ...init.headers },
  });

  const { data: meta } = await fetch(`${API}/metadata/obv3`).then((r) => r.json());

  // 1. Read the book in YOUR frame. showTakerPerspective=true shows each side as
  //    the price someone wanting that outcome would pay.
  const { data: book } = await fetch(
    `${API}/orderbook-v3/snapshot/market?marketHash=${MARKET}&showTakerPerspective=true`,
    authed()
  ).then((r) => r.json());

  // 2. Best available price for outcome TWO is index 0 of outcomeTwo.
  const best = book.outcomeTwo[0];
  if (!best) throw new Error("no liquidity on outcome two");

  // 3. Submit an IOC at that price. Anything at this price or better fills.
  const order = {
    marketHash: MARKET,
    maker: wallet.address,
    totalBetSize: "1000000",
    percentageOdds: best.percentageOdds,
    salt: hexlify(randomBytes(32)),
    expiry: Math.floor(Date.now() / 1000) + 3600, // unix seconds; here, 1 hour out
    baseToken: meta.activeAsset.baseToken,
    isMakerBettingOutcomeOne: false,       // betting outcome TWO
    timeInForce: "IOC",
  };
  const TYPES = { Order: [
    { name: "marketHash", type: "bytes32" }, { name: "baseToken", type: "address" },
    { name: "totalBetSize", type: "uint256" }, { name: "percentageOdds", type: "uint256" },
    { name: "salt", type: "uint256" }, { name: "expiry", type: "uint256" },
    { name: "maker", type: "address" }, { name: "isMakerBettingOutcomeOne", type: "bool" },
  ] };
  const domain = meta.domain; // complete EIP-712 domain
  const message = {
    marketHash: zeroPadValue(order.marketHash, 32), baseToken: getAddress(order.baseToken),
    totalBetSize: BigInt(order.totalBetSize), percentageOdds: BigInt(order.percentageOdds),
    salt: BigInt(order.salt), expiry: BigInt(order.expiry), maker: getAddress(order.maker),
    isMakerBettingOutcomeOne: order.isMakerBettingOutcomeOne,
  };
  const orderSignature = await wallet.signTypedData(domain, TYPES, message);
  const orderId = TypedDataEncoder.hash(domain, TYPES, message).toLowerCase();

  const res = await fetch(`${API}/orders-v3`, authed({
    method: "POST",
    body: JSON.stringify({ orders: [{ ...order, orderSignature }] }),
  }));
  console.log(`HTTP ${res.status}`, JSON.stringify(await res.json()));

  // 4. The result is in your trades, not your orders — an IOC never rests.
  await new Promise((r) => setTimeout(r, 8000));
  const { data: f } = await fetch(`${API}/fills-v3?orderId=${orderId}`, authed()).then((r) => r.json());
  for (const x of f.fills) console.log(`  ${x.fillAmount} @ ${x.fillOdds} → returns ${x.returnAmount}`);
  ```

  ```python take_now.py theme={null}
  import json, os, secrets, time, requests
  from eth_account import Account
  from eth_account.messages import encode_typed_data
  from eth_utils import keccak, to_checksum_address

  API = "https://api.sx.bet"  # Mainnet — use https://api.toronto.sx.bet for testnet
  MARKET = os.environ["SX_MARKET_HASH"]
  account = Account.from_key(os.environ["SX_PRIVATE_KEY"])
  headers = {"Content-Type": "application/json", "x-sx-api-key": os.environ["SX_API_KEY"]}

  meta = requests.get(f"{API}/metadata/obv3").json()["data"]

  # 1. Read the book in YOUR frame. showTakerPerspective=true shows each side as
  #    the price someone wanting that outcome would pay.
  book = requests.get(
      f"{API}/orderbook-v3/snapshot/market",
      params={"marketHash": MARKET, "showTakerPerspective": "true"},
      headers=headers,
  ).json()["data"]

  # 2. Best available price for outcome TWO is index 0 of outcomeTwo.
  outcome_two = book["outcomeTwo"]
  if not outcome_two:
      raise Exception("no liquidity on outcome two")
  best = outcome_two[0]

  # 3. Submit an IOC at that price. Anything at this price or better fills.
  order = {
      "marketHash": MARKET,
      "maker": account.address,
      "totalBetSize": "1000000",
      "percentageOdds": best["percentageOdds"],
      "salt": str(int.from_bytes(secrets.token_bytes(32), "big")),
      "expiry": int(time.time()) + 3600,      # unix seconds; here, 1 hour out
      "baseToken": meta["activeAsset"]["baseToken"],
      "isMakerBettingOutcomeOne": False,      # betting outcome TWO
      "timeInForce": "IOC",
  }
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
  domain = {
      "name": meta["domain"]["name"],
      "version": meta["domain"]["version"],
      "chainId": meta["chainId"],
      "verifyingContract": to_checksum_address(meta["activeAsset"]["escrowAddress"]),
  }
  message = {
      **order,
      "baseToken": to_checksum_address(order["baseToken"]),
      "totalBetSize": int(order["totalBetSize"]),
      "percentageOdds": int(order["percentageOdds"]),
      "salt": int(order["salt"]),
      "maker": to_checksum_address(order["maker"]),
  }
  del message["timeInForce"]  # not part of the signed struct
  signable = encode_typed_data(domain_data=domain, message_types=TYPES, message_data=message)
  order_signature = account.sign_message(signable).signature.to_0x_hex()
  order_id = "0x" + keccak(b"\x19" + signable.version + signable.header + signable.body).hex()

  res = requests.post(f"{API}/orders-v3", headers=headers,
                      json={"orders": [{**order, "orderSignature": order_signature}]})
  print(f"HTTP {res.status_code}", res.json())

  # 4. The result is in your trades, not your orders — an IOC never rests.
  time.sleep(8)
  fills = requests.get(
      f"{API}/fills-v3", headers=headers, params={"orderId": order_id}
  ).json()["data"]["fills"]
  for f in fills:
      print(f"  {f['fillAmount']} @ {f['fillOdds']} -> returns {f['returnAmount']}")
  ```
</CodeGroup>

## Partial fills

`IOC` discards whatever didn't match; `FOK` cancels entirely rather than executing partially — see
[Time in force](/developers/time-in-force) for the full behavior table.

An `IOC` that fills partially still reports `inactiveReason: "NO_LIQUIDITY"` on the order. That reason
describes the discarded remainder. A `FOK` checks
fillable depth first and cancels entirely if the depth can't cover your size.

## Related

<CardGroup cols={2}>
  <Card title="Posting orders" icon="paper-plane" href="/developers/posting-orders">
    The same signing and submission path, from the maker's side.
  </Card>

  <Card title="Market making" icon="chart-line" href="/developers/market-making">
    The global notional budget aggressive orders are checked against.
  </Card>

  <Card title="Time in force" icon="clock" href="/developers/time-in-force">
    Why IOC and FOK are the only two immediate types.
  </Card>

  <Card title="Betting delays" icon="hourglass-half" href="/developers/betting-delays">
    The wait an order takes on before it matches.
  </Card>

  <Card title="Create orders" icon="paper-plane" href="/api-reference/post-orders-v3">
    Fields, validation rules and every error body.
  </Card>

  <Card title="Get orderbook snapshot" icon="layer-group" href="/api-reference/get-market-orderbook-snapshot">
    Reading depth, and the prices from the taker's side.
  </Card>

  <Card title="Bet lifecycle" icon="receipt" href="/developers/bet-lifecycle">
    What happens to a bet after it matches.
  </Card>
</CardGroup>


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.