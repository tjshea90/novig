> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Sports

> List all sports available for betting on the SX Bet exchange.



## OpenAPI

````yaml GET /sports
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
  /sports:
    get:
      tags:
        - Sports Data
      summary: Get sports
      description: This endpoint retrieves all sports available on the exchange
      operationId: getSports
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
                      $ref: '#/components/schemas/Sport'
              example:
                status: success
                data:
                  - sportId: 1
                    label: Soccer
                  - sportId: 2
                    label: Basketball
                  - sportId: 3
                    label: American Football
components:
  schemas:
    Sport:
      type: object
      properties:
        sportId:
          type: integer
          description: Unique identifier for the sport
          example: 1
        label:
          type: string
          description: Human-readable sport name
          example: Soccer

````

This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.