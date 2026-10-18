> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Migrate to V3 in 30 minutes

> How to migrate an existing V2 integration to V3.

This guide is for developers with a **working V2 client**.

For the reasoning behind the changes, see [Improvements](/developers/new-in-v3). For a new integration, start at [Quickstart](/developers/quickstart).

## What's fundamentally different

Five changes underlie the rest of this guide:

1. **Capital moves to a proxy wallet.** You deploy and fund a secure proxy. A new prerequisite with no V2 equivalent.
2. **One endpoint to make and take.** Maker and taker both POST to `/orders-v3`. `timeInForce` (`GTC` rests; `IOC`/`FOK` execute immediately) determines which you are.
3. **API keys are required** on every authenticated request — and the header is renamed: V2 `X-Api-Key` → V3 `x-sx-api-key`. Search-and-replace it everywhere.
4. **Refund and capital efficiency events are in-lined.** No longer separate events or endpoints
5. **Order signing process has changed**
6. **`pendingFills`** fields have been removed.

## What has not changed

* `percentageOdds` is still implied probability × 10^20; `totalBetSize` is still your stake in base-token units (USDC = 6 decimals). See [Unit conversions](/developers/unit-conversions).
* The odds ladder is still enforced (default step 0.125%). Read `oddsLadderStepSize` from `GET /metadata/obv3`. See [Odds rounding](/developers/odds-rounding).
* Resting orders are still quoted in the maker frame; taker implied probability = 1 − maker probability. (`showTakerPerspective=true` on the orderbook snapshot does the inversion for you.)
* You still sign with your EOA private key, and maker is still your EOA address.
* Market discovery is unchanged: `GET /markets/active`, `GET /markets/find`, and the sports / leagues / fixtures endpoints and shapes.
* The `{ "status": ..., "data": ... }` response envelope is unchanged.
* The realtime endpoint and protocol are unchanged (`wss://realtime.sx.bet/connection/websocket`, Centrifugo), and the `fixtures:*`, `markets:global`, `main_line:global` and `parlay_markets:global` channels are unchanged.
* Testnet hosts are unchanged: `https://api.toronto.sx.bet` and `wss://realtime.toronto.sx.bet/connection/websocket`.

## Step-by-step guide

