> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Execute a withdrawal

> Submit your signature for a prepared withdrawal.

`POST /user/multisig/withdraw/execute` submits the signature for the transaction from
[`POST /user/multisig/withdraw/prepare`](/api-reference/post-user-multisig-withdraw-prepare).

> [!WARNING]
>
> This route returns `400 HAS_OPEN_ORDERS` while you have `ACTIVE` orders.


## OpenAPI

````yaml POST /user/multisig/withdraw/execute
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
  /user/multisig/withdraw/execute:
    post:
      tags:
        - Wallet & Funding
      summary: Execute a withdrawal
      description: >-
        Submits your signature for the transaction from `POST
        /user/multisig/withdraw/prepare`.
      operationId: executeUserMultisigWithdraw
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              required:
                - safeAddress
                - safeTxHash
                - safeTransactionData
                - signature
                - signerAddress
              properties:
                safeAddress:
                  type: string
                  description: >-
                    Your proxy's Safe. Must be the Safe on your own deployed
                    proxy.
                safeTxHash:
                  type: string
                  description: '`safeTxHash` from the matching prepare response.'
                safeTransactionData:
                  $ref: '#/components/schemas/SafeTransactionData'
                signature:
                  type: string
                  description: Your signer key's signature over `typedData` from prepare.
                signerAddress:
                  type: string
                  description: >-
                    The address that produced `signature`. Must be the
                    authenticated address.
                glideSessionId:
                  type: string
                  minLength: 2
                  maxLength: 128
                  description: >-
                    Bridge session this withdrawal pays, when prepare was given
                    a `glideSessionId`.
            example:
              safeAddress: '0x8E4c91A7D2b0f35e6A19c84d70B2e5F1a3c6D9e8'
              safeTxHash: >-
                0x9c8f5b21e4d0a7c3f18b6e94d2a05c7318fa4b62d9e0173c5a8b2f4e6019d3c7
              safeTransactionData:
                to: '0x4361123dbdc1D812fdf7D27045aF358C9C8AA70A'
                value: '0'
                data: 0x
                operation: 0
                nonce: 3
              signature: >-
                0xababababababababababababababababababababababababababababababababababababababababababababababababababababababababababababababababab
              signerAddress: '0x52adf738AAD93c31f798a30b2C74D658e1E9a562'
              glideSessionId: glide_session_8f3a
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
                    $ref: '#/components/schemas/SafeTxProgress'
              example:
                status: success
                data:
                  safeTxHash: >-
                    0x9c8f5b21e4d0a7c3f18b6e94d2a05c7318fa4b62d9e0173c5a8b2f4e6019d3c7
                  confirmedBy:
                    - '0x52adf738AAD93c31f798a30b2C74D658e1E9a562'
                  signedCount: 1
                  requiredThreshold: 1
                  isReadyToExecute: true
                  isExecuted: false
                  executionTxHash: null
        '400':
          description: >-
            `HAS_OPEN_ORDERS` while you have a `PENDING` or `ACTIVE` order. Also
            returned when `signerAddress` is not the authenticated address, or
            the signed transaction is not a withdraw.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorNest'
              example:
                message: HAS_OPEN_ORDERS
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
    SafeTransactionData:
      type: object
      description: >-
        The Safe transaction built by a prepare route. Send this object back
        unchanged on the matching execute route.
      additionalProperties: true
      properties:
        to:
          type: string
          description: The contract the Safe calls.
        value:
          type: string
          description: Native value, in wei, as a decimal string. `0` for a token call.
        data:
          type: string
          description: Calldata. The execute route accepts only the call it is named for.
        operation:
          type: integer
          description: '`0` for a normal call.'
        nonce:
          type: integer
          description: >-
            The Safe's current nonce. A later prepare at this nonce replaces a
            transaction already pending there.
    SafeTxProgress:
      type: object
      properties:
        safeTxHash:
          type: string
          description: The Safe transaction this signature was applied to.
        confirmedBy:
          type: array
          items:
            type: string
          description: Signer addresses that have signed so far.
        signedCount:
          type: integer
          description: How many signers have signed.
        requiredThreshold:
          type: integer
          description: >-
            Signatures required before the transaction can be submitted. A new
            Safe starts at 1.
        isReadyToExecute:
          type: boolean
          description: >-
            True when enough signers have signed and the transaction has not
            been submitted yet. The exchange then submits it.
        isExecuted:
          type: boolean
          description: True once the transaction has landed on chain.
        executionTxHash:
          type: string
          nullable: true
          description: On-chain transaction hash once executed, otherwise null.
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