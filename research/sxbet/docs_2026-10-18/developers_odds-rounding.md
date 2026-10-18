> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Odds rounding

> How to validate and round odds to the SX Bet odds ladder.

`percentageOdds` must land exactly on a step of the **odds ladder**, or the order is rejected. The
ladder exists to prevent diming — undercutting a resting quote by a meaninglessly small increment.

Read the step size from `oddsLadderStepSize` on
[`GET /metadata/obv3`](/api-reference/get-metadata-obv3).

## Units

| | |
| - | - |
| `percentageOdds` | An implied probability scaled by 10<sup>20</sup>, sent as a **string**. `100%` is `10^20`; `1%` is `10^18`; `0.001%` is `10^15`. |
| `oddsLadderStepSize` | A **number** in units of 10<sup>15</sup>. `125` means the step is `125 × 10^15 = 1.25 × 10^17` = **0.125%**. |

So with a step size of 125, the valid values are `0.000%, 0.125%, 0.250%, …`. `40.125%` is on the
ladder, and `40.100%` is not.

`oddsLadderStepSize` is returned as a number from 0 to 1000, which reads as a percentage like this:

| Metadata value | Step size |
| - | - |
| 10 | 0.010% |
| 25 | 0.025% |
| 125 | 0.125% |

## The rule

An order's `percentageOdds` is accepted when all of the following hold:

1. `0 < percentageOdds < 10^20` — strict on both ends.
2. `percentageOdds % (oddsLadderStepSize × 10^15) == 0`, **or** the value is one of two hardcoded
   exceptions.
3. The two exceptions are exactly **0.1%** (`10^17`) and exactly **99.9%** (`999 × 10^17`). They are
   always legal regardless of step size, so the extreme ends of the book stay quotable.

One `POST /orders-v3` per row:

| `percentageOdds` | Implied | Result |
| - | - | - |
| `40000000000000000000` | 40.000% | `200` accepted |
| `40125000000000000000` | 40.125% | `200` accepted |
| `40100000000000000000` | 40.100% | `400` `ODDS_NOT_ON_LADDER` |
| `100000000000000000` | 0.100% | `200` accepted — exception |
| `99900000000000000000` | 99.900% | `200` accepted — exception |
| `0` | 0.000% | `400` — bounds, not the ladder |
| `100000000000000000000` | 100.000% | `400` — bounds, not the ladder |

## Checking and rounding

Read the step size once at startup, then keep the arithmetic in integers.

<CodeGroup>
  ```javascript JavaScript theme={null}
  const ODDS_PRECISION = 10n ** 20n;
  const stepSize = 125n;                 // GET /metadata/obv3 → oddsLadderStepSize
  const STEP = stepSize * 10n ** 15n;    // 1.25e17 = 0.125%

  const EXTRA = new Set([10n ** 17n, 999n * 10n ** 17n]); // 0.1% and 99.9%

  const onLadder = (odds) => {
    const o = BigInt(odds);
    if (o <= 0n || o >= ODDS_PRECISION) return false;
    if (EXTRA.has(o)) return true;
    return o % STEP === 0n;
  };

  const roundDown = (odds) => (BigInt(odds) / STEP) * STEP;
  const roundUp = (odds) => {
    const o = BigInt(odds);
    return o % STEP === 0n ? o : (o / STEP + 1n) * STEP;
  };
  ```

  ```python Python theme={null}
  ODDS_PRECISION = 10 ** 20
  step_size = 125                      # GET /metadata/obv3 -> oddsLadderStepSize
  STEP = step_size * 10 ** 15          # 1.25e17 = 0.125%
  EXTRA = {10 ** 17, 999 * 10 ** 17}   # 0.1% and 99.9%

  def on_ladder(odds: int) -> bool:
      if odds <= 0 or odds >= ODDS_PRECISION:
          return False
      if odds in EXTRA:
          return True
      return odds % STEP == 0

  def round_down(odds: int) -> int:
      return (odds // STEP) * STEP

  def round_up(odds: int) -> int:
      return odds if odds % STEP == 0 else ((odds // STEP) + 1) * STEP
  ```
</CodeGroup>

Real output from both, run against the values in the table above:

```
  40000000000000000000   40.000%  on_ladder=True
  40125000000000000000   40.125%  on_ladder=True
  40100000000000000000   40.100%  on_ladder=False
    100000000000000000    0.100%  on_ladder=True
  99900000000000000000   99.900%  on_ladder=True
                     0    0.000%  on_ladder=False
 100000000000000000000  100.000%  on_ladder=False

rounding 40.100%:
  down -> 40000000000000000000 (40.000%)
  up   -> 40125000000000000000 (40.125%)
```

## Example of turning a probability into a valid price

This is the full path from a model output to a submittable field. Multiply into integer space **first**,
apply your margin in integer space, then snap.

<CodeGroup>
  ```javascript JavaScript theme={null}
  // Your model says 40.3%, and you want to rest on it.
  const modelProbability = 0.403;

  // 1. Into 1e20 space, via an integer basis — never Number * 1e20 directly.
  const basisPoints = BigInt(Math.round(modelProbability * 10_000));   // 4030
  const raw = (basisPoints * ODDS_PRECISION) / 10_000n;                // 40300000000000000000n

  // 2. Snap. Resting order → round down.
  const percentageOdds = roundDown(raw);                               // 40250000000000000000n

  // 3. Submit as a string, and re-check before you do.
  if (!onLadder(percentageOdds)) throw new Error("off ladder");
  const field = percentageOdds.toString();                             // "40250000000000000000"
  ```

  ```python Python theme={null}
  # Your model says 40.3%, and you want to rest on it.
  model_probability = 0.403

  # 1. Into 1e20 space via an integer basis.
  basis_points = round(model_probability * 10_000)          # 4030
  raw = basis_points * ODDS_PRECISION // 10_000             # 40300000000000000000

  # 2. Snap. Resting order -> round down.
  percentage_odds = round_down(raw)                         # 40250000000000000000

  # 3. Submit as a string, and re-check before you do.
  assert on_ladder(percentage_odds)
  field = str(percentage_odds)                             # "40250000000000000000"
  ```
</CodeGroup>

Both produce `40250000000000000000` — 40.250%, one step below the model's 40.300%.

## Do not hardcode the step size

`oddsLadderStepSize` is **125** today and can change in the future.

```bash theme={null}
curl -s https://api.sx.bet/metadata/obv3 | jq '.data.oddsLadderStepSize'
# 125
```

Read it at startup alongside the rest of the bootstrap document, and derive `STEP` from it. See
[Exchange metadata](/api-reference/get-metadata-obv3) for the other values.

## Related

<CardGroup cols={2}>
  <Card title="Exchange metadata" icon="gear" href="/api-reference/get-metadata-obv3">
    Where `oddsLadderStepSize` comes from.
  </Card>

  <Card title="Unit conversions" icon="calculator" href="/developers/unit-conversions">
    Implied/decimal/American odds, the 1e20 scale, base units, and time formats in one place.
  </Card>

  <Card title="Create orders" icon="paper-plane" href="/api-reference/post-orders-v3" />
</CardGroup>


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.