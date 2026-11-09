> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Search fixtures

> Find active fixtures by team name on SX Bet.



## OpenAPI

````yaml GET /search
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
  /search:
    get:
      tags:
        - Sports Data
      summary: Search fixtures by team name
      description: >-
        Returns up to 8 active fixtures where either `teamOneName` or
        `teamTwoName` contains the search query. Search is case-insensitive,
        supports partial matches, and results are ordered by `gameTime`.
      operationId: searchEvents
      parameters:
        - name: query
          in: query
          required: true
          schema:
            type: string
            minLength: 3
            maxLength: 100
          description: >-
            A team name or partial team name. Must be between 3 and 100
            characters.
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
                      type: object
                      required:
                        - gameTime
                        - teamOneName
                        - teamTwoName
                        - providerEventId
                        - type
                        - eventId
                        - chainVersion
                        - league
                      properties:
                        gameTime:
                          type: string
                          description: Scheduled event time
                        teamOneName:
                          type: string
                          description: Name of the first team
                        teamTwoName:
                          type: string
                          description: Name of the second team
                        providerEventId:
                          type: string
                          description: Event ID from the data provider
                        provider:
                          type: string
                          description: Name of the data provider for this event
                        type:
                          type: array
                          items:
                            type: integer
                          description: List of available market type IDs for the event
                        eventId:
                          type: string
                          description: Event identifier
                        chainVersion:
                          type: string
                          description: Chain version associated with the event
                        league:
                          type: object
                          description: League information object
                          required:
                            - leagueId
                            - sportId
                            - label
                          properties:
                            leagueId:
                              type: integer
                            sportId:
                              type: integer
                            label:
                              type: string
              example:
                status: success
                data:
                  - gameTime: '2026-04-28T02:10:00.000Z'
                    teamOneName: Los Angeles Dodgers
                    teamTwoName: Miami Marlins
                    providerEventId: '17495682'
                    provider: LSPORT
                    type:
                      - 28
                      - 226
                      - 236
                      - 342
                      - 1618
                    eventId: L17495682
                    chainVersion: SXR
                    league:
                      leagueId: 171
                      sportId: 3
                      label: MLB
        '400':
          description: ''

````

This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.