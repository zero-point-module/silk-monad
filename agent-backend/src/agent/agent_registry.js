import { readFileSync } from 'fs';

// The repo-root agents.json is the source of truth for persona-driven trading
// agents. Each entry: { id, name, persona, post:{x,y,z}, address, start:{TOKEN:amount} }.
// Agents whose name matches an `id` here adopt that persona and reuse that wallet
// (resolved by address), instead of getting an auto-generated wallet/identity.
const ROOT_AGENTS_PATH = '../agents.json';

function loadPersonas() {
    try {
        const data = JSON.parse(readFileSync(ROOT_AGENTS_PATH, 'utf8'));
        return Array.isArray(data?.agents) ? data.agents : [];
    } catch {
        return [];
    }
}

/** Every persona definition from the root agents.json. */
export function listPersonas() {
    return loadPersonas();
}

/**
 * Persona definition for an agent name (matched against `id`, case-insensitive).
 * @returns the persona object, or null if the name isn't a defined persona.
 */
export function getPersona(name) {
    const lower = String(name).toLowerCase();
    return loadPersonas().find((a) => String(a.id).toLowerCase() === lower) || null;
}

/** Pre-assigned wallet address for a persona, or null. */
export function getPersonaAddress(name) {
    return getPersona(name)?.address || null;
}
