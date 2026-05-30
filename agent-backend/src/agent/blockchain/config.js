import { readFileSync } from 'fs';
import { createPublicClient, http } from 'viem';
import { monadTestnet } from 'viem/chains';
import { hasKey, getKey } from '../../utils/keys.js';

// We always target Monad testnet (chainId 10143). viem ships the chain config,
// so we don't define our own. RPC can be overridden via MONAD_RPC_URL in keys.json
// or the environment; otherwise we fall back to the public testnet endpoint.
export const MONAD_CHAIN = monadTestnet;
const DEFAULT_RPC = 'https://testnet-rpc.monad.xyz';

export function getRpcUrl() {
    return hasKey('MONAD_RPC_URL') ? getKey('MONAD_RPC_URL') : DEFAULT_RPC;
}

// Shared read-only client. Building it is lazy (no connection until a call is made),
// so importing this module is cheap even when blockchain features are disabled.
export const publicClient = createPublicClient({
    chain: MONAD_CHAIN,
    transport: http(getRpcUrl()),
});

// Token registry, cwd-relative like keys.json. Shape:
//   { "SILK": { "address": "0x..", "decimals": 18 } }
// decimals is optional; if absent we read it on-chain.
const TOKENS_PATH = './tokens.json';

function loadTokens() {
    try {
        return JSON.parse(readFileSync(TOKENS_PATH, 'utf8'));
    } catch {
        return {};
    }
}

/**
 * Look up a token by symbol (case-insensitive) in tokens.json.
 * @returns {{symbol: string, address: string, decimals?: number}}
 * @throws if the symbol is not registered.
 */
export function getToken(symbol) {
    const tokens = loadTokens();
    const key = Object.keys(tokens).find((k) => k.toLowerCase() === String(symbol).toLowerCase());
    if (!key)
        throw new Error(`Token "${symbol}" is not registered in tokens.json.`);
    return { symbol: key, ...tokens[key] };
}

/**
 * List every tradeable token in the registry. Skips metadata keys (e.g. "_comment")
 * and any entry without an address.
 * @returns {Array<{symbol: string, address: string, decimals?: number}>}
 */
export function listTokens() {
    const tokens = loadTokens();
    return Object.entries(tokens)
        .filter(([key, val]) => !key.startsWith('_') && val && typeof val === 'object' && val.address)
        .map(([symbol, val]) => ({ symbol, ...val }));
}
