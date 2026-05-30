/**
 * Verify the deployed goods on all Monad explorers via the verification API.
 * Optional; failures only warn.
 */
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { encodeAbiParameters, parseAbiParameters } from 'viem';
import { ROOT, loadTokens } from '../lib/config.js';

const CONTRACTS_DIR = path.join(ROOT, 'blockchain', 'contracts');
const ARTIFACT = path.join(CONTRACTS_DIR, 'out', 'Good.sol', 'Good.json');
const VERIFY_URL = 'https://agents.devnads.com/v1/verify';
const CONTRACT_NAME = 'src/Good.sol:Good';
const INITIAL_SUPPLY_WEI = 1_000_000n * 10n ** 18n;

function compilerVersion() {
  const artifact = JSON.parse(fs.readFileSync(ARTIFACT, 'utf8'));
  const metadata = typeof artifact.metadata === 'string' ? JSON.parse(artifact.metadata) : artifact.metadata;
  return `v${metadata.compiler.version}`;
}

function standardJsonInput(address) {
  const out = execFileSync(
    'forge',
    ['verify-contract', address, CONTRACT_NAME, '--chain', '10143', '--show-standard-json-input'],
    { cwd: CONTRACTS_DIR, encoding: 'utf8' },
  );
  return JSON.parse(out);
}

async function verify(symbol, meta) {
  const constructorArgs = encodeAbiParameters(
    parseAbiParameters('string, string, uint256'),
    [meta.name ?? symbol, symbol, INITIAL_SUPPLY_WEI],
  ).slice(2);

  const body = {
    chainId: 10143,
    contractAddress: meta.address,
    contractName: CONTRACT_NAME,
    compilerVersion: compilerVersion(),
    standardJsonInput: standardJsonInput(meta.address),
    constructorArgs,
  };

  process.stdout.write(`  Verifying ${symbol.padEnd(6)} (${meta.address}) ... `);
  const res = await fetch(VERIFY_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  console.log(res.ok ? 'ok' : `warn (${res.status}: ${(await res.text()).slice(0, 120)})`);
}

const tokens = loadTokens();
for (const [symbol, meta] of Object.entries(tokens)) {
  if (!meta.address) {
    console.log(`  ${symbol}: no address — deploy first, skipping`);
    continue;
  }
  try {
    await verify(symbol, meta);
  } catch (error) {
    console.log(`warn — ${error.message}`);
  }
}
console.log('');
