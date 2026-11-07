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