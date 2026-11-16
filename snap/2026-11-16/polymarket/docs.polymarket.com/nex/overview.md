> ## Documentation Index
> Fetch the complete documentation index at: https://docs.polymarket.com/llms.txt
> Use this file to discover all available pages before exploring further.

# NEX Sandbox

> Early access to Polymarket's new exchange.

NEX is Polymarket's new exchange, built from the ground up in Rust. The sandbox
gives you early access to run your strategies with your existing trading stack.

## Why NEX

The current CLOB grew quickly and accumulated a lot of technical debt. We've
made it faster and increased its capacity, but fixing one part can still cause
problems elsewhere. We need an exchange we can keep improving as volume grows.
NEX rebuilds the matching engine and WebSocket stack so we can address those
problems in the core design.

> [!TIP]
>
> NEX is a drop-in replacement for the CLOB API, with no breaking changes. Use
> your existing integration with the sandbox configuration and credentials.

## Early Results

We're running production traffic through NEX in shadow mode. The initial results
show faster order processing and higher capacity than the current CLOB.

> [card] Over 200× Faster
>
> Place-order processing in early shadow tests.

> [card] 10× More Capacity
>
> 100k commands/s sustained and 150k commands/s in bursts.

## Try the Beta

The sandbox runs on Polygon Amoy (`80002`) with test pUSD and bot-provided
liquidity. Its books are separate from production. This is an early beta, and
we'll be actively updating it as we fix issues and improve performance. We're
aiming to release NEX before the end of 2026.

[Request access](https://docs.polymarket.com/nex/get-access.md) through public IP allowlisting or AWS
PrivateLink. For the best latency results, use PrivateLink in AWS London, in the
same zone as NEX. We'll help with setup and test funds through your onboarding
chat.

After approval, [connect to NEX](https://docs.polymarket.com/nex/connect.md), then
[start trading](https://docs.polymarket.com/nex/start-trading.md).


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.