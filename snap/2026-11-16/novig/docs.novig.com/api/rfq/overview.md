> ## Documentation Index
> Fetch the complete documentation index at: https://docs.novig.com/llms.txt
> Use this file to discover all available pages before exploring further.

# Overview

> How makers price Novig parlays through RFQs.

A request for quote (RFQ) is how we get a price for a parlay.
A parlay is one order on two or more outcomes, called legs, that must all win.

When a user asks for a parlay, we send an RFQ to every maker.
A maker is a subaccount that prices RFQs.
We pick the best quote and ask its maker to confirm before the trade executes.

> [!NOTE]
>
> We send RFQs only for parlays of two or more legs. [Eligibility](https://docs.novig.com/api/rfq/eligibility.md) lists which legs can combine.

## How a round works

**1. A user asks for a parlay**

We check the legs, create an `rfqId`, and open a 3-second auction.

**2. We send the RFQ to every maker**

`rfq_created` arrives on the `rfq` channel of your [socket](https://docs.novig.com/api/rfq/socket.md).

**3. You answer with a price**

Send a `price` and a `maxWager`, the largest stake you'll take. You can also decline.

**4. We pick the winner**

The lowest price wins, because it pays the user the most. A tie goes to the larger `maxWager`.

**5. The winner confirms**

When the user accepts, `quote_accepted` asks you to confirm their stake. You have 1 second to answer, called the last look.

**6. The parlay settles**

The parlay settles as one position when its legs grade. [Settlement](https://docs.novig.com/api/rfq/settlement.md) explains the payout.

If you confirm in time, the trade executes.
If you reject or miss the last look, it doesn't, and it counts against your [confirm rate](https://docs.novig.com/api/rfq/confirm-rate.md).

> [!WARNING]
>
> During the beta, you must confirm 99% of your last looks, or we lock you out of quoting for 5 minutes. Quote only prices you're sure you'll confirm.

We pass over a quote in these cases:

* Its `maxWager` is below the user's minimum stake, or below \$10, whichever is higher.
* Your subaccount's balance can't cover your collateral for the full `maxWager`.
* You rejected or missed a last look on the same legs in the past 2 minutes, and no game in the parlay has started.
* The RFQ is your own.

Your collateral is the money you put up against the user's stake.
It comes from the balance of your maker's subaccount.

## Get started

**1. Open a subaccount**

Each maker is one subaccount. [Open one](https://docs.novig.com/api/subaccounts/manage.md) and [fund it](https://docs.novig.com/api/subaccounts/funding.md).

**2. Register it as a maker**

Call `POST /rfq/v3/maker` with the subaccount's `trading` key. [Makers](https://docs.novig.com/api/rfq/makers.md) shows the call.

**3. Open the socket**

Connect to `GET /rfq/v3/ws` and subscribe to `rfq` and `quotes`. [Socket](https://docs.novig.com/api/rfq/socket.md) shows every message.

**4. Quote and confirm**

Answer each `rfq_created` before it expires, and confirm each `quote_accepted` within 1 second.

RFQ routes live under `/rfq/v3` on the same host as the rest of the API.
You sign them like any other route. [Signing](https://docs.novig.com/api/signing.md) shows how.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.