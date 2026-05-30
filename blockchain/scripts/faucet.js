/**
 * Fund the deployer + every agent with testnet MON via the agent faucet API.
 * On failure, prints the official faucet URL to fund by hand. Safe to re-run.
 */
import { loadAgents, deployerAccount, publicClient, txUrl } from '../lib/config.js';

const FAUCET_URL = 'https://agents.devnads.com/v1/faucet';
const CHAIN_ID = 10143;
const mon = (wei) => `${(Number(wei) / 1e18).toFixed(3)} MON`;

async function requestFaucet(address) {
  const res = await fetch(FAUCET_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ chainId: CHAIN_ID, address }),
  });
  const text = await res.text();
  if (!res.ok) throw new Error(`${res.status} ${text.slice(0, 160)}`);
  return JSON.parse(text);
}

async function fund(label, address) {
  process.stdout.write(`  ${label.padEnd(11)}  ${address}  ... `);
  try {
    const { txHash, amount } = await requestFaucet(address);
    console.log(`+${amount ? mon(amount) : '1.000 MON'}   ${txHash ? txUrl(txHash) : ''}`);
  } catch (error) {
    console.log(`FAILED — ${error.message}`);
    console.log(`               fund manually at https://faucet.monad.xyz  →  ${address}`);
  }
}

const { agents } = loadAgents();
const missing = agents.filter((a) => !a.address);
if (missing.length) {
  console.error(`\n  Agents without an address: ${missing.map((a) => a.id).join(', ')}`);
  console.error('  Run `npm run setup` first.\n');
  process.exit(1);
}

const targets = [
  ['deployer', deployerAccount().address],
  ...agents.map((a) => [a.id, a.address]),
];

console.log('\n  Requesting MON from the agent faucet...\n');
for (const [label, address] of targets) {
  await fund(label, address);
}

console.log('\n  Balances:');
for (const [label, address] of targets) {
  console.log(`  ${label.padEnd(11)}  ${mon(await publicClient.getBalance({ address }))}`);
}
console.log('\n  Next:  npm run deploy\n');
