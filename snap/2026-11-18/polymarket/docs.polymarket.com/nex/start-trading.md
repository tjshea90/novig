> ## Documentation Index
> Fetch the complete documentation index at: https://docs.polymarket.com/llms.txt
> Use this file to discover all available pages before exploring further.

# Start Trading

> Connect your trading stack and place a sandbox order.

Complete [connection setup](https://docs.polymarket.com/nex/connect.md) first. We'll arrange test pUSD,
credentials and approvals during onboarding.

## Configure Your Integration

> [tab] API
>
> NEX is a drop-in replacement for the CLOB API.
>
> **1. Switch the Base URL**
>
> | Environment | Base URL |
> | - | - |
> | Production | `https://clob.polymarket.com` |
> | NEX Sandbox | `https://nex.preprod.defi.euw2.polymarket.sh` |
>
> Keep your existing routes, payloads and response handling.
>
> **2. Use Sandbox Signing and Credentials**
>
> Sign for Polygon Amoy (`80002`) using the Amoy exchange contracts
> configured during onboarding. Authenticate with your sandbox trading
> credentials. Use an EOA or Deposit Wallet; Safe and Proxy Wallets are
> unavailable. EOAs need Amoy POL for their own approval transactions.
>
> **3. Check the API**
>
> ```bash theme={null}
> curl -i https://nex.preprod.defi.euw2.polymarket.sh/health
> ```
>
> Expect HTTP `200`.

> [tab] TypeScript
>
> Use the latest stable release of every Polymarket SDK in your stack, including
> separate trading and relayer packages. Set `NEX_CLOB_URL` to
> `https://nex.preprod.defi.euw2.polymarket.sh`. You'll also need an Amoy RPC URL
> and your sandbox signer's private key.
>
> This example uses your signer's address (EOA) as the trading account on Amoy.
>
> > [!NOTE]
> >
> > The sandbox supports EOAs and Deposit Wallets; Safe and Proxy Wallets are
> > unavailable. EOAs need Amoy POL for their own approval transactions. Use your
> > Deposit Wallet address for funding if you trade through one.
>
> **1. Update the SDK**
>
> Requires Node.js 24 or later.
>
> ```bash theme={null}
> npm install @polymarket/client@latest viem
> ```
>
> **2. Create the Client**
>
> Set `GAMMA_CLIENT_ID` and `GAMMA_CLIENT_SECRET` to the Gamma credentials
> sent after approval.
>
> ```typescript theme={null}
> import { createSecureClient, forkEnvironmentConfig } from "@polymarket/client";
> import { privateKey } from "@polymarket/client/viem";
> import { http } from "viem";
> import { polygonAmoy } from "viem/chains";
>
> const rpcUrl = process.env.AMOY_RPC_URL!;
> const signer = privateKey(process.env.POLYMARKET_PRIVATE_KEY, {
>   chain: polygonAmoy,
>   transport: http(rpcUrl),
> });
>
> const amoy = forkEnvironmentConfig({
>   name: "amoy",
>   chainId: polygonAmoy.id,
>   rpc: rpcUrl,
>   clob: { rest: process.env.NEX_CLOB_URL! },
>   gamma: {
>     rest: "https://gamma-api-preprod.polymarket.dev",
>     headers: {
>       "x-client-id": process.env.GAMMA_CLIENT_ID!,
>       "x-client-secret": process.env.GAMMA_CLIENT_SECRET!,
>     },
>   },
> });
>
> const client = await createSecureClient({
>   signer,
>   wallet: await signer.getAddress(),
>   environment: amoy,
> });
> ```
>
> See the [TypeScript SDK guide](https://docs.polymarket.com/getting-started/typescript.md) for the rest of
> the client API.

> [tab] Python
>
> Use the latest stable release of every Polymarket SDK in your stack, including
> separate trading and relayer packages. Set `NEX_CLOB_URL` to
> `https://nex.preprod.defi.euw2.polymarket.sh`. You'll also need an Amoy RPC URL
> and your sandbox signer's private key.
>
> This example uses your signer's address (EOA) as the trading account on Amoy.
>
> > [!NOTE]
> >
> > The sandbox supports EOAs and Deposit Wallets; Safe and Proxy Wallets are
> > unavailable. EOAs need Amoy POL for their own approval transactions. Use your
> > Deposit Wallet address for funding if you trade through one.
>
> **1. Update the SDK**
>
> Requires Python 3.11 or later.
>
> ```bash theme={null}
> python -m pip install --upgrade polymarket-client eth-account httpx
> ```
>
> **2. Create the Client**
>
> Amoy currently uses the SDK team's internal environment helper. Run
> this setup inside your existing async application.
>
> ```python theme={null}
> import os
> from dataclasses import replace
>
> from eth_account import Account
> from polymarket import AsyncSecureClient
> from polymarket._internal.environment import PRODUCTION_CONFIG, create_environment
>
> private_key = os.environ["POLYMARKET_PRIVATE_KEY"]
> eoa_address = Account.from_key(private_key).address
>
> amoy = create_environment(
>     name="amoy",
>     config=replace(
>         PRODUCTION_CONFIG,
>         chain_id=80002,
>         rpc_url=os.environ["AMOY_RPC_URL"],
>         clob_url=os.environ["NEX_CLOB_URL"].rstrip("/"),
>         gamma_url="https://gamma-api-preprod.polymarket.dev",
>         exchange_v3="0xe3333700cA9d93003F00f0F71f8515005F6c00Aa",
>         position_manager="0x006F54F7f9A22e0000CC2AB60031000000ae9fEF",
>     ),
> )
>
> client = await AsyncSecureClient.create(
>     private_key=private_key,
>     wallet=eoa_address,
>     environment=amoy,
> )
> ```
>
> For synchronous code, import `SecureClient` instead and call
> `SecureClient.create` without `await`.
>
> See the [Python SDK guide](https://docs.polymarket.com/getting-started/python.md) for the rest of the
> client API.

> [accordion] Legacy Clients
>
> Set `NEX_CLOB_URL` to the NEX base URL above. These examples use an EOA;
> complete your existing authentication flow before trading. For a Deposit
> Wallet, retain its signing configuration.
>
> > [tab] TypeScript
> >
> > `@polymarket/clob-client-v2` is deprecated. Prefer `@polymarket/client`
> > for new integrations.
> >
> > ```bash theme={null}
> > npm install @polymarket/clob-client-v2@latest
> > ```
> >
> > Use your existing signer, configured for Amoy:
> >
> > ```typescript theme={null}
> > import { Chain, ClobClient } from "@polymarket/clob-client-v2";
> >
> > const client = new ClobClient({
> >   host: process.env.NEX_CLOB_URL!,
> >   chain: Chain.AMOY,
> >   signer,
> > });
> > ```
>
> > [tab] Python
> >
> > `py-clob-client-v2` is deprecated. Prefer `polymarket-client` for new
> > integrations.
> >
> > ```bash theme={null}
> > python -m pip install --upgrade py-clob-client-v2
> > ```
> >
> > ```python theme={null}
> > import os
> > from py_clob_client_v2 import ClobClient
> >
> > client = ClobClient(
> >     host=os.environ["NEX_CLOB_URL"],
> >     chain_id=80002,
> >     key=os.environ["POLYMARKET_PRIVATE_KEY"],
> > )
> > ```
>
> > [tab] Rust
> >
> > The Rust CLOB SDK remains supported. The unified Rust SDK is in progress.
> >
> > ```bash theme={null}
> > cargo add polymarket_client_sdk_v2 --features clob
> > ```
> >
> > ```rust theme={null}
> > use polymarket_client_sdk_v2::clob::{Client, Config};
> >
> > let host = std::env::var("NEX_CLOB_URL")?;
> > let client = Client::new(&host, Config::default())?;
> > ```
> >
> > This creates an unauthenticated client. Configure your trading signer
> > for Amoy (`80002`) and continue with your existing authentication flow.
>
> See the [SDK migration guide](https://docs.polymarket.com/migrate/clob-sdk-to-unified-sdk.md) to move an
> existing TypeScript or Python integration to the unified SDK.

## Find a Market

Choose an outcome from a sandbox market, then read its order book.

> [tab] API
>
> Use the Gamma credentials we sent privately to list sandbox markets:
>
> ```bash theme={null}
> curl --fail-with-body -G \
>   "https://gamma-api-preprod.polymarket.dev/markets" \
>   --header "x-client-id: $GAMMA_CLIENT_ID" \
>   --header "x-client-secret: $GAMMA_CLIENT_SECRET" \
>   --data-urlencode "closed=false" \
>   --data-urlencode "limit=5"
> ```
>
> Choose an outcome from the response. Use its position ID for Polymarket V2 or
> its token ID for CTF. Set `NEX_ASSET_ID` to that identifier, then read its book:
>
> ```bash theme={null}
> curl --fail-with-body -G \
>   "https://nex.preprod.defi.euw2.polymarket.sh/book" \
>   --data-urlencode "token_id=$NEX_ASSET_ID"
> ```
>
> See [List Markets](https://docs.polymarket.com/api-reference/markets/list-markets.md) and
> [Get Order Book](https://docs.polymarket.com/api-reference/market-data/get-order-book.md) for response schemas.

> [tab] TypeScript
>
> Call `listMarkets()` to find Polymarket Protocol V2 markets with the
> Gamma credentials configured above.
>
> ```typescript theme={null}
> import { ProtocolVersion } from "@polymarket/client";
>
> const pages = client.listMarkets({
>   closed: false,
>   version: ProtocolVersion.V2,
>   pageSize: 5,
> });
> const page = await pages.firstPage();
>
> for (const market of page.items) {
>   console.log(market.id, market.question, market.version);
> }
> ```
>
> Select YES in a market accepting orders, then call `fetchOrderBook()` with
> its position ID:
>
> ```typescript theme={null}
> const market = page.items.find((market) => market.state.acceptingOrders);
> if (!market) throw new Error("None of these markets is accepting orders.");
>
> const assetId = market.outcomes.yes.positionId;
> if (!assetId) throw new Error("No trading identifier for this outcome.");
>
> const book = await client.fetchOrderBook({ assetId });
> console.log(book.tickSize, book.minOrderSize);
> ```

> [tab] Python
>
> The Python SDK cannot yet attach Gamma's sandbox headers. Fetch
> Polymarket Protocol V2 market data with HTTPX using the Gamma credentials
> sent after approval, then
> parse the binary markets with the SDK's `Market` model:
>
> ```python theme={null}
> import json
>
> import httpx
> from polymarket import Market
>
> async with httpx.AsyncClient() as gamma:
>     response = await gamma.get(
>         "https://gamma-api-preprod.polymarket.dev/markets/keyset",
>         headers={
>             "x-client-id": os.environ["GAMMA_CLIENT_ID"],
>             "x-client-secret": os.environ["GAMMA_CLIENT_SECRET"],
>         },
>         params=[
>             ("closed", "false"),
>             ("version", "v2"),
>             ("limit", "5"),
>         ],
>     )
>     response.raise_for_status()
>     markets = []
>     for item in response.json()["markets"]:
>         outcomes = item.get("outcomes") or []
>         if isinstance(outcomes, str):
>             outcomes = json.loads(outcomes)
>         if len(outcomes) == 2:
>             markets.append(Market.parse_response(item))
>
> for market in markets:
>     print(market.id, market.question, market.version)
> ```
>
> Select YES in a market accepting orders and read its book with
> `get_order_book()`:
>
> ```python theme={null}
> market = next((market for market in markets if market.state.accepting_orders), None)
> if market is None:
>     raise ValueError("None of these markets is accepting orders.")
>
> asset_id = market.outcomes.yes.position_id
> if asset_id is None:
>     raise ValueError("No trading identifier for this outcome.")
>
> book = await client.get_order_book(asset_id=asset_id)
> print(book.tick_size, book.min_order_size)
> ```

## Place and Track an Order

Place a small limit order, read it back, then cancel the remaining quantity.
A resting order can fill before you cancel it.

> [tab] API
>
> Use your sandbox credentials and the Amoy signing configuration validated
> during onboarding.
>
> **1. Place an Order**
>
> Submit a small limit order. Follow the market's tick size and minimum
> order size. See [Post a New Order](https://docs.polymarket.com/api-reference/trade/post-a-new-order.md).
>
> **2. Read It Back**
>
> [Fetch the order by ID](https://docs.polymarket.com/api-reference/trade/get-single-order-by-id.md).
>
> **3. Cancel the Remaining Quantity**
>
> See [Manage Orders](https://docs.polymarket.com/trading/manage-orders.md) for cancellation requests.

> [tab] TypeScript
>
> Use the authenticated `client`, `assetId` and `book` from the steps above.
> The example uses the book's minimum size and a buy price of one tick.
>
> **1. Place an Order**
>
> ```typescript theme={null}
> import { OrderSide } from "@polymarket/client";
>
> const result = await client.placeLimitOrder({
>   assetId,
>   side: OrderSide.BUY,
>   price: book.tickSize,
>   size: book.minOrderSize,
>   postOnly: true,
> });
>
> if (!result.ok) throw new Error(result.message);
> console.log(result.orderId);
> ```
>
> **2. Read It Back**
>
> ```typescript theme={null}
> const order = await client.fetchOrder({ orderId: result.orderId });
> console.log(order.status);
> ```
>
> **3. Cancel the Remaining Quantity**
>
> ```typescript theme={null}
> const cancellation = await client.cancelOrder({ orderId: result.orderId });
> console.log(cancellation.canceled, cancellation.notCanceled);
> ```

> [tab] Python
>
> Use the authenticated `client`, `asset_id` and `book` from the steps above.
> The example uses the book's minimum size and a buy price of one tick.
>
> **1. Place an Order**
>
> ```python theme={null}
> result = await client.place_limit_order(
>     asset_id=asset_id,
>     side="BUY",
>     price=book.tick_size,
>     size=book.min_order_size,
>     post_only=True,
> )
>
> if not result.ok:
>     raise RuntimeError(result.message)
> print(result.order_id)
> ```
>
> **2. Read It Back**
>
> ```python theme={null}
> order = await client.get_order(order_id=result.order_id)
> print(order.status)
> ```
>
> **3. Cancel the Remaining Quantity**
>
> ```python theme={null}
> cancellation = await client.cancel_order(order_id=result.order_id)
> print(cancellation.canceled, cancellation.not_canceled)
> ```

Order reads return open orders. If it fills before you read it back, follow
updates on the [user feed](https://docs.polymarket.com/nex/websocket-feeds.md). A match happens before
settlement; follow settlement before checking your position.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.