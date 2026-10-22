> ## Documentation Index
> Fetch the complete documentation index at: https://docs.polymarket.com/llms.txt
> Use this file to discover all available pages before exploring further.

# Wallet Activity

> Track the positions and activity associated with a Polymarket wallet.

Use a wallet address to understand an account's current exposure and trace how
its activity has changed over time.

## Open Positions

Inspect a wallet's current outcome exposure and unrealized performance.

**TypeScript**

Call `listPositions()` on a `PublicClient` or `SecureClient`.

```ts theme={null}
const address = "0x8ba1f109551bD432803012645Ac136ddd64DBA72";

const pages = client.listPositions({ user: address, pageSize: 1 });

for await (const page of pages) {
  // page.items: Position[]
}
```

> **Output: Position\[]**
>
> ```ts Position Type theme={null}
> // Trimmed for brevity.
> type Position = {
>   wallet: EvmAddress;
>   assetId: TokenId | PositionId;
>   conditionId: ConditionId;
>   /** The current holding, in shares. */
>   currentSize: DecimalString;
>   avgPrice: DecimalString;
>   /** Fee-exclusive entry basis in USD. */
>   entryCostUsdc: DecimalString;
>   /** Attributed BUY fees in USD (disclosure; already excluded from entryCostUsdc). */
>   entryFeesUsdc: DecimalString;
>   /** Always entryCostUsdc + entryFeesUsdc. */
>   totalCostUsdc: DecimalString;
>   currentPrice: DecimalString;
>   currentValue: DecimalString;
>   realizedPnl: DecimalString;
>   /** currentValue - entryCostUsdc. */
>   unrealizedPnl: DecimalString;
>   /** Always realizedPnl + unrealizedPnl. */
>   totalPnl: DecimalString;
>   percentPnl: DecimalString;
>   status: PositionStatus;
>   redeemable: boolean;
>   mergeable: boolean;
>   negativeRisk: boolean;
>   title?: string;
>   slug?: string;
>   eventSlug?: string;
>   outcome?: string;
>   outcomeIndex?: number;
>   endDate?: IsoCalendarDateString;
>   lastEventAt?: EpochMilliseconds;
>   firstEntryAt?: EpochMilliseconds;
> };
> ```
>
> ```json Position Example theme={null}
> [
>   {
>     "conditionId": "0x747dc809fb79e1b05be09c42d6179459a58de2ef3e40f02484a4e1260f741f75",
>     "wallet": "0x8ba1f109551bd432803012645ac136ddd64dba72",
>     "assetId": "107505882767731489358349912513945399560393482969656700824895970500493757150417",
>     "currentSize": "125.5",
>     "avgPrice": "0.42",
>     "entryCostUsdc": "52.71",
>     "entryFeesUsdc": "0",
>     "totalCostUsdc": "52.71",
>     "currentPrice": "0.49",
>     "currentValue": "61.495",
>     "realizedPnl": "0",
>     "unrealizedPnl": "8.785",
>     "totalPnl": "8.785",
>     "percentPnl": "16.66",
>     "status": "OPEN",
>     "redeemable": false,
>     "mergeable": false,
>     "negativeRisk": false,
>     "title": "Will the US confirm that aliens exist before 2027?",
>     "slug": "will-the-us-confirm-that-aliens-exist-before-2027",
>     "eventSlug": "will-the-us-confirm-that-aliens-exist-before-2027",
>     "outcome": "Yes",
>     "outcomeIndex": 0
>   }
> ]
> ```

`firstEntryAt` is the first acquisition time in Unix epoch milliseconds, or `undefined` when unrecorded. Acquisitions include buys, splits, mints, migrations, and incoming transfers.

**Python**

Call `list_positions()` on an existing `AsyncPublicClient` or `AsyncSecureClient`.

```python theme={null}
address = "0x8ba1f109551bD432803012645Ac136ddd64DBA72"

pages = client.list_positions(
    user=address,
    page_size=1,
)

async for page in pages:
    # page.items: tuple[Position, ...]
    pass
```

