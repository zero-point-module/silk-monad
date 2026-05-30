/**
 * Mock settlement — same signatures as chain.js, no network/keys/deps. Dev 3
 * builds against this and flips CHAIN_IMPL=real later. Keeps an in-memory ledger
 * seeded from each agent's `start` in agents.json, so balances move like the
 * real thing.
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { enqueue } from './queue.js';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const EXPLORER_URL = (process.env.EXPLORER_URL ?? 'https://monad-testnet.socialscan.io').replace(/\/+$/, '');
const txUrl = (hash) => `${EXPLORER_URL}/tx/${hash}`;
const readJson = (file) => JSON.parse(fs.readFileSync(path.join(ROOT, file), 'utf8'));

let ledger = null;
function getLedger() {
  if (ledger) return ledger;
  const { agents } = readJson('agents.json');
  const symbols = Object.keys(readJson('tokens.json'));
  ledger = {};
  for (const agent of agents) {
    ledger[agent.id] = Object.fromEntries(symbols.map((symbol) => [symbol, agent.start?.[symbol] ?? 0]));
  }
  return ledger;
}

let hashCounter = 1;
const fakeHash = () => `0x${(hashCounter++).toString(16).padStart(64, '0')}`;
const fail = (error) => ({ ok: false, error, legs: [], hashes: [], explorerUrls: [] });

export async function getBalances(agentId) {
  const balances = getLedger()[agentId];
  if (!balances) throw new Error(`Unknown agent id "${agentId}" — not in agents.json`);
  return { ...balances };
}

async function runSettleTrade({ fromAgent, toAgent, giveToken, giveAmount, wantToken, wantAmount }) {
  const book = getLedger();
  if (!book[fromAgent]) return fail(`Unknown agent "${fromAgent}"`);
  if (!book[toAgent]) return fail(`Unknown agent "${toAgent}"`);
  if ((book[fromAgent][giveToken] ?? 0) < giveAmount) {
    return fail(`${fromAgent} has ${book[fromAgent][giveToken] ?? 0} ${giveToken}, needs ${giveAmount}`);
  }
  if ((book[toAgent][wantToken] ?? 0) < wantAmount) {
    return fail(`${toAgent} has ${book[toAgent][wantToken] ?? 0} ${wantToken}, needs ${wantAmount}`);
  }

  await new Promise((resolve) => setTimeout(resolve, 250));

  book[fromAgent][giveToken] -= giveAmount;
  book[toAgent][giveToken] = (book[toAgent][giveToken] ?? 0) + giveAmount;
  book[toAgent][wantToken] -= wantAmount;
  book[fromAgent][wantToken] = (book[fromAgent][wantToken] ?? 0) + wantAmount;

  const giveHash = fakeHash();
  const wantHash = fakeHash();
  const legs = [
    { dir: 'give', from: fromAgent, to: toAgent, token: giveToken, amount: giveAmount, hash: giveHash, explorerUrl: txUrl(giveHash) },
    { dir: 'want', from: toAgent, to: fromAgent, token: wantToken, amount: wantAmount, hash: wantHash, explorerUrl: txUrl(wantHash) },
  ];
  return { ok: true, mock: true, fromAgent, toAgent, legs, hashes: [giveHash, wantHash], explorerUrls: legs.map((leg) => leg.explorerUrl) };
}

export function settleTrade(trade) {
  return enqueue(() => runSettleTrade(trade));
}
