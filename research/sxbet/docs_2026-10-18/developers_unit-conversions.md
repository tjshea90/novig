> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Unit conversions

> How odds and amounts are represented on the SX Bet API, and how to convert them.

Odds are 1e20-scaled integers and amounts are base unit integers.

## Quick reference

| Value | Wire format | To human-readable |
| - | - | - |
| Odds | integer scaled by **10^20**, as a **string** | `BigInt(raw) * 100n / 10n**18n` → percent, to 3 decimal places |
| USDC amounts | integer, **6 decimals**, as a **string** | `raw / 10**6` → dollars |

<Warning>
  **Use your library's BigInt or equivalent library to process these values**
</Warning>

## Odds

Odds are an **implied probability scaled by 10^20**. There is no separate decimal or American field
anywhere in the API. Those are display formats you compute yourself.

<CodeGroup>
  ```javascript JavaScript theme={null}
  const SCALE = 10n ** 20n;

  // Display: exact, no float in the path until the last step.
  const toPercent = (raw) => Number(BigInt(raw) * 100000n / SCALE) / 1000;   // 3 dp
  toPercent("40000000000000000000");   // → 40
  toPercent("100000000000000000");     // → 0.1

  // Build: from a percentage you chose.
  const fromPercent = (pct) => (BigInt(Math.round(pct * 1000)) * SCALE / 100000n).toString();
  fromPercent(40);      // → "40000000000000000000"
  fromPercent(0.125);   // → "125000000000000000"
  ```

  ```python Python theme={null}
  SCALE = 10 ** 20

  def to_percent(raw: str) -> float:
      return int(raw) * 100000 // SCALE / 1000        # 3 dp, integer maths first

  def from_percent(pct: float) -> str:
      return str(round(pct * 1000) * SCALE // 100000)

  to_percent("40000000000000000000")   # → 40.0
  from_percent(40)                     # → '40000000000000000000'
  ```
</CodeGroup>

### Maker frame and taker frame

```
takerOdds = 10^20 − makerOdds
```

Example:

| | Raw | Percent |
| - | - | - |
| Maker is offering, and receives | `40000000000000000000` | 40.000% |
| Taker is betting, and receives | `60000000000000000000` | 60.000% |

A maker resting at 40% offers the taker a 60% price.

## Token amounts

Amounts are integers in the token's smallest unit, as strings. Read the scale from
[`GET /metadata/obv3`](/api-reference/get-metadata-obv3):

```json theme={null}
{
  "activeAsset": {
    "symbol": "USDC",
    "baseToken": "0x1BC6326EA6aF2aB8E4b6Bc83418044B1923b2956",
    "escrowAddress": "0x0Db3aBA7de0BCb41FF99D35306f366cFFEEede38",
    "decimals": 6
  }
}
```

| Token | Address | Decimals |
| - | - | - |
| USDC | `activeAsset.baseToken` from [`GET /metadata/obv3`](/api-reference/get-metadata-obv3) | 6 |

```
baseUnits    = nominal × 10^decimals
nominal      = baseUnits ÷ 10^decimals
```

<CodeGroup>
  ```javascript JavaScript theme={null}
  const DECIMALS = 6;                              // read from metadata, do not hard-code
  const toBaseUnits = (usdc) => (BigInt(Math.round(usdc * 10 ** DECIMALS))).toString();
  const toUsdc = (base) => Number(BigInt(base)) / 10 ** DECIMALS;

  toBaseUnits(1);         // → "1000000"
  toUsdc("1000000");      // → 1
  ```

  ```python Python theme={null}
  DECIMALS = 6

  def to_base_units(usdc: float) -> str:
      return str(round(usdc * 10 ** DECIMALS))

  def to_usdc(base: str) -> float:
      return int(base) / 10 ** DECIMALS
  ```
</CodeGroup>

## Payouts

The payout relationship is the same on every grain: **return = stake ÷ your own implied odds.**

<CodeGroup>
  ```javascript JavaScript theme={null}
  const SCALE = 10n ** 20n;

  // Both arguments in the SAME frame: the staker's own odds.
  function payout(stakeBaseUnits, ownOdds) {
    const stake = BigInt(stakeBaseUnits);
    const ret = (stake * SCALE) / BigInt(ownOdds);      // integer division, matches the API
    return { stake, return: ret, profit: ret - stake };
  }

  payout("1000000", "40000000000000000000");
  // → { stake: 1000000n, return: 2500000n, profit: 1500000n }
  ```

  ```python Python theme={null}
  SCALE = 10 ** 20

  def payout(stake_base_units: str, own_odds: str) -> dict:
      stake = int(stake_base_units)
      ret = stake * SCALE // int(own_odds)
      return {"stake": stake, "return": ret, "profit": ret - stake}

  payout("1000000", "40000000000000000000")
  # → {'stake': 1000000, 'return': 2500000, 'profit': 1500000}

  def taker_frame(maker_odds: str) -> str:
      return str(SCALE - int(maker_odds))
  ```
</CodeGroup>

## Display formats

The API never sends decimal or American odds. Compute them yourselves:

| From implied `p` | To |
| - | - |
| `1 / p` | Decimal |
| `p ≥ 0.5` → `−(p / (1 − p)) × 100` | American, favourite |
| `p < 0.5` → `((1 − p) / p) × 100` | American, underdog |

| Implied | Decimal | American |
| - | - | - |
| 40.000% | 2.500 | +150 |
| 60.000% | 1.667 | −150 |
| 33.333% | 3.000 | +200 |
| 90.909% | 1.100 | −1000 |
| 9.091% | 11.000 | +1000 |


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.