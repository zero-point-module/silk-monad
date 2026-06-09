# MonadQuest — controlled demo runbook

A tight, deterministic demo. The relic chest is **prefilled** (no slow live building), so the
Quest Master only has to **create the quest + announce it**, and the adventurers **apply →
go to the chest → view it → claim on-chain**.

## The fixed demo facts (keep these in sync)

| Thing            | Value                                      | Where it must match |
| ---------------- | ------------------------------------------ | ------------------- |
| Relic (secret)   | `golden_apple`                             | `agent-backend/profiles/galactus.json` goal + `seed-chest.js` |
| Chest location   | `10 64 12`                                 | galactus.json clue + `seed-chest.js` |
| Decoy chest      | `iron_ingot` @ `40 64 30`                  | `seed-chest.js` (optional flavor) |
| Reward           | `0.02` MON                                 | galactus.json |

If you change the relic or coords, change them in **both** `galactus.json` and `seed-chest.js`.

## Run order

```bash
# 1 · Minecraft server is up.

# 2 · Prefill the chest — pick ONE:
#   (a) server CONSOLE (no op needed):
/setblock 10 64 12 minecraft:chest
/item replace block 10 64 12 container.0 with minecraft:golden_apple 1
/setblock 40 64 30 minecraft:chest
/item replace block 40 64 30 container.0 with minecraft:iron_ingot 1
#   (b) or the seeder script (needs an op'd 'seeder' bot):
cd agent-backend && node seed-chest.js

# 3 · On-chain factory (once). From blockchain/:
npm install && npm run setup && npm run faucet && npm run deploy:quests
#   deploy:quests writes QUEST_FACTORY_ADDRESS into agent-backend/keys.json automatically.
#   (Optional sanity check, off-Minecraft:)  npm run test:quests

# 4 · Release the agents. From agent-backend/:
node main.js     # MindServer UI: http://localhost:8080
```

## What the audience sees

1. **Galactus** self-prompts → `!createQuest("golden_apple", 0.02, ...)` (on-chain escrow) →
   proclaims *"Quest #0 is OPEN... near 10, 64, 12... APPLY then claim"* in chat.
2. **Trump** and **Xi** **apply** to Galactus in chat (in character), then head to `10 64 12`.
3. Each `!viewChest` → reads `golden_apple` → `!claim(0, "golden_apple")`.
4. The **contract pays the first correct claim** atomically (tx hash in chat); the loser gets
   *"already closed."* Galactus congratulates the winner — judged by the chain, not by hand.

## Offline fallback (no chain)

If the testnet/faucet is flaky, demo against the mock: point the two imports in
`agent-backend/src/agent/commands/actions.js` and `queries.js` from `./quests.js` to
`./quests.mock.js`. The agent flow is identical; "tx hashes" are faked. Steps 1–4 of the
audience flow look the same.
