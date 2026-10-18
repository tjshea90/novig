> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Get market orderbook snapshot

> Get a snapshot of the order book for a market on SX Bet. Requires an API key.

`GET /orderbook-v3/snapshot/market` returns the **aggregated** book for a **single** market. It requires an [API key](/api-reference/api-key).

<Tip>
  Prefer the realtime [`orderbook_v3:{marketHash}`](/api-reference/channel-orderbook-v3) channel over
  polling this endpoint. Use the snapshot once to bootstrap state, then apply stream publications.
</Tip>

<Tip>
  Subscribed to a very high number of markets? Seed from
  [`GET /orderbook-v3/snapshot/event`](/api-reference/get-event-orderbook-snapshot) and follow
  [`orderbook_v3_event:{eventId}`](/api-reference/channel-orderbook-v3-event) instead of one snapshot
  and one channel per market.
</Tip>

<Warning>
  Use `showTakerPerspective` for display only. `percentageOdds` is from the perspective of the maker. [Read the order
  book](/developers/order-book#convert-a-level-into-what-you-can-bet) walks the conversion through.
</Warning>


## OpenAPI

````yaml GET /orderbook-v3/snapshot/market
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
  /orderbook-v3/snapshot/market:
    get:
      tags:
        - Orders
      summary: Get an orderbook snapshot
      description: >-
        The aggregated book for one market. Requires an API key. Levels are
        aggregated across orders at the same price, so order count is not
        recoverable.
      operationId: getMarketOrderbookSnapshotV3
      parameters:
        - name: marketHash
          in: query
          required: true
          schema:
            type: string
          description: >-
            The market hash for which to get the snapshot. For many markets in
            one call use `GET /orders-v3/odds/best`, which takes up to 100
            hashes but returns only the top level per side.
        - name: showTakerPerspective
          in: query
          required: false
          schema:
            type: boolean
          description: >-
            Return the book from the perspective of someone wanting each
            outcome, instead of the makers' own: each side's odds are inverted,
            the sides are swapped, and each `size` is restated as the taker
            stake that fully consumes that level. Accepts
            `true`/`false`/`1`/`0`, case-insensitively — `TRUE` and `True` work.
            `yes`, `on` and an empty value are a 400.
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
                    description: >-
                      Same flat shape as the realtime orderbook channel payload:
                      level arrays beside `marketHash` and `version`.
                    properties:
                      marketHash:
                        type: string
                        description: Echoed back verbatim from the request.
                      outcomeOne:
                        type: array
                        items:
                          $ref: '#/components/schemas/OrderbookLevel'
                        description: >-
                          Price levels for outcome one, sorted descending by
                          maker odds so index 0 is the best available price.
                      outcomeTwo:
                        type: array
                        items:
                          $ref: '#/components/schemas/OrderbookLevel'
                        description: >-
                          Price levels for outcome two, sorted descending by
                          maker odds so index 0 is the best available price.
                      version:
                        $ref: '#/components/schemas/OrderbookVersion'
              example:
                status: success
                data:
                  marketHash: >-
                    0xbf06c6379c922d8118612d1d7493b20f6df6929437fbf6022904327f9516a2eb
                  outcomeOne:
                    - percentageOdds: '50000000000000000000'
                      size: '1000000'
                    - percentageOdds: '40000000000000000000'
                      size: '5000000'
                  outcomeTwo: []
                  version: '00100000000000008914000'
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
                  - marketHash must be a valid hex string of length 32 bytes
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
        '404':
          description: ''
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorNest'
              example:
                message: Market not found
                error: Not Found
                statusCode: 404
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
    OrderbookLevel:
      type: object
      properties:
        percentageOdds:
          type: string
          description: Implied odds.
        size:
          type: string
          description: Aggregated MAKER stake at this level.
    OrderbookVersion:
      type: string
      description: >-
        Book version — a single string that increases as the book changes. Apply
        an update only when it is strictly greater than the version you hold;
        compare as a string. All zeros means no snapshot row exists yet. See
        [Book versioning](/developers/book-versioning) for the per-market scope
        and the sparse spacing.
      example: '00100000000000008914000'
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