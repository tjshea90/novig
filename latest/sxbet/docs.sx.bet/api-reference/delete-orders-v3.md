> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Cancel orders by id

> Cancel specific open orders by order id on the SX Bet exchange.

`DELETE /orders-v3` cancels orders you name by id, and is the only cancel route that reports a
**per-order outcome**. This is a synchronous endpoint.

> [!WARNING]
>
> **The HTTP status is not the cancel outcome** This route returns `200` whether or not
> anything was cancelled. An id you do not own, an id that no longer exists, and an id you already
> cancelled all arrive as `200` with the order in `notCancelled`.

> [!NOTE]
>
> **There is a 5-second confirmation timeout.** Orders that report in time land in
> `cancelled` or `notCancelled`; any that do not report before the timeout come back in the
> `unconfirmed` bucket. `unconfirmed` does **not** mean the cancel failed — the command was still
> submitted and usually takes effect shortly after. Re-read
> [`GET /orders-v3`](/api-reference/get-orders-v3) to confirm, rather than assuming either outcome.

> [!NOTE]
>
> **At most `limits.maxCancelOrders` ids per request (100 today).** Read the cap from
> [`GET /metadata/obv3`](/api-reference/get-metadata-obv3); exceeding it is a `400`. To cancel every
> order at once, use [`DELETE /orders-v3/all`](/api-reference/delete-orders-v3-all) instead of paging
> ids through this route.


## OpenAPI

````yaml DELETE /orders-v3
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
    delete:
      tags:
        - Orders
      summary: Cancel orders by id
      description: >-
        Synchronous cancel by id. The address comes from the credential, so a
        `maker` or `bettor` field in the body is ignored and you can only cancel
        your own orders. Every requested id lands in exactly one of three
        buckets.
      operationId: cancelOrdersV3
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
                    type: object
                    required:
                      - orderId
                    properties:
                      orderId:
                        type: string
                        description: The order id.
                  description: >-
                    An array of objects, each with an `orderId` — `{orders:
                    [{orderId}]}`. At most `limits.maxCancelOrders` unique items
                    (100 today). An empty array is valid and does nothing.
                    Duplicate ids are collapsed server-side: a repeated id is
                    cancelled once and appears once in the response, and the cap
                    counts unique ids.
            example:
              orders:
                - orderId: >-
                    0x0e9e6910f10e0aced2059f2736011c05b9a9cf5c587d150f3b413159560c7a76
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
                      cancelled:
                        type: array
                        items:
                          $ref: '#/components/schemas/CancelByIdItem'
                        description: >-
                          The matching engine confirmed the order is off the
                          book. A settled outcome.
                      notCancelled:
                        type: array
                        items:
                          $ref: '#/components/schemas/CancelByIdItem'
                        description: The order was not cancelled; `reason` says why.
                      unconfirmed:
                        type: array
                        items:
                          $ref: '#/components/schemas/CancelByIdItem'
                        description: >-
                          The cancel was published but reported no outcome
                          before the wait timeout. NOT a failure — the cancel
                          may still land. Re-read `GET /orders-v3` rather than
                          retrying blindly.
              example:
                status: success
                data:
                  cancelled:
                    - orderId: >-
                        0x0e9e6910f10e0aced2059f2736011c05b9a9cf5c587d150f3b413159560c7a76
                      commandId: 74a01fe8-6555-4492-8d28-879c151aef8b
                  notCancelled:
                    - orderId: >-
                        0xeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee
                      reason: NOT_FOUND
                  unconfirmed: []
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
                  - orders must be an array
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
        '500':
          description: ''
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorNest'
              example:
                message: Failed to publish cancel command(s) to the orderbook
                error: Internal Server Error
                statusCode: 500
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
    CancelByIdItem:
      type: object
      properties:
        orderId:
          type: string
          description: The order id.
        commandId:
          type: string
          description: >-
            Command id of the cancel that produced this outcome. Always present
            on `cancelled`, `unconfirmed` and `cancelledSubmitted`; on
            `notCancelled` it is present only when the matching engine reported
            the order was not on the book.
        reason:
          type: string
          description: >-
            Why the order was not cancelled. On `notCancelled`: NOT_FOUND (no
            such order, someone else's order, or not on the book),
            CANCEL_PENDING (a cancel is already in flight), NOT_ACTIVE (already
            terminal).
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