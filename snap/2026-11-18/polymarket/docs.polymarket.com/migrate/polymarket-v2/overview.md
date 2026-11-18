> ## Documentation Index
> Fetch the complete documentation index at: https://docs.polymarket.com/llms.txt
> Use this file to discover all available pages before exploring further.

# Migrate to Polymarket Protocol V2

> Add V2 markets to an existing SDK, API, or smart-contract integration.

> [!TIP]
>
> **App and website users:** No technical migration is required. Complete any
> approval prompts shown in the app.

Polymarket Protocol V2 introduces new position tokens and contracts for
trading, position operations, and resolution. Follow the guide for your
integration while retaining CTF support. Existing CTF holdings are not converted.

## Rollout Timeline

New markets begin trading on Polymarket Protocol V2 in phases. Existing CTF
markets keep their current contracts for trading, settlement, and redemption.

All dates are in **2026**, and times are **Eastern Time (ET)**. Prepare your V2
integration before market creation: some markets are created 24 hours before
the planned trading dates below.

| Phase and Trading Start | Markets |
| - | - |
| **Phase 1** Monday, November 23, at 8:30 AM | BNB, DOGE, HYPE, SOL, XRP, and ZEC Up/Down, each at 5-minute and 15-minute intervals. Also NYC highest temperature, Warsaw highest temperature, and Munich lowest temperature. |
| **Phase 2** Tuesday, November 24 | BTC and ETH Up/Down at 5-minute, 15-minute, and 4-hour intervals; other crypto, remaining weather, commodities, finance, and minor sports. |
| **Phase 3** Tuesday, December 1 | Soccer, American football (NFL and college football), basketball (including NBA and college basketball), tennis, esports, and social count. |

Later phases depend on successful trading and settlement, correct reward
scoring, and sufficient liquidity in the preceding phase. No cutovers are
scheduled for Thanksgiving on November 26. Thanksgiving week's NFL and college
football games remain on CTF.

## What Changes

* **Market identifiers:** Integrations select V2 position IDs or CTF token IDs using the market's version.
* **Trading:** V2 orders use ExchangeV3, signing domain version `"3"`, and new approval targets. CLOB balance and allowance requests for V2 positions use `CONDITIONAL-V2`.
* **Position operations:** V2 balances live in PositionManager. V2 split, merge, and redemption use Router, with updated calls and payout handling.

## Migration Guides

How does your integration connect to Polymarket?

Select all that apply.

For integrations already using pUSD and the CTFExchangeV2 order format,
collateral, wallets, CLOB credentials, and endpoints stay the same.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.