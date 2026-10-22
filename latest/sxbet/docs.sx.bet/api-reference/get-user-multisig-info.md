> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Safe info

> Signers, threshold, and the pending transaction on your proxy's Safe.

`GET /user/multisig/{safeAddress}/info` is the state of the Safe on your proxy.
`safeAddress` is `multisigSafeAddress` from [`GET /user/proxy`](/api-reference/get-user-proxy).

A Safe holds at most one pending transaction.


## OpenAPI

````yaml GET /user/multisig/{safeAddress}/info
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
  /user/multisig/{safeAddress}/info:
    get:
      tags:
        - Wallet & Funding
      summary: Get Safe info
      description: >-
        Signers, threshold, nonce, and the single pending transaction on your
        proxy's Safe. 
      operationId: getUserMultisigInfo
      parameters:
        - name: safeAddress
          in: path
          required: true
          schema:
            type: string
          description: Your proxy's Safe, from `GET /user/proxy`.
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
                    $ref: '#/components/schemas/SafeInfo'
              example:
                status: success
                data:
                  safeAddress: '0x8E4c91A7D2b0f35e6A19c84d70B2e5F1a3c6D9e8'
                  owners:
                    - '0x52adf738AAD93c31f798a30b2C74D658e1E9a562'
                  threshold: 1
                  nonce: 3
                  pendingTransaction: null
        '400':
          description: The address is not a valid Safe address.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorNest'
              example:
                message: invalid safeAddress
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
    SafeInfo:
      type: object
      properties:
        safeAddress:
          type: string
        owners:
          type: array
          items:
            type: string
          description: Current signers on the Safe.
        threshold:
          type: integer
          description: Signatures required. A new Safe starts at 1.
        nonce:
          type: integer
          description: The Safe's current nonce.
        pendingTransaction:
          nullable: true
          allOf:
            - $ref: '#/components/schemas/PendingSafeTx'
          description: >-
            The one transaction waiting at the current nonce, or null when
            nothing is pending.
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
    PendingSafeTx:
      type: object
      properties:
        safeTxHash:
          type: string
        nonce:
          type: integer
          description: >-
            Nonce this transaction occupies. A new prepare at the same nonce
            replaces it.
        to:
          type: string
        value:
          type: string
        data:
          type: string
        operation:
          type: integer
        kind:
          type: string
          enum:
            - withdraw
            - transfer
            - approveAsset
            - add-signer
            - remove-signer
            - set-threshold
            - unknown
          description: What the pending call does, decoded from its selector.
        confirmedBy:
          type: array
          items:
            type: string
          description: Signers who have signed.
        confirmationsRequired:
          type: integer
        isExecutable:
          type: boolean
          description: True when `confirmedBy` has reached `confirmationsRequired`.
        typedData:
          type: object
          description: >-
            EIP-712 typed data for this pending transaction. A signer who still
            needs to sign signs this.
          additionalProperties: true
  securitySchemes:
    SxApiKey:
      type: apiKey
      in: header
      name: x-sx-api-key
      description: 'API key, sent as the `x-sx-api-key` header. '

````

This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.