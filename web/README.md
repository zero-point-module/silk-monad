# web — MonadQuest Quest Board

A single self-contained `index.html` (no build, no deps, no backend). A fantasy
guild quest-board dashboard for **MonadQuest**: a Quest Master posts an on-chain
MON bounty, two rival adventurers (Aria vs Kai) race to solve it, and the
QuestFactory contract pays the first correct solver — all surfaced as a live
quest log.

## View it

```bash
# simplest: double-click web/index.html, or
open web/index.html

# or serve it
python3 -m http.server -d web 8080   # → http://localhost:8080
```

### Demo mode (works right now, no contract needed)

The page **defaults to demo mode** while the factory address is still the
placeholder, so it's fully demoable out of the box. To force it explicitly:

```
index.html?demo=1
```

Demo mode simulates the whole lifecycle on a loop — Quest Created → Aria & Kai
racing (photo-finish progress bars) → a winner (alternating Aria/Kai) → reward
paid — with mock explorer links.

### Live on-chain mode (once the contract is deployed)

Point the page at a deployed **QuestFactory** by either:

1. editing one line near the top of the inline `<script>`:
   ```js
   const FACTORY_ADDRESS = "0xYourDeployedFactory…";
   ```
2. or passing it on the URL (no edit needed):
   ```
   index.html?factory=0xYourDeployedFactory…
   ```

It then reads `QuestCreated` / `QuestSolved` / `QuestCancelled` events directly
from Monad testnet (chainId `10143`, RPC `https://testnet-rpc.monad.xyz`) via
**viem** imported from a CDN, polling every ~2s. Rewards are formatted from wei
to MON; creator/winner addresses are mapped back to character names. If viem or
the RPC is unreachable, it falls back to demo mode automatically.

## How the feeds are wired

Both feeds emit the **same event objects** into a single `pushEvent()` renderer,
so they're swappable 1:1:

- `startDemo()` — the MOCK feed (clearly separated, top of the `MOCK / DEMO FEED`
  section).
- `startOnChainFeed(factory)` — the REAL viem feed (the `REAL ON-CHAIN FEED`
  section).

`boot()` chooses between them based on `?demo=1` / `?factory=` / whether
`FACTORY_ADDRESS` is still the placeholder.

## The cast (addresses are the single source of truth)

| Character        | Role                   | Wallet                                       |
|------------------|------------------------|----------------------------------------------|
| The Quest Master | game master            | `0xe5C41641100b5a5d607C4Dba1dde3DB07AF01002` |
| Aria             | methodical adventurer  | `0xdCAc1995AD6E069a0665A703A09e8839799FDdC8` |
| Kai              | reckless adventurer    | `0xd509E3cb6c9eA1E00e9Ca53a6777Ee31477d67F9` |
