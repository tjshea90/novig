> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Get an order by client ID

> Look up one of your orders by the clientOrderId you set on submit — any status.

`GET /orders-v3/client/{clientOrderId}` returns **one order** belonging to the authenticated account.
Unlike [`GET /orders-v3`](/api-reference/get-orders-v3), this is **not** limited to orders
resting on the book — filled, cancelled, and expired orders are returned with `status: "INACTIVE"`.

Pass the `clientOrderId` you set on [`POST /orders-v3`](/api-reference/post-orders-v3). The tag is
[unique per address](/developers/client-order-id#uniqueness), so this returns at most one row.

<Note>
  An order stays retrievable for about 10–20 minutes after it becomes `INACTIVE`. After that, it
  returns **404**, the same as a tag that was never set or belongs to another account. To keep an
  order's final state, record it from the [orders channel](/api-reference/channel-orders-v3) when it
  arrives.
</Note>


## OpenAPI

````yaml GET /orders-v3/client/{clientOrderId}
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
  /orders-v3/client/{clientOrderId}:
    get:
      tags:
        - Orders
      summary: Get an order by client ID
      description: >-
        Returns one order belonging to the authenticated account, by the
        `clientOrderId` set on submit. Unlike `GET /orders-v3`, this is not
        limited to `ACTIVE` — filled, cancelled, and expired orders are returned
        as `INACTIVE` for about 10–20 minutes after they become inactive. After
        that, and for a missing, unset, or foreign tag, it returns 404.
      operationId: getOrderV3ByClientId
      parameters:
        - name: clientOrderId
          in: path
          required: true
          schema:
            type: string
            maxLength: 64
            pattern: ^[A-Za-z0-9_-]+$
          description: >-
            The client-supplied order id (same charset and uniqueness rules as
            on `POST /orders-v3`).
          example: mm-abc-1
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
                      order:
                        $ref: '#/components/schemas/OrderV3'
                    required:
                      - order
              example:
                status: success
                data:
                  order:
                    id: >-
                      0xd9b693a1075ab77c0638fe98fcd3144dbb8f91d0f7ee454d442bf25ca7cbcb66
                    marketHash: >-
                      0x81cfc23d0a02403f32d29b5a7c5686acd5eaedcdaa461cf253a1488e4cac0fcf
                    userAddress: '0xbcc6D643e4159A75ED1dB4e13330230B82F2AEe5'
                    wallet: '0x4361123dbdc1D812fdf7D27045aF358C9C8AA70A'
                    isBettingOutcomeOne: true
                    percentageOdds: '40000000000000000000'
                    totalBetSize: '2000000'
                    remainingSize: '0'
                    expiry: '2026-07-31T19:45:07.000Z'
                    status: INACTIVE
                    inactiveReason: FILLED
                    eventId: L12952568
                    clientOrderId: mm-abc-1
                    createdAt: '2026-07-31T18:45:07.437Z'
                    updatedAt: '2026-07-31T18:46:12.100Z'
        '400':
          description: ''
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorNest'
              example:
                message:
                  - clientOrderId must contain only [A-Za-z0-9_-] characters
                error: Bad Request
                statusCode: 400
        '404':
          description: ''
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorNest'
              example:
                message: Order not found
                error: Not Found
                statusCode: 404
        '503':
          description: ''
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorNest'
      security:
        - SxApiKey: []
components:
  schemas:
    OrderV3:
      description: >-
        An order as read back. `timeInForce` is write-only and is NOT present
        here, and there is no `maker` or `baseToken` field.
      type: object
      properties:
        id:
          type: string
          description: >-
            The signed order ID, lowercase — the same key `POST /orders-v3`
            returned. See [Posting orders](/developers/posting-orders) for
            computing it locally before you submit.
        marketHash:
          type: string
          description: The market the order is on.
        userAddress:
          type: string
          description: Your user address — the address that signed.
        wallet:
          type: string
          description: Your proxy wallet — where the funds come from.
        isBettingOutcomeOne:
          type: boolean
          description: >-
            Which outcome the order backs. You submit this field as
            `isMakerBettingOutcomeOne` and read it back as
            `isBettingOutcomeOne`.
        percentageOdds:
          type: string
          description: Implied probability.
        totalBetSize:
          type: string
          description: Your original maximum stake.
        remainingSize:
          type: string
          description: >-
            The unfilled portion of the original order size. The filled amount
            is `totalBetSize - remainingSize`. Use the `status` field to
            determine whether the remaining amount is still active.
        expiry:
          type: string
          format: date-time
          nullable: true
          description: >-
            ISO 8601 string — note you **send** `expiry` to `POST /orders-v3` as
            a unix epoch timestamp in seconds and read it back here as ISO 8601.
            Every order carries a real expiry; `null` appears only on legacy
            orders created before `expiry` became mandatory.
        status:
          type: string
          enum:
            - PENDING
            - ACTIVE
            - INACTIVE
          description: >-
            `PENDING`, `ACTIVE`, or `INACTIVE`. See [Order
            lifecycle](/developers/order-lifecycle).
        inactiveReason:
          type: string
          nullable: true
          description: >-
            Why the order stopped resting. Set when `status` is `INACTIVE`,
            otherwise null.
        eventId:
          type: string
          description: >-
            Prefixed form, e.g. "L12003787" — the value to pass back as the
            `eventId` filter.
        clientOrderId:
          type: string
          description: What you sent on `POST /orders-v3`, present only when set.
        externalUserId:
          type: string
          description: Optional partner tag from `POST /orders-v3`, present only when set.
        createdAt:
          type: string
          description: >-
            ISO 8601. When the order was submitted — at `POST` time, before the
            order rested.
        updatedAt:
          type: string
          description: ISO 8601. Last change; bumped by a fill or a status change.
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
  securitySchemes:
    SxApiKey:
      type: apiKey
      in: header
      name: x-sx-api-key
      description: 'API key, sent as the `x-sx-api-key` header. '

````

This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.