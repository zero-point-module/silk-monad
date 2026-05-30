import { readFileSync, writeFileSync } from 'fs';
import { createWalletClient, http } from 'viem';
import { generatePrivateKey, privateKeyToAccount } from 'viem/accounts';
import { MONAD_CHAIN, getRpcUrl } from './config.js';
import { getPersona } from '../agent_registry.js';

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

// Map of derived wallet address (lowercased) -> private key, across every
// *_PRIVATE_KEY in keys.json and the environment. Lets us resolve the signing
// key for a wallet by ADDRESS rather than by agent name — which is how persona
// agents (e.g. marco) reuse a wallet whose key is stored under a different name
// (e.g. GPT_PRIVATE_KEY).
function keyByAddress() {
    const map = {};
    const add = (v) => {
        try { map[privateKeyToAccount(v).address.toLowerCase()] = v; } catch { /* skip invalid key */ }
    };
    const keys = readJson(KEYS_PATH);
    for (const [k, v] of Object.entries(keys))
        if (k.endsWith('_PRIVATE_KEY') && v) add(v);
    for (const [k, v] of Object.entries(process.env))
        if (k.endsWith('_PRIVATE_KEY') && v) add(v);
    return map;
}

/**
 * Ensure this agent has a wallet. A persona agent (defined in the root
 * agents.json) reuses its pre-assigned wallet — we don't generate anything,
 * just verify a matching private key exists. Any other agent gets a fresh
 * wallet generated and persisted (key in keys.json, address in agents.json).
 * Returns the address.
 */
export async function ensureWallet(agentName) {
    const persona = getPersona(agentName);
    if (persona) {
        const address = persona.address;
        const hasKey = !!(address && keyByAddress()[address.toLowerCase()]);
        console.log(`[blockchain] ${agentName} (${persona.name}) uses persona wallet ${address}.`);
        if (!hasKey)
            console.warn(`[blockchain] WARNING: no private key in keys.json matches ${agentName}'s wallet ${address}; it cannot send payments.`);
        return address;
    }

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
 * Resolves the key by the agent's wallet ADDRESS, so persona agents sign with
 * the key stored under their original name (e.g. GPT_PRIVATE_KEY).
 * @throws if no address is known or no matching private key exists.
 */
export function getAccount(agentName) {
    const address = getAddress(agentName);
    if (!address)
        throw new Error(`No wallet address known for ${agentName}.`);
    const pk = keyByAddress()[address.toLowerCase()];
    if (!pk)
        throw new Error(`No private key found for ${agentName}'s wallet ${address}.`);
    const account = privateKeyToAccount(pk);
    const walletClient = createWalletClient({
        account,
        chain: MONAD_CHAIN,
        transport: http(getRpcUrl()),
    });
    return { account, walletClient };
}

/**
 * Resolve an agent's/player's public wallet address. Persona agents resolve from
 * the root agents.json; everyone else from the auto-generated agents.json.
 * @returns {string|null}
 */
export function getAddress(agentName) {
    const personaAddress = getPersona(agentName)?.address;
    if (personaAddress)
        return personaAddress;
    const agents = readJson(AGENTS_PATH);
    return agents[agentName]?.address || null;
}
