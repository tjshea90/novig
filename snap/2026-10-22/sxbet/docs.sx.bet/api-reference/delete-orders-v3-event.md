> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Cancel orders by event

> Cancel all your open orders for a specific event on SX Bet.

`DELETE /orders-v3/event` submits a cancel command for every `PENDING` or `ACTIVE` order you hold on a
single event, across all of that event's markets and both outcomes. To scope a cancel to one market,
use [`DELETE /orders-v3/market`](/api-reference/delete-orders-v3-market); for one side or one price
level, name the ids on [`DELETE /orders-v3`](/api-reference/delete-orders-v3).

> [!WARNING]
>
> **This is an asynchronous endpoint — a `200` means submitted, not cancelled.** The route publishes
> cancel commands and returns without waiting for a response, so your orders are still on the
> book when the response arrives. Confirm with
> [`GET /orders-v3?eventId=…`](/api-reference/get-orders-v3) or watch
> [`account:orders_v3_#{address}`](/api-reference/channel-orders-v3) before acting on the result.


## OpenAPI

````yaml DELETE /orders-v3/event
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
  /orders-v3/event:
    delete:
      tags:
        - Orders
      summary: Cancel orders on an event
      description: >-
        Cancels open orders on one event for the credential's address, across
        every market on that event and both outcomes. Loop while `hasMore` is
        true. Submission is asynchronous — a 200 does not mean the orders are
        off the book.
      operationId: cancelOrdersV3Event
      parameters:
        - name: eventId
          in: query
          required: true
          schema:
            type: string
          description: Event ID to cancel orders for.
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
                      cancelledSubmitted:
                        type: array
                        items:
                          $ref: '#/components/schemas/CancelByIdItem'
                        description: >-
                          A cancel command for this order reached the orderbook.
                          This is not confirmation that it is off the book.
                      hasMore:
                        type: boolean
                        description: >-
                          True when open orders remain beyond this batch; call
                          again. Loop until false.
              example:
                status: success
                data:
                  cancelledSubmitted:
                    - orderId: >-
                        0x49a35fd509c430cb5e65d3892d9adfb69b6be30ef724bb208ae6ae718f062f9b
                      commandId: 5305bad0-e0ee-4e2e-b2ea-197131b03c8b
                  hasMore: false
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
                  - eventId should not be empty
                  - eventId must be a string
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