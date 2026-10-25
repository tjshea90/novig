> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Get de-anonymized positions

> Read one bettor's positions, counting only the bets they placed while de-anonymized.

`GET /positions-v3/public` reads a single bettor's exposure, aggregated per market. Only bets that bettor placed while de-anonymized are counted.


## OpenAPI

````yaml GET /positions-v3/public
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
  /positions-v3/public:
    get:
      tags:
        - Trades
      summary: Get a bettor's de-anonymized positions
      description: >-
        One bettor's positions, aggregated per market, counting only the bets
        they placed while de-anonymized. Anonymized bets are excluded from every
        figure, so a bettor who was de-anonymized and later became anonymized
        still reads here — showing what they exposed at the time, and nothing
        since. Realised profit is never exposed on this route.


        Requires the address you want to read: there is no query for every
        bettor on a market.
      operationId: getPublicPositionsV3
      parameters:
        - name: userAddress
          in: query
          required: true
          schema:
            type: string
          description: The bettor to read, as a checksummed address. Required.
        - name: status
          in: query
          required: true
          schema:
            type: string
          description: >-
            Bet statuses to include, comma-separated. Open positions are
            `MATCHED,LOCKED`; history is `SETTLED`.
        - name: eventId
          in: query
          required: false
          schema:
            type: string
            minLength: 2
            maxLength: 64
          description: Restrict to one event (prefixed form, e.g. `L12003787`).
        - name: sortBy
          in: query
          required: false
          schema:
            type: string
            enum:
              - gameTime
              - maxLoss
          description: Sort the full result before paging. Defaults to `gameTime`.
        - name: sortAsc
          in: query
          required: false
          schema:
            type: boolean
            default: false
          description: >-
            Sort ascending. Keep `sortBy` and `sortAsc` fixed for the whole walk
            — changing either with a cursor in hand is rejected.
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
                      userAddress:
                        type: string
                        description: Echoes the address you requested, checksummed.
                      positions:
                        type: array
                        items:
                          $ref: '#/components/schemas/PublicPositionV3'
                      nextKey:
                        type: string
                        description: >-
                          Pass as `nextKey` to retrieve the next page. Absent —
                          not null — on the last page.
              example:
                status: success
                data:
                  userAddress: '0x685D1D76A2771FEd07297c83e723eAc320a30d7a'
                  positions:
                    - marketHash: >-
                        0x1fec4ed9b71c2f812db900af7d12446158a0eda56041106f99551c496efd3266
                      market:
                        marketHash: >-
                          0x1fec4ed9b71c2f812db900af7d12446158a0eda56041106f99551c496efd3266
                        teamOneName: Toronto Raptors
                        teamTwoName: Charlotte Hornets
                      totalStake: '1000000'
                      maxWin: '1500000'
                      maxLoss: '-1000000'
                      isOutcomeOneMaxWin: true
                      odds:
                        outcomeOne: '0.4'
                        outcomeTwo: '0.6'
                      tradeCount: 1
                      betTime: '2026-09-18T19:58:21.751Z'
                      updatedAt: '2026-09-18T19:58:22.100Z'
        '404':
          description: >-
            The bettor has no de-anonymized positions. A bettor who keeps their
            activity anonymized, one who has never bet, and an address that does
            not exist all answer the same way — this route will not confirm that
            an account exists. Returned on the first page only; once you hold a
            cursor, later pages come back empty rather than 404.
      security:
        - SxApiKey: []
components:
  schemas:
    PublicPositionV3:
      description: Aggregated per market, counting de-anonymized bets only.
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
          description: >-
            Sum of the stake of every de-anonymized bet in this market for this
            user, in base units. Anonymized bets are excluded.
        maxWin:
          type: string
          description: >-
            Best-case profit: best return minus total stake, in base units. The
            stake is already excluded.
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
        tradeCount:
          type: integer
          description: How many bets rolled into this row.
        betTime:
          type: string
          description: ISO 8601 timestamp of the earliest bet in this group of trades.
        updatedAt:
          type: string
          description: ISO 8601. The latest mutation — a maximum across the group.
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