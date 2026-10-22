> ## Documentation Index
> Fetch the complete documentation index at: https://docs.novig.com/llms.txt
> Use this file to discover all available pages before exploring further.

# Order lifecycle

> The five order statuses, and the events that move an order between them.

Every status change arrives as an event on the [private stream](/api/streaming/private.md).
The status you read over REST is a snapshot of the same state.

Order states: PENDING leads to OPEN, FILLED, CANCELED, or REJECTED. OPEN loops on a partial fill and leads to FILLED or CANCELED.

Every order starts `PENDING`. A resting order is `OPEN`. The right column holds the three final states.

| Event | From | To |
| - | - | - |
| `open` | `PENDING` | `OPEN` |
| `fill`, `remaining > 0` | `OPEN` | `OPEN` |
| `fill`, `remaining = 0` | `PENDING` `OPEN` | `FILLED` |
| `cancel` | `PENDING` `OPEN` | `CANCELED` |
| `reject` | `PENDING` | `REJECTED` |

* `open` means the order is resting. Any fills that matched when it entered the book arrive first.
* A partial `fill` keeps the order `OPEN` and shrinks `remaining`.
* `cancel` means you canceled the order, it expired, or we voided it. An `IOC` order that fills in part cancels the rest.
* `reject` means we refused the order after accepting it. An `IOC`, `FOK`, or `PO` order that fills nothing is rejected.

| Status | Means | Terminal |
| - | - | - |
| `PENDING` | Accepted, waiting on the exchange | |
| `OPEN` | Resting on the book | |
| `FILLED` | Completely filled | ✓ |
| `CANCELED` | Left the book unfilled or partly filled | ✓ |
| `REJECTED` | Refused after acceptance | ✓ |

> [!NOTE]
>
> There's no `PARTIALLY_FILLED` status. Track `remaining` instead.

## Track order state

**1. Subscribe first**

Subscribe to orders on the private stream before you place an order.

**2. Take a snapshot**

Take a snapshot of your open orders and its sequence number, `seq`.

**3. Apply events in order**

Apply events in `seq` order. If you see a gap, take a new snapshot.

**4. Key on orderId**

Key your state on `orderId`.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.