> **Output: Position**
>
> ```python Position Type theme={null}
> class Position:
>     wallet: EvmAddress
>     asset_id: ClobAssetId
>     condition_id: ConditionId
>     current_size: Decimal
>     avg_price: Decimal
>     entry_cost_usdc: Decimal
>     entry_fees_usdc: Decimal
>     total_cost_usdc: Decimal
>     current_price: Decimal
>     current_value: Decimal
>     total_size: Decimal
>     realized_pnl: Decimal
>     unrealized_pnl: Decimal
>     total_pnl: Decimal
>     percent_pnl: Decimal
>     percent_realized_pnl: Decimal
>     status: PositionStatus
>     redeemable: bool
>     mergeable: bool
>     negative_risk: bool
>     archived: bool
>     verified: bool
>     title: str | None
>     slug: str | None
>     icon: str | None
>     event_slug: str | None
>     outcome: str | None
>     opposite_outcome: str | None
>     name: str | None
>     profile_image: str | None
>     event_id: EventId | None
>     outcome_index: int | None
>     opposite_asset_id: ClobAssetId | None
>     end_date: date | None
>     last_event_at: datetime | None
>     first_entry_at: datetime | None
> ```
>
> ```json Position Example theme={null}
> {
>   "wallet": "0x7c3db723f1d4d8cb9c550095203b686cb11e5c6b",
>   "asset_id": "94476829201604408463453426454480212459887267917122244941405244686637914508323",
>   "condition_id": "0x7ad403c3508f8e3912940fd1a913f227591145ca0614074208e0b962d5fcc422",
>   "current_size": "400000.0",
>   "avg_price": "0.5",
>   "entry_cost_usdc": "200000.0",
>   "entry_fees_usdc": "0.0",
>   "total_cost_usdc": "200000.0",
>   "current_price": "0.7545",
>   "current_value": "301800.0",
>   "total_size": "10386654.0",
>   "realized_pnl": "0.0",
>   "unrealized_pnl": "101800.0",
>   "total_pnl": "101800.0",
>   "percent_pnl": "50.9",
>   "percent_realized_pnl": "-94.1886",
>   "status": "OPEN",
>   "redeemable": false,
>   "mergeable": true,
>   "negative_risk": true,
>   "archived": false,
>   "verified": true,
>   "title": "Will JD Vance win the 2028 US Presidential Election?",
>   "slug": "will-jd-vance-win-the-2028-us-presidential-election",
>   "icon": "https://polymarket-upload.s3.us-east-2.amazonaws.com/will-jd-vance-win-the-2028-us-presidential-election-P-zEgXjCWbdY.png",
>   "event_slug": "presidential-election-winner-2028",
>   "outcome": "No",
>   "opposite_outcome": "Yes",
>   "name": "Car",
>   "profile_image": "https://polymarket-upload.s3.us-east-2.amazonaws.com/profile-image-501613-aa434e55-7732-41b1-9650-83a9d1d716ef.png",
>   "event_id": "31552",
>   "outcome_index": 1,
>   "opposite_asset_id": "16040015440196279900485035793550429453516625694844857319147506590755961451627",
>   "end_date": "2028-11-07",
>   "last_event_at": "2026-08-23T15:46:10Z",
>   "first_entry_at": "2025-08-13T21:51:32Z"
> }
> ```

`current_size` is in shares. Costs, values, and PnL use `Decimal` values in USDC. `entry_cost_usdc` excludes fees. Do not subtract `entry_fees_usdc` from it again. `first_entry_at` is the first acquisition time as a UTC `datetime`, or `None` when unrecorded. Acquisitions include buys, splits, mints, migrations, and incoming transfers.

Position reads have no time bounds by default. `full_history=True` preserves that behavior, including holdings without activity timestamps. Use `start` and `end` to filter by last activity instead.

**API**

List the open positions for a wallet:

```bash theme={null}
ADDRESS="0x8ba1f109551bD432803012645Ac136ddd64DBA72"

curl "https://data-api.polymarket.com/v2/positions?user=$ADDRESS&limit=1"
```

The response contains the wallet's open positions (row fields trimmed for
brevity):

> **Response**
>
> ```json theme={null}
> {
>   "data": [
>     {
>       "proxy_wallet": "0x8ba1f109551bD432803012645Ac136ddd64DBA72",
>       "token_id": "107505882767731489358349912513945399560393482969656700824895970500493757150417",
>       "condition_id": "0x747dc809fb79e1b05be09c42d6179459a58de2ef3e40f02484a4e1260f741f75",
>       "status": "OPEN",
>       "current_size": 125.5,
>       "avg_price": 0.42,
>       "entry_cost_usdc": 52.71,
>       "current_price": 0.49,
>       "current_value": 61.5,
>       "unrealized_pnl": 8.79,
>       "total_pnl": 8.79,
>       "percent_pnl": 16.68,
>       "redeemable": false,
>       "mergeable": false,
>       "title": "Will the US confirm that aliens exist before 2027?",
>       "slug": "will-the-us-confirm-that-aliens-exist-before-2027",
>       "event_slug": "will-the-us-confirm-that-aliens-exist-before-2027",
>       "outcome": "Yes",
>       "outcome_index": 0
>     }
>   ],
>   "pagination": {
>     "limit": 1,
>     "offset": 0,
>     "has_more": true,
>     "next_cursor": "eyJkYXRhIjp7InR5cGUiOiJwb3NpdGlvbnMi…"
>   }
> }
> ```

The position's acquisition timestamp has this shape:

| Field | Type |
| - | - |
| `first_entry_at` | integer |

`first_entry_at` is the first acquisition time in Unix epoch seconds, or `0` when unrecorded. Acquisitions include buys, splits, mints, migrations, and incoming transfers.

Follow `pagination.next_cursor` with the `cursor` parameter for the next
page.

### Filter Positions by Title

Narrow positions to markets whose titles contain a search term, ignoring case.
This works across position statuses and when listing a market's holders.

**TypeScript**

Pass `title` to `listPositions()` on the existing `PublicClient` or `SecureClient`:

```ts theme={null}
const pages = client.listPositions({ user: address, title: "election" });

for await (const page of pages) {
  // page.items: Position[]
}
```

