> ## Documentation Index
> Fetch the complete documentation index at: https://docs.polymarket.com/llms.txt
> Use this file to discover all available pages before exploring further.

# Connect to NEX

> Connect through PrivateLink or an allowlisted public IP.

After [approval](https://docs.polymarket.com/nex/get-access.md), use either connection below. For the best
latency results, use PrivateLink in NEX's AWS London zone.

## Set Up Your Connection

> [tab] AWS PrivateLink
>
> **1. Create an Endpoint**
>
> We'll allowlist your 12-digit AWS account ID and send the service name.
> Create an IPv4 Interface VPC endpoint in `eu-west-2`, AZ ID `euw2-az1` only.
> Allow TCP `443` from your clients in its security group and disable Private
> DNS (`private_dns_enabled = false`). Check the AZ ID: names such as
> `eu-west-2a` vary across accounts. Another AZ will not provide failover.
>
> **2. Get the Connection Approved**
>
> Send us the endpoint ID (`vpce-…`). Wait for approval and status `available`.
>
> **3. Set Up DNS**
>
> In a private hosted zone attached to your VPC, point these CNAMEs to your
> endpoint DNS name:
>
> * `nex.preprod.defi.euw2.polymarket.sh`
> * `nex-ws.preprod.defi.euw2.polymarket.sh`
>
> Preserve the service hostname in TLS SNI and the HTTP `Host` header if
> connecting through endpoint DNS directly.
>
> **4. Check Connectivity**
>
> Run from a client inside your connected VPC after setting up DNS:
>
> ```bash theme={null}
> curl -i https://nex.preprod.defi.euw2.polymarket.sh/health
> ```
>
> Expect HTTP `200`.
>
> AWS bills endpoint usage to your account.

> [tab] Public IP Allowlisting
>
> **1. Send Your Outbound IPs**
>
> Send each stable public IPv4 address your clients use, individually (`/32`).
> For clients behind NAT, use the NAT gateway's public addresses. Wait for our
> approval before connecting.
>
> **2. Map the Service Hostnames**
>
> Map the trading and WebSocket hostnames to `51.24.102.79` in your resolver
> or hosts file. Their default DNS does not select the public connection.
> Allow outbound TCP `443` and keep the hostname in TLS SNI and HTTP `Host`.
>
> **3. Check Connectivity**
>
> Run from an allowlisted client. This selects the public address while
> preserving the hostname for TLS and routing:
>
> ```bash theme={null}
> curl -i \
>   --connect-to nex.preprod.defi.euw2.polymarket.sh:443:51.24.102.79:443 \
>   https://nex.preprod.defi.euw2.polymarket.sh/health
> ```
>
> Expect HTTP `200`.

## Service Addresses

| Service | Base URL |
| - | - |
| Trading API | `https://nex.preprod.defi.euw2.polymarket.sh` |
| WebSocket feeds | `wss://nex-ws.preprod.defi.euw2.polymarket.sh` |
| Markets (Gamma) | `https://gamma-api-preprod.polymarket.dev` |
| Relayer | `https://relayer-preprod.polymarket.dev` |

Gamma and the relayer require `x-client-id` and `x-client-secret` headers. We'll
send the credentials privately after approval. These are separate from your
trading credentials.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.