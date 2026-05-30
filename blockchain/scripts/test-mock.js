/**
 * Exercise the mock seam end-to-end — no network/keys. Reads agents.json with
 * plain fs to prove the mock path needs zero deps.  (npm run test:mock)
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

process.env.CHAIN_IMPL = 'mock';
const { getBalances, settleTrade, IMPL } = await import('../index.js');

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const ids = JSON.parse(fs.readFileSync(path.join(ROOT, 'agents.json'), 'utf8')).agents.map((a) => a.id);

function assert(cond, msg) {
  if (!cond) {
    console.error(`  ✗ ${msg}`);
    process.exit(1);
  }
  console.log(`  ✓ ${msg}`);
}

console.log('');
assert(IMPL === 'mock', `index resolved to mock impl (got "${IMPL}")`);
assert(ids.length >= 2, 'at least two agents in agents.json');

const [a, b] = ids;
const beforeA = await getBalances(a);
const beforeB = await getBalances(b);
console.log(`\n  before:  ${a}=${JSON.stringify(beforeA)}  ${b}=${JSON.stringify(beforeB)}\n`);

const give = Object.keys(beforeA).find((s) => beforeA[s] > 0);
const want = Object.keys(beforeB).find((s) => beforeB[s] > 0);
assert(Boolean(give) && Boolean(want), `each side has a good to give (${a}:${give}, ${b}:${want})`);

const N = 5;
const result = await settleTrade({ fromAgent: a, toAgent: b, giveToken: give, giveAmount: N, wantToken: want, wantAmount: N });
assert(result.ok, `settleTrade ok ${result.error ? `(${result.error})` : ''}`);
assert(result.hashes.length === 2, 'two tx hashes returned');
assert(/^0x[0-9a-f]{64}$/.test(result.hashes[0]), 'hash looks like a 32-byte hex string');

const afterA = await getBalances(a);
const afterB = await getBalances(b);
console.log(`\n  after:   ${a}=${JSON.stringify(afterA)}  ${b}=${JSON.stringify(afterB)}\n`);
assert(afterA[give] === beforeA[give] - N, `${a} ${give} decreased by ${N}`);
assert(afterA[want] === (beforeA[want] ?? 0) + N, `${a} ${want} increased by ${N}`);
assert(afterB[give] === (beforeB[give] ?? 0) + N, `${b} ${give} increased by ${N}`);
assert(afterB[want] === beforeB[want] - N, `${b} ${want} decreased by ${N}`);

const overspend = await settleTrade({ fromAgent: a, toAgent: b, giveToken: give, giveAmount: 1_000_000_000, wantToken: want, wantAmount: N });
assert(!overspend.ok, 'over-spend trade is rejected');

console.log('\n  ✓ mock seam OK\n');
