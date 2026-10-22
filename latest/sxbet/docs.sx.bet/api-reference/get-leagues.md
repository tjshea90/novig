> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Leagues

> List all leagues supported by the SX Bet exchange.



## OpenAPI

````yaml GET /leagues
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
  /leagues:
    get:
      tags:
        - Sports Data
      summary: Get leagues
      description: This endpoint returns all the leagues supported by SX.bet
      operationId: getLeagues
      parameters:
        - name: sportId
          in: query
          required: false
          schema:
            type: integer
          description: Only return leagues for this particular sport ID.
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
                      $ref: '#/components/schemas/League'
              example:
                status: success
                data:
                  - leagueId: 1
                    label: NBA
                    sportId: 1
                    active: true
components:
  schemas:
    League:
      type: object
      properties:
        leagueId:
          type: integer
          description: The ID for this league
          example: 1
        label:
          type: string
          description: The name of this league
          example: English Premier League
        sportId:
          type: integer
          description: The ID of the sport this league corresponds to
          example: 1
        active:
          type: boolean
          description: Whether or not the league is active on SX.bet currently
          example: true

````

This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.