> ## Documentation Index
> Fetch the complete documentation index at: https://docs.novig.com/llms.txt
> Use this file to discover all available pages before exploring further.

# Makers

> Register a subaccount as an RFQ maker, and check its standing.

A maker is one subaccount that prices RFQs.
It quotes from that subaccount's balance, and you call every RFQ route with that subaccount's `trading` key.

A `management` key has no maker, so RFQ routes refuse it.

## Register a maker

[`POST /rfq/v3/maker`](https://docs.novig.com/api-reference/rfq/maker/register-a-maker) makes the key's subaccount a maker and returns its `makerId`.

Calling it again is safe. It returns the same `makerId`.

A new maker can take up to a minute to reach every server.
Until then, a request or a socket upgrade can still get `MAKER_NOT_FOUND`. Retry it after a few seconds.

To run more than one maker, open more subaccounts and register each one.

## Your account must stay eligible

We check the account that owns your maker on `POST /rfq/v3/maker`, and on every `quote`, `decline`, and `confirm` you send on the [socket](https://docs.novig.com/api/rfq/socket.md).

| Your account | Status | `code` |
| - | - | - |
| Its KYC or KYB is no longer verified | 403 | `KYC_REQUIRED` |
| It has a self-exclusion | 423 | `SELF_EXCLUDED` |

On the socket, we refuse the message with the same `code`.

KYC is our identity check for a person, and KYB is the same check for a business.

Reads still answer: `GET /rfq/v3/maker`, `GET /rfq/v3/collateral`, and `GET /rfq/v3/executions`.

Finish verification to clear `KYC_REQUIRED`. `SELF_EXCLUDED` doesn't clear on its own, so contact support.

## Check your maker

[`GET /rfq/v3/maker`](https://docs.novig.com/api-reference/rfq/maker/get-your-maker) returns your tier, your confirm rate against the bar, and any lock.

Every maker is `basic` during the beta. [Confirm rate](https://docs.novig.com/api/rfq/confirm-rate.md) explains the bar and the lock.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.