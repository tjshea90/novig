> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Get your ledger

> Get your ledger from the SX Bet API.

`GET /ledger-v3` is an audit trail for your account. It lists all bets, inflows, and outflows.

| Event Type | Meaning |
| - | - |
| `DEPOSIT` | Money in |
| `WITHDRAWAL` | Money out |
| `SETTLEMENT` | A bet settled |
| `PAYOUT` | Winnings paid |
| `REFUND` | Money returned — including [capital-efficiency](/developers/capital-efficiency) refunds |
| `ADJUSTMENT` | A manual or corrective movement |

**`eventStatus`** — `PENDING`, `SUCCESS`, `FAILED`.

**`depositType`** — `BRIDGE`, `FROM_EOA`, `PROXY_TO_PROXY`.

**`withdrawalType`** — `BRIDGE`, `PROXY_TO_PROXY`.


## OpenAPI

````yaml GET /ledger-v3
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
  /ledger-v3:
    get:
      tags:
        - Connection
      summary: Get your ledger
      description: >-
        The audit trail behind your balance: one entry per movement, newest
        first. Each amount is a signed base-unit delta (how the balance
        changed), not a running total, so values can be negative. Optional
        fields are omitted, not null.
      operationId: getLedgerV3
      parameters:
        - name: eventType
          in: query
          required: false
          schema:
            type: string
            enum:
              - DEPOSIT
              - WITHDRAWAL
              - SETTLEMENT
              - PAYOUT
              - FEE
              - REFUND
              - ADJUSTMENT
          description: Filter to a single event type.
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
                      entries:
                        type: array
                        items:
                          type: object
                          properties:
                            eventType:
                              type: string
                              enum:
                                - DEPOSIT
                                - WITHDRAWAL
                                - SETTLEMENT
                                - PAYOUT
                                - REFUND
                                - ADJUSTMENT
                            eventStatus:
                              type: string
                              enum:
                                - PENDING
                                - SUCCESS
                                - FAILED
                              description: Lifecycle of the movement.
                            tokenAddress:
                              type: string
                              description: Collateral token this entry is denominated in.
                            escrowAddress:
                              type: string
                              description: Escrow contract serving the token.
                            availableAmount:
                              type: string
                              description: Signed base-unit delta on available balance.
                            pendingAvailableAmount:
                              type: string
                              description: >-
                                Signed base-unit delta on pending-available
                                balance.
                            escrowedAmount:
                              type: string
                              description: Signed base-unit delta on escrowed balance.
                            pendingEscrowAmount:
                              type: string
                              description: >-
                                Signed base-unit delta on pending-escrow
                                balance.
                            transactionHash:
                              type: string
                              description: >-
                                On-chain transaction hash, once the movement is
                                on chain. Omitted while pending.
                            tradeId:
                              type: string
                              description: >-
                                Bet this movement belongs to, when the event is
                                a settlement, payout, fee, or refund. Omitted
                                otherwise.
                            orderId:
                              type: string
                              description: >-
                                Order this movement belongs to, when applicable.
                                Omitted otherwise.
                            marketHash:
                              type: string
                              description: >-
                                Market this movement belongs to, when
                                applicable. Omitted otherwise.
                            depositType:
                              type: string
                              enum:
                                - BRIDGE
                                - FROM_EOA
                                - PROXY_TO_PROXY
                              description: Present on DEPOSIT entries. Omitted otherwise.
                            withdrawalType:
                              type: string
                              enum:
                                - BRIDGE
                                - PROXY_TO_PROXY
                              description: >-
                                Present on WITHDRAWAL entries. Omitted
                                otherwise.
                            createdAt:
                              type: string
                              format: date-time
                              description: When the entry was written.
                      nextKey:
                        type: string
                        description: >-
                          Pass as `nextKey` to retrieve the next page. Absent —
                          not null — on the last page.
              example:
                status: success
                data:
                  entries:
                    - eventType: DEPOSIT
                      eventStatus: SUCCESS
                      tokenAddress: '0x1BC6326EA6aF2aB8E4b6Bc83418044B1923b2956'
                      escrowAddress: '0x0Db3aBA7de0BCb41FF99D35306f366cFFEEede38'
                      availableAmount: '514000007'
                      pendingAvailableAmount: '-514000007'
                      escrowedAmount: '0'
                      pendingEscrowAmount: '0'
                      transactionHash: >-
                        0x64307e2f35d0a81b0f44e9043e99cf01def3b2f079fb68cf2c5a942b88d95e9a
                      depositType: FROM_EOA
                      createdAt: '2026-07-10T12:00:07.000Z'
                    - eventType: DEPOSIT
                      eventStatus: PENDING
                      tokenAddress: '0x1BC6326EA6aF2aB8E4b6Bc83418044B1923b2956'
                      escrowAddress: '0x0Db3aBA7de0BCb41FF99D35306f366cFFEEede38'
                      availableAmount: '0'
                      pendingAvailableAmount: '514000007'
                      escrowedAmount: '0'
                      pendingEscrowAmount: '0'
                      depositType: FROM_EOA
                      createdAt: '2026-07-10T12:00:00.000Z'
                  nextKey: eyJpZCI6IjQyIn0=
        '400':
          description: ''
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorNest'
              example:
                message: Bad Request
                statusCode: 400
        '401':
          description: ''
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorNest'
              example:
                message: BAD_AUTH
                error: Unauthorized
                statusCode: 401
        '503':
          description: ''
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorNest'
              example:
                message: Service Unavailable
                statusCode: 503
      security:
        - SxApiKey: []
components:
  schemas:
    ErrorNest:
      description: Most common shape.
      type: object
      properties:
        message:
          type: string
          example: INVALID_USER
        error:
          type: string
          example: Unauthorized
        statusCode:
          type: integer
          example: 401
  securitySchemes:
    SxApiKey:
      type: apiKey
      in: header
      name: x-sx-api-key
      description: 'API key, sent as the `x-sx-api-key` header. '

````

This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.