`title` accepts at most 200 Unicode characters. Empty or whitespace-only
input disables the filter. Other patterns are preserved unchanged; `%`
matches any sequence of characters and `_` matches one character.
The paginator retains `title` on every page, including when resumed with
`pages.from(nextCursor)`.

**Python**

Pass `title` to `list_positions()` on the existing `AsyncPublicClient` or
`AsyncSecureClient`. The synchronous clients accept the same argument:

```python theme={null}
pages = client.list_positions(user=address, title="election")

async for page in pages:
    # page.items: tuple[Position, ...]
    pass
```

`title` accepts at most 200 Unicode characters. Empty or whitespace-only
input disables the filter. Other patterns are preserved unchanged; `%`
matches any sequence of characters and `_` matches one character.
The paginator retains `title` on every page, including when resumed with
`pages.from_cursor(next_cursor)`.

**API**

Send `title` with the position request:

```bash theme={null}
curl --get "https://data-api.polymarket.com/v2/positions" \
  --data-urlencode "user=$ADDRESS" \
  --data-urlencode "title=election"
```

`title` accepts at most 200 Unicode characters. Empty or whitespace-only
input disables the filter. Other patterns are preserved unchanged; SQL
`LIKE` wildcards keep their meaning: `%` matches any sequence of characters
and `_` matches one character.
Re-send the same `title` and anchor with every `cursor` request. The cursor
does not carry the title filter, so omitting it broadens subsequent pages.

## Closed Positions

Review the positions a wallet has exited or that have resolved, including
their realized performance.

**TypeScript**

One method serves the whole position lifecycle: call `listPositions()`
with `status: PositionStatus.Closed`. Closed rows keep the same `Position`
shape with realized economics (`currentSize` is a \~0 residual and
`realizedPnl` carries the outcome); they default to sorting by realized
PnL.

