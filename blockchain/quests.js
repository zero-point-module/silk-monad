/**
 * ⚠️  MOCK IMPLEMENTATION — for parallel development only.
 *
 * REPLACE the body of this file with the real on-chain QuestFactory bridge per
 * docs/onchain-spec.md (§4). Keep the EXACT exported signatures below — the agent
 * actions (!createQuest / !claim) import this module and must not change at
 * integration time.
 *
 * This mock is file-backed (./quests-mock.json, cwd-relative) so it works ACROSS
 * the separate per-agent Node processes — the quest master process creates a quest
 * and the player processes can see + claim it. It uses the real viem keccak so the
 * answer-hash convention is identical to on-chain; only the "chain" is faked.
 *
 * Limitation: it does not perfectly serialize two simultaneous claims (the real
 * contract does, via solved-flag atomicity). Use the real chain to demo the race.
 */
import { keccak256, toBytes } from 'viem';
import { readFileSync, writeFileSync } from 'fs';

const STATE_PATH = './quests-mock.json';

function readState() {
    try { return JSON.parse(readFileSync(STATE_PATH, 'utf8')); } catch { return []; }
}
function writeState(quests) {
    writeFileSync(STATE_PATH, JSON.stringify(quests, null, 2) + '\n');
}

// Must match Solidity keccak256(bytes(answer)). Normalize both on create and claim
// so what the bot reads from the chest ("Golden_Apple") matches what the QM committed.
const normalize = (s) => String(s).trim().toLowerCase();
export const answerHash = (secret) => keccak256(toBytes(normalize(secret)));

const MOCK_FACTORY = '0xMOCKFACTORY0000000000000000000000000000';

/** QM creates a quest, "escrowing" rewardMon. @returns {{ hash, questId, factory, from }} */
export async function createQuest(agentName, secret, rewardMon, description = '') {
    const quests = readState();
    const questId = quests.length;
    quests.push({
        creator: agentName,
        reward: String(rewardMon),
        answerHash: answerHash(secret),
        winner: null,
        solved: false,
        cancelled: false,
        description,
    });
    writeState(quests);
    return { hash: `0xMOCK_CREATE_${questId}`, questId, factory: MOCK_FACTORY, from: agentName };
}

/** Player submits answer. First correct + open claim wins. @returns {{ ok, won, hash?, reason? }} */
export async function claim(agentName, questId, answer) {
    const quests = readState();
    const q = quests[questId];
    if (!q) return { ok: false, won: false, reason: `quest #${questId} not found` };
    if (q.solved || q.cancelled) return { ok: false, won: false, reason: `quest #${questId} is already closed` };
    if (answerHash(answer) !== q.answerHash) return { ok: false, won: false, reason: 'wrong answer' };
    q.solved = true;
    q.winner = agentName;
    writeState(quests);
    return { ok: true, won: true, hash: `0xMOCK_CLAIM_${questId}` };
}

/** Read a quest's state. @returns {{ creator, reward, solved, cancelled, winner, answerHash }} */
export async function getQuest(questId) {
    const q = readState()[questId];
    if (!q) throw new Error(`quest #${questId} not found`);
    return {
        creator: q.creator, reward: q.reward, solved: q.solved,
        cancelled: q.cancelled, winner: q.winner, answerHash: q.answerHash,
    };
}
