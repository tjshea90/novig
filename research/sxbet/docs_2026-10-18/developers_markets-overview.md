> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Overview

> Sports, leagues, fixtures, and markets need no API key. 

## Data hierarchy

Market data on SX Bet is organized in a hierarchy. Understanding this structure will help you navigate the API efficiently.

<div style={{display:"flex",flexDirection:"column",alignItems:"center",gap:"0",fontFamily:"inherit",margin:"2rem 0"}}>
  {[
      {icon:"🏟️", label:"Sports", example:"e.g. Soccer", endpoint:"/sports", desc:"All sports on SX Bet", color:"#1565C0", width:"63%"},
      {icon:"🏆", label:"Leagues", example:"e.g. English Premier League", endpoint:"/leagues/active", desc:"Active leagues for a sport", color:"#1976D2", width:"72%"},
      {icon:"📅", label:"Fixtures", example:"e.g. Man United vs. Man City", endpoint:"/fixture/active", desc:"Active games for a league", color:"#1E88E5", width:"81%"},
      {icon:"📊", label:"Markets", example:"e.g. Over/Under 2.5 Goals", endpoint:"/markets/active", desc:"Bettable outcomes for a fixture", color:"#42A5F5", width:"90%"},
      {icon:"📋", label:"Orderbook", example:"e.g. Best price and depth", endpoint:"/orderbook-v3/snapshot/market", desc:"Resting liquidity on the market — the odds available to bet", color:"#64B5F6", width:"100%"},
    ].map((item, i) => (
      <div key={i} style={{display:"flex",flexDirection:"column",alignItems:"center",width:"100%"}}>
        <div style={{
          display:"grid",gridTemplateColumns:"1fr 1fr",
          background:item.color,borderRadius:"12px",
          padding:"0.9rem 1.5rem",width:item.width,
          boxShadow:"0 2px 8px rgba(0,0,0,0.15)",
          boxSizing:"border-box"
        }}>
          <div style={{display:"flex",alignItems:"center",justifyContent:"flex-end",gap:"0.5rem",paddingRight:"0.5rem"}}>
            <span style={{fontSize:"1.4rem"}}>{item.icon}</span>
            <span style={{color:"#fff",fontWeight:700,fontSize:"1rem",whiteSpace:"nowrap"}}>{item.label}</span>
          </div>
          <div style={{display:"flex",alignItems:"center",justifyContent:"flex-start",paddingLeft:"0.5rem"}}>
            <code style={{
              background:"rgba(0,0,0,0.25)",color:"#fff",
              padding:"0.15rem 0.5rem",borderRadius:"6px",
              fontSize:"0.75rem",whiteSpace:"nowrap"
            }}>{item.endpoint}</code>
          </div>
          <div style={{display:"flex",justifyContent:"flex-end",paddingRight:"0.5rem",color:"rgba(255,255,255,0.65)",fontSize:"0.8rem",marginTop:"0.2rem"}}>
            <span style={{fontStyle:"italic"}}>{item.example}</span>
          </div>
          <div style={{display:"flex",justifyContent:"flex-start",paddingLeft:"0.5rem",color:"rgba(255,255,255,0.65)",fontSize:"0.8rem",marginTop:"0.2rem"}}>
            <span style={{marginRight:"0.4rem",opacity:0.5}}>·</span>
            <span>{item.desc}</span>
          </div>
        </div>
        {i < 4 && (
          <div style={{display:"flex",flexDirection:"column",alignItems:"center",margin:"0"}}>
            <div style={{width:"2px",height:"16px",background:"#CBD5E1"}}/>
            <div style={{width:0,height:0,borderLeft:"7px solid transparent",borderRight:"7px solid transparent",borderTop:"9px solid #CBD5E1"}}/>
          </div>
        )}
      </div>
    ))}
</div>

| Level | Endpoint | Description | Filter by |
| - | - | - | - |
| Sports | `GET /sports` | All sports available on SX Bet | — |
| Leagues | `GET /leagues/active` | Active leagues for a given sport | `sportId` |
| Fixtures | `GET /fixture/active` | Active games/events for a given league | `leagueId` |
| Markets | `GET /markets/active` | Bettable binary outcomes for a given fixture | `sportIds`, `eventId`, `leagueId`, `type`, `betGroup`, `onlyMainLine`, `liveOnly`, `gameTime` |
| Orderbook | `GET /orderbook-v3/snapshot/market` | The full resting book for **one** market | `marketHash` |
| Best odds | `GET /orders-v3/odds/best` | Best level per side for many markets | `marketHashes` (≤100) |

