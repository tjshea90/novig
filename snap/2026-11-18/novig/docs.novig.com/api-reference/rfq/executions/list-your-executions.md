> ## Documentation Index
> Fetch the complete documentation index at: https://docs.novig.com/llms.txt
> Use this file to discover all available pages before exploring further.

# List your executions

> | | |
| --- | --- |
| **Key** | `trading` `trading::read` |
| **Throttle** | `history` |
| **Cost** | `1 /request` |
| **Answers** | <span class="st st-ok">200</span> <span class="st st-warn">401</span> <span class="st st-warn">403</span> <span class="st st-warn">404</span> <span class="st st-hold">423</span> <span class="st st-hold">429</span> <span class="st st-warn">451</span> |
| **Idempotent** | `true` |

Lists the parlays your maker filled, newest first.



## OpenAPI

````yaml /api-reference/spec-files/openapi-rfq-v3.json get /rfq/v3/executions
openapi: 3.1.0
info:
  title: Novig RFQ API
  description: >-
    Price parlays as an RFQ maker. Each maker is one subaccount, and you call
    these endpoints with that subaccount's trading key.
  contact:
    name: Contact
    url: https://novig.com
    email: tech@novig.com
  version: 3.0.0
servers:
  - url: https://api.paper.novig.com
    description: Paper
security: []
tags:
  - name: Maker
    description: Register a subaccount as a maker, and check its standing.
  - name: Executions
    description: See the parlays your maker filled and the collateral they hold.
  - name: Streaming
    description: Receive RFQs and quote, decline and confirm over one websocket.
paths:
  /rfq/v3/executions:
    get:
      tags:
        - Executions
      summary: List your executions
      description: >-
        | | |

        | --- | --- |

        | **Key** | `trading` `trading::read` |

        | **Throttle** | `history` |

        | **Cost** | `1 /request` |

        | **Answers** | <span class="st st-ok">200</span> <span class="st
        st-warn">401</span> <span class="st st-warn">403</span> <span class="st
        st-warn">404</span> <span class="st st-hold">423</span> <span class="st
        st-hold">429</span> <span class="st st-warn">451</span> |

        | **Idempotent** | `true` |


        Lists the parlays your maker filled, newest first.
      operationId: listExecutions
      parameters:
        - name: status
          in: query
          description: Only executions with this status.
          required: false
          schema:
            $ref: '#/components/schemas/ExecutionStatus'
        - name: limit
          in: query
          description: How many to return, from 1 to 500. The default is 500.
          required: false
          schema:
            type: integer
            format: int32
            default: 500
            maximum: 500
            minimum: 1
        - name: after
          in: query
          description: Pass the previous page's `next` to get the page after it.
          required: false
          schema:
            type: string
      responses:
        '200':
          description: One page of executions.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Page_Execution'
        '401':
          description: >-
            The signature is absent or invalid, or the key is unknown, revoked,
            or expired.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorBody'
              example:
                code: SIGNATURE_REJECTED
                message: signature verification failed
        '403':
          description: The key's scope does not grant this route.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorBody'
              example:
                code: SCOPE_INSUFFICIENT
                message: api key scope is insufficient for this route
        '404':
          description: Your subaccount isn't a maker yet.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/RfqErrorBody'
        '423':
          description: The account is locked out of trading.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorBody'
              example:
                code: ACCOUNT_LOCKED
                message: The account is locked out of trading.
        '429':
          description: >-
            The throttle is empty. Wait the number of seconds in `Retry-After`.
            Then retry.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorBody'
              example:
                code: RATE_LIMIT_EXCEEDED
                message: Rate limit exceeded. Please wait before retrying.
        '451':
          description: >-
            Geolocation refused the request: an anonymized network, a restricted
            region, or, for a placement, no device geolocation in the last 3
            days.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorBody'
              example:
                code: ANONYMIZED_NETWORK
                message: The request came over a VPN, a proxy, or a Tor exit.
      security:
        - keyId: []
          signature: []
          timestamp: []
