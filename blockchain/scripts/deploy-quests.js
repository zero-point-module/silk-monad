/**
 * Deploy the persistent QuestFactory to Monad testnet. Compiles with Foundry, deploys
 * the artifact with viem (so the address is captured directly), and persists it to:
 *   - root .env            QUEST_FACTORY_ADDRESS   (for ops scripts)
 *   - root quests.json     { "factory": "0x.." }   (for tooling / UI)
 *   - agent-backend/keys.json QUEST_FACTORY_ADDRESS (where the runtime bridge reads it)
 */
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { createWalletClient, http } from 'viem';
import {
  ROOT, monadTestnet, RPC_URL, publicClient, txUrl, deployerAccount,
} from './lib/config.js';

const CONTRACTS_DIR = path.join(ROOT, 'blockchain', 'contracts');
const ARTIFACT = path.join(CONTRACTS_DIR, 'out', 'QuestFactory.sol', 'QuestFactory.json');
const ENV_PATH = path.join(ROOT, '.env');
const QUESTS_JSON = path.join(ROOT, 'quests.json');
const KEYS_JSON = path.join(ROOT, 'agent-backend', 'keys.json');

function compile() {
  console.log('  Compiling QuestFactory.sol (forge build)...');
  execFileSync('forge', ['build'], { cwd: CONTRACTS_DIR, stdio: 'inherit' });
  const artifact = JSON.parse(fs.readFileSync(ARTIFACT, 'utf8'));
  return { abi: artifact.abi, bytecode: artifact.bytecode.object };
}

/** Upsert KEY=value in a dotenv-style file (creating it if missing), preserving other lines. */
function upsertEnv(file, key, value) {
  const lines = fs.existsSync(file) ? fs.readFileSync(file, 'utf8').split('\n') : [];
  let found = false;
  const out = lines.map((line) => {
    const t = line.trim();
    if (!t || t.startsWith('#')) return line;
    if (t.slice(0, t.indexOf('=')).trim() === key) { found = true; return `${key}=${value}`; }
    return line;
  });
  if (!found) {
    if (out.length && out[out.length - 1].trim() === '') out.splice(out.length - 1, 0, `${key}=${value}`);
    else out.push(`${key}=${value}`);
  }
  fs.writeFileSync(file, out.join('\n').replace(/\n*$/, '\n'));
}

/** Upsert a top-level key in a JSON file (creating it if missing), preserving other keys. */
function upsertJson(file, key, value) {
  let obj = {};
  try { obj = JSON.parse(fs.readFileSync(file, 'utf8')); } catch { /* new / empty */ }
  obj[key] = value;
  fs.writeFileSync(file, `${JSON.stringify(obj, null, 4)}\n`);
}

async function main() {
  const { abi, bytecode } = compile();
  const account = deployerAccount();
  const wallet = createWalletClient({ account, chain: monadTestnet, transport: http(RPC_URL) });

  console.log(`\n  Deployer ${account.address}`);
  process.stdout.write('  Deploying QuestFactory ... ');
  const hash = await wallet.deployContract({ abi, bytecode, args: [] });
  const receipt = await publicClient.waitForTransactionReceipt({ hash });
  if (receipt.status !== 'success' || !receipt.contractAddress) {
    throw new Error(`QuestFactory deploy reverted (${hash})`);
  }
  const factory = receipt.contractAddress;
  console.log(`${factory}   ${txUrl(hash)}`);

  upsertEnv(ENV_PATH, 'QUEST_FACTORY_ADDRESS', factory);
  upsertJson(QUESTS_JSON, 'factory', factory);
  upsertJson(KEYS_JSON, 'QUEST_FACTORY_ADDRESS', factory);

  console.log('\n  Saved QUEST_FACTORY_ADDRESS to .env, quests.json, and agent-backend/keys.json.');
  console.log('  The agents can now create + claim quests on-chain.\n');
}

main().catch((error) => {
  console.error(`\n  deploy-quests failed: ${error.shortMessage ?? error.message}\n`);
  process.exit(1);
});
