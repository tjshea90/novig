> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Unit conversion

> Converting token amounts and odds formats.

## Tokens

Token amounts in the API use an integer representation with a `decimals` value to avoid rounding
issues. For example, 100 USDC is stored as `100 * 10^6 = 100000000`.

| Token | Address | Decimals |
| - | - | - |
| USDC | See [`GET /metadata/obv3`](/api-reference/get-metadata-obv3) (`activeAsset.baseToken`) | 6 |

To convert from a nominal amount (such as 100 USDC) to the integer amount used in the API:

`apiAmount = nominalAmount * 10^decimals`

To convert back:

`nominalAmount = apiAmount / 10^decimals`

where `decimals` comes from `activeAsset.decimals` on metadata (currently `6` for USDC).

***

## Odds

Odds are specified in an implied-odds format like `839135214364235000`. To convert to a readable
implied probability, divide by `10^20`. That example is `0.0839`, or `8.39%`.

To convert from implied probability to decimal odds, take the inverse. For example,
`0.0839` in decimal format is `1 / 0.0839 ≈ 11.917`.

***

## Bookmaker odds

`percentageOdds` is always from the **market maker's** perspective. Odds shown on sx.bet in the order
book are what the **taker** receives. See [Order book](/developers/order-book).

Suppose a maker is betting outcome one (`isMakerBettingOutcomeOne = true`) and receiving implied odds
of `704552840724436400000 / 10^20 = 0.704552841`. The taker is betting *outcome two* and receiving
implied odds of `1 - 0.704552841 = 0.295447159`. That displays under the second order book as about
`29.5%` implied, or `1 / 0.295447159 ≈ 3.3847` decimal.

For BigInt-safe helpers, time encodings, and more worked examples, see
[Unit conversions](/developers/unit-conversions).


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.