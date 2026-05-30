/**
 * Health check: RPC reachable + chain id matches, then print each agent's
 * on-chain MON + token balances. Read-only.
 */
import { formatEther } from 'viem';
import {
  publicClient, CHAIN_ID, RPC_URL, EXPLORER_URL,
  loadAgents, loadTokens,
} from '../lib/config.js';
import { getBalances } from '../lib/chain.js';

async function main() {
  console.log(`\n  RPC       ${RPC_URL}`);
  console.log(`  Explorer  ${EXPLORER_URL}`);

  const id = await publicClient.getChainId();
  const block = await publicClient.getBlockNumber();
  console.log(`  chainId   ${id} ${id === CHAIN_ID ? '✓' : `✗ expected ${CHAIN_ID}`}`);
  console.log(`  block     ${block}`);
  if (id !== CHAIN_ID) throw new Error('chain id mismatch — check RPC_URL / CHAIN_ID');

  const { agents } = loadAgents();
  const symbols = Object.keys(loadTokens());
  console.log(`\n  ${'agent'.padEnd(11)} ${'MON'.padStart(9)}   ${symbols.map((s) => s.padStart(6)).join(' ')}`);
  for (const agent of agents) {
    if (!agent.address) {
      console.log(`  ${agent.id.padEnd(11)} (no address — run: npm run setup)`);
      continue;
    }
    const monRaw = await publicClient.getBalance({ address: agent.address });
    const balances = await getBalances(agent.id);
    const cols = symbols.map((s) => String(balances[s] ?? 0).padStart(6)).join(' ');
    console.log(`  ${agent.id.padEnd(11)} ${Number(formatEther(monRaw)).toFixed(3).padStart(9)}   ${cols}`);
  }
  console.log('');
}

main().catch((error) => {
  console.error(`\n  check failed: ${error.shortMessage ?? error.message}\n`);
  process.exit(1);
});
