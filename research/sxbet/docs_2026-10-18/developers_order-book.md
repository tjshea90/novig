> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# The order book

> How the order book is structured and working code to fetch it and update it.

A market has one order book with two sides, each a list of **price levels** — an implied price and the
total stake resting at it:

```json theme={null}
{
  "outcomeOne": [
    { "percentageOdds": "52000000000000000000", "size": "2000000" },
    { "percentageOdds": "51500000000000000000", "size": "1000000" },
    { "percentageOdds": "50000000000000000000", "size": "3000000" }
  ],
  "outcomeTwo": [
    { "percentageOdds": "46750000000000000000", "size": "1000000" },
    { "percentageOdds": "46000000000000000000", "size": "3000000" },
    { "percentageOdds": "42000000000000000000", "size": "1000000" }
  ]
}
```

* **`percentageOdds` is implied probability scaled by 10<sup>20</sup>** — `"52000000000000000000"` is 52.000%.
* **`size` is stake in base units** — USDC has 6 decimals, so `"2000000"` is 2 USDC. It is **the resting
  party's** stake, not the amount you can bet against it.

Note that the side here is the side the resting order is betting. So if you were to match, you would be betting the opposite.

**To bet an outcome, you consume the orders resting on the *other* outcome.** Someone resting on outcome
one wants outcome one. They are not offering it to you. They offer outcome
two instead, funded by their stake.

Orders at the same odds collapse into one level. Each level sums the unfilled part of every order at that price.

Matching is **price-time priority**. A taker consumes the best opposing price first. When several orders sit at that price, the earliest resting order fills first.

## Odds must sit on the ladder

`percentageOdds` on a new order must be on the ladder or the order will be rejected.

[Odds rounding](/developers/odds-rounding) has the full rules

## Fetching the book

| | [`GET /orderbook-v3/snapshot/market`](/api-reference/get-market-orderbook-snapshot) | [`GET /orders-v3/odds/best`](/api-reference/get-best-odds-v3) |
| - | - | - |
| Markets per call | **1** | up to **100** hashes, or **5** leagues |
| Levels per side | **all** | **1** (top of book) |
| Carries a version | **yes** | no |

Both read the same book. Use best odds to simply get the best odds available on each side and the snapshot to size and price a bet.

## Convert a level into what you can bet

A level's `size` is the resting party's stake. The stake **you** can put up against it is:

```
takerCapacity = size × (10^20 − percentageOdds) / percentageOdds
```

and the price you pay is `10^20 − percentageOdds`. Do both calculations using BigInt. Examples:

<CodeGroup>
  ```javascript read_book.mjs theme={null}
  const API = "https://api.sx.bet"; // Mainnet — use https://api.toronto.sx.bet for testnet
  const marketHash =
    "0x5be2a55e4b7dec1cec1b75af93b3fdef178ec1473866da95b3c68c1d4c99d4fd";

  const ODDS = 10n ** 20n; // 1e20 == 100% implied

  // A level's `size` is the resting party's stake. This is what you can bet.
  const takerCapacity = ({ size, percentageOdds }) =>
    (BigInt(size) * (ODDS - BigInt(percentageOdds))) / BigInt(percentageOdds);

  const pct = (odds) => (Number(BigInt(odds) / 10n ** 14n) / 1e4).toFixed(3);
  const usdc = (base) => (Number(base) / 1e6).toFixed(2);

  const res = await fetch(
    `${API}/orderbook-v3/snapshot/market?marketHash=${marketHash}`,
    { headers: { "x-sx-api-key": process.env.SX_API_KEY } }
  );
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  const { data } = await res.json();

  console.log(`version ${data.version}`);

  for (const side of ["outcomeOne", "outcomeTwo"]) {
    const levels = data[side];
    console.log(`\n${side}: ${levels.length} level(s)`);
    levels.forEach((level, i) => {
      console.log(
        `  [${i}] maker ${pct(level.percentageOdds)}%  ` +
          `size ${usdc(level.size)} USDC  ` +
          `-> taker ${pct(ODDS - BigInt(level.percentageOdds))}% ` +
          `for up to ${usdc(takerCapacity(level))} USDC`
      );
    });
  }
  ```

  ```python read_book.py theme={null}
  import os
  import requests

  API = "https://api.sx.bet"  # Mainnet — use https://api.toronto.sx.bet for testnet
  MARKET_HASH = "0x5be2a55e4b7dec1cec1b75af93b3fdef178ec1473866da95b3c68c1d4c99d4fd"

  ODDS = 10 ** 20  # 1e20 == 100% implied


  def taker_capacity(level: dict) -> int:
      """A level's `size` is the resting party's stake. This is what you can bet."""
      size = int(level["size"])
      maker_odds = int(level["percentageOdds"])
      return size * (ODDS - maker_odds) // maker_odds


  res = requests.get(
      f"{API}/orderbook-v3/snapshot/market",
      params={"marketHash": MARKET_HASH},
      headers={"x-sx-api-key": os.environ["SX_API_KEY"]},
  )
  res.raise_for_status()
  data = res.json()["data"]

  print(f"version {data['version']}")

  for side in ("outcomeOne", "outcomeTwo"):
      levels = data[side]
      print(f"\n{side}: {len(levels)} level(s)")
      for i, level in enumerate(levels):
          maker_odds = int(level["percentageOdds"])
          print(
              f"  [{i}] maker {maker_odds / ODDS:.3%}  "
              f"size {int(level['size']) / 1e6:.2f} USDC  "
              f"-> taker {(ODDS - maker_odds) / ODDS:.3%} "
              f"for up to {taker_capacity(level) / 1e6:.2f} USDC"
          )
  ```
