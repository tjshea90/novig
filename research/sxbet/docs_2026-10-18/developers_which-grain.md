> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Bet grains

> How to choose between bets, fills, and positions when querying your trade data.

## The three grains

| | **Positions** | **Bets** | **Fills** |
| - | - | - | - |
| Route | [`GET /positions-v3`](/api-reference/get-positions-v3) | [`GET /trades-v3`](/api-reference/get-trades-v3) | [`GET /fills-v3`](/api-reference/get-fills-v3) |
| A row is | one **market** | one **bet** you placed | one **match** against one counterparty |
| Answers | *What am I exposed to?* | *What did I bet?* | *What did I get filled at?* |
| Key | `marketHash` | `tradeId` | `matchId` |
| Amount field | `totalStake` (summed) | `totalStake` | `fillAmount` |
| Price field | `odds.outcomeOne/Two` (blended) | `weightedAverageOdds` (blended) | `fillOdds` (**actual**) |

They nest strictly: one position contains N bets, one bet contains N fills.

## Positions

One row per market, netted across all your bets in it. The grain
for a portfolio view.

```js theme={null}
const qs = new URLSearchParams({ status: "MATCHED,LOCKED", perPage: "50" });
const { data } = await (await fetch(`https://api.sx.bet/positions-v3?${qs}`, {
  headers: { "x-sx-api-key": process.env.SX_API_KEY },
})).json();

for (const p of data.positions) {
  const name = p.market?.outcomeOneName ?? p.marketHash.slice(0, 10) + "…";
  console.log(
    `${name}  staked ${(Number(p.totalStake) / 1e6).toFixed(2)}  ` +
    `win +${(Number(p.maxWin) / 1e6).toFixed(2)}  lose ${(Number(p.maxLoss) / 1e6).toFixed(2)}`
  );
}
```

## Bets

One row per bet you placed. All the fills for a bet are blended into a single row.

```js theme={null}
const res = await fetch("https://api.sx.bet/trades-v3?perPage=25", {
  headers: { "x-sx-api-key": process.env.SX_API_KEY },
});
const { data } = await res.json();

for (const bet of data.trades) {
  const stake  = Number(bet.totalStake) / 1e6;                 // USDC, 6 dp
  const profit = (Number(bet.totalReturn) - Number(bet.totalStake)) / 1e6;
  const odds   = Number(bet.weightedAverageOdds) / 1e18;       // → percent
  console.log(`${bet.betTime}  ${bet.status}  ${stake.toFixed(2)} USDC @ ${odds.toFixed(3)}% → +${profit.toFixed(2)} if it wins`);
}
```

## Fills

One row per match against one counterparty. The finest grain, and the only one carrying the price you
actually got and the order it came from. Narrow with `tradeId` for one bet, or `orderId` for the
fills that came from one of your orders.

```js theme={null}
const { data } = await (await fetch(
  `https://api.sx.bet/fills-v3?tradeId=${bet.tradeId}`,
  { headers: { "x-sx-api-key": process.env.SX_API_KEY } }
)).json();

for (const f of data.fills) {
  console.log(
    `${(Number(f.fillAmount) / 1e6).toFixed(6)} USDC ` +
    `@ ${(Number(f.fillOdds) / 1e18).toFixed(3)}% → ${(Number(f.returnAmount) / 1e6).toFixed(6)}`
  );
}
```

## Realtime equivalents

Each grain has a channel, and they carry the same shapes as the REST rows:

| Grain | Channel |
| - | - |
| Bets | [`account:trades_v3_#{address}`](/api-reference/channel-trades-v3) |
| Fills | [`account:fills_v3_#{address}`](/api-reference/channel-fills-v3) |
| Positions | **None** — positions are a derived roll-up, so re-call the route |
| Public tape | [`recent_trades_v3`](/api-reference/channel-recent-trades-v3) |

## Related

<CardGroup cols={2}>
  <Card title="Get your bets" icon="receipt" href="/api-reference/get-trades-v3">
    The bet row in full and its filters.
  </Card>

  <Card title="Get your fills" icon="list-check" href="/api-reference/get-fills-v3">
    The fill row, its four ids, and the sort order.
  </Card>

  <Card title="Get your positions" icon="chart-pie" href="/api-reference/get-positions-v3">
    Every field, the required-status rule, and realised PnL.
  </Card>

  <Card title="Tracking your orders" icon="list" href="/developers/my-orders">
    Keeping a live view of what is resting on the book.
  </Card>
</CardGroup>


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.