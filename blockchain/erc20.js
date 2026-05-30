import { parseUnits, formatUnits, decodeEventLog } from 'viem';
import { publicClient, getToken, listTokens } from './config.js';
import { getAccount } from './wallets.js';

// Minimal ERC20 ABI: just what we need to read balances/decimals, transfer, and
// decode Transfer logs for payment verification.
const ERC20_ABI = [
    { type: 'function', name: 'balanceOf', stateMutability: 'view', inputs: [{ name: 'account', type: 'address' }], outputs: [{ type: 'uint256' }] },
    { type: 'function', name: 'decimals', stateMutability: 'view', inputs: [], outputs: [{ type: 'uint8' }] },
    { type: 'function', name: 'transfer', stateMutability: 'nonpayable', inputs: [{ name: 'to', type: 'address' }, { name: 'amount', type: 'uint256' }], outputs: [{ type: 'bool' }] },
    { type: 'event', name: 'Transfer', inputs: [{ indexed: true, name: 'from', type: 'address' }, { indexed: true, name: 'to', type: 'address' }, { indexed: false, name: 'value', type: 'uint256' }] },
];

async function resolveDecimals(token) {
    if (token.decimals !== undefined && token.decimals !== null)
        return token.decimals;
    return await publicClient.readContract({ address: token.address, abi: ERC20_ABI, functionName: 'decimals' });
}

/** Decimals for a registered token symbol. */
export async function decimalsOf(symbol) {
    return await resolveDecimals(getToken(symbol));
}

/** On-chain balance of `symbol` held by `address`. Returns raw + human-formatted. */
export async function balanceOf(symbol, address) {
    const token = getToken(symbol);
    const [raw, decimals] = await Promise.all([
        publicClient.readContract({ address: token.address, abi: ERC20_ABI, functionName: 'balanceOf', args: [address] }),
        resolveDecimals(token),
    ]);
    return { raw, formatted: formatUnits(raw, decimals), decimals, symbol: token.symbol };
}

/**
 * Balances of every registered token for `address`. Each entry resolves
 * independently; a token that fails to read is returned with an `error` field
 * instead of aborting the whole list.
 * @returns {Promise<Array<{symbol: string, formatted?: string, error?: string}>>}
 */
export async function allBalances(address) {
    return await Promise.all(listTokens().map(async (token) => {
        try {
            const bal = await balanceOf(token.symbol, address);
            return { symbol: bal.symbol, formatted: bal.formatted };
        } catch (err) {
            return { symbol: token.symbol, error: err.shortMessage || err.message };
        }
    }));
}

/**
 * Send `amount` (human units) of `symbol` from `agentName`'s wallet to `toAddress`.
 * Returns the transaction hash and sender address. Does not wait for confirmation.
 */
export async function transfer(agentName, symbol, toAddress, amount) {
    const token = getToken(symbol);
    const decimals = await resolveDecimals(token);
    const value = parseUnits(String(amount), decimals);
    const { walletClient, account } = getAccount(agentName);
    const hash = await walletClient.writeContract({
        address: token.address,
        abi: ERC20_ABI,
        functionName: 'transfer',
        args: [toAddress, value],
    });
    return { hash, from: account.address };
}

/**
 * Verify that transaction `txHash` contains an ERC20 Transfer of the expected
 * token, of at least `minAmount` (human units), matching `from`/`to` when given.
 * @returns {Promise<{ok: boolean, reason?: string, value?: string, symbol?: string}>}
 */
export async function verifyTransfer(txHash, { symbol, from, to, minAmount }) {
    const token = getToken(symbol);
    const decimals = await resolveDecimals(token);
    const min = parseUnits(String(minAmount), decimals);

    let receipt;
    try {
        receipt = await publicClient.waitForTransactionReceipt({ hash: txHash, timeout: 60_000 });
    } catch (e) {
        return { ok: false, reason: `Could not fetch a receipt for ${txHash}: ${e.shortMessage || e.message}` };
    }
    if (receipt.status !== 'success')
        return { ok: false, reason: `Transaction ${txHash} failed on-chain.` };

    for (const lg of receipt.logs) {
        if (lg.address.toLowerCase() !== token.address.toLowerCase())
            continue;
        let decoded;
        try {
            decoded = decodeEventLog({ abi: ERC20_ABI, data: lg.data, topics: lg.topics });
        } catch {
            continue;
        }
        if (decoded.eventName !== 'Transfer')
            continue;
        const fromOk = !from || decoded.args.from.toLowerCase() === from.toLowerCase();
        const toOk = !to || decoded.args.to.toLowerCase() === to.toLowerCase();
        if (fromOk && toOk && decoded.args.value >= min)
            return { ok: true, value: formatUnits(decoded.args.value, decimals), symbol: token.symbol };
    }
    return { ok: false, reason: `No matching ${token.symbol} transfer of at least ${minAmount} to ${to} found in ${txHash}.` };
}
