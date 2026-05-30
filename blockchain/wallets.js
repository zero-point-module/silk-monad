import { readFileSync, writeFileSync } from 'fs';
import { createWalletClient, http } from 'viem';
import { generatePrivateKey, privateKeyToAccount } from 'viem/accounts';
import { MONAD_CHAIN, getRpcUrl } from './config.js';

// Each agent gets one EVM wallet. Private keys live in keys.json under
// <NAME>_PRIVATE_KEY; public addresses live in agents.json so peers can resolve
// where to send payments. Both files are cwd-relative, matching keys.json.
const KEYS_PATH = './keys.json';
const AGENTS_PATH = './agents.json';

function keyName(agentName) {
    return `${agentName.toUpperCase()}_PRIVATE_KEY`;
}

function readJson(path) {
    try {
        return JSON.parse(readFileSync(path, 'utf8'));
    } catch {
        return {};
    }
}

function writeJson(path, obj) {
    writeFileSync(path, JSON.stringify(obj, null, 4) + '\n');
}

// Read the private key directly from disk/env rather than through utils/keys.js,
// which caches keys.json at import time and so wouldn't see a key we just wrote.
function getPrivateKey(agentName) {
    const kn = keyName(agentName);
    const keys = readJson(KEYS_PATH);
    return keys[kn] || process.env[kn] || null;
}

/**
 * Ensure this agent has a wallet. Generates and persists one on first run.
 * Records the public address in agents.json. Returns the address.
 */
export async function ensureWallet(agentName) {
    let pk = getPrivateKey(agentName);
    if (!pk) {
        pk = generatePrivateKey();
        const keys = readJson(KEYS_PATH);
        keys[keyName(agentName)] = pk;
        writeJson(KEYS_PATH, keys);
        console.log(`[blockchain] Generated a new wallet for ${agentName}; saved ${keyName(agentName)} to keys.json.`);
    }
    const account = privateKeyToAccount(pk);

    const agents = readJson(AGENTS_PATH);
    if (agents[agentName]?.address !== account.address) {
        agents[agentName] = { ...(agents[agentName] || {}), address: account.address };
        writeJson(AGENTS_PATH, agents);
    }
    console.log(`[blockchain] ${agentName} wallet address: ${account.address}`);
    console.log(`[blockchain] Fund it with native MON (gas) and tokens before trading.`);
    return account.address;
}

/**
 * Get this agent's signing account + wallet client for sending transactions.
 * @throws if the agent has no wallet yet.
 */
export function getAccount(agentName) {
    const pk = getPrivateKey(agentName);
    if (!pk)
        throw new Error(`No wallet for ${agentName} (missing ${keyName(agentName)} in keys.json). Run with blockchain enabled to generate one.`);
    const account = privateKeyToAccount(pk);
    const walletClient = createWalletClient({
        account,
        chain: MONAD_CHAIN,
        transport: http(getRpcUrl()),
    });
    return { account, walletClient };
}

/**
 * Resolve another agent's/player's public wallet address from agents.json.
 * @returns {string|null}
 */
export function getAddress(agentName) {
    const agents = readJson(AGENTS_PATH);
    return agents[agentName]?.address || null;
}
