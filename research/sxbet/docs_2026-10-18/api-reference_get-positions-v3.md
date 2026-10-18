> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Get your positions

> Get your positions from the SX Bet API.

`GET /positions-v3` rolls your bets up **per market**. Ten bets on one market are one row here, with
the exposure already netted: what you win if outcome one lands, what you lose if it does not, and the
blended price you got on each side. [Tracking positions](/developers/which-grain#positions) covers what the roll-up
does and does not net.


## OpenAPI

````yaml GET /positions-v3
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
  /positions-v3:
    get:
      tags:
        - Trades
      summary: Get your positions
      description: >-
        Your exposure aggregated per MARKET, with embedded market metadata.
        `status` is required here. Note the grain: counts and PnL on this
        surface are per market, not per bet. Default sort is `gameTime`
        descending.
      operationId: getPositionsV3
      parameters:
        - name: status
          in: query
          required: true
          schema:
            type: string
          description: >-
            Required, and comma-separated: any non-empty subset of MATCHED,
            LOCKED, SETTLED and FAILED. `MATCHED,LOCKED` reads as open bets and
            SETTLED as history. Request SETTLED alone to populate pnl.
        - name: eventId
          in: query
          required: false
          schema:
            type: string
            minLength: 2
            maxLength: 64
          description: Event ID to query.
        - name: sortBy
          in: query
          required: false
          schema:
            type: string
            enum:
              - gameTime
              - netMaxWin
              - maxLoss
              - pnl
            default: gameTime
          description: >-
            Sort the full result before paging. Default `gameTime`. `pnl` is
            only valid when `status` is exclusively `SETTLED` (otherwise `400`
            with `sortBy=pnl is only valid when status is SETTLED`). `netMaxWin`
            sorts by pre-fee `maxWin` (the `netMaxWin` field subtracts a
            projected payout fee).
        - name: sortAsc
          in: query
          required: false
          schema:
            type: boolean
            default: false
          description: >-
            Sort ascending. Default false (later `gameTime` first; larger
            financial values first).
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
                      positions:
                        type: array
                        items:
                          $ref: '#/components/schemas/PositionV3'
                      nextKey:
                        type: string
                        description: >-
                          Pass as `nextKey` to retrieve the next page. Absent —
                          not null — on the last page.
              example:
                status: success
                data:
                  positions:
                    - marketHash: >-
                        0x8120ae9408cd7556246473be7ceada8cba47d42418cce31ffad0b67b196e3218
                      market:
                        status: ACTIVE
                        marketHash: >-
                          0x8120ae9408cd7556246473be7ceada8cba47d42418cce31ffad0b67b196e3218
                        outcomeOneName: Golden State Warriors
                        outcomeTwoName: Minnesota Timberwolves
                        outcomeVoidName: NO_CONTEST
                        teamOneName: Golden State Warriors
                        teamTwoName: Minnesota Timberwolves
                        type: 226
                        gameTime: 1773453600
                        sportXeventId: L18272456
                        liveEnabled: true
                        sportLabel: Basketball
                        sportId: 1
                        leagueId: 1
                        leagueLabel: NBA
                        group1: NBA
                        chainVersion: SXR
                        participantOneId: 15002
                        participantTwoId: 14999
                        __type: Market
                      totalStake: '300000000'
                      maxWin: '200000000'
                      netMaxWin: '198400000'
                      maxLoss: '-100000000'
                      isOutcomeOneMaxWin: true
                      odds:
                        outcomeOne: '40000000000000000000'
                        outcomeTwo: '50000000000000000000'
                      pnl: null
                      tradeCount: 2
                      betTime: '2026-03-13T21:14:02.118Z'
                      updatedAt: '2026-03-13T22:03:41.502Z'
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
                  - status should not be empty
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
    PositionV3:
      description: Aggregated per market.
      type: object
      properties:
        marketHash:
          type: string
          description: The market this position aggregates.
        market:
          $ref: '#/components/schemas/Market'
          description: Embedded market metadata.
        totalStake:
          type: string
          description: Sum of every bet's stake in this market, in base units.
        maxWin:
          type: string
          description: >-
            Best-case profit: best return minus total stake, in base units. The
            stake is already excluded.
        netMaxWin:
          type: string
          description: >-
            `maxWin` less the settlement payout fee, projected from your live
            per-wallet rate — the best-case profit you would actually keep.
        maxLoss:
          type: string
          description: >-
            Worst-case profit, by the same formula as `maxWin`, so it is
            normally negative and reaches exactly `-totalStake` on a one-sided
            position. Base units.
        isOutcomeOneMaxWin:
          type: boolean
          description: True when outcome one is the better case. Ties resolve to true.
        odds:
          description: Blended implied odds per side.
          type: object
          properties:
            outcomeOne:
              type: string
              description: Implied odds for outcome one.
            outcomeTwo:
              type: string
              description: Implied odds for outcome two.
        pnl:
          type: string
          nullable: true
          description: >-
            Realised profit, in base units. Populated only when every requested
            status is SETTLED.
        tradeCount:
          type: integer
          description: How many bets rolled into this row.
        betTime:
          type: string
          description: ISO 8601 timestamp of the earliest bet in this group of trades.
        updatedAt:
          type: string
          description: ISO 8601. The latest mutation — a maximum across the group.
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
    Market:
      type: object
      properties:
        status:
          type: string
          description: '`ACTIVE` or `INACTIVE`'
        marketHash:
          type: string
          description: The unique identifier for the market
        outcomeOneName:
          type: string
          description: Outcome one for this market
        outcomeTwoName:
          type: string
          description: Outcome two for this market
        outcomeVoidName:
          type: string
          description: Outcome void for this market
        teamOneName:
          type: string
          description: The name of the first team/player participating
        teamTwoName:
          type: string
          description: The name of the second team/player participating
        type:
          type: integer
          description: The type of the market
        gameTime:
          type: number
          description: Scheduled start of the game, as a UNIX timestamp in seconds
        line:
          type: number
          nullable: true
          description: The line of the market. Only applicable to markets with a line
        sportXeventId:
          type: string
          description: The unique event ID for this market
        liveEnabled:
          type: boolean
          description: Whether or not this match is available for live betting
        sportLabel:
          type: string
          description: The name of the sport for this market
        sportId:
          type: number
          description: The ID of the sport for this market
        leagueId:
          type: number
          description: The league ID for this market
        leagueLabel:
          type: string
          description: The name of the league for this market
        mainLine:
          type: boolean
          description: >-
            If this market is currently the main line or not. If the market is
            not a market with multiple lines, this field will not be present
        isQuarterLineMarket:
          type: boolean
          description: >-
            `true` if this market is a quarter-line market (Asian handicap or
            totals in .25 increments). See the [quarter lines changelog
            entry](/changelog#quarter-lines-gradual-rollout) for details.
        group1:
          type: string
          description: Indicator to the client of how to display this market
        chainVersion:
          type: string
          description: The chain version this market is deployed on
          example: SXR
        participantOneId:
          type: integer
          description: The team ID of participant one
        participantTwoId:
          type: integer
          description: The team ID of participant two
        __type:
          type: string
          description: The type of this object
          example: Market
        legs:
          type: array
          items:
            $ref: '#/components/schemas/Market'
          description: >-
            If this is a Parlay Market, this field will contain an array of the
            underlying Legs as a Market object
        group2:
          type: string
          description: Secondary display grouping indicator for the market
        teamOneMeta:
          type: object
          description: Extra metadata for team one
        teamTwoMeta:
          type: object
          description: Extra metadata for team two
        marketMeta:
          type: object
          description: Extra metadata for the market overall
        reportedDate:
          type: number
          description: UNIX timestamp of when the market was reported/settled
        outcome:
          type: integer
          description: >-
            The reported outcome once settled: `0` for void, `1` or `2` for each
            side.
        teamOneScore:
          type: number
          description: Final score for team one
        teamTwoScore:
          type: number
          description: Final score for team two
  securitySchemes:
    SxApiKey:
      type: apiKey
      in: header
      name: x-sx-api-key
      description: 'API key, sent as the `x-sx-api-key` header. '

````

This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.