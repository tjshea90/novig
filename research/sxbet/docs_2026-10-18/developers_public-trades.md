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

<CardGroup cols={2}>
  <Card title="Get public trades" icon="chart-simple" href="/api-reference/get-trades-v3-public">
    Core trade fields plus optional market metadata, and what the API holds back.
  </Card>

  <Card title="Recent trades channel" icon="bolt" href="/api-reference/channel-recent-trades-v3">
    The same data, in realtime.
  </Card>

  <Card title="The order book" icon="book-open" href="/developers/order-book">
    What people will trade at, versus what they already traded at.
  </Card>

  <Card title="Reading your bets" icon="receipt" href="/developers/which-grain#bets">
    The authenticated view — full bet grain, including identity and settlement.
  </Card>
</CardGroup>


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.