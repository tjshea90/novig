> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Get event orderbook snapshot

> Get a snapshot of the order book for markets on an event.

`GET /orderbook-v3/snapshot/event` returns the **aggregated** book for markets on a single event. It requires an [API key](/api-reference/api-key).

<Tip>
  Use this to re-seed state for the realtime
  [`orderbook_v3_event:{eventId}`](/api-reference/channel-orderbook-v3-event) channel after a
  `recovered: false` publication — page until `nextKey` is omitted, instead of hitting the
  single-market [snapshot](/api-reference/get-market-orderbook-snapshot) once per market.
</Tip>

<Warning>
  Use `showTakerPerspective` for display only. `percentageOdds` is from the perspective of the maker. [Read the order
  book](/developers/order-book#convert-a-level-into-what-you-can-bet) walks the conversion through.
</Warning>


## OpenAPI

````yaml GET /orderbook-v3/snapshot/event
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
  /orderbook-v3/snapshot/event:
    get:
      tags:
        - Orders
      summary: Get an event orderbook snapshot
      description: >-
        The aggregated book for markets on one event. Follow `data.nextKey`
        until it is absent. Use it to re-seed state for the realtime
        `orderbook_v3_event:{eventId}` channel after a `recovered: false`
        publication, instead of fetching each market's snapshot one at a time.
      operationId: getEventOrderbookSnapshotV3
      parameters:
        - name: eventId
          in: query
          required: true
          schema:
            type: string
          description: >-
            The event to fetch market books for — the same identifier markets
            report as `sportXeventId`.
        - name: nextKey
          in: query
          required: false
          schema:
            type: string
          description: Cursor from the previous response to continue pagination.
        - name: showTakerPerspective
          in: query
          required: false
          schema:
            type: boolean
          description: >-
            Applied to every market in the response. Return each book from the
            perspective of someone wanting each outcome, instead of the makers'
            own: each side's odds are inverted, the sides are swapped, and each
            `size` is restated as the taker stake that fully consumes that
            level. Accepts `true`/`false`/`1`/`0`, case-insensitively — `TRUE`
            and `True` work. `yes`, `on` and an empty value are a 400.
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
                      eventId:
                        type: string
                        description: Echoed back verbatim from the request.
                      markets:
                        type: array
                        description: >-
                          One page of markets on the event that have a book,
                          each the same flat shape as the single-market
                          snapshot. An event with no books returns an empty
                          array, not a 404.
                        items:
                          type: object
                          properties:
                            marketHash:
                              type: string
                              description: The market this book belongs to.
                            outcomeOne:
                              type: array
                              items:
                                $ref: '#/components/schemas/OrderbookLevel'
                              description: >-
                                Price levels for outcome one, sorted descending
                                by maker odds so index 0 is the best available
                                price.
                            outcomeTwo:
                              type: array
                              items:
                                $ref: '#/components/schemas/OrderbookLevel'
                              description: >-
                                Price levels for outcome two, sorted descending
                                by maker odds so index 0 is the best available
                                price.
                            version:
                              $ref: '#/components/schemas/OrderbookVersion'
                      nextKey:
                        type: string
                        description: >-
                          Pass as `nextKey` to retrieve the next page. Absent —
                          not null — on the last page.
              example:
                status: success
                data:
                  eventId: L13058397
                  nextKey: >-
                    eyJtYXJrZXRIYXNoIjoiMHhiZjA2YzYzNzljOTIyZDgxMTg2MTJkMWQ3NDkzYjIwZjZkZjY5Mjk0MzdmYmY2MDIyOTA0MzI3Zjk1MTZhMmViIn0=
                  markets:
                    - marketHash: >-
                        0xbf06c6379c922d8118612d1d7493b20f6df6929437fbf6022904327f9516a2eb
                      outcomeOne:
                        - percentageOdds: '50000000000000000000'
                          size: '1000000'
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
                  - eventId must be longer than or equal to 2 characters
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