> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Active markets

> Retrieve all active betting markets on the SX Bet exchange, with filters for sport, league, and event.

<Note>To retrieve odds for a particular market, you must query the orderbook separately — [`GET /orderbook-v3/snapshot/market`](/api-reference/get-market-orderbook-snapshot) </Note>

<Info>Only one of `type` and `betGroup` can be present. Not both.</Info>


## OpenAPI

````yaml GET /markets/active
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
  /markets/active:
    get:
      tags:
        - Markets
      summary: Get active markets
      description: >-
        This endpoint retrieves active markets on the exchange. It does not
        return markets that have been settled or reported.
      operationId: getActiveMarkets
      parameters:
        - name: onlyMainLine
          in: query
          required: false
          schema:
            type: boolean
          description: >-
            Return only the main line for market types that have lines. See
            [Markets and
            events](/developers/markets-and-events#lines-and-main-lines) for how
            types without lines are affected.
        - name: eventId
          in: query
          required: false
          schema:
            type: string
          description: >-
            If set, it will only include markets for a particular sportXeventId.
            Pass the full sportXeventId, including the provider letter, for
            example L12003787
        - name: leagueId
          in: query
          required: false
          schema:
            type: integer
          description: If set, it will only include markets for a particular league ID
        - name: sportIds
          in: query
          required: false
          schema:
            type: array
            items:
              type: integer
          description: >-
            If set, it will only include markets for particular sport IDs (comma
            separated)
        - name: liveOnly
          in: query
          required: false
          schema:
            type: boolean
          description: >-
            If set, it will only include markets that are currently available
            for in-play betting
        - name: betGroup
          in: query
          required: false
          schema:
            type: string
          description: If set, it will only include markets for a particular bet group
        - name: type
          in: query
          required: false
          schema:
            type: array
            items:
              type: integer
          description: >-
            Comma-separated market type ids. See [Market
            types](/api-reference/market-types#the-table) for the list
        - name: paginationKey
          in: query
          required: false
          schema:
            type: string
          description: >-
            Pass the previous response's nextKey as paginationKey to get the
            next page.
        - name: pageSize
          in: query
          required: false
          schema:
            type: integer
          description: >-
            Used for pagination. Requested page size. Each call will only return
            up to this amount of records. Maximum of 100
        - name: gameTime
          in: query
          required: false
          schema:
            type: integer
          description: >-
            If set, only return markets for games starting at or after this UNIX
            timestamp
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
                      markets:
                        type: array
                        items:
                          $ref: '#/components/schemas/Market'
                      nextKey:
                        type: string
                        description: >-
                          Pass as `paginationKey` to get the next page.  — a
                          short page is not the last page, and the final
                          `nextKey` can point at an empty page.
              example:
                status: success
                data:
                  markets:
                    - status: ACTIVE
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
                    - status: ACTIVE
                      marketHash: >-
                        0x531e9e6d6469f6323d464340e91840b3666e4e43cf04b8c2a52c2ac0d67839fb
                      outcomeOneName: Golden State Warriors +6.5
                      outcomeTwoName: Minnesota Timberwolves -6.5
                      outcomeVoidName: NO_GAME_OR_EVEN
                      teamOneName: Golden State Warriors
                      teamTwoName: Minnesota Timberwolves
                      type: 342
                      gameTime: 1773453600
                      line: 6.5
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
                      mainLine: true
                      __type: Market
                  nextKey: 60c7b8f54da0ad001aa3261c
components:
  schemas:
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

````

This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.