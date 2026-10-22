> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Get privacy setting

> Read whether your betting activity is published.

Reads your own privacy setting. See [Change privacy setting](/api-reference/post-user-privacy-v3.md) for what the setting does and does
not apply to.


## OpenAPI

````yaml GET /user/privacy-v3
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
  /user/privacy-v3:
    get:
      tags:
        - Wallet & Funding
      summary: Read your privacy setting
      description: >-
        Whether your betting activity is currently anonymized. Scoped to your
        own credential — there is no address parameter, and no way to read
        anyone else's setting.
      operationId: getUserPrivacyV3
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
                      isPrivate:
                        type: boolean
                        description: '`true` when your activity is anonymized.'
              example:
                status: success
                data:
                  isPrivate: false
      security:
        - SxApiKey: []
components:
  securitySchemes:
    SxApiKey:
      type: apiKey
      in: header
      name: x-sx-api-key
      description: 'API key, sent as the `x-sx-api-key` header. '

````

This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.