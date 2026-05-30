/**
 * Generate the deployer + one wallet per agent. Private keys → root .env
 * (idempotent: existing keys are kept). Addresses → agents.json. Safe to re-run.
 */
import fs from 'node:fs';
import path from 'node:path';
import { generatePrivateKey, privateKeyToAccount } from 'viem/accounts';
import { ROOT, loadAgents, saveAgents } from './lib/config.js';

const ENV_PATH = path.join(ROOT, '.env');

const DEFAULTS = {
  RPC_URL: 'https://testnet-rpc.monad.xyz',
  CHAIN_ID: '10143',
  EXPLORER_URL: 'https://monad-testnet.socialscan.io',
};

function readEnv() {
  const map = new Map();
  if (!fs.existsSync(ENV_PATH)) return map;
  for (const line of fs.readFileSync(ENV_PATH, 'utf8').split('\n')) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    const eq = trimmed.indexOf('=');
    if (eq === -1) continue;
    map.set(trimmed.slice(0, eq).trim(), trimmed.slice(eq + 1).trim());
  }
  return map;
}

function writeEnv(map) {
  const lines = [...map.entries()].map(([key, value]) => `${key}=${value}`);
  fs.writeFileSync(ENV_PATH, `${lines.join('\n')}\n`);
  fs.chmodSync(ENV_PATH, 0o600);
}

const ensureKey = (env, name) => {
  if (!env.has(name)) env.set(name, generatePrivateKey());
  return env.get(name);
};

const env = readEnv();
for (const [key, value] of Object.entries(DEFAULTS)) {
  if (!env.has(key)) env.set(key, value);
}

const deployer = privateKeyToAccount(ensureKey(env, 'DEPLOYER_PK'));

const agentsDoc = loadAgents();
for (const agent of agentsDoc.agents) {
  agent.address = privateKeyToAccount(ensureKey(env, `AGENT_${agent.id.toUpperCase()}_PK`)).address;
}

writeEnv(env);
saveAgents(agentsDoc);

console.log('\n  Wallets ready (keys → .env, addresses → agents.json)\n');
console.log(`  deployer     ${deployer.address}`);
for (const agent of agentsDoc.agents) {
  console.log(`  ${agent.id.padEnd(11)}  ${agent.address}`);
}
console.log('\n  Next:  npm run faucet   (fund these with MON)\n');
