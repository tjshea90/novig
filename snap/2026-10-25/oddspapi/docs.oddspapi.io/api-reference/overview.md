> ## Documentation Index
> Fetch the complete documentation index at: https://docs.oddspapi.io/llms.txt
> Use this file to discover all available pages before exploring further.

# REST API Overview - HTTP Endpoints for Sports Data

> OddsPapi REST API reference. HTTP endpoints for sports betting data snapshots, metadata retrieval, historical odds, and recovery workflows. Supports 8 languages.

The HTTP API complements WebSockets by providing:

* **Initial snapshots** (bootstrap your local state)
* **Recovery** after `snapshot_required`
* **Metadata** (sports, tournaments, bookmakers)
* **Backfills** (historical odds / CLV) when replay is not possible

If you only need realtime updates, WebSockets may be enough.

## Base URL & languages

All endpoints are prefixed with a language code:

* `https://v5.oddspapi.io/{lang}`

Examples:

* `https://v5.oddspapi.io/en/bookmakers`
* `https://v5.oddspapi.io/de/sports`

Available languages are: `en`, `es`, `fr`, `pt`, `de`, `it`, `ru`, `zh`.

Anything else falls back to English names rather than erroring (`zh-CN` → use
`zh`), and the prefix is case-insensitive but cannot be omitted.

Only name fields are translated (sport, tournament, season, status, participant
and venue names). Ids, odds, prices and timestamps are identical in every
language.

For simplicity, the documentation is written in English.

## Rate limits

This API enforces request limits per `apiKey` per endpoint.

See **Rate Limits** for the authoritative limits and endpoint groups:

* `api-reference/rate-limits`

***

## 💬 Ask an AI Assistant

Want to explore or ask questions about this page using your favorite AI?

Click one of the links below — each one opens this page in the selected tool with a pre-filled prompt:

* [Ask ChatGPT](https://chatgpt.com/?prompt=Read+from+https%3A%2F%2Fdocs.oddspapi.io%2Fllms-full.txt+and+help+me+with+this+API.)
* [Ask Claude](https://claude.ai/?prompt=Please+read+https%3A%2F%2Fdocs.oddspapi.io%2Fllms-full.txt+and+help+me+use+this+API.)
* [Ask Perplexity](https://www.perplexity.ai/search?q=Read+from+https%3A%2F%2Fdocs.oddspapi.io%2Fllms-full.txt)
* [Ask Gemini](https://gemini.google.com/app?query=Read+from+https%3A%2F%2Fdocs.oddspapi.io%2Fllms-full.txt+and+help+me+use+this+API.)


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.