A market row carries no odds — prices live at the book level, not on the market. The two pricing
routes are not interchangeable. The snapshot gives full depth for one market. Best odds gives the top
of book for up to a hundred. See [Active markets](/api-reference/get-markets-active).

## Try it yourself

Run the script below in your terminal. It walks you through the full hierarchy interactively. Select a sport, then a league, then a fixture. It then displays all available main-line markets for that fixture.

<CodeGroup>
  ```python Python theme={null}
  import requests

  BASE_URL = "https://api.sx.bet"  # Mainnet — use https://api.toronto.sx.bet for testnet

  # Step 1: Fetch and display all sports
  sports = requests.get(f"{BASE_URL}/sports").json()["data"]
  print("=== Sports ===")
  for s in sports:
      print(f"  [{s['sportId']}] {s['label']}")

  sport_id = int(input("\nEnter a sport ID: "))

  # Step 2: Fetch active leagues for the selected sport
  leagues = requests.get(
      f"{BASE_URL}/leagues/active",
      params={"sportId": sport_id}
  ).json()["data"]

  print("\n=== Active Leagues ===")
  for l in leagues:
      print(f"  [{l['leagueId']}] {l['label']}")

  league_id = int(input("\nEnter a league ID: "))

  # Step 3: Fetch active fixtures for the selected league
  fixtures = requests.get(
      f"{BASE_URL}/fixture/active",
      params={"leagueId": league_id}
  ).json()["data"]

  print("\n=== Active Fixtures ===")
  for f in fixtures:
      home = f.get("participantOneName", "N/A")
      away = f.get("participantTwoName", "N/A")
      print(f"  [{f['eventId']}] {home} vs {away} — {f['startDate']}")

  event_id = input("\nEnter an event ID: ")

  # Step 4: Fetch main-line markets for the selected fixture
  markets = requests.get(
      f"{BASE_URL}/markets/active",
      params={"eventId": event_id, "onlyMainLine": True}
  ).json()["data"]["markets"]

  print(f"\n=== Markets ({len(markets)} found) ===")
  for m in markets:
      print(f"  {m['outcomeOneName']} vs {m['outcomeTwoName']}")
      print(f"    marketHash: {m['marketHash']}")
  ```

  ```javascript JavaScript theme={null}
  import * as readline from "readline";

  const BASE_URL = "https://api.sx.bet"; // Mainnet — use https://api.toronto.sx.bet for testnet

  const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
  const ask = (q) => new Promise((res) => rl.question(q, res));

  // Step 1: Fetch and display all sports
  const sportsData = await fetch(`${BASE_URL}/sports`).then((r) => r.json());
  console.log("=== Sports ===");
  for (const s of sportsData.data) {
    console.log(`  [${s.sportId}] ${s.label}`);
  }
  const sportId = await ask("\nEnter a sport ID: ");

  // Step 2: Fetch active leagues for the selected sport
  const leaguesData = await fetch(
    `${BASE_URL}/leagues/active?${new URLSearchParams({ sportId })}`
  ).then((r) => r.json());
  console.log("\n=== Active Leagues ===");
  for (const l of leaguesData.data) {
    console.log(`  [${l.leagueId}] ${l.label}`);
  }
  const leagueId = await ask("\nEnter a league ID: ");

  // Step 3: Fetch active fixtures for the selected league
  const fixturesData = await fetch(
    `${BASE_URL}/fixture/active?${new URLSearchParams({ leagueId })}`
  ).then((r) => r.json());
  console.log("\n=== Active Fixtures ===");
  for (const f of fixturesData.data) {
    const home = f.participantOneName ?? "N/A";
    const away = f.participantTwoName ?? "N/A";
    console.log(`  [${f.eventId}] ${home} vs ${away} — ${f.startDate}`);
  }
  const eventId = await ask("\nEnter an event ID: ");

  // Step 4: Fetch main-line markets for the selected fixture
  const marketsData = await fetch(
    `${BASE_URL}/markets/active?${new URLSearchParams({ eventId, onlyMainLine: true })}`
  ).then((r) => r.json());
  const markets = marketsData.data.markets;
  console.log(`\n=== Markets (${markets.length} found) ===`);
  for (const m of markets) {
    console.log(`  ${m.outcomeOneName} vs ${m.outcomeTwoName}`);
    console.log(`    marketHash: ${m.marketHash}`);
  }

  rl.close();
  ```
