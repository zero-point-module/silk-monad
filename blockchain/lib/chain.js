/**
 * On-chain settlement on Monad testnet. Custodial: the backend holds each
 * agent's key and signs both legs. Two ERC-20 transfers, balance-checked up
 * front — not an atomic swap. Whole integers here; decimals come from
 * tokens.json. Public settleTrade is serialized by queue.js.
 */
import { createWalletClient, http, parseUnits, formatUnits } from 'viem';
import {
  publicClient, monadTestnet, RPC_URL, txUrl,
  loadTokens, getAgent, agentAccount,
} from './config.js';
import { ERC20_ABI } from './abi.js';
import { enqueue } from './queue.js';

const RETRY_DELAYS_MS = [600, 1500, 3000];

async function withRetry(label, fn) {
  let lastError;
  for (let attempt = 0; attempt <= RETRY_DELAYS_MS.length; attempt++) {
    try {
      return await fn();
    } catch (error) {
      lastError = error;
      const delay = RETRY_DELAYS_MS[attempt];
      if (delay === undefined) break;
      await new Promise((resolve) => setTimeout(resolve, delay));
    }
  }
  throw new Error(`${label} failed: ${lastError?.shortMessage ?? lastError?.message ?? lastError}`);
}

function resolveToken(tokens, symbol) {
  const token = tokens[symbol];
  if (!token) throw new Error(`Unknown token "${symbol}" — not in tokens.json`);
  if (!token.address) throw new Error(`Token "${symbol}" has no address — run \`npm run deploy\` first`);
  return { address: token.address, decimals: token.decimals ?? 18 };
}

const fail = (error) => ({ ok: false, error, legs: [], hashes: [], explorerUrls: [] });

/** Token balances for an agent as whole integers: { SPICE: 100, SILK: 0, ... }. */
export async function getBalances(agentId) {
  const { address } = getAgent(agentId);
  const tokens = loadTokens();
  const balances = {};
  for (const [symbol, meta] of Object.entries(tokens)) {
    if (!meta.address) {
      balances[symbol] = 0;
      continue;
    }
    const raw = await withRetry(`read ${symbol} balance`, () =>
      publicClient.readContract({
        address: meta.address,
        abi: ERC20_ABI,
        functionName: 'balanceOf',
        args: [address],
      }));
    balances[symbol] = Math.round(Number(formatUnits(raw, meta.decimals ?? 18)));
  }
  return balances;
}

async function sendLeg({ tokens, fromId, toId, symbol, amount }) {
  const { address: tokenAddress, decimals } = resolveToken(tokens, symbol);
  const recipient = getAgent(toId).address;
  const wallet = createWalletClient({
    account: agentAccount(fromId),
    chain: monadTestnet,
    transport: http(RPC_URL),
  });

  const hash = await withRetry(`transfer ${amount} ${symbol} (${fromId}→${toId})`, () =>
    wallet.writeContract({
      address: tokenAddress,
      abi: ERC20_ABI,
      functionName: 'transfer',
      args: [recipient, parseUnits(String(amount), decimals)],
    }));

  const receipt = await withRetry(`receipt ${hash}`, () =>
    publicClient.waitForTransactionReceipt({ hash }));

  if (receipt.status !== 'success') throw new Error(`transfer reverted (${hash})`);
  return { from: fromId, to: toId, token: symbol, amount, hash, explorerUrl: txUrl(hash) };
}

async function runSettleTrade({ fromAgent, toAgent, giveToken, giveAmount, wantToken, wantAmount }) {
  const tokens = loadTokens();
  resolveToken(tokens, giveToken);
  resolveToken(tokens, wantToken);

  // Check both sides up front so cooperative trades are effectively all-or-nothing.
  const [giverBalances, wanterBalances] = await Promise.all([
    getBalances(fromAgent),
    getBalances(toAgent),
  ]);
  if ((giverBalances[giveToken] ?? 0) < giveAmount) {
    return fail(`${fromAgent} has ${giverBalances[giveToken] ?? 0} ${giveToken}, needs ${giveAmount}`);
  }
  if ((wanterBalances[wantToken] ?? 0) < wantAmount) {
    return fail(`${toAgent} has ${wanterBalances[wantToken] ?? 0} ${wantToken}, needs ${wantAmount}`);
  }

  const legs = [];
  legs.push({ dir: 'give', ...(await sendLeg({ tokens, fromId: fromAgent, toId: toAgent, symbol: giveToken, amount: giveAmount })) });

  try {
    legs.push({ dir: 'want', ...(await sendLeg({ tokens, fromId: toAgent, toId: fromAgent, symbol: wantToken, amount: wantAmount })) });
  } catch (error) {
    // Leg 1 already landed — report the partial state, don't claim success.
    return {
      ok: false,
      partial: true,
      error: `leg 1 settled but leg 2 failed: ${error.message}`,
      fromAgent,
      toAgent,
      legs,
      hashes: legs.map((leg) => leg.hash),
      explorerUrls: legs.map((leg) => leg.explorerUrl),
    };
  }

  return {
    ok: true,
    fromAgent,
    toAgent,
    legs,
    hashes: legs.map((leg) => leg.hash),
    explorerUrls: legs.map((leg) => leg.explorerUrl),
  };
}

/**
 * Settle a trade as two ERC-20 transfers, serialized behind the global queue.
 * @param {{fromAgent:string, toAgent:string, giveToken:string, giveAmount:number, wantToken:string, wantAmount:number}} trade
 */
export function settleTrade(trade) {
  return enqueue(() => runSettleTrade(trade));
}
