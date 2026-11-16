> ## Documentation Index
> Fetch the complete documentation index at: https://docs.novig.com/llms.txt
> Use this file to discover all available pages before exploring further.

# Confirm rate

> How many last looks you must confirm, and what happens when you fall short.

When your quote wins and the user accepts, we ask you to confirm the trade.
That 1-second step is the last look. The [overview](https://docs.novig.com/api/rfq/overview.md) shows where it fits in a round.

You must confirm a set share of your recent last looks.
If you fall below it, we lock you out of quoting for just 5 minutes. The lock lifts on its own, and you can quote again right after.

## What you must confirm

Every maker must confirm 99% of their last looks over the past 24 hours.
We count from your first last look, so quote only prices you're sure you'll confirm.

We're building a way for makers to earn a looser bar. Until then, this bar applies to every maker.

## How we count

| How the last look ended | Counts toward your rate |
| - | - |
| You confirmed in time | Yes, as a confirm |
| You rejected | Yes, against you |
| You didn't answer within 1 second | Yes, against you |
| We failed on our side | No |

## While you're locked out

We refuse every `quote`, `decline`, and `confirm` you send with `MAKER_LOCKED`.
The lock ends on its own after 5 minutes. You don't need to do anything to lift it.

When it ends, we start counting from zero.
Last looks from before the lock never count again, so one bad stretch can't lock you twice.

## Check where you stand

[`GET /rfq/v3/maker`](https://docs.novig.com/api-reference/rfq/maker/get-your-maker) returns your current count against the bar, and any live lock.

While a confirm-rate lock is live, `counted` is `0`, because counting restarts when it ends.

> [!NOTE]
>
> We can also lock a maker by hand. That lock can last longer, and its `source` is `admin`. Contact us if you see one.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.