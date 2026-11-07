> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Get your balance

> Get your wallet balance from the SX Bet API.

One row per token and escrow pair.

| Field | Meaning |
| - | - |
| `availableAmount` | Settled funds not locked in bets |
| `escrowedAmount` | Collateral locked behind open bets |
| `pendingAvailableAmount` | Signed delta on available (in-flight bet, deposit, or withdrawal) |
| `pendingEscrowAmount` | Signed delta on escrow (collateral entering or leaving) |

Spendable balance for orders: `availableAmount`.


## OpenAPI

````yaml GET /user/balance-v3
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
  /user/balance-v3:
    get:
      tags:
        - Connection
      summary: Get your balance
      description: >-
        Your balances, one row per (token, escrow) pair. Every amount is a
        base-unit string. An empty array is normal for an account that has never
        been funded.
      operationId: getUserBalanceV3
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
                      balances:
                        type: array
                        items:
                          $ref: '#/components/schemas/BalanceV3'
                        description: >-
                          One row per (token, escrow) pair. An empty array is a
                          normal 200 for an account that has never been funded.
              example:
                status: success
                data:
                  balances:
                    - userAddress: '0xbcc6D643e4159A75ED1dB4e13330230B82F2AEe5'
                      wallet: '0x4361123dbdc1D812fdf7D27045aF358C9C8AA70A'
                      tokenAddress: '0x1BC6326EA6aF2aB8E4b6Bc83418044B1923b2956'
                      escrowAddress: '0x0Db3aBA7de0BCb41FF99D35306f366cFFEEede38'
                      availableAmount: '1028000014'
                      pendingAvailableAmount: '0'
                      escrowedAmount: '0'
                      pendingEscrowAmount: '0'
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
    BalanceV3:
      type: object
      properties:
        userAddress:
          type: string
          description: Your EOA.
        wallet:
          type: string
          description: Your proxy — the balance belongs to this address.
        tokenAddress:
          type: string
          description: >-
            The ERC-20 these amounts are denominated in. Rows are keyed by
            `(tokenAddress, escrowAddress)`.
        escrowAddress:
          type: string
          description: The escrow contract these amounts sit in.
        availableAmount:
          type: string
          description: Available balance.
        pendingAvailableAmount:
          type: string
          description: >-
            Signed delta on `availableAmount` for a movement that has not
            settled yet.
        escrowedAmount:
          type: string
          description: Escrowed amount.
        pendingEscrowAmount:
          type: string
          description: >-
            Signed delta on `escrowedAmount` for a movement that has not settled
            yet.
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