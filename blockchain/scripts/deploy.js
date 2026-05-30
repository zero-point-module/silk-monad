/**
 * Deploy SPICE/SILK/JADE to Monad testnet. Compiles with Foundry, deploys the
 * artifact with viem (so addresses are captured directly), writes tokens.json.
 */
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { createWalletClient, http } from 'viem';
import {
  ROOT, monadTestnet, RPC_URL, publicClient, txUrl,
  loadTokens, saveTokens, deployerAccount,
} from './lib/config.js';

const CONTRACTS_DIR = path.join(ROOT, 'blockchain', 'contracts');
const ARTIFACT = path.join(CONTRACTS_DIR, 'out', 'Good.sol', 'Good.json');
const INITIAL_SUPPLY = 1_000_000n; // whole tokens, minted to the deployer

function compile() {
  console.log('  Compiling Good.sol (forge build)...');
  execFileSync('forge', ['build'], { cwd: CONTRACTS_DIR, stdio: 'inherit' });
  const artifact = JSON.parse(fs.readFileSync(ARTIFACT, 'utf8'));
  return { abi: artifact.abi, bytecode: artifact.bytecode.object };
}

async function main() {
  const { abi, bytecode } = compile();
  const wallet = createWalletClient({ account: deployerAccount(), chain: monadTestnet, transport: http(RPC_URL) });
  const tokens = loadTokens();

  for (const [symbol, meta] of Object.entries(tokens)) {
    process.stdout.write(`  Deploying ${symbol.padEnd(6)} ... `);
    const hash = await wallet.deployContract({
      abi,
      bytecode,
      args: [meta.name ?? symbol, symbol, INITIAL_SUPPLY],
    });
    const receipt = await publicClient.waitForTransactionReceipt({ hash });
    if (receipt.status !== 'success' || !receipt.contractAddress) {
      throw new Error(`${symbol} deploy reverted (${hash})`);
    }
    meta.address = receipt.contractAddress;
    meta.decimals = meta.decimals ?? 18;
    console.log(`${receipt.contractAddress}   ${txUrl(hash)}`);
  }

  saveTokens(tokens);
  console.log('\n  tokens.json updated.  Next:  npm run fund\n');
}

main().catch((error) => {
  console.error(`\n  deploy failed: ${error.shortMessage ?? error.message}\n`);
  process.exit(1);
});
