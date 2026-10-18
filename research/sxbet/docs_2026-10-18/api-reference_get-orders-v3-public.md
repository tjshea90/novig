> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Get a market's de-anonymized orders

> Read the de-anonymized resting orders on a market.

`GET /orders-v3/public` lists the de-anonymized orders for a particular market. Only orders placed while the order's owner was de-anonymized are returned.

<Info>
  This is not the order book and it only shows a very small fraction of the total liquidity. Anonymized orders are omitted. To
  recreate the actual book, subscribe to [order book updates](/api-reference/channel-orderbook-v3) and seed
  from the [snapshot](/api-reference/get-market-orderbook-snapshot).
</Info>

For a live view of de-anonymized orders, subscribe to [de-anonymized order updates](/api-reference/channel-public-orders-v3) and seed
from this route.


## OpenAPI

````yaml GET /orders-v3/public
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
  /orders-v3/public:
    get:
      tags:
        - Orders
      summary: Get de-anonymized orders on a market
      description: >-
        Resting orders on one market that were placed while de-anonymized.
        Anonymized orders are omitted. Every returned order carries
        `userAddress`. Identity is fixed when the order is placed and never
        changes afterwards: anonymizing your activity later does not un-name
        orders you already placed, and de-anonymizing it does not name them
        retroactively.


        No order carries the maker's funding address.
      operationId: getPublicOrdersV3
      parameters:
        - name: marketHash
          in: query
          required: true
          schema:
            type: string
          description: The market to read. Required.
        - name: perPage
          in: query
          required: false
          schema:
            type: integer
            minimum: 1
            maximum: 100
            default: 50
          description: Rows per page.
        - name: nextKey
          in: query
          required: false
          schema:
            type: string
          description: Cursor from the previous response to continue pagination.
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
                      orders:
                        type: array
                        items:
                          $ref: '#/components/schemas/PublicOrderV3'
                      nextKey:
                        type: string
                        description: >-
                          Pass as `nextKey` to retrieve the next page. Absent —
                          not null — on the last page.
              example:
                status: success
                data:
                  orders:
                    - id: >-
                        0x5c1bb3b5a7d2d8a2d7dcd2ba00b2aa96b0f0b6e6b0a2f7d2cbe1a2f1f0c1d2e3
                      marketHash: >-
                        0x1fec4ed9b71c2f812db900af7d12446158a0eda56041106f99551c496efd3266
                      userAddress: '0x685D1D76A2771FEd07297c83e723eAc320a30d7a'
                      isBettingOutcomeOne: true
                      percentageOdds: '60000000000000000000'
                      totalBetSize: '5000000'
                      remainingSize: '4000000'
                      expiry: '2026-09-19T19:50:20.000Z'
                      status: ACTIVE
                      eventId: L12003787
                      createdAt: '2026-09-18T19:50:23.840Z'
                      updatedAt: '2026-09-18T19:50:23.850Z'
                    - id: >-
                        0x07ec744d42bc4eb693d7a22fe2b068013bbea907250caf44869b62068bd4cda6
                      marketHash: >-
                        0x1fec4ed9b71c2f812db900af7d12446158a0eda56041106f99551c496efd3266
                      userAddress: '0xA11CE00000000000000000000000000000000000'
                      isBettingOutcomeOne: true
                      percentageOdds: '55000000000000000000'
                      totalBetSize: '5000000'
                      remainingSize: '5000000'
                      expiry: '2026-09-19T19:52:41.000Z'
                      status: ACTIVE
                      eventId: L12003787
                      createdAt: '2026-09-18T19:52:44.120Z'
                      updatedAt: '2026-09-18T19:52:44.130Z'
                  nextKey: eyJjcmVhdGVkQXQiOiIyMDI2LTA5LTE4VDE5OjUyOjQ0LjEyMFoifQ==
        '404':
          description: De-anonymized identity is not available on this environment.
      security:
        - SxApiKey: []
components:
  schemas:
    PublicOrderV3:
      description: A resting order as shown to everyone.
      type: object
      properties:
        id:
          type: string
          description: A unique identifier for this order.
        marketHash:
          type: string
          description: The market this order rests on.
        userAddress:
          type: string
          description: The maker's checksummed address.
        isBettingOutcomeOne:
          type: boolean
          description: The outcome this order backs.
        percentageOdds:
          type: string
          description: >-
            The odds the maker receives in the sx.bet protocol format. To
            convert to implied odds divide by 10^20. To get taker implied odds:
            `takerOdds = 1 - percentageOdds / 10^20`. See [unit
            conversions](/developers/unit-conversions).
        totalBetSize:
          type: string
          description: >-
            Total size of this order in base units. See [unit
            conversions](/developers/unit-conversions).
        remainingSize:
          type: string
          description: >-
            The unfilled portion still available to take. The filled amount is
            `totalBetSize - remainingSize`.
        expiry:
          type: string
          nullable: true
          description: ISO 8601 timestamp after which this order is no longer valid.
        status:
          type: string
          description: '`ACTIVE` if resting, `INACTIVE` if no longer resting.'
        eventId:
          type: string
          description: Prefixed event id (e.g. `L12003787`) for the order's market.
        createdAt:
          type: string
          description: ISO 8601 timestamp for when the order first rested.
        updatedAt:
          type: string
          description: ISO 8601 timestamp for when the order last changed.
  securitySchemes:
    SxApiKey:
      type: apiKey
      in: header
      name: x-sx-api-key
      description: 'API key, sent as the `x-sx-api-key` header. '

````

This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.