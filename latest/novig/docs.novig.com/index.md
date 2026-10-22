> ## Documentation Index
> Fetch the complete documentation index at: https://docs.novig.com/llms.txt
> Use this file to discover all available pages before exploring further.

# Overview

> Trade sports contracts on the Novig exchange through our API.

# Just Sports

Place orders with signed REST requests. Watch the order book and your fills on one websocket.

[Quickstart](/api/quickstart.md)
[OpenAPI 3.1](/api-reference/spec-files/openapi-v3.json)

## Start

***

Each subaccount has its own balance and its own trading key.

***

You create your management key in your Novig profile. It opens and funds subaccounts but can't place orders.

***

Sign every request with Ed25519 or P-256, and test your code against our 30 sample signatures.

***

`POST /v3/echo` returns your body with a `200` when your signature is correct.

***

Your management key opens a subaccount, registers its trading key, and funds it.

***

Find a market in the [catalog](/api/catalog), place an [order](/api/execution/orders), and follow it on the [private stream](/api/streaming/private.md).

Hostnames for each environment are on [Environments](/api/environments.md). The full API is in the [OpenAPI document](/api-reference/spec-files/openapi-v3.json).


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.