</CodeGroup>

***

## Sports

SX Bet covers a wide range of sports. Each sport is identified by a numeric `sportId`. Pass a `sportId` to other endpoints (such as `/leagues/active`) to filter results by sport.

<Accordion title="Full list of sports">
  | `sportId` | Sport |
  | - | - |
  | `1` | Basketball |
  | `2` | Hockey |
  | `3` | Baseball |
  | `4` | Golf |
  | `5` | Soccer |
  | `6` | Tennis |
  | `7` | Mixed Martial Arts |
  | `8` | Football |
  | `9` | E Sports |
  | `10` | Novelty Markets |
  | `11` | Rugby Union |
  | `12` | Racing |
  | `13` | Boxing |
  | `14` | Crypto |
  | `15` | Cricket |
  | `16` | Economics |
  | `17` | Politics |
  | `18` | Entertainment |
  | `20` | Rugby League |
  | `24` | Horse Racing |
  | `26` | AFL |

  This list may not be exhaustive. Query `GET /sports` for the full, up-to-date list.
</Accordion>

***

## Leagues

SX Bet supports many leagues across all sports. Each league is identified by a `leagueId`.

There are two ways to fetch leagues:

1. **`GET /leagues/active`** — Returns only leagues that currently have active fixtures. Pass a `sportId` to filter by sport.
2. **`GET /leagues`** — Returns all leagues supported by SX Bet, including those without any active fixtures. Also accepts an optional `sportId` filter.

***

## Fixtures

A fixture on SX Bet represents an individual game or event — for example, Toronto Raptors vs. Detroit Pistons on March 10th, 2026. Many markets exist for any given fixture.

To fetch active fixtures, query `GET /fixture/active` with a `leagueId`. Each fixture is identified by a unique `eventId`.

**Example fixture response:**

```json theme={null}
{
  "participantOneName": "William Jewell",
  "participantTwoName": "Indianapolis",
  "startDate": "2020-11-28T03:45:00.000Z",
  "status": 1,
  "leagueId": 2,
  "leagueLabel": "NCAA",
  "sportId": 1,
  "eventId": "L6217784"
}
```

The `startDate` field contains the scheduled start time of the game in UTC. The `status` field indicates the current state of the fixture:

| Status ID | Name | Description |
| - | - | - |
| `1` | Not started | The event has not started yet |
| `2` | In progress | The event is live |
| `3` | Finished | The event has finished |
| `4` | Cancelled | The event has been cancelled |
| `5` | Postponed | The event is postponed. If a new start time is confirmed within 48 hours, it will revert to "Not started". Otherwise, it will be cancelled. |
| `6` | Interrupted | The event has been interrupted (e.g. a rain delay). Coverage will resume under the same event ID. |
| `7` | Abandoned | The event has been abandoned and will not resume (e.g. a player injury in tennis). |
| `8` | Coverage lost | Coverage for this event has been lost |
| `9` | About to start | The event is about to start (shown up to 30 minutes before tip-off) |

<Warning>
  **The wrong spelling of the event id returns `200`, not an error.** Validation strips the unknown
  query field, so `/markets/active?sportXeventId=…` returns **every** market with your filter
  ignored.
</Warning>

One identifier has three spellings, and the casing matters:

* You **read** `sportXeventId` — lowercase `e` — from market, fixture and search rows.
* You **pass** `eventId` to every route and channel.
* `GET /live-scores` and `GET /fixture/status` take a third form: **`sportXEventIds`**, capital `E`
  and plural. Lowercase there is a `400 ["sportXEventIds must be a string"]`.

***

## Markets

A market represents a single binary outcome you can bet on — for example, "Over 2.5 Total Goals" vs. "Under 2.5 Total Goals". Many markets exist for any given fixture.

Every market on SX Bet is identified by a unique `marketHash`, which is used throughout the API to fetch orderbooks, post orders, and query trades.

<Card title="Fetching markets" icon="arrow-right" href="/developers/fetch-markets">
  Querying active markets from the API.
</Card>


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.