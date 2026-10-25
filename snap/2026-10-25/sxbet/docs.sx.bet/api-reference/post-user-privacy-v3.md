> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Change privacy setting

> Choose whether your bets and orders are published with your identity.

Controls whether your betting activity is de-anonymized. When it is, your positions, orders, and trades are attributed to you. Otherwise your activity is anonymized.

> [!WARNING]
>
> The setting applies only to what you place **after** the setting is changed. Orders and bets already placed
> keep the setting they were placed under, permanently. Becoming anonymized does not retract bets already
> published under your identity. Becoming de-anonymized does not reveal previous bets that were placed while you were anonymized.


## OpenAPI

````yaml POST /user/privacy-v3
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
    post:
      tags:
        - Wallet & Funding
      summary: Change your privacy setting
      description: >-
        Set whether your betting activity is anonymized.


        The setting applies **only to orders and bets you place after it takes
        effect**. Everything already placed keeps the setting it was placed
        under, permanently — becoming anonymized does not retract bets already
        published under your name, and becoming de-anonymized does not reveal
        ones placed while anonymized. A bettor's history can therefore mix the
        two.


        Allow up to a minute for a change to take effect before placing orders
        that depend on it.
      operationId: setUserPrivacyV3
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              required:
                - isPrivate
              properties:
                isPrivate:
                  type: boolean
                  description: >-
                    `true` to anonymize your activity, `false` to de-anonymize
                    it.
            example:
              isPrivate: true
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
                        description: The setting now stored.
              example:
                status: success
                data:
                  isPrivate: true
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