> ## Documentation Index
> Fetch the complete documentation index at: https://docs.novig.com/llms.txt
> Use this file to discover all available pages before exploring further.

# Executions

> Reconcile the parlays your maker filled, and check its open collateral.

Two routes show what your maker filled and what it still has at stake.
Both cover only the subaccount that signs the call.

| Route | Returns |
| - | - |
| [`GET /rfq/v3/executions`](https://docs.novig.com/api-reference/rfq/executions/list-your-executions) | The parlays your maker filled, newest first |
| [`GET /rfq/v3/collateral`](https://docs.novig.com/api-reference/rfq/executions/get-your-collateral) | Your collateral in parlays that haven't settled |

To watch every RFQ execution live, subscribe to `executions` on the [socket](https://docs.novig.com/api/rfq/socket.md#on-executions).

## Read a result

Each execution's `result` describes the user's side.
A `loss` means the user lost and you won the pot.
[Settlement](https://docs.novig.com/api/rfq/settlement.md) explains each result.

## Page through your history

Pass the `next` value of one page as `after` to get the page after it.
The last page has no `next`.

## Fill a gap in the live feed

On the socket, `offset` on each `executed` event goes up by one.
If it jumps, page `GET /rfq/v3/executions` from the newest until you reach an `rfqId` you already hold.

The listing holds only your own fills. A gap can also be fills by other makers, which no route lists.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.