> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Teams

> Get all teams within a specific league on SX Bet.



## OpenAPI

````yaml GET /teams
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
  /teams:
    get:
      tags:
        - Sports Data
      summary: Get teams
      description: >-
        Returns all teams supported by SX.bet. Results are paginated starting at
        page 0 with a maximum of 500 records per page.
      operationId: getTeams
      parameters:
        - name: sportId
          in: query
          required: false
          schema:
            type: integer
          description: Filter teams by sport ID.
        - name: page
          in: query
          required: false
          schema:
            type: integer
            default: 0
          description: Page number for pagination, starting at 0.
        - name: perPage
          in: query
          required: false
          schema:
            type: integer
            maximum: 500
          description: Number of results per page. Default is 20. Maximum is 500.
        - name: id
          in: query
          required: false
          schema:
            type: integer
          description: Filter by a specific team ID.
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
                      $ref: '#/components/schemas/Team'
              example:
                status: success
                data:
                  - id: 275
                    name: Boston Celtics
                    sport:
                      sportId: 1
                      label: Basketball
                      active: true
                  - id: 276
                    name: Miami Heat
                    sport:
                      sportId: 1
                      label: Basketball
                      active: true
        '400':
          description: ''
components:
  schemas:
    Team:
      type: object
      properties:
        id:
          type: number
          description: The ID for this team
          example: 725
        name:
          type: string
          description: The name of this team
          example: Juventus
        sport:
          type: object
          description: The sport this team belongs to
          properties:
            sportId:
              type: integer
              description: Unique identifier for the sport
              example: 1
            label:
              type: string
              description: Human-readable sport name
              example: Basketball
            active:
              type: boolean
              description: Whether this sport is currently active on SX.bet
              example: true

````

This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.