> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Get your proxy wallet

> Get the proxy wallet address for the authenticated account.

`GET /user/proxy` returns the proxy wallet for the authenticated account.

<Warning>
  **`deployed` is the only field that reports existence.** The proxy address is a deterministic
  derivation from your user address, so it is returned whether or not the contract exists. `deployed` indicates if it has been successfully deployed or not.
</Warning>


## OpenAPI

````yaml GET /user/proxy
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
  /user/proxy:
    get:
      tags:
        - Connection
      summary: Get your proxy wallet
      description: >-
        Returns the proxy wallet for the authenticated account. Takes no path or
        query param — the account is inferred from your API key or session. The
        address is returned whether or not the proxy exists, because it is
        CREATE2-deterministic; read `deployed` to know whether the contract is
        on chain.
      operationId: getUserProxy
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
                      obv3ProxyWalletAddress:
                        type: string
                        description: The address of the proxy wallet.
                      deployed:
                        type: boolean
                        description: Whether the proxy contract exists on chain.
                      multisigSafeAddress:
                        type: string
                        nullable: true
                        description: >-
                          The Safe governing the proxy. Null before the proxy is
                          deployed, and null for Toronto proxies deployed before
                          a Safe was attached. Pass this address to the
                          `/user/multisig` routes.
              example:
                status: success
                data:
                  obv3ProxyWalletAddress: '0x4361123dbdc1D812fdf7D27045aF358C9C8AA70A'
                  deployed: true
                  multisigSafeAddress: '0x8E4c91A7D2b0f35e6A19c84d70B2e5F1a3c6D9e8'
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