# Silk Road — Chain

The on-chain side: the agent wallets, the ERC-20 goods, and the scripts that
deploy and fund them on Monad testnet. Requires **node 18+** and **forge**
(Foundry).

## Folder

```
blockchain/
├─ index.js            entry point the agent backend imports
├─ lib/                runtime chain module
│  ├─ config.js          loads .env + agents.json + tokens.json; Monad clients
│  ├─ chain.js           on-chain implementation
│  ├─ chain.mock.js      in-memory implementation (no network / no keys)
│  ├─ queue.js           runs one trade at a time
│  └─ abi.js             ERC-20 ABI
├─ contracts/          Foundry project — build time only
│  ├─ foundry.toml        solc 0.8.28 · evm prague · OpenZeppelin from npm
│  └─ src/Good.sol        the ERC-20, deployed 3× as SPICE / SILK / JADE
└─ scripts/            ops scripts (below)
```

Shared config lives at the **repo root**, not here:

- `agents.json` — roster: id, persona, post coords, address, starting goods
- `tokens.json` — deployed token addresses
- `.env` — private keys + RPC url (git-ignored, created by `setup`)

## Using the scripts

Run once, in order, to bring the chain up:

| # | command | what it does | needs |
|---|---|---|---|
| 1 | `npm install` | install deps (viem · OpenZeppelin · dotenv) | — |
| 2 | `npm run setup` | generate deployer + agent wallets → keys to `.env`, addresses to `agents.json` | — |
| 3 | `npm run faucet` | request MON for every wallet from the agent faucet API | network |
| 4 | `npm run deploy` | `forge build` + deploy the three goods → addresses to `tokens.json` | deployer has MON |
| 5 | `npm run fund` | deployer sends each agent its starting goods | tokens deployed |

Run anytime:

| command | what it does |
|---|---|
| `npm run check` | RPC reachable + chain id + every agent's on-chain balances |
| `npm run test:mock` | exercise the in-memory module end-to-end (no network / no keys) |
| `npm run verify` | verify the deployed contracts on the explorers (optional) |

`setup` is idempotent (existing keys are kept); `faucet` and `fund` are safe to
re-run.
