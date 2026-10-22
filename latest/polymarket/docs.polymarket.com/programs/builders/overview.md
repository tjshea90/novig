> ## Documentation Index
> Fetch the complete documentation index at: https://docs.polymarket.com/llms.txt
> Use this file to discover all available pages before exploring further.

# Overview

> Build applications that route orders through Polymarket

A **builder** is a person, group, or organization that routes orders from users to Polymarket. If you've created a platform that allows users to trade on Polymarket through your system, this program is for you.

## Program Benefits

> **Gasless Transactions**
>
> All onchain operations are gas-free through our relayer

> **Order Attribution**
>
> Get credit for orders and compete for grants on the Builder Leaderboard

### What You Get

| Benefit | Description |
| - | - |
| **Relayer Access** | Gas-free wallet deployment, approvals, order execution and position operations |
| **Volume Tracking** | All orders attributed to your builder profile |
| **Leaderboard** | Public visibility on [builders.polymarket.com](https://builders.polymarket.com) |
| **Support** | Telegram channel and engineering support (Verified+) |

## How It Works

**1. User Places Order**

User places an order through your application.

**2. Attach Builder Code**

Your app adds your builder code to each order.

**3. Submit to CLOB**

Order is submitted to Polymarket's CLOB — the builder code is serialized
onchain as part of the signed order.

**4. Trade Execution**

Polymarket matches the order and covers gas fees for onchain operations.

**5. Volume Attribution**

Volume is credited to your builder account for every matched trade where
your code is attached.

## Getting Started

**1. Create Builder Profile**

Open polymarket.com → Settings →
[Builders](https://polymarket.com/settings?tab=builder) and copy your builder
code.

**2. Attach Your Builder Code**

Attach your builder code to every order you submit. See [Builder
Attribution](/trading/place-orders.md#builder-attribution).

**3. Enable Gasless Transactions**

Use the Polymarket relayer to deploy wallets and [execute gasless
transactions](/trading/wallets-auth.md#execute-gasless-transactions).

**4. Track Performance**

Monitor your volume on the [Builder
Leaderboard](https://builders.polymarket.com).

Allow up to **24 hours** for matched volume to appear on the leaderboard.

***

## Next Steps

> **[Create New Accounts](/trading/wallets-auth.md#create-new-accounts)**
>
> Create a builder account and the credentials used to provision accounts for
> your users.

> **[Understand Tiers](/programs/builders/tiers.md)**
>
> Learn about rate limits and how to upgrade.

> **[Attribute Orders](/trading/place-orders.md#builder-attribution)**
>
> Configure your client to credit trades to your account.

> **[Execute Gasless Transactions](/trading/wallets-auth.md#execute-gasless-transactions)**
>
> Approve tokens, transfer funds, and manage positions without paying gas.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.