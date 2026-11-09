> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Get your orders

> Query your active orders on the SX Bet orderbook.

`GET /orders-v3` returns the orders the authenticated account has **resting on the book right now**,
oldest first. The scope is your own account.


## OpenAPI

````yaml GET /orders-v3
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
    get:
      tags:
        - Orders
      summary: Get your orders
      description: >-
        Returns the orders the authenticated account has resting on the book
        right now. The route is hardcoded to `ACTIVE` — there is no `status`
        parameter, and filled, cancelled and expired orders are simply absent.
        Default sort is `createdAt` ascending (longest-resting first). Pass
        `sortBy` and `sortAsc` to change it.
      operationId: getOrdersV3
      parameters:
        - name: marketHash
          in: query
          required: false
          schema:
            type: string
          description: Market ID to filter by.
          example: '0xbf06c6379c922d8118612d1d7493b20f6df6929437fbf6022904327f9516a2eb'
        - name: eventId
          in: query
          required: false
          schema:
            type: string
            minLength: 2
            maxLength: 64
          description: Event ID to filter by.
          example: L12003787
        - name: sortBy
          in: query
          required: false
          schema:
            type: string
            enum:
              - createdAt
              - event
              - totalBetSize
            default: createdAt
          description: >-
            Sort the full result before paging. Default `createdAt`. `event`
            groups by fixture then market (`eventId` order, not soonest
            kickoff). `totalBetSize` is the order's original size.
        - name: sortAsc
          in: query
          required: false
          schema:
            type: boolean
            default: true
          description: Sort ascending. Defaults to true (oldest / smallest first).
        - name: perPage
          in: query
          required: false
          schema:
            type: integer
            minimum: 1
            maximum: 100
            default: 50
          description: Rows per page.
        - name: nextKey
          in: query
          required: false
          schema:
            type: string
          description: Cursor from the previous response to continue pagination.
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
                        items:
                          $ref: '#/components/schemas/OrderV3'
                      nextKey:
                        type: string
                        description: >-
                          Pass as `nextKey` to retrieve the next page. Absent —
                          not null — on the last page.
              example:
                status: success
                data:
                  orders:
                    - id: >-
                        0xd9b693a1075ab77c0638fe98fcd3144dbb8f91d0f7ee454d442bf25ca7cbcb66
                      marketHash: >-
                        0x81cfc23d0a02403f32d29b5a7c5686acd5eaedcdaa461cf253a1488e4cac0fcf
                      userAddress: '0xbcc6D643e4159A75ED1dB4e13330230B82F2AEe5'
                      wallet: '0x4361123dbdc1D812fdf7D27045aF358C9C8AA70A'
                      isBettingOutcomeOne: true
                      percentageOdds: '40000000000000000000'
                      totalBetSize: '2000000'
                      remainingSize: '2000000'
                      expiry: '2026-07-31T19:45:07.000Z'
                      status: ACTIVE
                      inactiveReason: null
                      eventId: L12952568
                      createdAt: '2026-07-31T18:45:07.437Z'
                      updatedAt: '2026-07-31T18:45:07.456Z'
                    - id: >-
                        0x112a4f19f20395aefcdb5d26f3f8aab0d96b1aff7ce346c0bfb13d9490340cdf
                      marketHash: >-
                        0xbf06c6379c922d8118612d1d7493b20f6df6929437fbf6022904327f9516a2eb
                      userAddress: '0xbcc6D643e4159A75ED1dB4e13330230B82F2AEe5'
                      wallet: '0x4361123dbdc1D812fdf7D27045aF358C9C8AA70A'
                      isBettingOutcomeOne: true
                      percentageOdds: '40000000000000000000'
                      totalBetSize: '3000000'
                      remainingSize: '3000000'
                      expiry: '2026-07-31T19:45:42.000Z'
                      status: ACTIVE
                      inactiveReason: null
                      eventId: L12003787
                      createdAt: '2026-07-31T18:45:42.636Z'
                      updatedAt: '2026-07-31T18:45:42.838Z'
                  nextKey: >-
                    eyJjcmVhdGVkQXQiOiIyMDI2LTA3LTMxVDE4OjQ1OjQyLjYzNloiLCJpZCI6IjB4MTEyYTRmMTlmMjAzOTVhZWZjZGI1ZDI2ZjNmOGFhYjBkOTZiMWFmZjdjZTM0NmMwYmZiMTNkOTQ5MDM0MGNkZiJ9
        '400':
          description: ''
          content:
            application/json:
              schema:
                oneOf:
                  - $ref: '#/components/schemas/ErrorNestArray'
                  - $ref: '#/components/schemas/ErrorNest'
              example:
                message:
                  - perPage must not be greater than 100
                error: Bad Request
                statusCode: 400
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
  securitySchemes:
    SxApiKey:
      type: apiKey
      in: header
      name: x-sx-api-key
      description: 'API key, sent as the `x-sx-api-key` header. '

````

This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.