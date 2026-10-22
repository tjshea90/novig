> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Active leagues

> Get leagues that currently have active markets on the SX Bet exchange.



## OpenAPI

````yaml GET /leagues/active
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
  /leagues/active:
    get:
      tags:
        - Sports Data
      summary: Get active leagues
      description: >-
        This endpoint returns all the currently active leagues with markets in
        them. Note that this endpoint is only updated every 10m. The response
        format is the same as the get leagues endpoint, with an additional field
        `eventsByType` which maps the number of unique events within a
        particular bet group (for example, `game-lines` or `outright-winner`).
      operationId: getActiveLeagues
      parameters:
        - name: sportId
          in: query
          required: false
          schema:
            type: integer
          description: Only return active leagues under this sport
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
                      properties:
                        leagueId:
                          type: integer
                          description: The ID for this league
                          example: 1236
                        label:
                          type: string
                          description: The name of this league
                          example: Portugal Primeira Liga
                        sportId:
                          type: integer
                          description: The ID of the sport this league corresponds to
                          example: 5
                        eventsByType:
                          type: object
                          additionalProperties:
                            type: integer
                          description: >-
                            Maps bet group types to the number of unique events
                            within that type
              example:
                status: success
                data:
                  - leagueId: 1236
                    label: Portugal Primeira Liga
                    sportId: 5
                    eventsByType:
                      game-lines: 6
                      1X2: 5

````

This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.