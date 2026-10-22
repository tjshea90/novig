> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Get the trade tape

> Query recent trades on the SX Bet exchange.

`GET /trades-v3/public` is the trade tape: bets other people placed. A row carries `userAddress` and `username` only when that bet was de-anonymized at placement. Every other row is anonymized.


## OpenAPI

````yaml GET /trades-v3/public
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
  /trades-v3/public:
    get:
      tags:
        - Trades
      summary: Get the trade tape
      description: >-
        The trade tape — bets other people placed. A row carries `userAddress`
        and `username` only when that bet was de-anonymized at placement; every
        other row is anonymized. Identity is fixed when the bet is placed and
        never changes afterwards: anonymizing your activity later does not
        un-name bets you already placed, and de-anonymizing it does not name
        them retroactively. LOCKED bets only, taker-side only. The only
        trade-grain route that needs no credential.
      operationId: getPublicTradesV3
      parameters:
        - name: marketHash
          in: query
          required: false
          schema:
            type: string
          description: Market ID to filter by.
        - name: eventId
          in: query
          required: false
          schema:
            type: string
            minLength: 2
            maxLength: 64
          description: >-
            Event ID to filter by (prefixed form, e.g. `L12003787`). Not
            mutually exclusive with `marketHash`: when both are sent,
            `marketHash` wins and this parameter is never evaluated.
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
                      trades:
                        type: array
                        items:
                          $ref: '#/components/schemas/PublicTradeV3'
                      nextKey:
                        type: string
                        description: >-
                          Pass as `nextKey` to retrieve the next page. Absent —
                          not null — on the last page.
              example:
                status: success
                data:
                  trades:
                    - tradeId: >-
                        0xac6bb30bba6ea82d8717219a4fa49d15b58fd638fba479f294b4d402376d14d6
                      marketHash: >-
                        0x1fec4ed9b71c2f812db900af7d12446158a0eda56041106f99551c496efd3266
                      isBettingOutcomeOne: false
                      isParlay: false
                      totalStake: '1000000'
                      totalReturn: '2500000'
                      weightedAverageOdds: '40000000000000000000'
                      betTime: '2026-07-31T18:47:09.577Z'
                      gameTime: '2040-01-01T00:00:00.000Z'
                      outcomeLabel: Hornets
                      teamOneName: Toronto Raptors
                      teamTwoName: Charlotte Hornets
                      type: 1
                      leagueLabel: NBA
                      sportId: 1
                      eventId: L12003787
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
        '503':
          description: ''
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorNest'
              example:
                message: Service Unavailable
                statusCode: 503
      security: []
components:
  schemas:
    PublicTradeV3:
      description: >-
        The trade tape. A row carries identity only when that bet was
        de-anonymized at placement.
      type: object
      properties:
        tradeId:
          type: string
          description: The bet ID.
        marketHash:
          type: string
          description: The market. Parent hash for quarter lines and parlays.
        userAddress:
          type: string
          description: >-
            The bettor's checksummed address. Present only when that bet was
            de-anonymized at placement; absent when the bet is anonymized.
        username:
          type: string
          description: 'The bettor''s display name. '
        isBettingOutcomeOne:
          type: boolean
          description: Which outcome was backed.
        isParlay:
          type: boolean
          description: Whether `marketHash` is a parlay parent.
        totalStake:
          type: string
          description: Amount risked, in base units.
        totalReturn:
          type: string
          description: Gross return if the bet wins, in base units, stake included.
        weightedAverageOdds:
          type: string
          description: Blended implied odds across every fill.
        betTime:
          type: string
          description: ISO 8601. When the bet was placed, and the sort key for this route.
        gameTime:
          type: string
          description: ISO 8601. Scheduled fixture start.
        outcomeLabel:
          type: string
          description: >-
            Label for the side that was backed — outcome one when
            `isBettingOutcomeOne` is true, otherwise outcome two. Absent when
            market metadata cannot be resolved.
        teamOneName:
          type: string
          description: >-
            Team one display name. Absent when market metadata cannot be
            resolved.
        teamTwoName:
          type: string
          description: >-
            Team two display name. Absent when market metadata cannot be
            resolved.
        type:
          type: integer
          description: >-
            Market type id. See [Market types](/api-reference/market-types).
            Absent when market metadata cannot be resolved.
        leagueLabel:
          type: string
          description: League display name. Absent when market metadata cannot be resolved.
        sportId:
          type: integer
          description: Sport id. Absent when market metadata cannot be resolved.
        eventId:
          type: string
          description: >-
            Prefixed event id (e.g. `L12003787`). Absent when market metadata
            cannot be resolved.
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

````

This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.