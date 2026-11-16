> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# List your users

> The users you have created, newest first.

Partner keys only. See [Partner API](https://docs.sx.bet/developers/partner-api.md) for the full flow.

`proxyDeployed` turns `true` once the user's proxy wallet is deployed.


## OpenAPI

````yaml GET /partner/users
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
  - name: Partner API
    description: Create and list your platform's users (partner keys only)
paths:
  /partner/users:
    get:
      tags:
        - Partner API
      summary: List your users
      description: >-
        Lists the users you have created, newest first. Requires a partner key
        with the read permission.
      operationId: listPartnerUsers
      parameters:
        - name: address
          in: query
          required: false
          schema:
            type: string
          description: Return only the user with this wallet address.
        - name: perPage
          in: query
          required: false
          schema:
            type: integer
            minimum: 1
            maximum: 100
            default: 50
          description: Users per page, 1–100.
        - name: nextKey
          in: query
          required: false
          schema:
            type: string
          description: The `nextKey` from the previous page. Pass it back unchanged.
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
                      users:
                        type: array
                        items:
                          $ref: '#/components/schemas/PartnerUser'
                      nextKey:
                        type: string
                        description: >-
                          Pass to the next request for the following page.
                          Absent on the last page.
                    required:
                      - users
              example:
                status: success
                data:
                  users:
                    - address: '0x8a1F9c2E4b7D3a6C5e0B9d8F2A4c6E1b3D5f7A90'
                      email: trader+1324@yourplatform.com
                      proxyWalletAddress: '0x4361123dbdc1D812fdf7D27045aF358C9C8AA70A'
                      status: ACTIVE
                      proxyDeployed: true
                      createdAt: 1794316600
                  nextKey: >-
                    eyJjcmVhdGVkQXQiOiIyMDI2LTExLTExIDE0OjU2OjQwLjEyMzQ1NiswMCIsInVzZXJJZCI6ImFiYyJ9
        '400':
          description: ''
          content:
            application/json:
              schema:
                oneOf:
                  - $ref: '#/components/schemas/ErrorNestArray'
                  - $ref: '#/components/schemas/ErrorNest'
              example:
                message: Invalid nextKey
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
          description: ''
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorNest'
              example:
                message: PARTNER_DISABLED
                error: Forbidden
                statusCode: 403
        '404':
          description: ''
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorNest'
              example:
                message: Not Found
                statusCode: 404
      security:
        - SxApiKey: []
components:
  schemas:
    PartnerUser:
      type: object
      properties:
        address:
          type: string
          description: The user's wallet address.
        email:
          type: string
          nullable: true
          description: The email you gave when creating the user.
        proxyWalletAddress:
          type: string
          description: >-
            The user's proxy wallet. Fund this address once `proxyDeployed` is
            `true`.
        status:
          type: string
          enum:
            - ACTIVE
            - SUSPENDED
          description: '`SUSPENDED` while SX Bet has suspended the account.'
        proxyDeployed:
          type: boolean
          description: Whether the proxy wallet is deployed.
        createdAt:
          type: integer
          description: When the user was created, in Unix seconds.
      required:
        - address
        - email
        - proxyWalletAddress
        - status
        - proxyDeployed
        - createdAt
    ErrorNestArray:
      description: >-
        Validation errors. Same envelope as ErrorNest but `message` is an array
        — normalise both.
      type: object
      properties:
        message:
          type: array
          items:
            type: string
          example:
            - 'range must be one of the following values: 1, 7, 30'
        error:
          type: string
          example: Bad Request
        statusCode:
          type: integer
          example: 400
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