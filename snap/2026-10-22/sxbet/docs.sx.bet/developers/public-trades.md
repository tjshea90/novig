> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Public trades

> Query the public trade feed from the SX Bet API.

The public tape is bets other people placed, anonymized.

Two ways to fetch:

| | |
| - | - |
| [`GET /trades-v3/public`](/api-reference/get-trades-v3-public) | Paginated history, filtered by market |
| [`recent_trades_v3:global`](/api-reference/channel-recent-trades-v3) | Realtime |

## Read the tape for a market

```js theme={null}
const qs = new URLSearchParams({ marketHash, perPage: "50" });
const { data } = await (await fetch(
  `https://api.sx.bet/trades-v3/public?${qs}`
)).json();

for (const t of data.trades) {
  const stake = Number(t.totalStake) / 1e6;
  const odds  = Number(t.weightedAverageOdds) / 1e20;
  const side  = t.outcomeLabel ?? (t.isBettingOutcomeOne ? "one" : "two");
  console.log(`${t.betTime}  ${stake.toFixed(2)} USDC on ${side} @ ${odds.toFixed(3)}`);
}
```

## Prefer the channel for anything live

```js theme={null}
centrifuge.newSubscription("recent_trades_v3:global")
  .on("publication", ({ data }) => onTrade(data.trade))
  .subscribe();
```

Seed from REST on connect, then apply channel messages.

## Related

> **[Get public trades](/api-reference/get-trades-v3-public.md)**
>
> Core trade fields plus optional market metadata, and what the API holds back.

> **[Recent trades channel](/api-reference/channel-recent-trades-v3.md)**
>
> The same data, in realtime.

> **[The order book](/developers/order-book.md)**
>
> What people will trade at, versus what they already traded at.

> **[Reading your bets](/developers/which-grain.md#bets)**
>
> The authenticated view — full bet grain, including identity and settlement.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.