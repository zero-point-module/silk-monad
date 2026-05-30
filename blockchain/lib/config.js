/**
 * Shared run-time config: loads the root .env + agents.json + tokens.json and
 * exposes the Monad chain, a public client, and per-agent accounts.
 * Not imported by chain.mock.js — the mock stays free of viem and .env.
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { config as loadDotenv } from 'dotenv';
import { createPublicClient, http, defineChain } from 'viem';
import * as chains from 'viem/chains';
import { privateKeyToAccount } from 'viem/accounts';

/** Repo root — parent of blockchain/, where the shared config lives. */
export const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');

loadDotenv({ path: path.join(ROOT, '.env') });

export const RPC_URL = process.env.RPC_URL ?? 'https://testnet-rpc.monad.xyz';
export const CHAIN_ID = Number(process.env.CHAIN_ID ?? 10143);
export const EXPLORER_URL = (process.env.EXPLORER_URL ?? 'https://monad-testnet.socialscan.io').replace(/\/+$/, '');

// Prefer viem's built-in chain; define one only if this viem predates it.
export const monadTestnet = chains.monadTestnet ?? defineChain({
  id: CHAIN_ID,
  name: 'Monad Testnet',
  nativeCurrency: { name: 'Monad', symbol: 'MON', decimals: 18 },
  rpcUrls: { default: { http: [RPC_URL] } },
  blockExplorers: { default: { name: 'Socialscan', url: EXPLORER_URL } },
  testnet: true,
});

export const publicClient = createPublicClient({ chain: monadTestnet, transport: http(RPC_URL) });
export const txUrl = (hash) => `${EXPLORER_URL}/tx/${hash}`;

const AGENTS_PATH = path.join(ROOT, 'agents.json');
const TOKENS_PATH = path.join(ROOT, 'tokens.json');
const readJson = (file) => JSON.parse(fs.readFileSync(file, 'utf8'));
const writeJson = (file, obj) => fs.writeFileSync(file, `${JSON.stringify(obj, null, 2)}\n`);

export const loadAgents = () => readJson(AGENTS_PATH);
export const saveAgents = (doc) => writeJson(AGENTS_PATH, doc);
export const loadTokens = () => readJson(TOKENS_PATH);
export const saveTokens = (doc) => writeJson(TOKENS_PATH, doc);

export function getAgent(id) {
  const agent = loadAgents().agents.find((a) => a.id === id);
  if (!agent) throw new Error(`Unknown agent id "${id}" — not in agents.json`);
  return agent;
}

const normalizePk = (pk) => (pk.startsWith('0x') ? pk : `0x${pk}`);

/** Deployer account — holds the faucet MON, deploys + distributes tokens. */
export function deployerAccount() {
  const pk = process.env.DEPLOYER_PK;
  if (!pk) throw new Error('DEPLOYER_PK missing in .env — run `npm run setup` first');
  return privateKeyToAccount(normalizePk(pk));
}

/** Custodial account for an agent id (env: AGENT_<ID>_PK). */
export function agentAccount(id) {
  const envKey = `AGENT_${id.toUpperCase()}_PK`;
  const pk = process.env[envKey];
  if (!pk) throw new Error(`${envKey} missing in .env — run \`npm run setup\` first`);
  return privateKeyToAccount(normalizePk(pk));
}
