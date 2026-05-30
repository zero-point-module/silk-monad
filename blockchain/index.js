/**
 * Chain entrypoint — selects mock or real from CHAIN_IMPL and re-exports the
 * seam (getBalances + settleTrade). The agent backend imports this file, so
 * switching implementations is a one-line .env change. Node built-ins only, so
 * the mock path stays dependency-free.
 *
 *   CHAIN_IMPL=mock (default) → in-memory   |   CHAIN_IMPL=real → Monad testnet
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

// Read from env, falling back to a light .env scan (dotenv may not be loaded yet).
function resolveImpl() {
  if (process.env.CHAIN_IMPL) return process.env.CHAIN_IMPL.trim().toLowerCase();
  try {
    const env = fs.readFileSync(path.join(ROOT, '.env'), 'utf8');
    const match = env.match(/^\s*CHAIN_IMPL\s*=\s*(.+?)\s*$/m);
    if (match) return match[1].trim().toLowerCase();
  } catch {
    // no .env yet — default to mock
  }
  return 'mock';
}

export const IMPL = resolveImpl() === 'real' ? 'real' : 'mock';

const impl = IMPL === 'real' ? await import('./lib/chain.js') : await import('./lib/chain.mock.js');

export const getBalances = impl.getBalances;
export const settleTrade = impl.settleTrade;