</CodeGroup>

Both produce:

```
version 00100000000000002210000

outcomeOne: 3 level(s)
  [0] maker 52.000%  size 2.00 USDC  -> taker 48.000% for up to 1.85 USDC
  [1] maker 51.500%  size 1.00 USDC  -> taker 48.500% for up to 0.94 USDC
  [2] maker 50.000%  size 3.00 USDC  -> taker 50.000% for up to 3.00 USDC

outcomeTwo: 3 level(s)
  [0] maker 46.750%  size 1.00 USDC  -> taker 53.250% for up to 1.14 USDC
  [1] maker 46.000%  size 3.00 USDC  -> taker 54.000% for up to 3.52 USDC
  [2] maker 42.000%  size 1.00 USDC  -> taker 58.000% for up to 1.38 USDC
```

## Aggregate depth across a side

Levels are already aggregated by exact price, so a depth ladder is a running total.

```javascript theme={null}
const ODDS = 10n ** 20n;

/** Cumulative capacity available for betting `bettingOutcomeOne`. */
function depth(book, bettingOutcomeOne) {
  const levels = bettingOutcomeOne ? book.outcomeTwo : book.outcomeOne;
  let cumulative = 0n;
  return levels.map((level) => {
    const makerOdds = BigInt(level.percentageOdds);
    cumulative += (BigInt(level.size) * (ODDS - makerOdds)) / makerOdds;
    return { price: (ODDS - makerOdds).toString(), cumulative: cumulative.toString() };
  });
}
```

Against the same book:

```
bet outcomeTwo: [{"price":"48000000000000000000","cumulative":"1846153"},
                 {"price":"48500000000000000000","cumulative":"2787900"},
                 {"price":"50000000000000000000","cumulative":"5787900"}]
bet outcomeOne: [{"price":"53250000000000000000","cumulative":"1139037"},
                 {"price":"54000000000000000000","cumulative":"4660776"},
                 {"price":"58000000000000000000","cumulative":"6041728"}]
```

## Staying up to date

Polling the snapshot works, but the book changes on every match, cancel and expiry. The channel
[`orderbook_v3:{marketHash}`](/api-reference/channel-orderbook-v3) sends immediate real-time updates. It carries the **same book and the
same version** the snapshot route serves, and the exchange publishes one message per book change.

```json theme={null}
{
  "marketHash": "0x5be2a55e4b7dec1cec1b75af93b3fdef178ec1473866da95b3c68c1d4c99d4fd",
  "version": "00100000000000002209000",
  "outcomeOne": [
    { "percentageOdds": "52500000000000000000", "size": "1000000" },
    { "percentageOdds": "52000000000000000000", "size": "2000000" },
    { "percentageOdds": "51500000000000000000", "size": "1000000" },
    { "percentageOdds": "50000000000000000000", "size": "3000000" }
  ],
  "outcomeTwo": [
    { "percentageOdds": "46750000000000000000", "size": "1000000" },
    { "percentageOdds": "46000000000000000000", "size": "3000000" },
    { "percentageOdds": "42000000000000000000", "size": "1000000" }
  ]
}
```

Each publication is a **complete replacement** for that market's book, not a delta. To apply one,
overwrite both arrays.

The channel is always from the **maker's perspective**

The full seed-and-subscribe order is on [Real-time](/developers/realtime-overview). The version
comparison is on [Book versioning](/developers/book-versioning#the-apply-rule).

## Related

<CardGroup cols={2}>
  <Card title="Posting orders" icon="paper-plane" href="/developers/posting-orders">
    Sign and submit an order onto the book.
  </Card>

  <Card title="Fetching odds" icon="chart-line" href="/developers/odds">
    Scanning many markets, and getting a live client its first snapshot.
  </Card>

  <Card title="Book versioning" icon="code-compare" href="/developers/book-versioning">
    Applying live updates in the right order.
  </Card>

  <Card title="Time in force" icon="clock" href="/developers/time-in-force">
    GTC, IOC, FOK — and what happens when there is not enough depth.
  </Card>
</CardGroup>


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.