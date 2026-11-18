> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Create a user

> Create an SX Bet account for a new wallet and get its API key.

Partner keys only. See [Partner API](https://docs.sx.bet/developers/partner-api.md) for the full flow.

> [!WARNING]
>
> `apiKey.key` is returned only in this response and cannot be fetched again. Store it before doing
> anything else.

The address must be new to SX Bet. An address that already has an account is refused, and nothing
changes:

| Code | Status | Meaning |
| - | - | - |
| `USER_ALREADY_EXISTS` | 409 | You already created a user for this address. |
| `ADDRESS_ALREADY_REGISTERED` | 409 | The address has an SX Bet account that is not yours. |
| `EMAIL_TAKEN` | 409 | Another SX Bet account uses this email. |
| `INVALID_ATTESTATION` | 400 | The signature is not the address's signature over [the attestation](https://docs.sx.bet/api-reference/get-partner-attestation.md) for your partner ID and this environment. |
| `INVALID_API_KEY_PERMISSIONS` | 400 | `apiKeyPermissions` contains a scope users cannot be given. |
| `PARTNER_DISABLED` | 403 | Your partner account is disabled. Contact us. |
| `INSUFFICIENT_API_KEY_PERMISSIONS` | 403 | The key is not a partner key, for example a user's key. |


## OpenAPI

````yaml POST /partner/users
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
    post:
      tags:
        - Partner API
      summary: Create a user
      description: >-
        Creates an SX Bet account for a new wallet, links it to you, starts
        deploying its proxy wallet and returns its API key. The key is shown
        only once. Requires a partner key with the create permission.
      operationId: createPartnerUser
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              properties:
                address:
                  type: string
                  description: >-
                    The user's wallet address. It must not already have an SX
                    Bet account.
                userEmail:
                  type: string
                  format: email
                  description: >-
                    The user's email. Must not be used by another SX Bet
                    account.
                apiKeyPermissions:
                  type: object
                  additionalProperties:
                    type: array
                    items:
                      type: string
                  description: >-
                    The scopes for the user's API key, as `{ resource: [actions]
                    }`. See [API key
                    permissions](/developers/api-key-permissions).
                signature:
                  type: string
                  description: >-
                    The wallet's EIP-712 signature over the typed data from `GET
                    /partner/attestation`.
              required:
                - address
                - userEmail
                - apiKeyPermissions
                - signature
            example:
              address: '0x8a1F9c2E4b7D3a6C5e0B9d8F2A4c6E1b3D5f7A90'
              userEmail: trader+1324@yourplatform.com
              apiKeyPermissions:
                orders:
                  - read
                  - create
                  - cancel
                account:
                  - read
                wallet:
                  - read
                  - admin
              signature: 0x3b1f…1c
      responses:
        '201':
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
                      sxUserId:
                        type: string
                        description: The new account's SX Bet user ID.
                      address:
                        type: string
                        description: The user's wallet address.
                      email:
                        type: string
                      username:
                        type: string
                        description: Generated by SX Bet.
                      status:
                        type: string
                        enum:
                          - ACTIVE
                          - SUSPENDED
                      proxyWalletAddress:
                        type: string
                        description: >-
                          The user's proxy wallet address. Fund it only after
                          deployment completes.
                      proxyDeployment:
                        type: object
                        properties:
                          status:
                            type: string
                            enum:
                              - PENDING
                              - SUCCESS
                              - FAILED
                              - NOT_STARTED
                            description: >-
                              `PENDING` or `SUCCESS` once started. On `FAILED`
                              or `NOT_STARTED`, call `POST /user/deploy-proxy`
                              with the user's key.
                          sessionId:
                            type: string
                            description: Job id for the deployment, when one was started.
                        required:
                          - status
                      apiKey:
                        type: object
                        description: >-
                          The user's API key. `key` is shown only in this
                          response.
                        properties:
                          id:
                            type: string
                          key:
                            type: string
                          permissions:
                            type: object
                            additionalProperties:
                              type: array
                              items:
                                type: string
                        required:
                          - id
                          - key
                          - permissions
                    required:
                      - sxUserId
                      - address
                      - email
                      - username
                      - status
                      - proxyWalletAddress
                      - proxyDeployment
                      - apiKey
              example:
                status: success
                data:
                  sxUserId: Kq3XbV9mT2pLwR7nYc4sHd8Ef1Gj6Ua0
                  address: '0x8a1F9c2E4b7D3a6C5e0B9d8F2A4c6E1b3D5f7A90'
                  email: trader+1324@yourplatform.com
                  username: externalPartnerUser-48213_k3j9x0qa
                  status: ACTIVE
                  proxyWalletAddress: '0x4361123dbdc1D812fdf7D27045aF358C9C8AA70A'
                  proxyDeployment:
                    status: PENDING
                    sessionId: 3f1c9d64-0a2e-4b77-9a51-2c0f6e8d4b31
                  apiKey:
                    id: xR4tP8vK2mQ7wN1z
                    key: <the user's API key>
                    permissions:
                      orders:
                        - read
                        - create
                        - cancel
                      account:
                        - read
                      wallet:
                        - read
                        - admin
        '400':
          description: ''
          content:
            application/json:
              schema:
                oneOf:
                  - $ref: '#/components/schemas/ErrorNestArray'
                  - $ref: '#/components/schemas/ErrorNest'
              example:
                message: INVALID_ATTESTATION
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
        '409':
          description: ''
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorNest'
              example:
                message: ADDRESS_ALREADY_REGISTERED
                error: Conflict
                statusCode: 409
      security:
        - SxApiKey: []
components:
  schemas:
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