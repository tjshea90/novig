> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Live scores

> Get real-time live scores for active events on SX Bet.



## OpenAPI

````yaml GET /live-scores
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
  /live-scores:
    get:
      tags:
        - Sports Data
      summary: Get live scores
      description: This endpoint retrieves live scores for a particular event ID.
      operationId: getLiveScores
      parameters:
        - name: sportXEventIds
          in: query
          required: true
          schema:
            type: array
            items:
              type: string
          description: An array of event IDs
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
                    type: array
                    items:
                      $ref: '#/components/schemas/LiveScore'
              example:
                status: success
                data:
                  - sportId: 3
                    leagueId: 171
                    sportXeventId: L17406328
                    currentPeriod: 5th Inning
                    periodTime: '-1'
                    teamOneScore: 4
                    teamTwoScore: 1
                    periods:
                      - label: 1st Inning
                        isFinished: true
                        teamOneScore: '4'
                        teamTwoScore: '0'
                      - label: 4th Inning
                        isFinished: true
                        teamOneScore: '0'
                        teamTwoScore: '1'
                      - label: 3rd Inning
                        isFinished: true
                        teamOneScore: '0'
                        teamTwoScore: '0'
                      - label: 2nd Inning
                        isFinished: true
                        teamOneScore: '0'
                        teamTwoScore: '0'
                      - label: 5th Inning
                        isFinished: false
                        teamOneScore: '0'
                        teamTwoScore: '0'
                    extra: >-
                      [{"Name":"Strikes","Value":"0"},{"Name":"Balls","Value":"0"},{"Name":"Outs","Value":"0"},{"Name":"Turn","Value":"2"},{"Name":"Bases","Value":"0/0/0"}]
                    createdAt: '2026-03-13T17:05:53.092Z'
                    updatedAt: '2026-03-13T18:21:26.741Z'
components:
  schemas:
    LiveScore:
      type: object
      properties:
        currentPeriod:
          type: string
          description: The current period label
        extra:
          type: string
          description: Extra data for this match
        leagueId:
          type: integer
          description: The league ID for this match
        periodTime:
          type: string
          description: >-
            The time in the period. If it's `"-1"`, it is not available or the
            game is finished
        periods:
          type: array
          items:
            type: object
            properties:
              label:
                type: string
                description: The current period name
              isFinished:
                type: boolean
                description: '`true` if the period is over'
              teamOneScore:
                type: string
                description: The score of team one in this period
              teamTwoScore:
                type: string
                description: The score of team two in this period
          description: All of the periods in the match
        sportId:
          type: integer
          description: The ID of the sport for this match
        teamOneScore:
          type: integer
          description: The current score for team one
        teamTwoScore:
          type: integer
          description: The current score for team two
        sportXeventId:
          type: string
          description: The event ID for this match
        createdAt:
          type: string
          description: ISO 8601 timestamp of when this record was created
        updatedAt:
          type: string
          description: ISO 8601 timestamp of when this record was last updated

````

This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.