<Steps>
  <Step title="Set up the account: deploy and fund a proxy wallet">
    Deploy a proxy your account owns and move USDC into it — see [Accounts](/developers/accounts). Easiest way is just login to [sx.bet](https://sx.bet) and go through the wizard.

    Prefer to stay in code? The whole flow is API-first too (all with the `x-sx-api-key` header):

    ```text theme={null}
    POST /user/deploy-proxy         # deploy
    GET  /user/proxy                # poll until deployed; returns the proxy address
    GET  /user/balance-v3           # confirm funds arrived
    ```

    Funding the proxy replaces V2's "enable betting" step. There is no longer an ERC-20 approval of the `TokenTransferProxy` (`POST /orders/approve` is gone)

    Already live on V2? Your trading capital currently sits in your EOA under V2's model; to trade on V3 it must be inside the proxy. Login to your SX Bet account and follow the pop-up wizard to migrate.

    <Warning>
      All V2 orders at cutoff will be auto-cancelled. **V2 clients will fail to run after V3 goes live at 10AM EST on August 26.**
    </Warning>
  </Step>

  <Step title="Get a new API key">
    Create a **new** API key — your existing V2 key will not work. Log in at [sx.bet](https://sx.bet), open **Account → Overview**, generate a key, and send it in the `x-sx-api-key` header on every authenticated request. See [API Keys](/api-reference/api-key).

    <Warning>
      This key is more sensitive than before, as it can now cancel all your orders.
    </Warning>
  </Step>

  <Step title="Use POST /orders-v3 for posting orders and fills">
    * There is no longer a separate endpoint for fills. The endpoint is [`POST /orders-v3`](/api-reference/post-orders-v3) `timeInForce` is new and required and the signature has changed. `timeInForce` implies if your order will rest or not.
    * If you're a market maker, use `GTC`. If you're a taker, use `IOC`. See [Time in force](/developers/time-in-force).
    * The signing of the order has also changed. See the example below.
    * The old `apiExpiry` field was eliminated and merged into just a single `expiry` field. In V3 `expiry` is a real unix epoch timestamp in seconds (not milliseconds) inside the signature, and it is mandatory — it must be in the future, and `0` is rejected. There is no never-expires order. Because it is signed, changing an order’s TTL means re-signing. V2’s constant `2209006800` is gone — do not send it.
    * The V2 slippage vocabulary is also gone — there are no `desiredOdds` or `oddsSlippage` fields, and no `ODDS_STALE` error. `percentageOdds` is the worst price you will accept; the engine matches at that price or better. See [Odds](/developers/odds).
    * **This endpoint, by default, is now asynchronous, meaning that the outcome of your order (filled / partially filled / cancelled) is not known immediately**. Use `waitForOutcome` to keep the old synchronous behaviour. This applies to both orders that will match, clear, or rest.

    Field-by-field, what changed on the order body from V2 (`POST /orders/new` and `POST /orders/fill/v2`) to V3 (`POST /orders-v3`):

    | Field | V2 | V3 |
    | - | - | - |
    | `expiry` | Dummy constant `2209006800` | Real TTL as a unix epoch timestamp in seconds, and part of the signature. Required, and must be in the future — `0` is rejected. |
    | `apiExpiry` | The real order TTL, sent alongside `expiry` | **Removed** — merged into `expiry` |
    | `executor` | Required signed address | **Removed** |
    | `timeInForce` | — (resting was implied; takers called `POST /orders/fill/v2`) | **New, required** — `GTC`, `IOC`, or `FOK` |
    | `signature` → `orderSignature` | `signature`, over a 9-field struct ordered `…, expiry, salt, maker, executor, …` | `orderSignature`, over the 8-field struct ordered `…, salt, expiry, maker, …` (no `executor`) |
    | `desiredOdds` / `oddsSlippage` | Taker slippage fields on the fill endpoint | **Removed** — `percentageOdds` is the worst price you'll accept |
    | `waitForOutcome` | — (fills were synchronous) | **New, optional** — the endpoint is async by default; set it to keep sync behaviour |

    `marketHash`, `maker` (your EOA, never the proxy), `baseToken`, `totalBetSize`, `percentageOdds`, `salt`, and `isMakerBettingOutcomeOne` are unchanged. The EIP-712 domain `version` is now `"1"`, not `"1.0"`.

    <CodeGroup>
      ```javascript JavaScript theme={null}
      import { Wallet, hexlify, randomBytes } from "ethers";

      const API = "https://api.sx.bet";                  // testnet: https://api.toronto.sx.bet
      const wallet = new Wallet(process.env.SX_PRIVATE_KEY);
      const marketHash = process.env.SX_MARKET_HASH;

      const { data: meta } = await fetch(`${API}/metadata/obv3`).then((r) => r.json());
      const order = {
        marketHash, maker: wallet.address,
        baseToken: meta.activeAsset.baseToken,
        totalBetSize: "100000000",
        percentageOdds: "52500000000000000000",
        salt: hexlify(randomBytes(32)),
        expiry: Math.floor(Date.now() / 1000) + 3600,    // ONE expiry, signed. Real unix seconds, required
        isMakerBettingOutcomeOne: true,
        timeInForce: "GTC",                              // NEW, required.
      };
      // EIP-712 typed data. 8 fields, salt BEFORE expiry. No executor.
      const domain = meta.domain; // complete EIP-712 domain; version is "1", not "1.0"
      const types = { Order: [
        { name: "marketHash", type: "bytes32" }, { name: "baseToken", type: "address" },
        { name: "totalBetSize", type: "uint256" }, { name: "percentageOdds", type: "uint256" },
        { name: "salt", type: "uint256" }, { name: "expiry", type: "uint256" },
        { name: "maker", type: "address" }, { name: "isMakerBettingOutcomeOne", type: "bool" },
      ] };
      const orderSignature = await wallet.signTypedData(domain, types, order);
      const res = await fetch(`${API}/orders-v3`, {
        method: "POST",
        headers: { "Content-Type": "application/json", "x-sx-api-key": process.env.SX_API_KEY },
        // waitForOutcome makes the call block for the matching result (the old sync behaviour).
        body: JSON.stringify({ orders: [{ ...order, orderSignature }], waitForOutcome: true }),
      });
      console.log(JSON.stringify(await res.json(), null, 2));
      ```

      ```python Python theme={null}
      import os, secrets, time, requests
      from eth_account import Account
      from eth_account.messages import encode_typed_data

      API = "https://api.sx.bet"                          # testnet: https://api.toronto.sx.bet
      account = Account.from_key(os.environ["SX_PRIVATE_KEY"])
      market_hash = os.environ["SX_MARKET_HASH"]

      meta = requests.get(f"{API}/metadata/obv3").json()["data"]
      order = {
          "marketHash": market_hash, "maker": account.address, "baseToken": meta["activeAsset"]["baseToken"],
          "totalBetSize": "100000000",
          "percentageOdds": "52500000000000000000",
          "salt": "0x" + secrets.token_hex(32),
          "expiry": int(time.time()) + 3600,             # ONE expiry, signed. Real unix seconds, required
          "isMakerBettingOutcomeOne": True,
          "timeInForce": "GTC",                          # NEW, required. GTC rests
      }
      # EIP-712 typed data. 8 fields, salt BEFORE expiry. No executor.
      domain = meta["domain"]  # complete EIP-712 domain; version is "1", not "1.0"
      types = {"Order": [
          {"name": "marketHash", "type": "bytes32"}, {"name": "baseToken", "type": "address"},
          {"name": "totalBetSize", "type": "uint256"}, {"name": "percentageOdds", "type": "uint256"},
          {"name": "salt", "type": "uint256"}, {"name": "expiry", "type": "uint256"},
          {"name": "maker", "type": "address"}, {"name": "isMakerBettingOutcomeOne", "type": "bool"}]}
      message = {**order, "totalBetSize": int(order["totalBetSize"]),
                 "percentageOdds": int(order["percentageOdds"]), "salt": int(order["salt"], 16)}
      signed = account.sign_message(encode_typed_data(domain, types, message))
      # waitForOutcome makes the call block for the matching result (the old sync behaviour).
      res = requests.post(f"{API}/orders-v3", headers={"x-sx-api-key": os.environ["SX_API_KEY"]},
                          json={"orders": [{**order, "orderSignature": signed.signature.to_0x_hex()}],
                                "waitForOutcome": True})
      print(res.json())
      ```

      ```json Response theme={null}
      {
        "status": "success",
        "data": {
          "orders": [
            {
              "orderId": "0x7f611d85216fa11810d5357ffaaeafbbefb88e69b9fd26bfae7b13a67cceb9dc",
              "status": "SUBMITTED",
              "commandId": "550e8400-e29b-41d4-a716-446655440000",
              "outcome": {
                "state": "FULLY_FILLED",
                "remainingAmount": "0",
                "fillAmount": "2000000",
                "blendedOdds": "50000000000000000000",
                "matchIds": ["0x9a1c2f3e4b5d6a7f8c9b0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a"],
                "tradeId": "0xtrade1"
              }
            }
          ]
        }
      }
      ```
    </CodeGroup>

    The top-level `status` (`SUBMITTED`/`FAILED`) is the accept/reject of your submission and is independent of `outcome.state`. `outcome` is present only because `waitForOutcome` was set; without it the call returns as soon as the order is accepted and you track the result on the `account:orders_v3` channel instead.

    Worked example — the two frames: you post GTC with `percentageOdds = "52500000000000000000"` (52.5% — your implied probability on your own outcome). A taker who wants the other outcome sees your level as 47.5% under `showTakerPerspective=true` and submits their own order with `percentageOdds = "47500000000000000000"` as their worst acceptable price.

    Full new signing guide: [EIP-712 order signing](/api-reference/eip712-order-signing).
  </Step>

  <Step title="Taking liquidity">
    As mentioned above, **the fill endpoint is gone.** Taking is just submitting an order with `timeInForce: "IOC" / "FOK"`. `percentageOdds` is the worst price you will accept.

    <CodeGroup>
      ```javascript JavaScript theme={null}
      import { Wallet, hexlify, randomBytes } from "ethers";

      const API = "https://api.sx.bet";                  // testnet: https://api.toronto.sx.bet
      const wallet = new Wallet(process.env.SX_PRIVATE_KEY);
      // `domain` and `types` are identical to the posting step — reuse them.

      // No inversion. percentageOdds is YOUR price for the outcome YOU want, read from
      // GET /orderbook-v3/snapshot/market?...&showTakerPerspective=true. No slippage field exists.
      const order = {
        marketHash, maker: wallet.address, baseToken: meta.activeAsset.baseToken,
        totalBetSize: "50000000",
        percentageOdds: takerPrice,          // your bound; matches at this or better
        salt: hexlify(randomBytes(32)), expiry: Math.floor(Date.now() / 1000) + 3600,
        isMakerBettingOutcomeOne: true,      // the outcome YOU want
        timeInForce: "IOC",                  // or "FOK". This is the only taker-specific field
      };
      const orderSignature = await wallet.signTypedData(domain, types, order);  // SAME domain + types as posting
      const res = await fetch(`${API}/orders-v3`, {
        method: "POST",
        headers: { "Content-Type": "application/json", "x-sx-api-key": process.env.SX_API_KEY },
        // waitForOutcome blocks for the fill result — takers usually want this.
        body: JSON.stringify({ orders: [{ ...order, orderSignature }], waitForOutcome: true }),
      });
      console.log(JSON.stringify(await res.json(), null, 2));
      ```

      ```python Python theme={null}
      import os, secrets, time, requests
      from eth_account import Account
      from eth_account.messages import encode_typed_data
      # `domain` and `types` are identical to the posting step — reuse them.

      API = "https://api.sx.bet"                          # testnet: https://api.toronto.sx.bet
      account = Account.from_key(os.environ["SX_PRIVATE_KEY"])

      # No inversion. percentage_odds is YOUR price for the outcome YOU want, read from
      # GET /orderbook-v3/snapshot/market?...&showTakerPerspective=true. No slippage field exists.
      order = {
          "marketHash": market_hash, "maker": account.address, "baseToken": meta["activeAsset"]["baseToken"],
          "totalBetSize": "50000000",
          "percentageOdds": taker_price,       # your bound; matches at this or better
          "salt": "0x" + secrets.token_hex(32), "expiry": int(time.time()) + 3600,
          "isMakerBettingOutcomeOne": True,    # the outcome YOU want
          "timeInForce": "IOC",                # or "FOK". This is the only taker-specific field
      }
      message = {**order, "totalBetSize": int(order["totalBetSize"]),
                 "percentageOdds": int(order["percentageOdds"]), "salt": int(order["salt"], 16)}
      signed = account.sign_message(encode_typed_data(domain, types, message))  # SAME domain + types as posting
      # waitForOutcome blocks for the fill result — takers usually want this.
      res = requests.post(f"{API}/orders-v3", headers={"x-sx-api-key": os.environ["SX_API_KEY"]},
                          json={"orders": [{**order, "orderSignature": signed.signature.to_0x_hex()}],
                                "waitForOutcome": True})
      print(res.json())
      ```

      ```json Response theme={null}
      {
        "status": "success",
        "data": {
          "orders": [
            {
              "orderId": "0x0e9e6910f10e0aced2059f2736011c05b9a9cf5c587d150f3b413159560c7a76",
              "status": "SUBMITTED",
              "commandId": "74a01fe8-6555-4492-8d28-879c151aef8b",
              "outcome": {
                "state": "PARTIAL_FILL_DONE",
                "remainingAmount": "20000000",
                "fillAmount": "30000000",
                "blendedOdds": "47500000000000000000",
                "matchIds": ["0x6c2d2a49c159720d26fa89a13ac49711aba2adac5dad2c12255440ebdf7a62c4"],
                "tradeId": "0xac6bb30bba6ea82d8717219a4fa49d15b58fd638fba479f294b4d402376d14d6"
              }
            }
          ]
        }
      }
      ```
    </CodeGroup>

    An `IOC` order fills what it can and cancels the rest, so a partial fill returns `outcome.state: "PARTIAL_FILL_DONE"` with the unmatched `remainingAmount` cancelled (a `FOK` order instead fills completely or cancels whole). Drop `waitForOutcome` and the call returns as soon as the order is accepted (`status: "SUBMITTED"`, no `outcome`), and you track fills on the `account:fills_v3` channel instead.
  </Step>

  <Step title="Adjust cancel actions">
    Cancels no longer have an extra signature. In V3 all three are just `DELETE`s authenticated by your API key. `CancelOrderV2SportX` / `CancelOrderEventsSportX` / `CancelAllOrdersSportX` EIP-712 domains are now gone.

    <CodeGroup>
      ```javascript JavaScript theme={null}
      const API = "https://api.sx.bet";                  // testnet: https://api.toronto.sx.bet

      // V3 — no signature. Just the key.
      const auth = { "x-sx-api-key": process.env.SX_API_KEY, "Content-Type": "application/json" };
      // By id: body is { orders: [{ orderId }] }, up to limits.maxCancelOrders (100).
      const res = await fetch(`${API}/orders-v3`, {
        method: "DELETE", headers: auth,
        body: JSON.stringify({ orders: orderIds.map((orderId) => ({ orderId })) }),
      });
      console.log(JSON.stringify(await res.json(), null, 2));
      // By event: eventId is a QUERY param, and there is no body.
      await fetch(`${API}/orders-v3/event?eventId=${eventId}`, { method: "DELETE", headers: auth });
      await fetch(`${API}/orders-v3/all`, { method: "DELETE", headers: auth });
      ```

      ```python Python theme={null}
      import os, requests

      API = "https://api.sx.bet"                          # testnet: https://api.toronto.sx.bet

      # V3 — no signature. Just the key.
      auth = {"x-sx-api-key": os.environ["SX_API_KEY"]}
      # By id: body is {"orders": [{"orderId": ...}]}, up to limits.maxCancelOrders (100).
      res = requests.delete(f"{API}/orders-v3", headers=auth,
                            json={"orders": [{"orderId": o} for o in order_ids]})
      print(res.json())
      # By event: eventId is a QUERY param, and there is no body.
      requests.delete(f"{API}/orders-v3/event", headers=auth, params={"eventId": event_id})
      requests.delete(f"{API}/orders-v3/all",   headers=auth)
      ```

      ```json Response (cancel by id) theme={null}
      {
        "status": "success",
        "data": {
          "cancelled": [
            {
              "orderId": "0x0e9e6910f10e0aced2059f2736011c05b9a9cf5c587d150f3b413159560c7a76",
              "commandId": "74a01fe8-6555-4492-8d28-879c151aef8b"
            }
          ],
          "notCancelled": [
            { "orderId": "0xeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee", "reason": "NOT_FOUND" }
          ],
          "unconfirmed": []
        }
      }
      ```
    </CodeGroup>

    Cancel-by-id waits for the engine and reports per order; cancel-by-event and cancel-all are asynchronous and only confirm the job was published. See [Cancelling orders](/developers/cancelling-orders).
  </Step>

  <Step title="Re-arm your heartbeat (dead-man switch)">
    Endpoint renamed: V2 `POST /heartbeat` → V3 [`POST /heartbeat/v3`](/api-reference/post-heartbeat-v3). V2's `requestor` field is gone (the timer is keyed to your API key's account):

    ```http theme={null}
    POST /heartbeat/v3        x-sx-api-key: $SX_API_KEY
    { "timeoutSeconds": 60 }  # range 0–3600; response returns expiresAt
    ```

    Semantics are unchanged: miss the window and every open order is cancelled, arriving on the `account:orders_v3` channel as `inactiveReason: "HEARTBEAT_TIMEOUT"`. Refresh well inside the window (e.g. every 20s on a 60s timeout).

    To disarm, call the same endpoint with `timeoutSeconds: 0` — that clears an armed timer. There is no separate cancel route; V2's `POST /heartbeat/cancel` has no V3 successor. See [Heartbeat](/developers/heartbeat).
  </Step>

  <Step title="Realtime changes">
    The token route: point it at `GET /user/realtime-token-v3/api-key` (V2 used `GET /user/realtime-token/api-key`). Adjust channel names and a few payload keys.

    <Note>
      The addresses in the channel keys are your EOA / user address **checksummed** and **NOT** your proxy wallet address.
    </Note>

    | Purpose | V2 | V3 | Page |
    | - | - | - | - |
    | Market depth (per market) | `order_book:market_{marketHash}` | `orderbook_v3:{marketHash}` | [→](/api-reference/channel-orderbook-v3) |
    | Market depth (per event) | `order_book:event_{sportXeventId}` | `orderbook_v3_event:{eventId}` | [→](/api-reference/channel-orderbook-v3-event) |
    | Best odds | `best_odds:global` | `best_odds_v3:global` | [→](/api-reference/channel-best-odds-v3) |
    | My open orders | `active_orders:{address}` | `account:orders_v3_#{address}` | [→](/api-reference/channel-orders-v3) |
    | My bets | `account:#{address}`, `type: "consolidated_trades"` | `account:trades_v3_#{address}` | [→](/api-reference/channel-trades-v3) |
    | My fills | - | `account:fills_v3_#{address}` | [→](/api-reference/channel-fills-v3) |
    | Public tape | `recent_trades:global` | `recent_trades_v3:global` | [→](/api-reference/channel-recent-trades-v3) |
    | Parlay RFQs | `parlay_markets:global` | `parlay_markets:global` (unchanged) | [→](/api-reference/channel-parlay-requests) |

    `fixtures:*`, `markets:global` and `main_line:global` are unchanged.

    The order book channels are more than a rename — they no longer stream individual orders. See the next step, **Re-model the order book feed**, for the new anonymous, aggregated shape.

    <Note>
      Delivery is at-least-once, so a message can be replayed. De-duplicate on the client using the `messageId` in `ctx.tags` — drop any publication whose `messageId` you have already seen. See [Realtime reliability](/developers/realtime-reliability).

      The orderbook channels ([`orderbook_v3`](/api-reference/channel-orderbook-v3) and [`orderbook_v3_event`](/api-reference/channel-orderbook-v3-event)) are the exception: use their `version` field to order and de-duplicate updates within a market — apply a publication only when its `version` is strictly greater than the one you hold. See [Book versioning](/developers/book-versioning).
    </Note>

    Full reference: [Real-time data](/developers/realtime-overview).
  </Step>

  <Step title="Re-model the order book feed">
    In V2, [`order_book:market_{marketHash}`](/api-reference/channel-orderbook-v3) published a stream of **individual order rows/deltas** you merged into a local book. In V3, [`orderbook_v3:{marketHash}`](/api-reference/channel-orderbook-v3) (and [`orderbook_v3_event:{eventId}`](/api-reference/channel-orderbook-v3-event)) publish the **entire resting book on every update, aggregated by price into anonymous levels**.

    * **No per-order identity.** Levels carry only `percentageOdds` and aggregate `size`. Your own orders still arrive individually on [`account:orders_v3_#{address}`](/api-reference/channel-orders-v3).
    * **Replace, don't merge.** V2 required upsert/remove by `orderHash`; every V3 publication is the complete book for that market.
    * **Version-gate instead of `updateTime`.** Each publication carries a `version` (a single monotonic string). Apply a message only when its `version` is strictly greater than the one you hold for that market. See [Book versioning](/developers/book-versioning).
    * **Sides are explicit.** Levels are split into `outcomeOne` / `outcomeTwo` (always the maker frame), replacing V2's per-row `isMakerBettingOutcomeOne`. There is no `showTakerPerspective` on this channel (unlike [`GET /orderbook-v3/snapshot/market`](/api-reference/get-market-orderbook-snapshot)); invert yourself with `takerOdds = 1 − percentageOdds / 10^20`.

    Before/after payloads:

    <CodeGroup>
      ```json V2 (array of orders) theme={null}
      [
        {
          "orderHash": "0x…",
          "status": "ACTIVE",
          "maker": "0x…",
          "totalBetSize": "1000000",
          "percentageOdds": "40000000000000000000",
          "isMakerBettingOutcomeOne": true,
          "expiry": 2209006800,
          "apiExpiry": 1234567890,
          "salt": "0x…",
          "signature": "0x…",
          "updateTime": "…",
          "marketHash": "0x…"
        }
      ]
      ```

      ```json V3 (full aggregated book) theme={null}
      {
        "marketHash": "0xbf06c6379c922d8118612d1d7493b20f6df6929437fbf6022904327f9516a2eb",
        "version": "00100000000000010015000",
        "outcomeOne": [{ "percentageOdds": "40000000000000000000", "size": "1000000" }],
        "outcomeTwo": []
      }
      ```
    </CodeGroup>

    Subscribe first, then seed from REST inside the `subscribed` handler, and run both the snapshot and every publication through the same version rule so ordering never depends on timing. When the channel replays the book from history (`recovered: true`), skip the seed entirely. Full pattern: [Seeding and subscribing to the order book](/developers/odds#seeding-and-subscribing-realtime-to-the-order-book).

    <CodeGroup>
      ```javascript JavaScript theme={null}
      const API = "https://api.sx.bet";  // testnet: https://api.toronto.sx.bet
      const marketHash = "0xbf06c6379c922d8118612d1d7493b20f6df6929437fbf6022904327f9516a2eb";
      const books = new Map();           // marketHash -> { version, outcomeOne, outcomeTwo }

      // Both the REST seed and live messages go through this. Strictly-greater version wins.
      function applyBook(book) {
        const held = books.get(book.marketHash);
        if (held && book.version <= held.version) return;   // stale — ignore
        books.set(book.marketHash, book);                   // replace — never merge by orderHash
      }

      const sub = client.newSubscription(`orderbook_v3:${marketHash}`, { recoverable: true });

      // Every publication runs through the version rule; stale ones are discarded.
      sub.on("publication", (ctx) => applyBook(ctx.data));

      sub.on("subscribed", async (ctx) => {
        // recovered: true means the book was replayed from history — no REST seed needed.
        if (ctx.recovered) return;
        const { data: seed } = await fetch(
          `${API}/orderbook-v3/snapshot/market?marketHash=${marketHash}`,
          { headers: { "x-sx-api-key": process.env.SX_API_KEY } },
        ).then((r) => r.json());
        // Same version rule, so a newer live update that arrived during the fetch is not clobbered.
        applyBook(seed);
      });

      sub.subscribe();
      ```

      ```python Python theme={null}
      import os

      API = "https://api.sx.bet"  # testnet: https://api.toronto.sx.bet
      market_hash = "0xbf06c6379c922d8118612d1d7493b20f6df6929437fbf6022904327f9516a2eb"
      books = {}  # market_hash -> {version, outcomeOne, outcomeTwo}

      # Both the REST seed and live messages go through this. Strictly-greater version wins.
      def apply_book(book) -> None:
          held = books.get(book["marketHash"])
          if held and book["version"] <= held["version"]:   # stale — ignore
              return
          books[book["marketHash"]] = book                   # replace — never merge by orderHash

      async def on_publication(ctx) -> None:
          apply_book(ctx.data)

      async def on_subscribed(ctx) -> None:
          # recovered=True means the book was replayed from history — no REST seed needed.
          if ctx.recovered:
              return
          seed = requests.get(f"{API}/orderbook-v3/snapshot/market",
                              params={"marketHash": market_hash},
                              headers={"x-sx-api-key": os.environ["SX_API_KEY"]}).json()["data"]
          apply_book(seed)  # same version rule, so a newer live update isn't clobbered

      sub = client.new_subscription(
          f"orderbook_v3:{market_hash}",
          SubscriptionEventHandler(on_publication=on_publication, on_subscribed=on_subscribed),
      )
      await sub.subscribe()
      ```
    </CodeGroup>

    Full reference: [Order book updates](/api-reference/channel-orderbook-v3) and [Event order book updates](/api-reference/channel-orderbook-v3-event).
  </Step>

  <Step title="Parlay RFQ market makers">
    Nothing changes about how you hear requests: subscribe to `parlay_markets:global` exactly as in V2.

    What changes is how you respond. Price the request, then post your quote to [`POST /orders-v3`](/api-reference/post-orders-v3) with `timeInForce: "GTC"` against the parlay `marketHash`, signed the new way (see the posting step above). Every order needs a real `expiry` (a future unix epoch timestamp in seconds); for a parlay it must be no later than the parlay market's own expiry. Full walkthrough: [Market making parlays](/developers/market-making-parlays).
  </Step>

  <Step title="Adjust query endpoints">
    We have renamed the data types to more intuitive names, and adjusted the endpoints. These endpoints all require an API-key and you can only see your own activity.

    | You want | V3 endpoint | Grain | Old |
    | - | - | - | - |
    | One row per bet | [`GET /trades-v3`](/api-reference/get-trades-v3) | bet | consolidated\_trades |
    | One row per matched leg | [`GET /fills-v3`](/api-reference/get-fills-v3) | fill | trades |
    | Net exposure per market | [`GET /positions-v3`](/api-reference/get-positions-v3) | position | consolidated\_trades (grouped) |
    | Your resting orders | [`GET /orders-v3`](/api-reference/get-orders-v3) | active orders only | - |

    See [Grain types](/developers/which-grain).

    Three changes apply across all four:

    * Rsults are bound to your API key, so V2's `bettor=...` and `maker=true` query params are gone.
    * Pagination is now uniform: `perPage` + `nextKey` cursor everywhere.
    * V2's `settled=true/false` becomes the `status` filter (`MATCHED` / `LOCKED` / `SETTLED` / `FAILED`).

    Field-level diffs are on each reference page.

    **Identifier changes.** If your V2 client keys off `orderHash` or `fillHash`, remap it.

    | Concept | V2 | V3 |
    | - | - | - |
    | Order | `orderHash` | `orderId` |
    | Bet | consolidated trade | `tradeId` |
    | Fill / match | `fillHash` | `id` on fills object |
  </Step>

  <Step title="Read refunds from rows, not a refund endpoint">
    There is no refund endpoint or refund channel in V3. Capital-efficiency refunds are republished as fields **inline** on the trade and fill rows you already read — not as a separate resource or event. (If you never consumed CE refunds in V2, there is nothing to do here.)

    | V2 | V3 |
    | - | - |
    | `GET /trades/portfolio/refunds` | `ceRefundAmount` / `ceRefundFeeAmount` on [`GET /fills-v3`](/api-reference/get-fills-v3) and [`GET /trades-v3`](/api-reference/get-trades-v3) |
    | `ce_refunds:{address}` channel | Read the fields off the `account:fills_v3_#{address}` / `account:trades_v3_#{address}` publications |
    | `marketHasRefunds` flag | No successor — test `ceRefundAmount > 0` |
  </Step>

  <Step title="Understand the new order and trade state machines">
    **Order status** — `PENDING → ACTIVE → INACTIVE` (IOC/FOK orders skip `ACTIVE` and go straight to `INACTIVE`):

    | Status | Meaning |
    | - | - |
    | `PENDING` | Accepted and published to the matching engine. Not live, not yet matchable. |
    | `ACTIVE` | Resting on the book, in whole or in part. |
    | `INACTIVE` | No longer resting. `inactiveReason` says why. |

    `FILLED` is an `inactiveReason`, not a status: a fully filled order is `status: "INACTIVE"` with `inactiveReason: "FILLED"`. See [Order lifecycle](/developers/order-lifecycle).

    **Bet status** — `MATCHED → LOCKED → SETTLED`, or `MATCHED → FAILED`:

    | Status | Meaning |
    | - | - |
    | `MATCHED` | The matching engine matched you. The on-chain lock is not confirmed yet. |
    | `LOCKED` | Funds are escrowed on chain. The bet is real and irreversible. |
    | `SETTLED` | The fixture is graded and the outcome is recorded in `settlement`. |
    | `FAILED` | The on-chain lock did not succeed. No funds moved. |

    See [Bet lifecycle](/developers/bet-lifecycle).
  </Step>
</Steps>

***

## Endpoint changes

| Action | V2 | V3 | Page |
| - | - | - | - |
| Exchange metadata | `GET /metadata` | `GET /metadata/obv3` | [→](/api-reference/get-metadata-obv3) |
| Deploy proxy wallet | — | `POST /user/deploy-proxy` | [→](/api-reference/post-user-deploy-proxy) |
| Look up proxy address | — | `GET /user/proxy` | [→](/api-reference/get-user-proxy) |
| Fund proxy | — | `POST /user/transfer-to-proxy` | [→](/developers/funding) |
| Balances | — | `GET /user/balance-v3` | [→](/api-reference/get-user-balance-v3) |
| Fees | `GET /metadata` (`oracleFees`, global) | `GET /user/fees-v3` (per account) | [→](/api-reference/get-user-fees-v3) |
| Withdraw | — | `POST /user/multisig/withdraw/prepare` | [→](/api-reference/post-user-multisig-withdraw-prepare) |
| Create order | `POST /orders/new` | `POST /orders-v3` | [→](/api-reference/post-orders-v3) |
| Take liquidity | `POST /orders/fill/v2` | `POST /orders-v3` with `IOC` / `FOK` | [→](/developers/taking-liquidity) |
| Enable betting | `POST /orders/approve` | — (fund the proxy) | [→](/developers/funding) |
| List my open orders | `GET /orders?maker=…` | `GET /orders-v3` | [→](/api-reference/get-orders-v3) |
| Get one order by id | `GET /orders?orderHashes=…` | `GET /orders-v3/{orderId}` | [→](/api-reference/get-order-v3) |
| Market order book | `GET /orders?marketHashes=…` | `GET /orderbook-v3/snapshot/market?marketHash=…` | [→](/api-reference/get-market-orderbook-snapshot) |
| Best odds | `GET /orders/odds/best` | `GET /orders-v3/odds/best` | [→](/api-reference/get-best-odds-v3) |
| Cancel by id | `POST /orders/cancel/v2` | `DELETE /orders-v3` | [→](/api-reference/delete-orders-v3) |
| Cancel by market | — | `DELETE /orders-v3/market` | [→](/api-reference/delete-orders-v3-market) |
| Cancel by event | `POST /orders/cancel/event` | `DELETE /orders-v3/event` | [→](/api-reference/delete-orders-v3-event) |
| Cancel all | `POST /orders/cancel/all` | `DELETE /orders-v3/all` | [→](/api-reference/delete-orders-v3-all) |
| Register / cancel heartbeat | `POST /heartbeat` | `POST /heartbeat/v3` (cancel = `timeoutSeconds: 0`) | [→](/developers/heartbeat) |
| Fill-grain history | `GET /trades` | `GET /fills-v3` | [→](/api-reference/get-fills-v3) |
| Bet-grain history | `GET /trades/consolidated` | `GET /trades-v3` | [→](/api-reference/get-trades-v3) |
| Get one bet by id | — | `GET /trades-v3/{tradeId}` | [→](/api-reference/get-trade-v3) |

## Rate limits have changed

V2 limited endpoint *groups* (all `POST /orders/*` shared 5,500/min; all `GET /trades/*` shared 200/min). V3 limits are **per-endpoint**. Relevant changes:

| V3 endpoint | Limit | Watch out |
| - | - | - |
| `POST /orders-v3` | 5,500 / 60s | Same as v2 |
| `GET /orderbook-v3/snapshot/market` | 27,500 / 300s | API key. Seed once, then use the `orderbook_v3` channels. |
| `GET /orders-v3` | 1,200 / 60s | Far below V2's 5,500/min GET orders group — move book-polling to the `orderbook_v3` channels. |
| `DELETE /orders-v3` | 10,000 / 60s | |
| `DELETE /orders-v3/market` | 3,000 / 60s | No v2 equivalent — scope a cancel to one market. |
| `DELETE /orders-v3/event` | 1,000 / 60s | |
| `DELETE /orders-v3/all` | 60 / 60s | |
| `POST /heartbeat/v3` | 600 / 60s | |
| `GET /fills-v3` · `GET /positions-v3` | 300 / 60s each | |

Full list, response headers, and the global bucket: [Rate limits](/developers/rate-limits).

## Migration checklist

Run it on testnet ([api.toronto.sx.bet](https://api.toronto.sx.bet)) first.

* [ ] Create an API key. Apply the key on every privileged endpoint.
* [ ] Deploy and fund the proxy.
* [ ] Fetch `GET /metadata/obv3`; wire `domain`, `activeAsset.baseToken`, `oddsLadderStepSize` and `limits.*` into config; delete the `executorAddress` lookup.
* [ ] Re-implement order signing.
* [ ] Post a minimum-size `GTC` with `waitForOutcome: true`; then take it with an `IOC` from a second account.
* [ ] Port cancels to the three `DELETE`s; delete the V2 cancel-signing code; test "by id", "by event", and "all".
* [ ] Move realtime: new token route, channel renames per the "Realtime changes" step, `messageId` dedup, and `version`-gating on the orderbook channels.
* [ ] Point accounting at `/trades-v3` / `/fills-v3` / `/positions-v3`; remap `orderHash` / `fillHash` ids; switch pagination to `perPage` + `nextKey`.
* [ ] Arm `POST /heartbeat/v3` and let it lapse once on testnet to watch `HEARTBEAT_TIMEOUT` cancel your orders.

<CardGroup cols={2}>
  <Card title="Improvements" icon="sparkles" href="/developers/new-in-v3">
    What changed in V3.
  </Card>

  <Card title="Quickstart" icon="rocket" href="/developers/quickstart">
    The full V3 flow, metadata to placed bet.
  </Card>
</CardGroup>


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.