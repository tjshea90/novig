> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Best odds

> Get the best available odds for a set of markets on the SX Bet exchange.

`GET /orders-v3/odds/best` returns the **best level on each side** for a set of markets. It requires
an [API key](/api-reference/api-key). For every level on a single market, use
[`GET /orderbook-v3/snapshot/market`](/api-reference/get-market-orderbook-snapshot) — both read the top of the same
book.

<Tip>
  Prefer the realtime [`best_odds_v3:global`](/api-reference/channel-best-odds-v3) channel over
  polling this endpoint. Use this route once to seed state, then apply stream publications.
</Tip>

<Note>
  `marketHashes` is required — comma-separated, maximum 100.
</Note>


## OpenAPI

````yaml GET /orders-v3/odds/best
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
  /orders-v3/odds/best:
    get:
      tags:
        - Orders
      summary: Get best odds
      description: Top of book for many markets in one call.
      operationId: getBestOddsV3
      parameters:
        - name: marketHashes
          in: query
          required: true
          schema:
            type: string
          description: Comma-separated, maximum 100. Required.
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
                    properties:
                      bestOdds:
                        type: array
                        items:
                          $ref: '#/components/schemas/BestOddsV3'
              example:
                status: success
                data:
                  bestOdds:
                    - marketHash: >-
                        0xbf06c6379c922d8118612d1d7493b20f6df6929437fbf6022904327f9516a2eb
                      outcomeOne:
                        percentageOdds: '50000000000000000000'
                        size: '1000000'
                      outcomeTwo: null
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
                  - Must provide marketHashes
                  - >-
                    marketHashes must be an array of valid hex strings of length
                    32 bytes
                  - marketHashes must contain no more than 100 elements
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
      security:
        - SxApiKey: []
components:
  schemas:
    BestOddsV3:
      type: object
      properties:
        marketHash:
          type: string
          description: The resulting market for the given best odds query
        outcomeOne:
          allOf:
            - $ref: '#/components/schemas/OrderbookLevel'
          nullable: true
          description: >-
            Best level on outcome one, or `null` when that side has no resting
            orders.
        outcomeTwo:
          allOf:
            - $ref: '#/components/schemas/OrderbookLevel'
          nullable: true
          description: >-
            Best level on outcome two, or `null` when that side has no resting
            orders.
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
    OrderbookLevel:
      type: object
      properties:
        percentageOdds:
          type: string
          description: Implied odds.
        size:
          type: string
          description: Aggregated MAKER stake at this level.
  securitySchemes:
    SxApiKey:
      type: apiKey
      in: header
      name: x-sx-api-key
      description: 'API key, sent as the `x-sx-api-key` header. '

````

This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.