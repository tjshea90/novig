> ## Documentation Index
> Fetch the complete documentation index at: https://docs.polymarket.com/llms.txt
> Use this file to discover all available pages before exploring further.

# Institutional Overview

> Settle trades matched on your platform as positions in Polymarket's prediction markets.

Polymarket's institutional integration is for trading platforms that match
counterparties themselves, such as request for quote (RFQ) venues. Your
platform runs price discovery, and Polymarket settles each agreed trade as
positions in its prediction markets. It is a separate integration from
building on the order book: to place orders on the order book, start with
[Trading](https://docs.polymarket.com/trading/overview.md).

## Request for Quote (RFQ) Trades

On the order book, prices are discovered by matching orders as they arrive.
With an RFQ trade, price discovery happens on your platform instead: two
counterparties agree on a price and size there, and your platform submits the
agreed trade to Polymarket. Polymarket executes it on Polygon as one matched
pair of signed orders, which never rest on the order book.

Each counterparty authorizes only its own side, with a session key for its
Polymarket Deposit Wallet. Your platform identifies itself with a partner API
key, which does not authorize moving funds.

### Same Markets and Positions as the Order Book

RFQ trades settle in the same prediction markets as the order book:

* **Markets:** an RFQ trade runs on a Polymarket market found through the
  public Gamma API, and its price is checked against that market's order book
  midpoint.
* **Positions:** each counterparty receives standard Polymarket positions. They
  can be held, sold on the order book, or redeemed when the market resolves.
* **Liquidity:** your platform can use RFQ trades and the order book together
  to find the best price and liquidity for a market.

## Get Access

Access is by invitation. To request access, email
[institutional@polymarket.com](mailto:institutional@polymarket.com). Polymarket
issues your API key during onboarding, together with access to a testing
environment. For questions about RFQ trade fees and guardrails, email
[blocks@polymarket.com](mailto:blocks@polymarket.com).

## Next Steps

> [card] [RFQ Trades Guide](https://docs.polymarket.com/partners/rfq-trades.md)
>
> Find a market, then validate, sign, submit, and track an RFQ trade.

> [card] [API Reference](https://docs.polymarket.com/api-reference/rfq-trades/overview.md)
>
> Review the base URLs, authentication, and every request.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.