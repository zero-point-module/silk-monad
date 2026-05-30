/**
 * Send each agent its starting goods from the deployer (per `start` in
 * agents.json), topping up a little MON if an agent has none. Sequential +
 * receipt-confirmed so the deployer's nonces don't race.
 */
import { createWalletClient, http, parseUnits, parseEther, formatEther } from 'viem';
import {
  monadTestnet, RPC_URL, publicClient, txUrl,
  loadAgents, loadTokens, deployerAccount,
} from './lib/config.js';
import { ERC20_ABI } from './lib/abi.js';

const MIN_AGENT_MON = parseEther('0.05');
const TOP_UP_MON = parseEther('0.2');

async function main() {
  const account = deployerAccount();
  const wallet = createWalletClient({ account, chain: monadTestnet, transport: http(RPC_URL) });
  const { agents } = loadAgents();
  const tokens = loadTokens();

  const deployerMon = await publicClient.getBalance({ address: account.address });
  console.log(`\n  Deployer ${account.address}  (${formatEther(deployerMon)} MON)`);
  if (deployerMon === 0n) throw new Error('deployer has 0 MON — run `npm run faucet` first');

  for (const agent of agents) {
    if (!agent.address) throw new Error(`agent "${agent.id}" has no address — run \`npm run setup\` first`);
    console.log(`\n  ${agent.id} → ${agent.address}`);

    const agentMon = await publicClient.getBalance({ address: agent.address });
    if (agentMon < MIN_AGENT_MON) {
      process.stdout.write(`    +${formatEther(TOP_UP_MON)} MON ... `);
      const hash = await wallet.sendTransaction({ to: agent.address, value: TOP_UP_MON });
      await publicClient.waitForTransactionReceipt({ hash });
      console.log(txUrl(hash));
    }

    for (const [symbol, amount] of Object.entries(agent.start ?? {})) {
      const token = tokens[symbol];
      if (!token?.address) throw new Error(`token "${symbol}" not deployed — run \`npm run deploy\` first`);
      process.stdout.write(`    +${amount} ${symbol} ... `);
      const hash = await wallet.writeContract({
        address: token.address,
        abi: ERC20_ABI,
        functionName: 'transfer',
        args: [agent.address, parseUnits(String(amount), token.decimals ?? 18)],
      });
      await publicClient.waitForTransactionReceipt({ hash });
      console.log(txUrl(hash));
    }
  }
  console.log('\n  Funding complete. Agents can now trade on-chain.\n');
}

main().catch((error) => {
  console.error(`\n  fund failed: ${error.shortMessage ?? error.message}\n`);
  process.exit(1);
});
