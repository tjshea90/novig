> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Market making parlays

> How to listen for parlay requests, price them, and submit orders as a market maker.

## Overview

This guide covers the full flow for market making parlays on SX Bet:

1. Connect to the WebSocket.
2. Receive parlay requests.
3. Analyse the legs.
4. Calculate odds.
5. Post orders.

All of it has to happen within the 3-second RFQ window.

## Step 1: Connect to the parlay channel

Subscribe to the [`parlay_markets:global` channel](/api-reference/channel-parlay-requests) to receive parlay requests in real time. You need a token from the [`GET /user/realtime-token-v3/api-key`](/developers/realtime-initialization#connect) endpoint.

<CodeGroup>
  ```javascript JavaScript theme={null}
  import { Centrifuge } from "centrifuge";

  const API_URL = "https://api.sx.bet"; // Mainnet — use https://api.toronto.sx.bet for testnet
  const WS_URL = "wss://realtime.sx.bet/connection/websocket";

  async function fetchToken() {
    const res = await fetch(`${API_URL}/user/realtime-token-v3/api-key`, {
      headers: { "x-sx-api-key": process.env.SX_API_KEY },
    });
    if (!res.ok) throw new Error(`Token endpoint returned ${res.status}`);
    const { token } = await res.json();
    return token;
  }

  const client = new Centrifuge(WS_URL, {
    getToken: fetchToken,
  });

  const sub = client.newSubscription("parlay_markets:global");

  sub.on("publication", (ctx) => {
    const parlayRequest = ctx.data;
    handleParlayRequest(parlayRequest);
  });

  sub.subscribe();
  client.connect();
  ```

  ```python Python theme={null}
  import asyncio
  import os
  import aiohttp
  from centrifuge import Client, PublicationContext, SubscriptionEventHandler

  API_URL = "https://api.sx.bet"  # Mainnet — use https://api.toronto.sx.bet for testnet
  WS_URL = "wss://realtime.sx.bet/connection/websocket"

  async def fetch_token(ctx=None):
      async with aiohttp.ClientSession() as session:
          async with session.get(
              f"{API_URL}/user/realtime-token-v3/api-key",
              headers={"x-sx-api-key": os.environ["SX_API_KEY"]},
          ) as resp:
              data = await resp.json()
              return data["token"]

  class ParlayHandler(SubscriptionEventHandler):
      async def on_publication(self, ctx: PublicationContext) -> None:
          handle_parlay_request(ctx.data)

  async def main():
      client = Client(WS_URL, get_token=fetch_token)
      sub = client.new_subscription("parlay_markets:global", ParlayHandler())

      await client.connect()
      await sub.subscribe()
      await asyncio.Future()  # run forever

  asyncio.run(main())
  ```
</CodeGroup>

## Step 2: Parse the parlay request

Each incoming message contains the parlay's `marketHash`, the requested token and size, and the legs:

```json theme={null}
{
  "marketHash": "0x38cceead7bda65c18574a34994ebd8af154725d08aa735dcbf26247a7dcc67bd",
  "requestorId": "08e489e45313812cf236b43ab68c6236e3e8d63375a07d6bafdec1ecbed85c1b",
  "requestSize": "100000000",
  "legs": [
    {
      "marketHash": "0x0d64c52e8781acdada86920a2d1e5acd6f29dcfe285cf9cae367b671dff05f7d",
      "bettingOutcomeOne": true
    },
    {
      "marketHash": "0xe609a49d083cd41214a0db276c1ba323c4a947eefd2e4260386fec7b5d258188",
      "bettingOutcomeOne": false
    }
  ]
}
```

| Field | Description |
| - | - |
| `marketHash` | The parlay market to post your order against — the **parent** |
| `requestorId` | An **HMAC of the requesting address**, not the address itself |
| `requestSize` | Requested size in base units (see [unit conversions](/developers/unit-conversions)) |
| `legs` | Array of individual markets and the outcomes the bettor selected |

## Step 3: Look up each leg

Query each leg's market via [`GET /markets/find`](/api-reference/get-markets-find) to see what the bettor selected.

<CodeGroup>
  ```javascript JavaScript theme={null}
  async function handleParlayRequest(parlayRequest) {
    const { marketHash: parlayMarketHash, requestSize, legs } = parlayRequest;

    // Fetch market data for each leg
    const legData = await Promise.all(
      legs.map(async (leg) => {
        const response = await fetch(
          `${BASE_URL}/markets/find?${new URLSearchParams({ marketHash: leg.marketHash })}`
        ).then((r) => r.json());
        const market = response.data;

        // Fetch best odds for this leg's market
        const ordersResponse = await fetch(
          `${BASE_URL}/orders-v3/odds/best?${new URLSearchParams({ marketHashes: leg.marketHash })}`,
          { headers: { "x-sx-api-key": process.env.SX_API_KEY } }
        ).then((r) => r.json());
        // bestOdds is an ARRAY, one row per requested hash, in request order.
        // Each row is {marketHash, outcomeOne, outcomeTwo}; a side with no
        // liquidity is a bare null, NOT an object with null fields.
        const row = ordersResponse.data.bestOdds[0];
        const side = leg.bettingOutcomeOne ? row?.outcomeOne : row?.outcomeTwo;

        return {
          ...leg,
          market,
          // null when that side of the leg is empty — handle it, do not assume a price
          reference: side ? { odds: side.percentageOdds, size: side.size } : null,
        };
      })
    );

    console.log("Parlay request received:");
    legData.forEach((leg, i) => {
      const m = leg.market;
      const side = leg.bettingOutcomeOne ? m.outcomeOneName : m.outcomeTwoName;
      console.log(`  Leg ${i + 1}: ${m.teamOneName} vs ${m.teamTwoName} — ${side}`);
    });

    // Calculate your price and post an order
    const odds = calculateParlayOdds(legData);
    if (odds) {
      await postParlayOrder(parlayMarketHash, odds);
    }
  }
  ```

  ```python Python theme={null}
  def handle_parlay_request(parlay_request):
      parlay_market_hash = parlay_request["marketHash"]
      request_size = parlay_request["requestSize"]
      legs = parlay_request["legs"]

      # Fetch market data for each leg
      leg_data = []
      for leg in legs:
          market_resp = requests.get(
              f"{BASE_URL}/markets/find",
              params={"marketHash": leg["marketHash"]}
          )
          market = market_resp.json()["data"]

          odds_resp = requests.get(
              f"{BASE_URL}/orders-v3/odds/best",
              params={"marketHashes": leg["marketHash"]},
              headers={"x-sx-api-key": os.environ["SX_API_KEY"]},
          )
          row = odds_resp.json()["data"]["bestOdds"][0]
          # A side with no liquidity is a bare None, not a dict of Nones.
          side = row.get("outcomeOne") if leg["bettingOutcomeOne"] else row.get("outcomeTwo")
          reference = {"odds": side["percentageOdds"], "size": side["size"]} if side else None

          leg_data.append({**leg, "market": market, "reference": reference})

      for i, leg in enumerate(leg_data):
          m = leg["market"]
          side = m["outcomeOneName"] if leg["bettingOutcomeOne"] else m["outcomeTwoName"]
          print(f"  Leg {i + 1}: {m['teamOneName']} vs {m['teamTwoName']} — {side}")

      # Calculate your price and post an order
      odds = calculate_parlay_odds(leg_data)
      if odds:
          post_parlay_order(parlay_market_hash, odds)
  ```
</CodeGroup>

## Step 4: Calculate your odds

The simplest approach is to multiply the implied probabilities of each leg. Your pricing strategy is up to you. This is a basic example:

<CodeGroup>
  ```javascript JavaScript theme={null}
  const ODDS_PRECISION = 10n ** 20n;

  function calculateParlayOdds(legData) {
    // Simple approach: multiply implied probabilities of each leg
    let combinedProbability = 1.0;

    for (const leg of legData) {
      // Use your own fair odds estimate per leg
      // This example uses a fixed estimate — replace with your model
      const legProbability = estimateLegProbability(leg);
      combinedProbability *= legProbability;
    }

    // Add your margin
    const margin = 0.03; // 3% edge
    const makerProbability = combinedProbability + margin;

    // Convert to SX protocol format (maker's percentageOdds)
    // percentageOdds = maker's implied probability * 10^20
    const percentageOdds = BigInt(Math.round(makerProbability * 1e20));

    return percentageOdds.toString();
  }

  function estimateLegProbability(leg) {
    // Replace with your actual pricing model
    // This is just a placeholder
    return 0.5;
  }
  ```

  ```python Python theme={null}
  ODDS_PRECISION = 10 ** 20

  def calculate_parlay_odds(leg_data):
      # Simple approach: multiply implied probabilities of each leg
      combined_probability = 1.0

      for leg in leg_data:
          leg_probability = estimate_leg_probability(leg)
          combined_probability *= leg_probability

      # Add your margin
      margin = 0.03  # 3% edge
      maker_probability = combined_probability + margin

      # Convert to SX protocol format
      percentage_odds = str(int(maker_probability * ODDS_PRECISION))
      return percentage_odds

  def estimate_leg_probability(leg):
      # Replace with your actual pricing model
      return 0.5
  ```
</CodeGroup>

`percentageOdds` is **your own** implied probability, scaled by 10^20 — the price *you* are paying,
for the side named by `isMakerBettingOutcomeOne`.

## Step 5: Post your order

Posting a parlay order is posting an order. There is nothing parlay-specific about the payload or
the signature. **The order's expiry must be no later than the parlay market's expiry.**

The signing code lives on the shared pages:

<CardGroup cols={2}>
  <Card title="Create orders" icon="paper-plane" href="/api-reference/post-orders-v3" />

  <Card title="EIP-712 order signing" icon="signature" href="/api-reference/eip712-order-signing">
    The struct, the domain, and signing the digest.
  </Card>
</CardGroup>

Keep your order's `expiry` — a unix epoch timestamp in seconds — inside the parlay's own expiry.

## Step 6: Manage your orders

Cancelling a parlay order is cancelling an order. Use [`DELETE /orders-v3`](/api-reference/delete-orders-v3) with the ids you want cancelled.

<Card title="Cancelling orders" icon="ban" href="/developers/cancelling-orders">
  Picking a route, confirming before you exit, and why a `200` is not enough.
</Card>

## Related

<CardGroup cols={2}>
  <Card title="Parlays" icon="link" href="/developers/parlays">
    The RFQ flow.
  </Card>

  <Card title="Create orders" icon="paper-plane" href="/api-reference/post-orders-v3">
    Posting new orders
  </Card>

  <Card title="EIP-712 order signing" icon="signature" href="/api-reference/eip712-order-signing">
    The eight-field struct every order shares.
  </Card>

  <Card title="Parlay requests channel" icon="bolt" href="/api-reference/channel-parlay-requests">
    The message you subscribe to.
  </Card>
</CardGroup>


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.