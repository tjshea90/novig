> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Create orders

> Submit a new order to the SX Bet orderbook.

Making and taking are the same request
with a different [`timeInForce`](/developers/time-in-force) — there is no separate fill endpoint.
[EIP-712 order signing](/api-reference/eip712-order-signing.md) covers the eight signed fields and the
domain.

> [!WARNING]
>
> **A deployed proxy wallet is required before any order is accepted**

> [!NOTE]
>
> **This endpoint is asynchronous by default.** A success response means the order was *submitted* to
> the matching engine, not that it rested or matched. Each order comes back as `SUBMITTED` — the terminal
> outcome (rested as `ACTIVE`, matched, or gone) arrives later on
> [`account:orders_v3_#{address}`](/api-reference/channel-orders-v3). Subscribe to that channel before
> you submit and treat it as the source of truth.

> [!NOTE]
>
> **Optional synchronous mode.** Set `waitForOutcome: true` to have the call wait up to `maxWaitTime` and return each accepted order's terminal matching `outcome` inline.
>
> `outcome` is the *matching* result, not on-chain finality. The funds are escrowed a moment later, when it reaches `LOCKED` on
> [`account:trades_v3_#{address}`](/api-reference/channel-trades-v3). See [which status to act on](/developers/bet-lifecycle.md#which-status-to-act-on).

> [!NOTE]
>
> `expiry` is required and must be a unix epoch timestamp in **seconds** (not milliseconds) that is in
> the future. Also an `expiry` that falls inside the market's betting delay plus two seconds is rejected.

> [!NOTE]
>
> **Posting an order does not lock capital**, regardless of `timeInForce`. Funds are escrowed only when
> the order matches. See [Risk limits](/developers/risk-limits.md).


## OpenAPI

````yaml POST /orders-v3
openapi: 3.0.1
info:
  title: SX Bet API
  version: 1.0.0
  description: >-
    REST API for the SX Bet decentralized sports betting exchange. Retrieve
    sports data, markets, and orderbook information. Post, cancel, and fill
    orders with signed payloads.


    Base URLs:

    - **Mainnet**: `https://api.sx.bet`

    - **Testnet**: `https://api.toronto.sx.bet`
servers:
  - url: https://api.sx.bet
    description: Mainnet (SX Network, chainId 4162)
  - url: https://api.toronto.sx.bet
    description: Testnet (Toronto, chainId 79479957)
security: []
tags:
  - name: Connection
    description: Server metadata and heartbeat management
  - name: Sports Data
    description: Sports, leagues, teams, fixtures, and live scores
  - name: Markets
    description: Active, specific, and popular betting markets
  - name: Trades
    description: Matched trades and portfolio history
  - name: Orders
    description: Orderbook queries, posting, cancelling, and filling orders
paths:
  /orders-v3:
    post:
      tags:
        - Orders
      summary: Create orders
      description: >-
        The only way to create an order. There is no separate fill endpoint —
        quoting and taking are the same request with a different `timeInForce`.
        Requires a deployed proxy.
      operationId: createOrdersV3
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              required:
                - orders
              properties:
                orders:
                  type: array
                  items:
                    $ref: '#/components/schemas/CreateOrderItem'
                  description: >-
                    1 to `limits.maxCreateOrders` signed orders — 10 today. An
                    empty array is a 400. All orders in a batch must share one
                    `maker`.
                waitForOutcome:
                  type: boolean
                  default: false
                  description: >-
                    When true, wait for each order's matching outcome and return
                    it inline as `outcome` on every accepted (`SUBMITTED`)
                    result. Defaults to false. See [Async
                    operations](/developers/async-operations).
                maxWaitTime:
                  type: integer
                  description: >-
                    Max time in ms to wait for outcomes when `waitForOutcome` is
                    true. Omitted uses the server default (10s today); capped
                    server-side (15s today). Ignored when `waitForOutcome` is
                    not true.
            example:
              waitForOutcome: true
              orders:
                - marketHash: >-
                    0x81cfc23d0a02403f32d29b5a7c5686acd5eaedcdaa461cf253a1488e4cac0fcf
                  maker: '0xbcc6D643e4159A75ED1dB4e13330230B82F2AEe5'
                  totalBetSize: '2000000'
                  percentageOdds: '40000000000000000000'
                  salt: >-
                    0x8fa0cd4dbb399048caf74b8455f0fcf502fc989fc3e2720003aa10756faa70ef
                  expiry: 1785527222
                  baseToken: '0x1BC6326EA6aF2aB8E4b6Bc83418044B1923b2956'
                  isMakerBettingOutcomeOne: true
                  timeInForce: GTC
                  orderSignature: >-
                    0xa343a8052087c6739375c157f716beb6ddad7f1549e4c746aafda2869eb1f3235e7ba71aa79fa5b3d99b51b51c9cfc1b41cbda4b02badc0ef31ec3e978e069d81b
                  clientOrderId: quote-2026-07-31-002
      responses:
        '200':
          description: ''
          content:
            application/json:
              schema:
                type: object
                properties:
                  status:
                    type: string
                    description: '`success` or `failure` if the request succeeded or not'
                  data:
                    type: object
                    properties:
                      orders:
                        type: array
                        description: >-
                          One result per submitted order, in the order you sent
                          them.
                        items:
                          $ref: '#/components/schemas/CreateOrderResult'
              example:
                status: success
                data:
                  orders:
                    - orderId: >-
                        0x7f611d85216fa11810d5357ffaaeafbbefb88e69b9fd26bfae7b13a67cceb9dc
                      status: SUBMITTED
                      commandId: 550e8400-e29b-41d4-a716-446655440000
                      clientOrderId: quote-2026-07-31-002
                      outcome:
                        state: FULLY_FILLED
                        remainingAmount: '0'
                        fillAmount: '2000000'
                        blendedOdds: '50000000000000000000'
                        matchIds:
                          - >-
                            0x9a1c2f3e4b5d6a7f8c9b0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a
                        tradeId: 0xtrade1
        '400':
          description: ''
          content:
            application/json:
              schema:
                oneOf:
                  - $ref: '#/components/schemas/ErrorNestArray'
                  - $ref: '#/components/schemas/ErrorNest'
                  - $ref: '#/components/schemas/ErrorOrderRule'
              example:
                message: TOTAL_BET_SIZE_TOO_LOW
                marketHash: >-
                  0x81cfc23d0a02403f32d29b5a7c5686acd5eaedcdaa461cf253a1488e4cac0fcf
                minimum: '1'
        '401':
          description: ''
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorNest'
              example:
                message: BAD_AUTH
                error: Unauthorized
                statusCode: 401
        '403':
          description: ''
        '503':
          description: ''
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorNest'
              example:
                message: Service Unavailable
                statusCode: 503
      security:
        - SxApiKey: []
components:
  schemas:
    CreateOrderItem:
      description: >-
        One signed order. Note there is no `useProxy` field — trading is
        proxy-only.
      type: object
      required:
        - marketHash
        - maker
        - totalBetSize
        - percentageOdds
        - salt
        - expiry
        - baseToken
        - isMakerBettingOutcomeOne
        - timeInForce
        - orderSignature
      properties:
        marketHash:
          type: string
          description: The market you wish to place this order under
        maker:
          type: string
          description: >-
            Your user address. Must match the account that signs the order. This
            is NOT your proxy wallet address.
        totalBetSize:
          type: string
          description: >-
            Must be at least `limits.orderSizeMinimumBaseUnits` from [`GET
            /metadata/obv3`](/api-reference/get-metadata-obv3).
        percentageOdds:
          type: string
          description: Implied probability you are paying (maker's frame).
        salt:
          type: string
          description: >-
            Random 32-byte hex string to differentiate between orders with
            otherwise identical parameters
        expiry:
          type: integer
          description: >-
            A unix epoch timestamp in **seconds** — not milliseconds, and an
            integer unlike the amount fields. Must be in the future — `0` is
            **not** accepted, and neither is any past timestamp (`expiry must be
            a unix-seconds timestamp in the future`). A value that falls inside
            the market's betting delay plus two seconds is rejected (`Order
            expiry is too close to the betting delay window`). Resolve the delay
            from `bettingDelay` on [`GET
            /metadata/obv3`](/api-reference/get-metadata-obv3).
          example: 1785527222
        baseToken:
          type: string
          description: >-
            Must equal `activeAsset.baseToken` from [`GET
            /metadata/obv3`](/api-reference/get-metadata-obv3).
        isMakerBettingOutcomeOne:
          type: boolean
          description: >-
            The outcome you want to bet. `true` selects outcome one; `false`
            selects outcome two.
        timeInForce:
          type: string
          enum:
            - GTC
            - IOC
            - FOK
          description: >-
            Exactly one of `GTC`, `IOC`, `FOK` — case-sensitive, with no
            default. See [Time in force](/developers/time-in-force) for what
            each does and when to use it.
        orderSignature:
          type: string
          description: >-
            EIP-712 signature over the 8-field `Order` type, packed v-r-s (65
            bytes). See [EIP-712 order
            signing](/api-reference/eip712-order-signing).
        clientOrderId:
          type: string
          description: >-
            Optional. Max 64 chars, `^[A-Za-z0-9_-]+$` — a braced UUID is
            rejected. Must be unique per address: one collision fails the whole
            batch, not just that order. See [Client order
            id](/developers/client-order-id).
        externalUserId:
          type: string
          description: >-
            Optional partner tag. Max 255 chars. Attribution-only — echoed on
            privileged order/fill/trade reads and account channels. See
            [External user id](/developers/external-user-id).
        useBetCredits:
          type: boolean
          description: >-
            Fund from bet credits. IOC/FOK only. Still requires a deployed
            proxy.
        postOnly:
          type: boolean
          description: >-
            Optional. Add liquidity only. `GTC` only. An order that would cross
            on arrival is cancelled with `POST_ONLY_WOULD_CROSS` instead of
            taking. See [Time in force](/developers/time-in-force#post-only).
    CreateOrderResult:
      type: object
      description: >-
        One per-order result from `POST /orders-v3`. The response `data.orders`
        array contains one of these per submitted order, in the same order you
        sent them.
      required:
        - orderId
        - status
        - commandId
      properties:
        orderId:
          type: string
          description: The order id — the EIP-712 digest of the signed `Order` struct.
        status:
          type: string
          enum:
            - SUBMITTED
            - FAILED
          description: >-
            `SUBMITTED` = submitted to the matching engine; `FAILED` = rejected
            before submitting. See [Posting orders](/developers/posting-orders).
        commandId:
          type: string
          description: >-
            Server-assigned id for this order's submission command. Correlate it
            with your own records; always present, including on `FAILED`.
        clientOrderId:
          type: string
          description: Your own tag, echoed back when you set one; omitted otherwise.
        externalUserId:
          type: string
          description: >-
            Optional partner tag, echoed back when you set one; omitted
            otherwise.
        outcome:
          $ref: '#/components/schemas/CreateOrderOutcome'
          description: >-
            Present only when `waitForOutcome` was requested and the order was
            accepted (`SUBMITTED`). Absent on `FAILED` results.
      example:
        orderId: '0x7f611d85216fa11810d5357ffaaeafbbefb88e69b9fd26bfae7b13a67cceb9dc'
        status: SUBMITTED
        commandId: 550e8400-e29b-41d4-a716-446655440000
        clientOrderId: quote-2026-07-31-002
    ErrorNestArray:
      description: >-
        Validation errors. Same envelope as ErrorNest but `message` is an array
        — normalise both.
      type: object
      properties:
        message:
          type: array
          items:
            type: string
          example:
            - 'range must be one of the following values: 1, 7, 30'
        error:
          type: string
          example: Bad Request
        statusCode:
          type: integer
          example: 400
    ErrorNest:
      description: Most common shape.
      type: object
      properties:
        message:
          type: string
          example: INVALID_USER
        error:
          type: string
          example: Unauthorized
        statusCode:
          type: integer
          example: 401
    ErrorOrderRule:
      description: >-
        Business-rule rejection from `POST /orders-v3`. `message` is an error
        CODE, and the sibling keys are context that varies by code. There is no
        `statusCode` and no `code` field.
      type: object
      properties:
        message:
          type: string
          example: TOTAL_BET_SIZE_TOO_LOW
        marketHash:
          type: string
          description: Present on most codes.
        minimum:
          type: string
          description: '`TOTAL_BET_SIZE_TOO_LOW` only. Whole tokens, not base units.'
        maker:
          type: string
          description: '`PROXY_NOT_DEPLOYED` only.'
        parlayExpiry:
          type: integer
          description: >-
            `PARLAY_ORDER_EXPIRY_BEYOND_PARLAY_EXPIRY` only, as a unix-seconds
            number. See [Time in force](/developers/time-in-force) for why that
            differs from the order read path.
        parentMarketHash:
          type: string
          description: Every `QUARTERLINE_LEG_*` and `PARLAY_LEG_*` code.
        legMarketHash:
          type: string
          description: >-
            `QUARTERLINE_LEG_MISSING`, `QUARTERLINE_LEG_INACTIVE`,
            `PARLAY_LEG_INACTIVE`.
        legStatus:
          type: string
          description: >-
            `QUARTERLINE_LEG_INACTIVE`, `PARLAY_LEG_INACTIVE` — the leg market's
            status.
    CreateOrderOutcome:
      type: object
      description: >-
        Terminal matching outcome for one order. Present only on `POST
        /orders-v3` results when `waitForOutcome` was requested and the order
        was accepted (`SUBMITTED`).
      required:
        - state
      properties:
        state:
          type: string
          enum:
            - RESTED
            - FULLY_FILLED
            - PARTIAL_FILL_DONE
            - PARTIAL_FILL_RESTED
            - CANCELLED
            - TIMEOUT
          description: >-
            RESTED = rested with no fill; FULLY_FILLED = fully matched on entry;
            PARTIAL_FILL_DONE = partially filled, remainder discarded
            (IOC/dust); PARTIAL_FILL_RESTED = partially filled, remainder
            rested; CANCELLED = cancelled without resting (see `cancelReason`);
            TIMEOUT = no outcome before the wait deadline.
        remainingAmount:
          type: string
          description: >-
            Amount left unfilled after a settled outcome. Omitted on `TIMEOUT`
            because the order is still in flight.
        fillAmount:
          type: string
          description: >-
            Amount this order executed, in base units. Absent when the order
            never matched. See [unit conversions](/developers/unit-conversions).
        blendedOdds:
          type: string
          description: >-
            Effective blended execution odds across this order's matches, in the
            taker frame at 1e20 scale (divide by 10^20 for implied probability).
            Absent when the order never matched. See [unit
            conversions](/developers/unit-conversions).
        matchIds:
          type: array
          items:
            type: string
          description: Match ids produced by this order. Absent when it never matched.
        tradeId:
          type: string
          description: Trade id grouping this order's fills. Absent when it never matched.
        cancelReason:
          type: string
          enum:
            - NO_LIQUIDITY
            - INTERNAL_ERROR
            - ENGINE_SHUTDOWN
            - EXPIRED
            - INSUFFICIENT_BALANCE
            - POST_ONLY_WOULD_CROSS
          description: >-
            Why the order was cancelled. Present only when `state` is
            `CANCELLED`. These are the only values. Not the same set as
            [`inactiveReason`](/developers/order-lifecycle).
      example:
        state: FULLY_FILLED
        remainingAmount: '0'
        fillAmount: '2000000'
        blendedOdds: '50000000000000000000'
        matchIds:
          - '0x9a1c2f3e4b5d6a7f8c9b0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a'
        tradeId: 0xtrade1
  securitySchemes:
    SxApiKey:
      type: apiKey
      in: header
      name: x-sx-api-key
      description: 'API key, sent as the `x-sx-api-key` header. '

````

This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.