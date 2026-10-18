> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Reading balances

> Understand your balances and ledger on SX Bet.

[`GET /user/balance-v3`](/api-reference/get-user-balance-v3) describes the balances of your account. The four amounts, and what each is for:

| Field | Meaning |
| - | - |
| `availableAmount` | Available to bet |
| `pendingAvailableAmount` | In-flight change to available. **Signed** |
| `escrowedAmount` | Locked behind your open bets — your "at risk" |
| `pendingEscrowAmount` | In-flight change to escrow. **Signed** |

Every amount is a string in base units. USDC has 6 decimals, so `"1028000014"` is
**1028.000014 USDC**.

## Reading a balance

```js theme={null}
const { data: meta } = await get("/metadata/obv3");
const { data } = await get("/user/balance-v3");

const row = data.balances.find(b => b.tokenAddress === meta.activeAsset.baseToken);
const spendable = row
  ? BigInt(row.availableAmount) + BigInt(row.pendingAvailableAmount)
  : 0n;
```

## Related

<CardGroup cols={2}>
  <Card title="Get your balance" icon="wallet" href="/api-reference/get-user-balance-v3">
    Every field, and what an empty array means.
  </Card>

  <Card title="Funding" icon="arrow-right-to-bracket" href="/developers/funding">
    Getting money in, and confirming it arrived.
  </Card>
</CardGroup>


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.