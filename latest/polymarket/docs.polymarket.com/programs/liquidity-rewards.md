> ## Documentation Index
> Fetch the complete documentation index at: https://docs.polymarket.com/llms.txt
> Use this file to discover all available pages before exploring further.

# Liquidity Rewards

> Earn rewards for providing liquidity on Polymarket

By posting resting limit orders, liquidity providers (makers) are automatically eligible for Polymarket's incentive program. Rewards are distributed directly to maker addresses daily at midnight UTC.

The program is designed to:

* Catalyze liquidity across all markets
* Encourage liquidity throughout a market's entire lifecycle
* Motivate passive, balanced quoting tight to a market's midpoint
* Encourage trading activity
* Discourage blatantly exploitative behaviors

> [!NOTE]
>
> The minimum reward payout is **\$1**; amounts below this will not be paid.

> [!TIP]
>
> Each incentivized market defines a minimum qualifying order size, maximum
> qualifying spread, and reward allocation. See [Liquidity Reward
> Settings](/market-data/market-details.md#liquidity-reward-settings) to read the
> current configuration for a market.

***

## Find Markets to Quote

Browse markets and their reward settings before choosing where to provide
liquidity. Search narrows the collection. Inspect each returned market's
configuration before assuming that rewards are currently available.

**TypeScript**

Call `listRewardMarkets()` using an existing `PublicClient` named `client`.
If needed, [create a public client](/getting-started/typescript.md#create-a-public-client).
The method is also available on `SecureClient`.

```ts theme={null}
import { RewardMarketSort } from "@polymarket/client";

const pages = client.listRewardMarkets({
  query: "sports",
  orderBy: RewardMarketSort.RatePerDay,
  sortDirection: "DESC",
  pageSize: 100,
});

for await (const page of pages) {
  for (const market of page.items) {
    console.log(
      market.question,
      market.rewardsMinSize,
      market.rewardsMaxSpread,
    );
    for (const config of market.rewardsConfig) {
      console.log(config.assetAddress, config.ratePerDay);
    }
  }
}
```

`RewardMarket` includes the market and event identifiers, token prices, spread,
volume, competitiveness, and reward configurations. Decimal values remain
strings. `rewardsConfig` can be empty. Do not infer an active reward solely from
a market appearing in the collection.

Use `tagSlugs` for category selection, or `marketId` and
`eventIds` for known markets and events. `minVolume24hr`, `maxVolume24hr`,
`minSpread`, `maxSpread`, `minPrice`, and `maxPrice` narrow numeric ranges.
Minimum bounds are exclusive, maximum bounds inclusive, and zero disables a
bound. A supplied `orderBy` defaults to ascending order unless you set
`sortDirection`.

**Python**

Call `list_reward_markets()` using an existing `AsyncPublicClient` named `client`.
If needed, [create a public client](/getting-started/python.md#create-a-public-client).
The method is also available on `AsyncSecureClient`. The synchronous clients
provide the same filters.

```python theme={null}
pages = client.list_reward_markets(
    query="sports",
    order_by="rate_per_day",
    sort_direction="DESC",
    page_size=100,
)

async for page in pages:
    for market in page.items:
        print(market.question, market.rewards_min_size, market.rewards_max_spread)
        for config in market.rewards_config:
            print(config.asset_address, config.rate_per_day)
```

`RewardMarket` includes the market and event identifiers, token prices, spread,
volume, competitiveness, and reward configurations. Decimal values are
`Decimal` instances. `rewards_config` can be empty. Do not infer an active reward
solely from a market appearing in the collection.

Use `tag_slugs` for category selection, or `market_id`
and `event_ids` for known markets and events. `min_volume_24hr`,
`max_volume_24hr`, `min_spread`, `max_spread`, `min_price`, and `max_price` narrow
numeric ranges. Minimum bounds are exclusive, maximum bounds inclusive, and
zero disables a bound. A supplied `order_by` defaults to ascending order unless
you set `sort_direction`.

**API**

Search the public reward-market collection without authentication.

```bash theme={null}
curl -G "https://clob.polymarket.com/rewards/markets/multi" \
  --data-urlencode "q=sports" \
  --data-urlencode "order_by=rate_per_day" \
  --data-urlencode "position=DESC" \
  --data-urlencode "page_size=100"
```

Illustrative response (fields trimmed):

> **Output: Reward Markets Page**
>
> ```json theme={null}
> {
>   "limit": 100,
>   "count": 1,
>   "next_cursor": "LTE=",
>   "data": [
>     {
>       "condition_id": "0x747dc809fb79e1b05be09c42d6179459a58de2ef3e40f02484a4e1260f741f75",
>       "question": "Will Team A win?",
>       "rewards_min_size": 100,
>       "rewards_max_spread": 3,
>       "rewards_config": [
>         {
>           "id": 7,
>           "asset_address": "0x9c4E1703476E875070EE25b56A58B008CFb8FA78",
>           "start_date": "2026-10-01",
>           "end_date": "2026-10-31",
>           "rate_per_day": 100,
>           "total_rewards": 3100
>         }
>       ]
>     }
>   ]
> }
> ```

Read markets from `data`, then pass the returned `next_cursor` with the same
filters for the next page. `next_cursor: "LTE="` marks the end. The default
page size is 100 and the maximum is 500.

Each market includes `rewards_min_size`, `rewards_max_spread`, and
`rewards_config`. The configuration can be empty. Inspect it before assuming
an active allocation. Repeat `tag_slug` or `event_id` to select multiple tags
or events. Numeric `min_*` bounds are exclusive and `max_*` bounds inclusive.
Zero disables a bound.

***

## Read Daily Earnings

Read daily totals for the authenticated account to reconcile liquidity rewards.
Choose whether to include sponsored rewards in the total.

**TypeScript**

Call `fetchTotalEarningsForUserForDay()` on a `SecureClient`.

```ts theme={null}
const earnings = await client.fetchTotalEarningsForUserForDay({
  date: "2026-10-06",
  sponsored: true,
});

for (const earning of earnings) {
  console.log(
    earning.earnings,
    earning.nativeEarnings,
    earning.sponsoredEarnings,
  );
}
```

`earnings` is the total decimal string for the row's reward asset. With
`sponsored: true`, the total includes native and sponsored rewards.
`nativeEarnings` and `sponsoredEarnings` expose the optional decimal-string
breakdown. An absent breakdown remains `undefined`. A returned zero is `"0"`.
Omitting `sponsored`, or setting it to `false`, requests native rewards only.

**Python**

Call `get_total_earnings_for_user_for_day()` on an `AsyncSecureClient`.
The synchronous `SecureClient` provides the same method.

```python theme={null}
earnings = await client.get_total_earnings_for_user_for_day(
    date="2026-10-06",
    sponsored=True,
)

for earning in earnings:
    print(earning.earnings, earning.native_earnings, earning.sponsored_earnings)
```

`earnings` is the `Decimal` total for the row's reward asset. With
`sponsored=True`, the total includes native and sponsored rewards.
`native_earnings` and `sponsored_earnings` expose the optional `Decimal`
breakdown. An absent breakdown remains `None`. A returned zero remains zero.
Omitting `sponsored`, or setting it to `False`, requests native rewards only.

**API**

Read daily totals with CLOB L2 authentication for the account:

```bash theme={null}
curl -G "https://clob.polymarket.com/rewards/user/total" \
  -H "POLY_ADDRESS: <signer_address>" \
  -H "POLY_API_KEY: <clob_api_key>" \
  -H "POLY_PASSPHRASE: <clob_api_passphrase>" \
  -H "POLY_SIGNATURE: <clob_l2_signature>" \
  -H "POLY_TIMESTAMP: <clob_request_timestamp>" \
  --data-urlencode "date=2026-10-06" \
  --data-urlencode "maker_address=0xFeA4cB3dD4ca7CefD3368653B7D6FF9BcDFca604" \
  --data-urlencode "sponsored=true"
```

Create fresh L2 headers using [API Authentication](/getting-started/api.md#authentication).
Sign the timestamp, `GET`, and `/rewards/user/total`, with no request body.

Illustrative response:

> **Output: Daily Earnings**
>
> ```json theme={null}
> [
>   {
>     "date": "2026-10-06T00:00:00Z",
>     "asset_address": "0x9c4E1703476E875070EE25b56A58B008CFb8FA78",
>     "maker_address": "0xFeA4cB3dD4ca7CefD3368653B7D6FF9BcDFca604",
>     "earnings": 12.5,
>     "native_earnings": 10,
>     "sponsored_earnings": 2.5,
>     "asset_rate": 1
>   }
> ]
> ```

Each response row reports `earnings` as a JSON number. Optional
`native_earnings` and `sponsored_earnings` numbers split the native and sponsored
amounts. With `sponsored=true`, the total includes both. The request defaults to
native rewards only when `sponsored` is omitted or `false`.

Group totals by reward asset when reconciling them. Amounts for different
assets should not be added without accounting for their exchange rates.

***

## Crypto TWAP Rewards

To support liquidity through this transition, Polymarket is adding **\$1M** in
liquidity rewards across impacted markets through the month of August. This
allocation applies only to crypto **5-minute**, **15-minute**, and **4-hour**
markets that settle on TWAP.

> [!NOTE]
>
> This program has ended. The August allocation is no longer active.

> [!NOTE]
>
> The pools below are configured reward caps. Actual payouts depend on eligible
> quoting and the scoring methodology on this page.

### 5-Minute Markets — \$550k

| Allocation | Amount |
| - | - |
| BTC | \$300k |
| SOL, ETH, HYPE, XRP | \$200k split evenly |
| BNB, DOGE | \$50k split evenly |

### 15-Minute Markets — \$350k

| Allocation | Amount |
| - | - |
| BTC | \$225k |
| SOL, ETH, HYPE, XRP | \$100k split evenly |
| BNB, DOGE | \$25k split evenly |

### 4-Hour Markets — \$100k

| Allocation | Amount |
| - | - |
| BTC | \$50k |
| SOL, ETH, HYPE, XRP | \$40k split evenly |
| BNB, DOGE | \$10k split evenly |

***

## Methodology

Liquidity providers are rewarded based on a formula that rewards participation in markets, boosts two-sided depth (single-sided orders still score), and tighter spread vs the size-cutoff-adjusted midpoint. Each market configures a max spread and min size cutoff within which orders are considered. The average of rewards earned is determined by the relative share of each participant's Q<sub>n</sub> in market m.

### Variables

| Variable | Description |
| - | - |
| S | Order position scoring function |
| v | Max spread from midpoint (in cents) |
| s | Spread from size-cutoff-adjusted midpoint |
| b | In-game multiplier |
| m | Market |
| m' | Market complement (i.e. NO if m = YES) |
| n | Trader index |
| u | Sample index |
| c | Scaling factor (currently 3.0 on all markets) |
| Q<sub>ne</sub> | Point total for book one for a sample |
| Q<sub>no</sub> | Point total for book two for a sample |
| Spread% | Distance from midpoint (bps or relative) for order n in market m |
| BidSize | Share-denominated quantity of bid |
| AskSize | Share-denominated quantity of ask |

***

## Equations

### 1. Order Scoring Function

Quadratic scoring rule for an order based on position between the adjusted midpoint and the minimum qualifying spread:

$S(v,s)= (\frac{v-s}{v})^2 \cdot b$

### 2. First Market Side Score

$Q_{one}= S(v,Spread_{m_1}) \cdot BidSize_{m_1} + S(v,Spread_{m_2}) \cdot BidSize_{m_2} + \dots $
$ + S(v, Spread_{m^\prime_1}) \cdot AskSize_{m^\prime_1} + S(v, Spread_{m^\prime_2}) \cdot AskSize_{m^\prime_2}$

### 3. Second Market Side Score

$Q_{two}= S(v,Spread_{m_1}) \cdot AskSize_{m_1} + S(v,Spread_{m_2}) \cdot AskSize_{m_2} + \dots $
$ + S(v, Spread_{m^\prime_1}) \cdot BidSize_{m^\prime_1} + S(v, Spread_{m^\prime_2}) \cdot BidSize_{m^\prime_2}$

### 4. Minimum Score

Boosts two-sided liquidity by taking the minimum of Q<sub>ne</sub> and Q<sub>no</sub>, while still rewarding single-sided liquidity at a reduced rate (divided by c).

**If midpoint is in range \[0.10, 0.90]** — single-sided liquidity can score:

$Q_{\min} = \max(\min({Q_{one}, Q_{two}}), \max(Q_{one}/c, Q_{two}/c))$

**If midpoint is in range \[0, 0.10) or (0.90, 1.0]** — liquidity must be double-sided to score:

$Q_{\min} = \min({Q_{one}, Q_{two}})$

### 5. Normalized Score

Q<sub>min</sub> of a market maker divided by the sum of all Q<sub>min</sub> across market makers in a given sample:

$Q_{normal} = \frac{Q_{min}}{\sum_{n=1}^{N}{(Q_{min})_n}}$

### 6. Epoch Score

Sum of all Q<sub>normal</sub> for a trader across all samples in an epoch. An epoch is one UTC day; the order book is sampled once per minute at a random offset, so an epoch contains up to 1,440 samples:

$Q_{epoch} = \sum_{u=1}^{1,440}{(Q_{normal})_u}$

### 7. Final Score

Normalizes Q<sub>epoch</sub> by dividing by the sum of all market makers' Q<sub>epoch</sub> in a given epoch. This value is multiplied by the rewards available for the market to get a trader's reward:

$Q_{final}=\frac{Q_{epoch}}{\sum_{n=1}^{N}{(Q_{epoch})_n}}$

***

## Worked Example

Assume an adjusted market midpoint of 0.50 and a max spread config of 3 cents for both m and m'.

### Step 2 - First Side Score

A trader has the following open orders:

* 100Q bid on m @ 0.49 (spread = 1 cent)
* 200Q bid on m @ 0.48 (spread = 2 cents)
* 100Q ask on m' @ 0.51 (spread = 1 cent)

$$
Q_{ne} = \left( \frac{(3-1)}{3} \right)^2 \cdot 100 + \left( \frac{(3-2)}{3} \right)^2 \cdot 200 + \left( \frac{(3-1)}{3} \right)^2 \cdot 100
$$

Q<sub>ne</sub> is calculated every minute using random sampling.

### Step 3 - Second Side Score

The same trader also has:

* 100Q bid on m @ 0.485 (spread = 1.5 cents)
* 100Q bid on m' @ 0.48 (spread = 2 cents)
* 200Q ask on m' @ 0.505 (spread = 0.5 cents)

$$
Q_{no} = \left( \frac{(3-1.5)}{3} \right)^2 \cdot 100 + \left( \frac{(3-2)}{3} \right)^2 \cdot 100 + \left( \frac{(3-.5)}{3} \right)^2 \cdot 200
$$

Q<sub>no</sub> is calculated every minute using random sampling.

### Steps 4-7

4. Take the minimum of Q<sub>ne</sub> and Q<sub>no</sub> (with single-sided adjustment if midpoint is in \[0.10, 0.90])
5. Normalize against all other market makers in the sample
6. Sum across all 1,440 samples in the epoch (one UTC day)
7. Normalize again to get final reward share


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.