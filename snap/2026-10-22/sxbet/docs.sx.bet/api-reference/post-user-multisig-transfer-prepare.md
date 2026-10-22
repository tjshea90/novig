> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Prepare a transfer

> Build the Safe transaction that moves funds to another proxy.

`POST /user/multisig/transfer/prepare` builds a transfer from your proxy to another deployed proxy.
Sign `typedData` and submit it to
[`POST /user/multisig/transfer/execute`](/api-reference/post-user-multisig-transfer-execute).

`toProxyWallet` must be a different registered, deployed proxy. Check it with
[`GET /user/multisig/proxy/{proxyAddress}`](/api-reference/get-user-multisig-proxy).

> [!WARNING]
>
> **Cancel open orders first.** This endpoint cannot be ran while you have `ACTIVE` orders.


## OpenAPI

````yaml POST /user/multisig/transfer/prepare
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
  /user/multisig/transfer/prepare:
    post:
      tags:
        - Wallet & Funding
      summary: Prepare a transfer
      description: >-
        Builds the Safe transaction that moves funds from your proxy to another
        deployed proxy.
      operationId: prepareUserMultisigTransfer
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              required:
                - safeAddress
                - proxyWallet
                - toProxyWallet
                - amount
              properties:
                safeAddress:
                  type: string
                  description: >-
                    Your proxy's Safe, from `multisigSafeAddress` on `GET
                    /user/proxy`.
                proxyWallet:
                  type: string
                  description: Your proxy wallet. Must be the proxy that this Safe governs.
                toProxyWallet:
                  type: string
                  description: >-
                    Recipient proxy. Must be a different registered, deployed
                    proxy.
                token:
                  type: string
                  description: >-
                    ERC-20 token to transfer. Omit, or pass the zero address,
                    for the native token.
                amount:
                  type: string
                  description: Amount in the token's base units, as a decimal string.
            example:
              safeAddress: '0x8E4c91A7D2b0f35e6A19c84d70B2e5F1a3c6D9e8'
              proxyWallet: '0x4361123dbdc1D812fdf7D27045aF358C9C8AA70A'
              toProxyWallet: '0xbcc6D643e4159A75ED1dB4e13330230B82F2AEe5'
              token: '0x1BC6326EA6aF2aB8E4b6Bc83418044B1923b2956'
              amount: '100000000'
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
                    $ref: '#/components/schemas/PreparedSafeTx'
              example:
                status: success
                data:
                  safeAddress: '0x8E4c91A7D2b0f35e6A19c84d70B2e5F1a3c6D9e8'
                  safeTxHash: >-
                    0x9c8f5b21e4d0a7c3f18b6e94d2a05c7318fa4b62d9e0173c5a8b2f4e6019d3c7
                  typedData:
                    primaryType: SafeTx
                    domain:
                      chainId: 4162
                      verifyingContract: '0x8E4c91A7D2b0f35e6A19c84d70B2e5F1a3c6D9e8'
                    message:
                      to: '0x4361123dbdc1D812fdf7D27045aF358C9C8AA70A'
                      value: '0'
                      operation: 0
                      nonce: 3
                  safeTransactionData:
                    to: '0x4361123dbdc1D812fdf7D27045aF358C9C8AA70A'
                    value: '0'
                    data: 0x
                    operation: 0
                    nonce: 3
        '400':
          description: >-
            `HAS_OPEN_ORDERS` while you have a `PENDING` or `ACTIVE` order. Also
            returned when `toProxyWallet` is your own proxy, is not a registered
            proxy, or is not deployed.
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
    PreparedSafeTx:
      type: object
      properties:
        safeAddress:
          type: string
          description: The Safe this transaction belongs to.
        safeTxHash:
          type: string
          description: Hash of the Safe transaction. Send it back on execute.
        typedData:
          type: object
          description: >-
            EIP-712 typed data for this Safe transaction. Sign this object with
            your signer key and send the signature to the matching execute
            route. 
          additionalProperties: true
        safeTransactionData:
          $ref: '#/components/schemas/SafeTransactionData'
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
  securitySchemes:
    SxApiKey:
      type: apiKey
      in: header
      name: x-sx-api-key
      description: 'API key, sent as the `x-sx-api-key` header. '

````

This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.