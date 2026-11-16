> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Get the attestation to sign

> The EIP-712 typed data a wallet signs to become one of your users.

Partner keys only. See [Partner API](https://docs.sx.bet/developers/partner-api.md) for the full flow.

Have the user's wallet sign the returned typed data exactly as returned, with
`eth_signTypedData_v4`, then send the signature to
[`POST /partner/users`](https://docs.sx.bet/api-reference/post-partner-users).

The signature must recover to the address, from `eth_signTypedData_v4` on a
standard externally owned account. Contract-wallet signatures (EIP-1271) are
not accepted.


## OpenAPI

````yaml GET /partner/attestation
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
  /partner/attestation:
    get:
      tags:
        - Partner API
      summary: Get the attestation to sign
      description: >-
        Returns the EIP-712 typed data a wallet signs to become one of your
        users. Requires a partner key with the create permission.
      operationId: getPartnerAttestation
      parameters:
        - name: address
          in: query
          required: true
          schema:
            type: string
          description: The wallet address of the user you are about to create.
          example: '0x8a1F9c2E4b7D3a6C5e0B9d8F2A4c6E1b3D5f7A90'
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
                    $ref: '#/components/schemas/PartnerAttestation'
              example:
                status: success
                data:
                  domain:
                    name: SX Bet Partner Account
                    version: '1'
                    chainId: 79479957
                  types:
                    PartnerAccount:
                      - name: partnerId
                        type: uint256
                      - name: account
                        type: address
                  primaryType: PartnerAccount
                  message:
                    partnerId: '48213'
                    account: '0x8a1F9c2E4b7D3a6C5e0B9d8F2A4c6E1b3D5f7A90'
        '400':
          description: ''
          content:
            application/json:
              schema:
                oneOf:
                  - $ref: '#/components/schemas/ErrorNestArray'
                  - $ref: '#/components/schemas/ErrorNest'
              example:
                message:
                  - address must be a valid address
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
    PartnerAttestation:
      type: object
      description: >-
        EIP-712 typed data for the user's wallet to sign. Sign it exactly as
        returned; `partnerId` is a uint256 sent as a string.
      properties:
        domain:
          type: object
          properties:
            name:
              type: string
              example: SX Bet Partner Account
            version:
              type: string
              example: '1'
            chainId:
              type: integer
              description: >-
                The environment's chain ID: 4162 on mainnet, 79479957 on
                testnet.
              example: 79479957
          required:
            - name
            - version
            - chainId
        types:
          type: object
          description: >-
            The `PartnerAccount` type: `partnerId` (uint256) and `account`
            (address).
          additionalProperties:
            type: array
            items:
              type: object
              properties:
                name:
                  type: string
                type:
                  type: string
        primaryType:
          type: string
          example: PartnerAccount
        message:
          type: object
          properties:
            partnerId:
              type: string
              description: Your partner ID.
              example: '48213'
            account:
              type: string
              description: The user's wallet address, checksummed.
          required:
            - partnerId
            - account
      required:
        - domain
        - types
        - primaryType
        - message
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