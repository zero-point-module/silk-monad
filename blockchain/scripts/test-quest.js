/**
 * Standalone end-to-end test of the on-chain quest loop — proves the QuestFactory +
 * quests.js bridge + answer-hash parity WITHOUT Minecraft. Run after deploy + faucet:
 *
 *   npm run setup && npm run faucet && npm run deploy:quests && npm run test:quests
 *
 * Exercises the 5 steps from the spec: create → wrong claim → first-correct win →
 * winner paid → duplicate claim rejected.
 */
import { formatEther } from 'viem';
import { publicClient, loadAgents } from './lib/config.js';
import * as quests from '../quests.js';

// The runtime bridge resolves a signing key by the agent's wallet ADDRESS, scanning
// keys.json + env for *_PRIVATE_KEY. The ops wallets live in .env as AGENT_<ID>_PK, so
// mirror each into a <ID>_PRIVATE_KEY env var the bridge's keyByAddress() can find.
function bridgeAgentKeys(agents) {
  for (const a of agents) {
    const opsKey = process.env[`AGENT_${a.id.toUpperCase()}_PK`];
    if (opsKey) process.env[`${a.id.toUpperCase()}_PRIVATE_KEY`] = opsKey;
  }
}

let failures = 0;
function check(label, ok, detail = '') {
  console.log(`  ${ok ? '✅' : '❌'} ${label}${detail ? `  — ${detail}` : ''}`);
  if (!ok) failures++;
}

async function main() {
  const { agents } = loadAgents();
  if (agents.length < 3) throw new Error('need at least 3 agents in agents.json (1 QM + 2 players)');
  bridgeAgentKeys(agents);

  const [qm, p1, p2] = agents;
  const SECRET = 'golden_apple';
  const REWARD = 0.002; // MON — small so a faucet top-up covers many runs

  console.log(`\n  QM=${qm.id}  player1=${p1.id}  player2=${p2.id}`);
  const qmMon = await publicClient.getBalance({ address: qm.address });
  console.log(`  QM balance: ${formatEther(qmMon)} MON  (needs reward + gas)\n`);

  // 1. create
  const created = await quests.createQuest(qm.id, SECRET, REWARD, 'find the relic chest');
  const id = created.questId;
  check('createQuest returns a questId', Number.isInteger(id), `questId=${id}, tx=${created.hash}`);

  // 2. read back
  const q0 = await quests.getQuest(id);
  check('quest is OPEN after creation', q0.solved === false && q0.cancelled === false);
  check('escrowed reward matches', Math.abs(Number(q0.reward) - REWARD) < 1e-9, `${q0.reward} MON`);
  check('committed answerHash matches local hash', q0.answerHash === quests.answerHash(SECRET));

  // 3. wrong answer — rejected, no state change, no gas spent (simulate revert)
  const wrong = await quests.claim(p1.id, id, 'WRONG');
  check('wrong answer rejected', wrong.ok === false && wrong.won === false, wrong.reason);
  const qAfterWrong = await quests.getQuest(id);
  check('quest still OPEN after wrong answer', qAfterWrong.solved === false);

  // 4. first correct claim wins (case/whitespace-insensitive via normalize)
  const p1Before = await publicClient.getBalance({ address: p1.address });
  const win = await quests.claim(p1.id, id, '  Golden_Apple ');
  check('first correct claim wins', win.won === true, `tx=${win.hash}`);
  const qSolved = await quests.getQuest(id);
  check('quest now SOLVED by player1',
    qSolved.solved === true && qSolved.winner.toLowerCase() === p1.address.toLowerCase(),
    `winner=${qSolved.winner}`);
  const p1After = await publicClient.getBalance({ address: p1.address });
  check('winner received the reward (net of gas)', p1After > p1Before,
    `${formatEther(p1Before)} → ${formatEther(p1After)} MON`);

  // 5. duplicate/late claim rejected
  const late = await quests.claim(p2.id, id, SECRET);
  check('duplicate claim rejected (already closed)', late.ok === false && late.won === false, late.reason);

  console.log(`\n  ${failures === 0 ? 'ALL CHECKS PASSED ✅' : `${failures} CHECK(S) FAILED ❌`}\n`);
  process.exit(failures === 0 ? 0 : 1);
}

main().catch((error) => {
  console.error(`\n  test-quest failed: ${error.shortMessage ?? error.message}\n`);
  process.exit(1);
});
