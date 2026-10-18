> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Fixture statuses

> Reference of fixture statuses on SX Bet.

A fixture's `status` is a number, returned by
[`GET /fixture/active`](/api-reference/get-fixture-active) and
[`GET /fixture/status`](/api-reference/get-fixture-status), and published on
[`fixtures:global`](/api-reference/channel-fixtures).

## The nine statuses

| ID | Name | Description |
| - | - | - |
| 1 | Not started yet | The event has not started yet |
| 2 | In progress | The event is live |
| 3 | Finished | The event is finished |
| 4 | Cancelled | The event has been cancelled |
| 5 | Postponed | Postponed and will be played later. If no new start time arrives within 48 hours the event is cancelled; if one does, it returns to **Not started yet** with the new time |
| 6 | Interrupted | Temporarily interrupted — a rain delay, for example. Coverage resumes under the same event id |
| 7 | Abandoned | A final status. The event will not resume |
| 8 | Coverage lost | Coverage for this event has been lost |
| 9 | About to start | Not started, but starting soon. Shown up to 30 minutes before the start |

## Which statuses sweep your resting orders

If a fixture transitions into an ineligible trading state, all orders are cancelled.

| Transition into | Sweeps your orders | `inactiveReason` you receive |
| - | - | - |
| **2 — In progress** | **Yes** | `EVENT_LIFECYCLE` |
| **3 — Finished** | **Yes** | `EVENT_LIFECYCLE` |
| **4 — Cancelled** | **Yes** | `EVENT_LIFECYCLE` |
| 1, 5, 6, 7, 8, 9 | No | — |

<Warning>
  **The sweep is not a substitute for your own risk controls.**
</Warning>


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.