components:
  schemas:
    ExecutionStatus:
      type: string
      description: >-
        Whether the parlay has settled. `open`: it hasn't graded yet. `settled`:
        it

        graded, and `result` says how.
      enum:
        - open
        - settled
    Page_Execution:
      allOf:
        - $ref: '#/components/schemas/PageCursorResponse'
        - type: object
          required:
            - items
          properties:
            items:
              type: array
              items:
                type: object
                description: A parlay your maker filled, and how it settled.
                required:
                  - rfqId
                  - instrumentId
                  - price
                  - cost
                  - userCost
                  - status
                  - outcomeIds
                  - ts
                properties:
                  cost:
                    type: string
                    description: >-
                      What your maker paid, in dollars. This is your collateral
                      against the user's stake.
                    example: '150'
                  fmvValue:
                    type:
                      - string
                      - 'null'
                    description: >-
                      The fair market value it settled at, between 0 and 1.
                      Present only when `result` is `fmv`.
                  instrumentId:
                    type: string
                    format: uuid
                    description: Identifies the set of outcomes the parlay is on.
                  outcomeIds:
                    type: array
                    items:
                      type: string
                  price:
                    type: string
                    description: >-
                      The user's price: the probability the parlay wins, between
                      0 and 1.
                    example: '0.25'
                  result:
                    oneOf:
                      - type: 'null'
                      - $ref: '#/components/schemas/ExecutionResult'
                        description: >-
                          How the parlay graded. Present once `status` is
                          `settled`.
                  rfqId:
                    type: string
                    format: uuid
                  settledTs:
                    type:
                      - integer
                      - 'null'
                    format: int64
                    description: >-
                      When it settled, in Unix milliseconds. Present once
                      `status` is `settled`.
                  status:
                    $ref: '#/components/schemas/ExecutionStatus'
                  ts:
                    type: integer
                    format: int64
                    description: When it filled, in Unix milliseconds.
                  userCost:
                    type: string
                    description: 'What the user paid, in dollars: their stake.'
                    example: '50'
      description: >-
        One page of a listing: the items in the listing's order, then the link
        to

        the next page. Every paged route answers this shape.
    ErrorBody:
      type: object
      description: Every error on the surface carries this body.
      required:
        - code
        - message
      properties:
        code:
          type: string
          description: Stable identifier. The only field to branch on.
          example: MARKET_NOT_FOUND
        message:
          type: string
          description: Human-readable. Not stable. Never match on it.
        rejected:
          type:
            - array
            - 'null'
          items:
            $ref: '#/components/schemas/BatchOrderError'
          description: >-
            One entry per refused order in a batch. Present only on an error
            that a batch caused.
    RfqErrorBody:
      type: object
      description: The body of every error from an RFQ endpoint.
      required:
        - code
        - message
      properties:
        code:
          type: string
          description: A stable code for the error. Branch on this field.
          example: MAKER_NOT_FOUND
        message:
          type: string
          description: A readable explanation. It can change, so don't match on it.
    PageCursorResponse:
      type: object
      description: The link to the next page of a listing.
      properties:
        next:
          type: string
          description: >-
            Pass it as `after` for the next page. Opaque: do not build or parse
            one. Absent on the last page.
    ExecutionResult:
      type: string
      description: >-
        How the user's parlay graded. `win`: it won. `loss`: it lost. `push`: it

        voided and the stake came back. `fmv`: it settled at a fair market
        value,

        which `fmvValue` holds.
      enum:
        - win
        - loss
        - push
        - fmv
    BatchOrderError:
      type: object
      description: >-
        One rejected order within a batch placement: which order failed, and
        why.
      required:
        - index
        - outcomeId
        - reason
      properties:
        index:
          type: integer
          description: Zero-based position in the request.
          minimum: 0
        outcomeId:
          type: string
          description: The outcome ID of the rejected order.
        reason:
          type: string
          description: >-
            Human-readable. Not stable. Branch on the envelope's `code` and on
            `index`.
  securitySchemes:
    keyId:
      type: apiKey
      in: header
      name: Novig-Key-Id
      description: The key's UUID.
    signature:
      type: apiKey
      in: header
      name: Novig-Signature
      description: Standard padded base64 of the NOVIG-V3 signature.
    timestamp:
      type: apiKey
      in: header
      name: Novig-Timestamp
      description: Unix milliseconds. ±30 s.

````

This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.