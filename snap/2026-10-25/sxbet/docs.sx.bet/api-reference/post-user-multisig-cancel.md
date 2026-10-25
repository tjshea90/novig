> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Cancel a pending transaction

> Delete a pending Safe transaction that has not been executed

`POST /user/multisig/cancel` deletes a pending Safe transaction that has not met the threshold.

`signature` is an EIP-712 signature over primary type `DeleteRequest`, from the signer who proposed
that transaction.

| Field | Value |
| - | - |
| Domain `name` | `Safe Transaction Service` |
| Domain `version` | `1.0` |
| Domain `chainId` | The rollup chain id |
| Domain `verifyingContract` | The Safe address |
| Message `safeTxHash` | The pending transaction hash |
| Message `totp` | `floor(unix seconds / 3600)` |


## OpenAPI

````yaml POST /user/multisig/cancel
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
  /user/multisig/cancel:
    post:
      tags:
        - Wallet & Funding
      summary: Cancel a pending transaction
      description: Deletes a pending Safe transaction before it is submitted.
      operationId: cancelUserMultisig
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              required:
                - safeAddress
                - safeTxHash
                - signature
              properties:
                safeAddress:
                  type: string
                  description: Your proxy's Safe.
                safeTxHash:
                  type: string
                  description: >-
                    Hash of the pending transaction, from `GET
                    /user/multisig/{safeAddress}/info`.
                signature:
                  type: string
                  description: >-
                    EIP-712 `DeleteRequest` signature from the signer who
                    proposed the transaction.
            example:
              safeAddress: '0x8E4c91A7D2b0f35e6A19c84d70B2e5F1a3c6D9e8'
              safeTxHash: >-
                0x9c8f5b21e4d0a7c3f18b6e94d2a05c7318fa4b62d9e0173c5a8b2f4e6019d3c7
              signature: >-
                0xababababababababababababababababababababababababababababababababababababababababababababababababababababababababababababababababab
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
                      safeTxHash:
                        type: string
                      cancelled:
                        type: boolean
                        description: True when the pending transaction was deleted.
              example:
                status: success
                data:
                  safeTxHash: >-
                    0x9c8f5b21e4d0a7c3f18b6e94d2a05c7318fa4b62d9e0173c5a8b2f4e6019d3c7
                  cancelled: true
        '400':
          description: >-
            The signature is not from the proposer, the hash is unknown, or the
            address is invalid.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorNest'
              example:
                message: Could not cancel pending transaction
                error: Bad Request
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
        '403':
          description: The Safe is not on your account.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorNest'
              example:
                message: the target Safe is not associated with your account
                error: Forbidden
                statusCode: 403
        '409':
          description: Your proxy wallet is not deployed yet.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorNest'
              example:
                message: your proxy wallet is not deployed yet
                error: Conflict
                statusCode: 409
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