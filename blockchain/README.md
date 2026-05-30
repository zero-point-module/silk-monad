# Silk Road — Chain

The on-chain side: the ERC-20 goods, the deploy/fund scripts, and the runtime
modules the agent backend imports to read balances and move tokens on Monad
testnet. Requires **node 18+** and **forge** (Foundry).

## Folder

```
blockchain/
├─ config.js          runtime — RPC + token registry
├─ erc20.js           runtime — balances · transfer · verifyTransfer
├─ wallets.js         runtime — per-agent wallets
├─ contracts/
│  ├─ foundry.toml      solc 0.8.28 · evm prague · OpenZeppelin from npm
│  └─ src/Good.sol      the ERC-20, deployed 3× as SPICE / SILK / JADE
└─ scripts/           deploy · faucet · fund · verify · setup-wallets
   └─ lib/            shared helpers (config · abi)
```

`config.js` / `erc20.js` / `wallets.js` are imported by the agent backend and
read `keys.json` / `agents.json` / `tokens.json` from its working directory. The
scripts use the repo-root `.env` + `agents.json` / `tokens.json`.

## Using the scripts

Run once, in order, to deploy the goods and fund the wallets:

| # | command | what it does | needs |
|---|---|---|---|
| 1 | `npm install` | install deps (viem · OpenZeppelin · dotenv) | — |
| 2 | `npm run setup` | generate deployer + agent wallets → keys to `.env`, addresses to `agents.json` | — |
| 3 | `npm run faucet` | request MON for every wallet from the agent faucet API | network |
| 4 | `npm run deploy` | `forge build` + deploy the three goods → addresses to `tokens.json` | deployer has MON |
| 5 | `npm run fund` | deployer sends each agent its starting goods | tokens deployed |

`npm run verify` (optional) verifies the deployed contracts on the explorers.
`setup` is idempotent; `faucet` and `fund` are safe to re-run.
