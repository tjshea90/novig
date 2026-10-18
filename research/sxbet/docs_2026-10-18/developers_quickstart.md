> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Quickstart

> Go from zero to your first bet on SX Bet.

This quickstart walks you through the essentials of programmatic betting on SX Bet. By the end, you'll have deployed a wallet, read a live orderbook, and placed a real bet.

## What you'll need

* An account on SX Bet (for signing orders)
* An API key (for authenticated requests)
* USDC (to place a bet)

Listing markets is public. Reading the order book requires your API key. You need an account, a key, and funds to place orders.

## Base URL

All API requests go to:

```
https://api.sx.bet
```

Use `https://api.toronto.sx.bet` for testnet. See [Testnet & Mainnet](/developers/testnet-and-mainnet).

## Install dependencies

<CodeGroup>
  ```bash Python theme={null}
  pip install requests eth-account python-dotenv centrifuge-python aiohttp
  ```

  ```bash JavaScript theme={null}
  npm install ethers dotenv centrifuge
  ```
</CodeGroup>

<Steps>
  <Step title="Create an account and an API key">
    Sign up at [sx.bet](https://sx.bet) with email or Google. Complete registration by choosing a username.

    Fetch your private key from the [assets page](https://sx.bet/wallet/assets) — you'll use it to sign orders. Then generate an API key from the **API section of your account page**; it is shown once.

    Store both in a `.env` file:

    ```bash theme={null}
    SX_PRIVATE_KEY="0xyour_private_key_here"
    SX_API_KEY="your_api_key_here"
    ```
  </Step>

  <Step title="Deploy and fund your proxy wallet">
    Bets are placed from a **proxy wallet** your account owns. You can either deploy this proxy wallet programmatically, or
    simply login to [sx.bet](https://sx.bet) and one will be deployed for you automatically.

    ```bash cURL theme={null}
    # Deploy
    curl -X POST "https://api.sx.bet/user/deploy-proxy" \
      -H "x-sx-api-key: $SX_API_KEY"

    # Poll until deployed
    curl "https://api.sx.bet/user/proxy" -H "x-sx-api-key: $SX_API_KEY"
    ```

    Once the proxy is deployed, fund it with USDC. Easiest path: deposit in the [sx.bet](https://sx.bet) UI.
    Programmatically, transfer from your EOA (`FROM_EOA`) with an EIP-2612 permit via
    [`POST /user/transfer-to-proxy`](/api-reference/post-user-transfer-to-proxy) — full flow in
    [Funding](/developers/funding).

    Confirm a spendable balance:

    ```bash cURL theme={null}
    curl "https://api.sx.bet/user/balance-v3" -H "x-sx-api-key: $SX_API_KEY"
    ```
  </Step>

  <Step title="Fetch a market">
    Fetch active markets with [`GET /markets/active`](/api-reference/get-markets-active) to find one you want to bet on. Copy the `marketHash` — you'll need it in the next steps.

    <CodeGroup>
      ```bash cURL theme={null}
      curl "https://api.sx.bet/markets/active?onlyMainLine=true"
      ```

      ```python Python theme={null}
      import requests

      BASE_URL = "https://api.sx.bet"  # Mainnet — use https://api.toronto.sx.bet for testnet

      markets = requests.get(
          f"{BASE_URL}/markets/active",
          params={"onlyMainLine": True}
      ).json()["data"]["markets"]

      first = markets[0]
      print("Market hash:", first["marketHash"])
      print("Event:", first["teamOneName"], "vs", first["teamTwoName"])
      print(f"Outcomes: [1 = {first['outcomeOneName']}, 2 = {first['outcomeTwoName']}]")
      print("Start time:", first["gameTime"])
      ```

      ```javascript JavaScript theme={null}
      const BASE_URL = "https://api.sx.bet"; // Mainnet — use https://api.toronto.sx.bet for testnet

      const data = await fetch(
        `${BASE_URL}/markets/active?onlyMainLine=true`
      ).then((r) => r.json());
      const markets = data.data.markets;

      const first = markets[0];
      console.log("Market hash:", first.marketHash);
      console.log("Event:", first.teamOneName, "vs", first.teamTwoName);
      console.log(`Outcomes: [1 = ${first.outcomeOneName}, 2 = ${first.outcomeTwoName}]`);
      console.log("Start time:", first.gameTime);
      ```
    </CodeGroup>
  </Step>

  <Step title="Fetch the best price">
    Read the orderbook with `showTakerPerspective=true`, which quotes each side as the price *you* would pay to bet that outcome. Index `0` is the best available. Copy the `percentageOdds` for the outcome you want — you submit it as-is in the next step.

    <CodeGroup>
      ```bash cURL theme={null}
      curl "https://api.sx.bet/orderbook-v3/snapshot/market?marketHash=YOUR_MARKET_HASH&showTakerPerspective=true" \
        -H "x-sx-api-key: $SX_API_KEY"
      ```

      ```python Python theme={null}
      import os

      market_hash = "YOUR_MARKET_HASH"  # from step 3

      book = requests.get(
          f"{BASE_URL}/orderbook-v3/snapshot/market",
          params={"marketHash": market_hash, "showTakerPerspective": True},
          headers={"x-sx-api-key": os.environ["SX_API_KEY"]},
      ).json()["data"]

      # Best price to bet each outcome. The list is empty if nobody is quoting that side.
      for label, levels in (("outcome 1", book["outcomeOne"]), ("outcome 2", book["outcomeTwo"])):
          if not levels:
              print(f"Best price on {label}: no liquidity")
              continue
          best = levels[0]
          print(f"Best price on {label}: {int(best['percentageOdds']) / 10**20:.2%}"
                f" ({best['percentageOdds']}), {int(best['size']) / 1e6:.2f} USDC resting")
      ```

      ```javascript JavaScript theme={null}
      const marketHash = "YOUR_MARKET_HASH"; // from step 3

      const { data } = await fetch(
        `${BASE_URL}/orderbook-v3/snapshot/market?${new URLSearchParams({
          marketHash,
          showTakerPerspective: true,
        })}`,
        { headers: { "x-sx-api-key": process.env.SX_API_KEY } }
      ).then((r) => r.json());
      const book = data;

      // Best price to bet each outcome. The list is empty if nobody is quoting that side.
      for (const [label, levels] of [["outcome 1", book.outcomeOne], ["outcome 2", book.outcomeTwo]]) {
        const best = levels[0];
        if (!best) {
          console.log(`Best price on ${label}: no liquidity`);
          continue;
        }
        console.log(
          `Best price on ${label}: ${(Number(best.percentageOdds) / 1e18).toFixed(2)}%` +
            ` (${best.percentageOdds}), ${(Number(best.size) / 1e6).toFixed(2)} USDC resting`
        );
      }
      ```
    </CodeGroup>
  </Step>

  <Step title="Place a bet">
    Sign an order for the market from step 3 at the price from step 4, and submit it with [`timeInForce: "FOK"`](/developers/time-in-force) — fill completely and immediately, or nothing happens. Contract addresses and the chain id come from `GET /metadata/obv3`.

    The order id *is* the EIP-712 digest of what you signed, see [EIP-712 order signing](/api-reference/eip712-order-signing).

    <CodeGroup>
      ```python Python theme={null}
      import os
      import secrets
      import time
      import requests
      from eth_account import Account
      from eth_account.messages import encode_typed_data
      from eth_utils import keccak

      BASE_URL = "https://api.sx.bet"  # Mainnet — use https://api.toronto.sx.bet for testnet

      account = Account.from_key(os.environ["SX_PRIVATE_KEY"])

      market_hash = "YOUR_MARKET_HASH"        # from step 3
      percentage_odds = "YOUR_BEST_ODDS"      # from step 4
      is_betting_outcome_one = True           # True = bet outcome 1, False = bet outcome 2
      stake = "1000000"                       # 1 USDC (6 decimals)

      meta = requests.get(f"{BASE_URL}/metadata/obv3").json()["data"]

      order = {
          "marketHash": market_hash,
          "baseToken": meta["activeAsset"]["baseToken"],
          "totalBetSize": stake,
          "percentageOdds": percentage_odds,
          "salt": "0x" + secrets.token_hex(32),
          "expiry": int(time.time()) + 3600,  # unix seconds; here, 1 hour out
          "maker": account.address,
          "isMakerBettingOutcomeOne": is_betting_outcome_one,
      }

      # --- Sign the order ---
      DOMAIN = meta["domain"]  # complete EIP-712 domain

      ORDER_TYPES = {
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
      }

      # The typed data wants integers where the request body wants strings.
      message = {
          **order,
          "totalBetSize": int(order["totalBetSize"]),
          "percentageOdds": int(order["percentageOdds"]),
          "salt": int(order["salt"], 16),
      }
      signable = encode_typed_data(DOMAIN, ORDER_TYPES, message)
      signed = account.sign_message(signable)

      # The order id IS the EIP-712 digest, lowercased — compute it and check the server agrees.
      order_id = "0x" + keccak(b"\x19" + signable.version + signable.header + signable.body).hex()

      # --- Submit ---
      response = requests.post(
          f"{BASE_URL}/orders-v3",
          headers={"x-sx-api-key": os.environ["SX_API_KEY"]},
          json={"orders": [{
              **order,
              "timeInForce": "FOK",
              "orderSignature": signed.signature.to_0x_hex(),
          }]},
      )
      result = response.json()
      print("Order result:", result)
      print("digest matches:", result["data"]["orders"][0]["orderId"].lower() == order_id)
      ```

      ```javascript JavaScript theme={null}
      import "dotenv/config";
      import { Wallet, TypedDataEncoder, randomBytes, hexlify } from "ethers";

      const BASE_URL = "https://api.sx.bet"; // Mainnet — use https://api.toronto.sx.bet for testnet

      const wallet = new Wallet(process.env.SX_PRIVATE_KEY);

      const marketHash = "YOUR_MARKET_HASH";     // from step 3
      const percentageOdds = "YOUR_BEST_ODDS";   // from step 4
      const isBettingOutcomeOne = true;          // true = bet outcome 1, false = bet outcome 2
      const stake = "1000000";                   // 1 USDC (6 decimals)

      const { data: meta } = await fetch(`${BASE_URL}/metadata/obv3`).then((r) => r.json());

      const order = {
        marketHash,
        baseToken: meta.activeAsset.baseToken,
        totalBetSize: stake,
        percentageOdds,
        salt: hexlify(randomBytes(32)),
        expiry: Math.floor(Date.now() / 1000) + 3600, // unix seconds; here, 1 hour out
        maker: wallet.address,
        isMakerBettingOutcomeOne: isBettingOutcomeOne,
      };

      // --- Sign the order ---
      const domain = meta.domain; // complete EIP-712 domain

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

      const orderSignature = await wallet.signTypedData(domain, types, order);

      // The order id IS the EIP-712 digest, lowercased — compute it and check the server agrees.
      const orderId = TypedDataEncoder.hash(domain, types, order).toLowerCase();

      // --- Submit ---
      const response = await fetch(`${BASE_URL}/orders-v3`, {
        method: "POST",
        body: JSON.stringify({
          orders: [{ ...order, timeInForce: "FOK", orderSignature }],
        }),
        headers: {
          "Content-Type": "application/json",
          "x-sx-api-key": process.env.SX_API_KEY,
        },
      });
      const result = await response.json();
      console.log("Order result:", result);
      console.log("digest matches:", result.data.orders[0].orderId === orderId);
      ```
    </CodeGroup>
  </Step>

  <Step title="Stream updates">
    Watch your order and the market in real time over a single WebSocket connection. Subscribe to two channels: `orderbook_v3:{marketHash}` is public and streams the complete resting book on every change, while `account:orders_v3_#{address}` is authenticated and pushes each of your own orders as it rests, fills, or goes inactive.

    Fetch a realtime JWT from [`GET /user/realtime-token-v3/api-key`](/developers/realtime-initialization#connect). The account channel suffix must equal your token's `sub`, which is your **user address** — the account that signs orders (the `maker` from step 5), **not** the proxy wallet that holds the funds. Using the proxy address here silently returns nothing. See [Real-time data](/developers/realtime-overview), [Order Book Updates](/api-reference/channel-orderbook-v3), and [Active Order Updates](/api-reference/channel-orders-v3).

    <CodeGroup>
      ```python Python theme={null}
      import asyncio
      import os
      import aiohttp
      from centrifuge import Client, PublicationContext, SubscriptionEventHandler

      BASE_URL = "https://api.sx.bet"                       # Mainnet — use https://api.toronto.sx.bet for testnet
      WS_URL = "wss://realtime.sx.bet/connection/websocket"  # Testnet: wss://realtime.toronto.sx.bet/connection/websocket

      market_hash = "YOUR_MARKET_HASH"                        # from step 3
      address = "YOUR_CHECKSUMMED_ADDRESS"                    # your USER address (the maker from step 5), checksummed — NOT the proxy wallet

      # Async so the client can refresh the token before its 24h expiry. Passing a
      # static string instead would kill the connection once the token lapses.
      async def get_token(ctx=None):
          async with aiohttp.ClientSession() as session:
              async with session.get(
                  f"{BASE_URL}/user/realtime-token-v3/api-key",
                  headers={"x-sx-api-key": os.environ["SX_API_KEY"]},
              ) as resp:
                  resp.raise_for_status()
                  return (await resp.json())["token"]

      async def on_book(ctx: PublicationContext) -> None:
          book = ctx.data
          # Every message is the full resting book — replace your state, don't patch it.
          print("book update:", book["marketHash"], book["outcomeOne"], book["outcomeTwo"])

      async def on_order(ctx: PublicationContext) -> None:
          order = ctx.data["order"]
          print("order update:", order["id"], order["status"], order["remainingSize"])

      async def main():
          client = Client(WS_URL, get_token=get_token)
          await client.connect()

          book_sub = client.new_subscription(
              f"orderbook_v3:{market_hash}",
              SubscriptionEventHandler(on_publication=on_book),
          )
          await book_sub.subscribe()

          order_sub = client.new_subscription(
              f"account:orders_v3_#{address}",
              SubscriptionEventHandler(on_publication=on_order),
          )
          await order_sub.subscribe()

          await asyncio.Future()  # keep running

      asyncio.run(main())
      ```

      ```javascript JavaScript theme={null}
      import "dotenv/config";
      import { Centrifuge, UnauthorizedError } from "centrifuge";

      const BASE_URL = "https://api.sx.bet";                        // Mainnet — use https://api.toronto.sx.bet for testnet
      const WS_URL = "wss://realtime.sx.bet/connection/websocket";   // Testnet: wss://realtime.toronto.sx.bet/connection/websocket

      const marketHash = "YOUR_MARKET_HASH";                         // from step 3
      const address = "YOUR_CHECKSUMMED_ADDRESS";                    // your USER address (the maker from step 5), checksummed — NOT the proxy wallet

      // Pass the FUNCTION, not a string, so Centrifugo can refresh the token before it expires.
      async function fetchToken() {
        const res = await fetch(`${BASE_URL}/user/realtime-token-v3/api-key`, {
          headers: { "x-sx-api-key": process.env.SX_API_KEY },
        });
        if (res.status === 401 || res.status === 403) throw new UnauthorizedError();
        if (!res.ok) throw new Error(`token endpoint returned ${res.status}`);
        return (await res.json()).token;
      }

      const client = new Centrifuge(WS_URL, { getToken: fetchToken });
      client.connect();

      // Public: every message is the full resting book — replace your state, don't patch it.
      client
        .newSubscription(`orderbook_v3:${marketHash}`)
        .on("publication", ({ data }) => {
          console.log("book update:", data.marketHash, data.outcomeOne, data.outcomeTwo);
        })
        .subscribe();

      // Authenticated: the suffix must match your token's `sub` (your user address, not the proxy wallet).
      client
        .newSubscription(`account:orders_v3_#${address}`, { recoverable: true })
        .on("publication", ({ data }) => {
          const order = data.order;
          console.log("order update:", order.id, order.status, order.remainingSize);
        })
        .subscribe();
      ```
    </CodeGroup>

    Order book levels are always the maker frame. Convert to the price you'd pay with `takerOdds = 1 - percentageOdds / 10^20`. See [unit conversions](/developers/unit-conversions).
  </Step>
</Steps>

<CardGroup cols={2}>
  <Card title="The exchange model" icon="arrows-left-right" href="/developers/exchange-model">
    How the exchange and orderbook work, in depth.
  </Card>

  <Card title="Taking liquidity" icon="bolt" href="/developers/taking-liquidity">
    Signing and submitting orders that execute immediately.
  </Card>
</CardGroup>


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.