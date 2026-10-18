> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Fees

> The five per-account fee rates on SX Bet v3, how each is charged, and how to read your own rates.

Fees on v3 are set **per account**, not globally, so your rates may differ from another account's.
There are exactly five, all returned by [`GET /user/fees-v3`](/api-reference/get-user-fees-v3). Each
is a decimal-fraction string — `"0.05"` is 5%, `"0.005"` is 0.5% — and `null` means the rate is unset
and nothing is charged.

## The five rates

| Rate | Applies to |
| - | - |
| `makerPayoutFee` | Profit on a winning single where you were the maker |
| `takerPayoutFee` | Profit on a winning single where you were the taker |
| `makerParlayPayoutFee` | Profit on a winning parlay where you were the maker |
| `takerParlayPayoutFee` | Profit on a winning parlay where you were the taker |
| `refundFee` | A capital-efficiency refund, charged at fill time |

## How payout fees are charged

The four payout fees are charged on **profit** (`totalReturn − totalStake`), at **settlement**, and
only on a bet that **won**. A loss and a void are never charged.

Which of each maker/taker pair applies comes from your side of the fill: the resting order is the
maker, the incoming order that filled it is the taker. What you were actually charged lands in
`settlement.settleFeeAmount` on the bet — see [`GET /trades-v3`](/api-reference/get-trades-v3).

## The refund fee

`refundFee` is charged at **fill time** instead of settlement, and applies to the whole
capital-efficiency refund rather than to profit. It has no maker or taker variant. The refund you
receive, `ceRefundAmount`, is already net of it, and the fee itself is `ceRefundFeeAmount`. See
[Capital efficiency](/developers/capital-efficiency).

## Finding your rates

```js theme={null}
const { data: fees } = await get("/user/fees-v3");

// Each rate is a decimal-fraction string, or null when unset.
const takerRate = fees.takerPayoutFee ? Number(fees.takerPayoutFee) : 0;
```

A `null` rate means nothing is charged for that category — treat it as `0`.

## Related

<CardGroup cols={2}>
  <Card title="Get your fees" icon="percent" href="/api-reference/get-user-fees-v3">
    The endpoint, every field, and what `null` means.
  </Card>

  <Card title="Capital efficiency" icon="scale-balanced" href="/developers/capital-efficiency">
    Where the refund fee applies, and how refunds are tracked.
  </Card>

  <Card title="Reading balances" icon="wallet" href="/developers/balances-and-ledger">
    What you can spend, and what settlement pays out.
  </Card>
</CardGroup>


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.