> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Fixtures

> Retrieve active fixtures (games and events) for a league on SX Bet.

<Note>This endpoint only returns fixtures that have a status of either 1, 2, 6, 7, 8, or 9. See the [fixture statuses](/api-reference/get-fixture-status) page for more details.</Note>


## OpenAPI

````yaml GET /fixture/active
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
  /fixture/active:
    get:
      tags:
        - Sports Data
      summary: Get active fixtures
      description: >-
        This endpoint returns current active fixtures for a particular league. A
        fixture can also be thought of as an event and multiple markets are
        under a particular event.
      operationId: getActiveFixtures
      parameters:
        - name: leagueId
          in: query
          required: true
          schema:
            type: integer
          description: The ID of the league
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
                      $ref: '#/components/schemas/Fixture'
              example:
                status: success
                data:
                  - participantOneName: Nevada Wolf Pack
                    participantTwoName: North Dakota State
                    startDate: '2020-11-25T20:00:00.000Z'
                    status: 1
                    leagueId: 2
                    leagueLabel: NCAA
                    sportId: 1
                    eventId: L6206070
components:
  schemas:
    Fixture:
      type: object
      properties:
        participantOneName:
          type: string
          description: >-
            The first participant in the fixture. Present if it's a
            two-participant event.
        participantTwoName:
          type: string
          description: >-
            The second participant in the fixture. Present if it's a
            two-participant event.
        participants:
          type: array
          items:
            type: string
          description: >-
            All the participants in the fixture. Present if it's an
            n-participant event.
        startDate:
          type: string
          description: The start date of the event in UTC time
        status:
          type: number
          description: >-
            The status of the fixture. See the [status
            table](/api-reference/get-fixture-status) for more details.
        leagueId:
          type: number
          description: The ID of the league this fixture belongs to
        leagueLabel:
          type: string
          description: The name of the league this fixture belongs to
        sportId:
          type: number
          description: The ID of the sport this fixture belongs to
        eventId:
          type: string
          description: The ID of this fixture

````

This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.