This example uses an existing `PublicClient` or `SecureClient`. If needed,
[create a Public Client](/getting-started/typescript.md#create-a-public-client) first.

```ts theme={null}
import {
  PositionSortBy,
  PositionStatus,
  SortDirection,
} from "@polymarket/client";

const pages = client.listPositions({
  user: address,
  status: PositionStatus.Closed,
  sortBy: PositionSortBy.Price,
  sortDirection: SortDirection.Desc,
  pageSize: 1,
});

for await (const page of pages) {
  // page.items: Position[]
}
```

> **Output: Position\[]**
>
> ```json Position Example theme={null}
> [
>   {
>     "conditionId": "0x747dc809fb79e1b05be09c42d6179459a58de2ef3e40f02484a4e1260f741f75",
>     "wallet": "0x8ba1f109551bd432803012645ac136ddd64dba72",
>     "assetId": "7305630249804085635496399869905769372294302716159034447326228509068694952392",
>     "currentSize": "0",
>     "avgPrice": "0.58",
>     "entryCostUsdc": "145",
>     "totalCostUsdc": "145",
>     "realizedPnl": "34.25",
>     "unrealizedPnl": "0",
>     "totalPnl": "34.25",
>     "status": "CLOSED",
>     "redeemable": false,
>     "title": "Will the US confirm that aliens exist before 2027?",
>     "outcome": "No",
>     "outcomeIndex": 1
>   }
> ]
> ```

For a `user`-anchored listing with `status: PositionStatus.Closed`,
`PositionSortBy.Tokens`, `CurrentValue`, `Price`, and `UnrealizedPnl`
rank by realized PnL, preserving the requested `sortDirection`. This
example therefore orders realized PnL descending. `RealizedPnl`,
`TotalPnl`, and `Timestamp` retain their semantics. Market-only CLOSED
listings do not use this fallback.

Keep the original filters and `sortBy` when continuing with a cursor.
Do not rewrite it to `RealizedPnl`. An old residual-sort cursor can raise
`RequestRejectedError` with a message containing
`positions order changed; restart pagination without cursor`. The SDK
passes the rejection to the caller without restarting. Explicitly discard
that cursor and start again with the original request and no `cursor`.
Restarting can return rows you have already consumed.

**Python**

Call `list_positions()` on an existing `AsyncPublicClient` or `AsyncSecureClient`.
If needed, [create a Public Client](/getting-started/python.md#create-a-public-client) first.

```python theme={null}
pages = client.list_positions(
    user=address,
    status="CLOSED",
    sort_by="PRICE",
    sort_direction="DESC",
    page_size=1,
)

async for page in pages:
    # page.items: tuple[Position, ...]
    pass
```

Closed positions use the same `Position` model as open positions. Read `realized_pnl` for realized performance. Secure clients can omit `user` to read the authenticated wallet.

For a `user`-anchored listing with `status="CLOSED"`, `sort_by` values
`TOKENS`, `CURRENT_VALUE`, `PRICE`, and `UNREALIZED_PNL` rank by realized PnL,
preserving `sort_direction`. This example therefore orders realized PnL
descending. `REALIZED_PNL`, `TOTAL_PNL`, and `TIMESTAMP` retain their semantics.
Market-only CLOSED listings do not use this fallback.

Keep the original filters and `sort_by` when continuing with a cursor.
Do not rewrite it to `REALIZED_PNL`. An old residual-sort cursor can raise
`RequestRejectedError` with a message containing
`positions order changed; restart pagination without cursor`. The SDK passes
the rejection to the caller without restarting. Explicitly discard that cursor
and start again with the original request and no `cursor`. Restarting can
return rows you have already consumed.

**API**

Closed positions are served by the same route behind the `status` filter:

```bash theme={null}
curl "https://data-api.polymarket.com/v2/positions?user=$ADDRESS&status=CLOSED&sort_by=PRICE&sort_direction=DESC&limit=1"
```

The response contains the wallet's closed positions (row fields trimmed
for brevity):

> **Response**
>
> ```json theme={null}
> {
>   "data": [
>     {
>       "proxy_wallet": "0x8ba1f109551bD432803012645Ac136ddd64DBA72",
>       "token_id": "7305630249804085635496399869905769372294302716159034447326228509068694952392",
>       "condition_id": "0x747dc809fb79e1b05be09c42d6179459a58de2ef3e40f02484a4e1260f741f75",
>       "status": "CLOSED",
>       "current_size": 0,
>       "total_size": 250,
>       "avg_price": 0.58,
>       "current_price": 0,
>       "realized_pnl": 34.25,
>       "total_pnl": 34.25,
>       "last_event_at": 1782752879,
>       "title": "Will the US confirm that aliens exist before 2027?",
>       "slug": "will-the-us-confirm-that-aliens-exist-before-2027",
>       "event_slug": "will-the-us-confirm-that-aliens-exist-before-2027",
>       "outcome": "No",
>       "outcome_index": 1
>     }
>   ],
>   "pagination": {
>     "limit": 1,
>     "offset": 0,
>     "has_more": true,
>     "next_cursor": "eyJkYXRhIjp7InR5cGUiOiJwb3NpdGlvbnMi…"
>   }
> }
> ```

For a `user`-anchored listing with `status=CLOSED`, `sort_by` values
`TOKENS`, `CURRENT_VALUE`, `PRICE`, and `UNREALIZED_PNL` rank by realized
PnL, preserving `sort_direction`. `REALIZED_PNL`, `TOTAL_PNL`, and
`TIMESTAMP` retain their semantics. Market-only CLOSED listings do not
use this fallback.

Continue with the returned `pagination.next_cursor`, retaining the original
filters and requested `sort_by`. Old residual-sort cursors return HTTP
`400` with `invalid cursor: positions order changed; restart pagination without cursor`.
Explicitly discard that cursor and start again with the original request
and no `cursor`. Changing the sort or repeatedly retrying the same cursor
does not recover it. Restarting can return rows you have already consumed.

## Sort Positions by Price

Rank a wallet's holdings by current effective price to find its lowest- or
highest-priced outcomes.

**TypeScript**

Call `listPositions()` on an existing `PublicClient` or `SecureClient`, using
the `address` from the open positions example.

```ts theme={null}
import { PositionSortBy, SortDirection } from "@polymarket/client";

const pages = client.listPositions({
  user: address,
  sortBy: PositionSortBy.Price,
  sortDirection: SortDirection.Asc,
});

for await (const page of pages) {
  // page.items: Position[]
}
```

`PositionSortBy.Price` orders by `currentPrice`. Use `SortDirection.Desc` for
highest prices first. The paginator retains the selected sort on later pages.

**Python**

Call `list_positions()` on an existing `AsyncPublicClient` or `AsyncSecureClient`,
using the `address` from the open positions example. The sync clients accept the
same sort options.

```python theme={null}
pages = client.list_positions(
    user=address,
    sort_by="PRICE",
    sort_direction="ASC",
)

async for page in pages:
    # page.items: tuple[Position, ...]
    pass
```

`sort_by="PRICE"` orders by `current_price`. Use `sort_direction="DESC"` for
highest prices first. The paginator retains the selected sort on later pages.

**API**

Request the wallet's positions using the `ADDRESS` from the open positions example:

```bash theme={null}
curl "https://data-api.polymarket.com/v2/positions?user=$ADDRESS&sort_by=PRICE&sort_direction=ASC&limit=100"
```

`sort_by=PRICE` orders by `current_price`. Use `sort_direction=DESC` for highest
prices first. For later pages, pass `pagination.next_cursor` as `cursor` and keep
the same sort parameters.

Redeemable holdings keep winners ahead of losing holdings, then apply the selected
price order within each group. A wallet's closed positions use realized profit
and loss ordering when price ordering is requested.

Without an explicit sort, mergeable holdings use token quantity, closed positions
use realized profit and loss, and other holdings use current value. The default
direction is descending.

## Activity History

Follow the timeline for a wallet across trades and other activity.

**TypeScript**

Call `listActivity()` on an existing `PublicClient` or `SecureClient`.
If needed, [create a Public Client](/getting-started/typescript.md#create-a-public-client) first.

```ts theme={null}
import { ActivityType } from "@polymarket/client";

const pages = client.listActivity({ user: address, pageSize: 1 });

for await (const page of pages) {
  for (const activity of page.items) {
    if (
      (activity.type === ActivityType.TRADE ||
        activity.type === ActivityType.REDEEM) &&
      activity.isCombo
    ) {
      console.log(
        activity.conditionId,
        activity.outcome,
        activity.outcomeIndex,
      );
    }
  }
}
```

You can narrow down the activity types using the `type` field:

```ts theme={null}
import { ActivityType } from "@polymarket/client";

const pages = client.listActivity({
  user: address,
  type: [ActivityType.DEPOSIT, ActivityType.WITHDRAWAL],
});
```

For `TRADE`, `SPLIT`, `MERGE` and `REDEEM`, check `type` first, then `isCombo`. A value of
`true` identifies Combo activity. `false` identifies ordinary market
activity. `RedeemActivity` is `ClobRedeemActivity | ComboRedemptionActivity`.
Combo rows carry a `ComboConditionId` in `conditionId`.
Combo splits, merges and redemptions have optional titles and no single
market or event URL slug.

Combo `outcome` and `outcomeIndex` describe the Yes/No side of the aggregate
basket token, rather than a selected leg's label. Unavailable labels or
indexes stay `undefined`. A known index of `0` is preserved. Automatic
redemption is also represented by `ActivityType.REDEEM`.

This source-breaking 0.x API update adds Combo variants to
`SplitActivity`, `MergeActivity` and `RedeemActivity`. Code that previously accessed market URLs after
checking only `type` must also check `!isCombo`. For example, read ordinary
market URLs within the same activity iterator:

```ts theme={null}
const pages = client.listActivity({ user: address });
for await (const page of pages) {
  for (const activity of page.items) {
    if (
      (activity.type === ActivityType.SPLIT ||
        activity.type === ActivityType.MERGE ||
        activity.type === ActivityType.REDEEM) &&
      !activity.isCombo
    ) {
      console.log(activity.slug, activity.eventSlug);
    }
  }
}
```

Parsed ordinary rows return `isCombo: false`. Manually constructed ordinary
split, merge and redemption values may omit it.

`Activity` is a discriminated union. The `type` field identifies each
variant:

> **Output: Activity\[]**
>
> ```ts Activity Union theme={null}
> type Activity =
>   | TradeActivity
>   | SplitActivity
>   | MergeActivity
>   | RedeemActivity
>   | ConversionActivity
>   | MigrationActivity
>   | RewardActivity
>   | MakerRebateActivity
>   | ReferralRewardActivity
>   | YieldActivity
>   | DepositActivity
>   | WithdrawalActivity
>   | TakerRebateActivity
>   | TipActivity;
> ```
>
> ```json Activity Example theme={null}
> [
>   {
>     "type": "TRADE",
>     "isCombo": false,
>     "wallet": "0x8ba1f109551bD432803012645Ac136ddd64DBA72",
>     "timestamp": 1782752879000,
>     "transactionHash": "0x4f3d2c1b0a9876543210fedcba9876543210fedcba9876543210fedcba987654",
>     "conditionId": "0x747dc809fb79e1b05be09c42d6179459a58de2ef3e40f02484a4e1260f741f75",
>     "assetId": "107505882767731489358349912513945399560393482969656700824895970500493757150417",
>     "side": "BUY",
>     "shares": "25",
>     "amount": "12.25",
>     "price": "0.49",
>     "title": "Will the US confirm that aliens exist before 2027?",
>     "slug": "will-the-us-confirm-that-aliens-exist-before-2027",
>     "eventSlug": "will-the-us-confirm-that-aliens-exist-before-2027",
>     "outcome": "Yes",
>     "outcomeIndex": 0
>   },
>   {
>     "type": "REWARD",
>     "wallet": "0x8ba1f109551bD432803012645Ac136ddd64DBA72",
>     "timestamp": 1782752979000,
>     "transactionHash": "0x7a6b5c4d3e2f109876543210fedcba9876543210fedcba9876543210fedcba98",
>     "amount": "5"
>   }
> ]
> ```

**Python**

Call `list_activity()` on an existing `AsyncPublicClient` or `AsyncSecureClient`.
If needed, [create a Public Client](/getting-started/python.md#create-a-public-client) first.

```python theme={null}
from polymarket import ComboTradeActivity, RedeemActivity

pages = client.list_activity(user=address, page_size=1)

async for page in pages:
    for activity in page.items:
        if isinstance(activity, (ComboTradeActivity, RedeemActivity)) and activity.is_combo:
            print(activity.condition_id, activity.outcome, activity.outcome_index)
```

Check `is_combo` on trades and redemptions to identify Combo activity.
`ComboTradeActivity` has `is_combo=True` and a `ComboConditionId`.
All redemptions remain `RedeemActivity`, with a `ConditionId`. Check
`is_combo` to distinguish a basket from an ordinary market. Existing
`isinstance(activity, RedeemActivity)` checks include Combo redemptions.

Combo `outcome` and `outcome_index` describe the Yes/No side of the aggregate
basket token, rather than a selected leg's label. Unavailable labels or indexes
stay `None`. A known index of `0` is preserved. Automatic redemption is also
represented by `REDEEM`.

Filter activity kinds with `activity_types`. Use `full_history=True` for complete history without explicit `start` or `end` bounds.

```python theme={null}
pages = client.list_activity(
    user=address,
    activity_types=["DEPOSIT", "WITHDRAWAL"],
    full_history=True,
)
```

> **Output: Activity**
>
> ```python Activity Union theme={null}
> Activity = (
>     TradeActivity
>     | ComboTradeActivity
>     | SplitActivity
>     | MergeActivity
>     | RedeemActivity
>     | ConversionActivity
>     | RewardActivity
>     | DepositActivity
>     | WithdrawalActivity
>     | MakerRebateActivity
>     | TakerRebateActivity
>     | ReferralRewardActivity
>     | YieldActivity
>     | MigrationActivity
>     | TipActivity
>     | UnknownActivity
> )
> ```
>
> ```json Activity Example theme={null}
> [
>   {
>     "wallet": "0x7c3db723f1d4d8cb9c550095203b686cb11e5c6b",
>     "timestamp": "2026-09-08T16:56:49Z",
>     "transaction_hash": "0xa661459fc2483ddb4e5c7d13c76347fa2b1fb2faae10eed484c810ae46c471dd",
>     "name": "Car",
>     "pseudonym": "Peppery-Capital",
>     "bio": "what is your plan",
>     "profile_image": "https://polymarket-upload.s3.us-east-2.amazonaws.com/profile-image-501613-aa434e55-7732-41b1-9650-83a9d1d716ef.png",
>     "profile_image_optimized": null,
>     "type": "TRADE",
>     "is_combo": false,
>     "condition_id": "0x0a91645ed98ad09817e051a17466cff087b920393aefba0fe74e8735fc192f15",
>     "asset_id": "98255364887669934733864645030488537034263699416357729920734267612805930214172",
>     "side": "BUY",
>     "shares": "14.285715",
>     "amount": "13.285715",
>     "price": "0.9300000035",
>     "outcome": "Yes",
>     "outcome_index": 0,
>     "title": "LAPTOP FDV above $25M one day after launch?",
>     "slug": "laptop-fdv-above-25m-one-day-after-launch",
>     "icon": "https://polymarket-upload.s3.us-east-2.amazonaws.com/laptop-fdv-above-25m-one-day-after-launch-UQwx2GeHcu_f.jpg",
>     "event_slug": "laptop-fdv-above-one-day-after-launch"
>   }
> ]
> ```

**API**

List the activity for a wallet:

```bash theme={null}
curl "https://data-api.polymarket.com/v2/activity?user=$ADDRESS&limit=1"
```

The endpoint excludes deposit and withdrawal records by default, even when
`type` requests them. To get them, pass
`exclude_deposits_withdrawals=false`.

The response contains the wallet's activity (row fields trimmed for
brevity):

> **Response**
>
> ```json theme={null}
> {
>   "data": [
>     {
>       "proxy_wallet": "0x8ba1f109551bD432803012645Ac136ddd64DBA72",
>       "timestamp": 1782752879,
>       "condition_id": "0x747dc809fb79e1b05be09c42d6179459a58de2ef3e40f02484a4e1260f741f75",
>       "type": "TRADE",
>       "size": 25,
>       "usdc_size": 12.25,
>       "transaction_hash": "0x4f3d2c1b0a9876543210fedcba9876543210fedcba9876543210fedcba987654",
>       "price": 0.49,
>       "token_id": "107505882767731489358349912513945399560393482969656700824895970500493757150417",
>       "side": "BUY",
>       "title": "Will the US confirm that aliens exist before 2027?",
>       "slug": "will-the-us-confirm-that-aliens-exist-before-2027",
>       "event_slug": "will-the-us-confirm-that-aliens-exist-before-2027",
>       "outcome": "Yes",
>       "outcome_index": 0
>     }
>   ],
>   "pagination": {
>     "limit": 1,
>     "offset": 0,
>     "has_more": true,
>     "next_cursor": "eyJkYXRhIjp7InR5cGUiOiJhY3Rpdml0eSI…"
>   }
> }
> ```

For `TRADE`, `SPLIT`, `MERGE` and `REDEEM` rows, `is_combo=true` identifies
Combo activity. Ordinary activity omits this field. On Combo trades and
redemptions, `outcome` and `outcome_index` are the aggregate basket
token's Yes/No label and slot, rather than a selected leg's outcome.
Automatic redemption also returns `type=REDEEM`. A blank label or index
`999` means unavailable. Index `0` is known. Preserve the returned
`condition_id` width and use the discriminator rather than its prefix to
classify the activity.

## Notifications

Notifications provide a short-lived record of unread events for the connected
account. The CLOB retains them for 48 hours.

> [!NOTE]
>
> Clients authenticated with [Session
> Keys](/trading/session-keys.md#session-key-considerations) receive only
> notifications generated by those Session Keys.

### List Notifications

**TypeScript**

Call `fetchNotifications()` on a `SecureClient`:

```ts theme={null}
const notifications = await client.fetchNotifications();

// notifications: Notification[]
```

> **Output: Notification\[]**
>
> ```ts Notification Type theme={null}
> type Notification = {
>   id: NotificationId;
>   owner: string;
>   payload: unknown;
>   timestamp: EpochMilliseconds;
>   type: number;
> };
> ```
>
> ```json Notification Example theme={null}
> [
>   {
>     "id": 1,
>     "owner": "f4f247b7-4ac7-ff29-a152-04fda0a8755a",
>     "type": 2,
>     "payload": {
>       "order_id": "0x72c66a1f70c00ac5e5eb9ce0452b7d118bc4869f8b822a1a8d8580c16e3ca83e"
>     },
>     "timestamp": 1675277676000
>   }
> ]
> ```

**Python**

Call `get_notifications()` on an `AsyncSecureClient`. The synchronous
`SecureClient` provides the same method:

```python theme={null}
notifications = await client.get_notifications()

# notifications: tuple[Notification, ...]
```

> **Output: tuple\[Notification, ...]**
>
> ```python Notification Type theme={null}
> class Notification:
>     id: int
>     owner: str
>     type: int
>     payload: Any
>     timestamp: datetime
> ```
>
> ```json Notification Example theme={null}
> [
>   {
>     "id": 1,
>     "owner": "f4f247b7-4ac7-ff29-a152-04fda0a8755a",
>     "type": 2,
>     "payload": {
>       "order_id": "0x72c66a1f70c00ac5e5eb9ce0452b7d118bc4869f8b822a1a8d8580c16e3ca83e"
>     },
>     "timestamp": "2023-02-01T18:54:36Z"
>   }
> ]
> ```

**API**

List unread notifications for the account:

```bash theme={null}
curl -G "https://clob.polymarket.com/notifications" \
  -H "POLY_ADDRESS: <signer_address>" \
  -H "POLY_SIGNATURE: <clob_l2_signature>" \
  -H "POLY_TIMESTAMP: <clob_request_timestamp>" \
  -H "POLY_API_KEY: <clob_api_key>" \
  -H "POLY_PASSPHRASE: <clob_api_passphrase>" \
  --data-urlencode "signature_type=<signature_type>"
```

Using the signer address and CLOB API credentials from [API
Authentication](/getting-started/api.md#authentication), create a fresh
`<clob_request_timestamp>` and generate `<clob_l2_signature>` for
`GET /notifications`. The `signature_type` query parameter is not part of
the signed path:

```text theme={null}
message = <clob_request_timestamp> + "GET" + "/notifications"

clob_l2_signature = urlsafeBase64WithPadding(
  HMAC-SHA256(base64Decode(<clob_api_secret>), message)
)
```

Select `signature_type` from the connected account's [wallet
type](/trading/wallets-auth.md#wallet-types):

| Wallet | `signature_type` |
| - | -: |
| Deposit Wallet | `3` |
| Proxy Wallet | `1` |
| Safe Wallet | `2` |
| EOA | `0` |

The response contains the unread notifications:

```json theme={null}
[
  {
    "id": 1,
    "owner": "f4f247b7-4ac7-ff29-a152-04fda0a8755a",
    "type": 2,
    "payload": {
      "order_id": "0x72c66a1f70c00ac5e5eb9ce0452b7d118bc4869f8b822a1a8d8580c16e3ca83e"
    },
    "timestamp": 1675277676
  }
]
```

### Drop Notifications

Mark notifications as read after processing them. Dropped notifications no
longer appear when you list unread notifications.

**TypeScript**

Call `dropNotifications()` on a `SecureClient` with the notification IDs:

```ts theme={null}
await client.dropNotifications({
  ids: notifications.map(({ id }) => String(id)),
});
```

**Python**

Call `drop_notifications()` on an `AsyncSecureClient`. The synchronous
`SecureClient` provides the same method:

```python theme={null}
await client.drop_notifications(
    ids=[notification.id for notification in notifications],
)
```

**API**

Send the notification IDs as a comma-separated list:

```bash theme={null}
curl -X DELETE -G "https://clob.polymarket.com/notifications" \
  -H "POLY_ADDRESS: <signer_address>" \
  -H "POLY_SIGNATURE: <clob_l2_signature>" \
  -H "POLY_TIMESTAMP: <clob_request_timestamp>" \
  -H "POLY_API_KEY: <clob_api_key>" \
  -H "POLY_PASSPHRASE: <clob_api_passphrase>" \
  --data-urlencode "ids=1,2"
```

Using the signer address and CLOB API credentials from [API
Authentication](/getting-started/api.md#authentication), create a fresh
`<clob_request_timestamp>` and generate `<clob_l2_signature>` for
`DELETE /notifications`. The `ids` query parameter is not part of the signed
path:

```text theme={null}
message = <clob_request_timestamp> + "DELETE" + "/notifications"

clob_l2_signature = urlsafeBase64WithPadding(
  HMAC-SHA256(base64Decode(<clob_api_secret>), message)
)
```

A successful request returns `OK`.

## Portfolio Value

Read the current portfolio value for a wallet.

**TypeScript**

Call `fetchPortfolioValue()` on a `PublicClient` or `SecureClient`. It
returns a single object: the wallet's marked portfolio value in USDC,
rounded to four decimals.

```ts theme={null}
const portfolioValue = await client.fetchPortfolioValue({ user: address });

// portfolioValue: PortfolioValue
```

> **Output: PortfolioValue**
>
> ```ts PortfolioValue Type theme={null}
> type PortfolioValue = {
>   wallet: EvmAddress;
>   /** Total marked portfolio value, in USDC, rounded to four decimals. */
>   value: DecimalString;
> };
> ```
>
> ```json PortfolioValue Example theme={null}
> {
>   "wallet": "0x8ba1f109551bd432803012645ac136ddd64dba72",
>   "value": "1842.37"
> }
> ```

**Python**

Call `get_portfolio_value()` on an existing `AsyncPublicClient` or `AsyncSecureClient`.

```python theme={null}
portfolio_value = await client.get_portfolio_value(user=address)

# portfolio_value: PortfolioValue
```

> **Output: PortfolioValue**
>
> ```python PortfolioValue Type theme={null}
> class PortfolioValue:
>     wallet: EvmAddress
>     value: Decimal
> ```
>
> ```json PortfolioValue Example theme={null}
> {
>   "wallet": "0x7c3db723f1d4d8cb9c550095203b686cb11e5c6b",
>   "value": "684953.8942"
> }
> ```

The result is one `PortfolioValue` object. `value` is the wallet's marked portfolio value in USDC.

**API**

Fetch the portfolio value for a wallet:

```bash theme={null}
curl "https://data-api.polymarket.com/v2/value?user=$ADDRESS"
```

The response contains the wallet's current portfolio value:

> **Response**
>
> ```json theme={null}
> {
>   "data": {
>     "proxy_wallet": "0x8ba1f109551bD432803012645Ac136ddd64DBA72",
>     "value": 1842.37
>   }
> }
> ```

## Wallet Stats

Read a wallet's lifetime trading statistics.

**TypeScript**

Call `fetchUserStats()` on a `PublicClient` or `SecureClient`.

```ts theme={null}
const stats = await client.fetchUserStats({ user: address });

// stats: UserStats | null
const tradedMarketCount = stats?.tradedMarketCount;
```

`tradedMarketCount` counts distinct markets, not fills. `stats` is `null`
for an unknown user.

**Python**

Call `get_user_stats()` on an existing `AsyncPublicClient` or `AsyncSecureClient`.

```python theme={null}
stats = await client.get_user_stats(user=address)

# stats: UserStats | None
traded_market_count = stats.traded_market_count if stats is not None else None
```

`traded_market_count` counts distinct markets, not fills. `stats` is `None` when no statistics are available. Secure clients default `user` to the authenticated wallet.

**API**

Fetch the wallet's profile statistics:

```bash theme={null}
curl "https://data-api.polymarket.com/v2/user-stats?user=$ADDRESS"
```

`data.trades` counts distinct markets and replaces the count from `/traded`.
Unknown users return `data: null`.

## Wallet PnL History

Track a wallet's cumulative profit and loss over time.

**TypeScript**

Call `fetchUserPnl()` on a `PublicClient` or `SecureClient`.

```ts theme={null}
const pnl = await client.fetchUserPnl({ user: address });

// pnl: UserPnlSeries
const points = pnl.points;
// points: UserPnlPoint[]
```

The default is one day sampled hourly. `points` are cumulative and ordered
oldest first. Nullable amounts mean unavailable, not zero.

**Python**

Call `get_user_pnl()` on an existing `AsyncPublicClient` or `AsyncSecureClient`.

```python theme={null}
pnl = await client.get_user_pnl(user=address, interval="1d", fidelity="1h")

# pnl: UserPnlSeries
points = pnl.points
# points: tuple[UserPnlPoint, ...]
```

`points` are cumulative and ordered oldest first. Do not sum them as period returns. Optional amounts remain `None` when unavailable. PnL is in USDC and volume is in shares.

**API**

Fetch the last day's cumulative PnL observations:

```bash theme={null}
curl "https://data-api.polymarket.com/v2/user-pnl?user=$ADDRESS&interval=1d&fidelity=1h"
```

`data.points` contains cumulative observations, oldest first. Do not sum
them as period returns or replace unavailable amounts with zero.

## Wallet Trading Volume

Measure a wallet's trading volume over a selected period.

**TypeScript**

Call `fetchUserVolume()` on a `PublicClient` or `SecureClient`.

```ts theme={null}
const volume = await client.fetchUserVolume({
  user: address,
  window: { start: new Date("2026-09-01T00:00:00Z") },
});

// volume: UserVolume
```

`volume.volume` is in shares, `volume.volumeUsdc` is in USD, and
`volume.tradeCount` counts fills. Window bounds expand to whole UTC days.

**Python**

Call `get_user_volume()` on an existing `AsyncPublicClient` or `AsyncSecureClient`.

```python theme={null}
from datetime import UTC, datetime

volume = await client.get_user_volume(
    user=address,
    start=datetime(2026, 9, 1, tzinfo=UTC),
)

# volume: UserVolume
```

`volume.volume` is in shares, `volume.volume_usdc` is in USDC, and `volume.trade_count` counts fills. Time bounds are floored to UTC days. Use `full_history=True` without `start` or `end` for full history.

**API**

Fetch trading volume from the start of September:

```bash theme={null}
curl "https://data-api.polymarket.com/v2/user-volume?user=$ADDRESS&start=1788220800"
```

`data.volume` is in shares, `data.volume_usdc` is in USD, and
`data.trade_count` counts fills. `start`/`end` use epoch seconds and expand
to whole UTC days.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.