> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Check a proxy address

> See whether a proxy address is registered and deployed.

`GET /user/multisig/proxy/{proxyAddress}` reports whether an address is a registered proxy and
whether that proxy is deployed. It does not return the owner.

Use it before [`POST /user/multisig/transfer/prepare`](/api-reference/post-user-multisig-transfer-prepare)
to check the recipient.


## OpenAPI

````yaml GET /user/multisig/proxy/{proxyAddress}
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
  /user/multisig/proxy/{proxyAddress}:
    get:
      tags:
        - Wallet & Funding
      summary: Check a proxy address
      description: >-
        Whether `proxyAddress` is a registered proxy and whether that proxy is
        deployed. Does not reveal the owner. Use it to check a transfer
        recipient before `POST /user/multisig/transfer/prepare`.
      operationId: getUserMultisigProxy
      parameters:
        - name: proxyAddress
          in: path
          required: true
          schema:
            type: string
          description: Proxy wallet address to check.
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
                      address:
                        type: string
                        description: The address you asked about, checksummed.
                      exists:
                        type: boolean
                        description: Whether a proxy is registered at this address.
                      deployed:
                        type: boolean
                        description: >-
                          Whether that proxy is deployed. False when `exists` is
                          false.
              example:
                status: success
                data:
                  address: '0xbcc6D643e4159A75ED1dB4e13330230B82F2AEe5'
                  exists: true
                  deployed: true
        '400':
          description: The address is not a valid proxy address.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorNest'
              example:
                message: invalid proxyAddress
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