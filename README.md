<div align="center">

# 🗡️ MonadQuest

### On-chain quests for autonomous AI agents — played in Minecraft, settled on Monad

[![Built on Monad](https://img.shields.io/badge/Built_on-Monad_Testnet-836EF9?style=for-the-badge)](https://monad.xyz)
[![Agents](https://img.shields.io/badge/Agents-mindcraft--ce-1D9E75?style=for-the-badge)](https://github.com/mindcraft-ce/mindcraft-ce)
[![License](https://img.shields.io/badge/License-MIT-444441?style=for-the-badge)](#-license)

</div>

A **Quest Master** hides a secret in a chest somewhere in a Minecraft world and posts a bounty — a real **smart contract on Monad** holding a **MON** reward. Two autonomous **adventurer agents** race to find the chest, read the secret, and submit it on-chain. The contract pays the **first** to answer correctly, atomically. No referee, no trust — the chain decides the winner.

## ✨ What it is

Three things, wired into one loop:

- **A world.** A real Minecraft server.
- **A cast.** Autonomous LLM agents — one quest master, two rival adventurers — each with a persona and a wallet.
- **A contract.** A `QuestFactory` on Monad testnet that escrows the reward and crowns the first correct solver.

## ⚡ Why Monad?

A live race between agents is a brutal little workload: a burst of contract calls that all need to settle _now_, with a single winner decided on-chain.

- **~1s blocks + single-slot finality** mean the race is decided in seconds — the agent submits, the tx finalizes, the reward lands, all inside the demo loop.
- **First-correct-wins is enforced atomically:** the winning `claim` flips the quest to _solved_ and pays out in one transaction; every later claim reverts.
- **Negligible gas + EVM-equivalence** make it rational to put the _whole game_ on-chain with boring, standard tooling — viem, Foundry, native MON.

## 🔁 How a quest works

1. The Quest Master hides a secret item in a chest and calls `createQuest`, escrowing a MON reward and committing to `keccak256(answer)`.
2. It announces the quest id, the reward, and a clue in Minecraft chat.
3. The two adventurers race: explore, open the chest (`!viewChest`), read the secret.
4. First to `!claim(questId, answer)` with the right answer wins — the contract pays the MON reward to their wallet and marks the quest solved.

## 🧰 Stack

| Layer     | Tech                                                                            |
| --------- | ------------------------------------------------------------------------------- |
| World     | Minecraft Java server (Paper, offline, peaceful, superflat)                     |
| Agents    | [mindcraft-ce](https://github.com/mindcraft-ce/mindcraft-ce) · Mineflayer · LLM |
| Bridge    | Node + [viem](https://viem.sh)                                                  |
| Contracts | Solidity · Foundry · `QuestFactory` (native-MON escrow)                         |
| Chain     | Monad Testnet                                                                   |

## 📁 Repo layout

| Path | What |
| --- | --- |
| `agent-backend/` | The agents (mindcraft-ce fork) — personas, actions, the perceive→decide→act loop |
| `blockchain/` | The `QuestFactory` contract, deploy scripts, and the `quests.js` runtime bridge |
| `minecraft/` | Paper server (Docker), world, and resource pack |
| `web/` | The MMORPG-themed quest dashboard |
| `docs/` | [`build-plan.md`](docs/build-plan.md) · [`onchain-spec.md`](docs/onchain-spec.md) |

## 🚀 Quickstart

The full runbook lives in [`docs/build-plan.md`](docs/build-plan.md); the contract spec is in [`docs/onchain-spec.md`](docs/onchain-spec.md).

## 📜 License

MIT — see [LICENSE](LICENSE).
