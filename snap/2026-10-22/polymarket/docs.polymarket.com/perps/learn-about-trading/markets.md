> ## Documentation Index
> Fetch the complete documentation index at: https://docs.polymarket.com/llms.txt
> Use this file to discover all available pages before exploring further.

# Markets

> Listed perpetual markets and trading parameters

Polymarket Perps markets track underlying assets across indices, commodities,
crypto assets, and equities. Each market has its own trading parameters.

## Instruments

Perps markets are represented by instruments, which are the listed perpetual
contracts available to trade.

Each instrument also includes details that shape how it trades:

* Underlying asset
* Collateral and quote asset
* Price and quantity precision
* Tick size
* Minimum order size
* Risk tiers and leverage caps
* Mark, index, and funding configuration

> [!NOTE]
>
> Market parameters can change as markets evolve. Builders should read live
> instrument details from [Market Data](/perps/market-data.md#fetch-instruments)
> before submitting orders.

## Price Feeds

Each market tracks an underlying market through external price feeds. Those
feeds drive the [Index Price](/perps/learn-about-trading/index-price.md), and the Index Price helps anchor the [Mark Price](/perps/learn-about-trading/mark-price.md).


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.