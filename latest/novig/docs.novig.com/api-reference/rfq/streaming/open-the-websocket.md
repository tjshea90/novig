> ## Documentation Index
> Fetch the complete documentation index at: https://docs.novig.com/llms.txt
> Use this file to discover all available pages before exploring further.

# Open the websocket

> | | |
| --- | --- |
| **Key** | `trading` `trading::read` |
| **Throttle** | `stream` |
| **Cost** | `32 /request` |
| **Answers** | <span class="st st-info">101</span> <span class="st st-warn">401</span> <span class="st st-warn">403</span> <span class="st st-warn">404</span> <span class="st st-hold">423</span> <span class="st st-hold">429</span> <span class="st st-warn">451</span> |
| **Idempotent** | `true` |

Sign the upgrade request like any route. The key's subaccount must be a maker to open the
socket. The channels are `rfq`, `quotes` and `executions`. The methods are `subscribe`,
`unsubscribe`, `quote`, `decline` and `confirm`. `quote`, `decline` and `confirm` need a write
key. A key with too many sockets open gets `429` with the code `TOO_MANY_SOCKETS`.



## OpenAPI

````yaml /api-reference/spec-files/openapi-rfq-v3.json get /rfq/v3/ws
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
  /rfq/v3/ws:
    get:
      tags:
        - Streaming
      summary: Open the websocket
      description: >-
        | | |

        | --- | --- |

        | **Key** | `trading` `trading::read` |

        | **Throttle** | `stream` |

        | **Cost** | `32 /request` |

        | **Answers** | <span class="st st-info">101</span> <span class="st
        st-warn">401</span> <span class="st st-warn">403</span> <span class="st
        st-warn">404</span> <span class="st st-hold">423</span> <span class="st
        st-hold">429</span> <span class="st st-warn">451</span> |

        | **Idempotent** | `true` |


        Sign the upgrade request like any route. The key's subaccount must be a
        maker to open the

        socket. The channels are `rfq`, `quotes` and `executions`. The methods
        are `subscribe`,

        `unsubscribe`, `quote`, `decline` and `confirm`. `quote`, `decline` and
        `confirm` need a write

        key. A key with too many sockets open gets `429` with the code
        `TOO_MANY_SOCKETS`.
      operationId: connectWebsocket
      responses:
        '101':
          description: The connection continues as a websocket.
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
            The throttle is empty, so wait the number of seconds in
            `Retry-After` and retry, or this key already has 10 sockets open
            (`TOO_MANY_SOCKETS`), so close one first.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/RfqErrorBody'
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