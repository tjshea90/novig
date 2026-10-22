> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Fixture status

> Check the current status of specific fixtures by event ID on SX Bet.



## OpenAPI

````yaml GET /fixture/status
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
  /fixture/status:
    get:
      tags:
        - Sports Data
      summary: Get fixture status
      description: This endpoint returns the status of the passed event IDs.
      operationId: getFixtureStatus
      parameters:
        - name: sportXEventIds
          in: query
          required: true
          schema:
            type: array
            items:
              type: string
          description: An array of event IDs (comma separated), maximum 30.
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
                    description: >-
                      Mapping from event ID to an object containing the fixture
                      status code.
                    additionalProperties:
                      type: object
                      properties:
                        status:
                          type: integer
                          description: >-
                            The status code for this fixture. See the [status
                            table](/api-reference/fixture-statuses) for more
                            details.
              example:
                status: success
                data:
                  L18016511:
                    status: 1
        '400':
          description: ''

````

This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.