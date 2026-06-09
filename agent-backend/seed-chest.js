/**
 * Demo chest seeder — prefills the quest's relic chest (and a decoy) so the Quest Master
 * doesn't have to build anything live. Connects as a throwaway bot, runs /setblock + /item
 * via chat, then disconnects.
 *
 *   node seed-chest.js
 *
 * REQUIRES the seeder bot to be op'd on the server (chat commands need op). If you can't op
 * a bot, just paste the equivalent commands into the SERVER CONSOLE instead (see demo/README.md):
 *   /setblock 10 64 12 minecraft:chest
 *   /item replace block 10 64 12 container.0 with minecraft:golden_apple 1
 *
 * Keep RELIC + the QM's secret in profiles/galactus.json in sync with CHESTS below.
 */
import mineflayer from 'mineflayer';
import settings from './settings.js';

// The "true" relic chest (matches galactus.json's secret + announced clue) plus one decoy.
const CHESTS = [
    { x: 10, y: 64, z: 12, item: 'golden_apple' }, // the prize — QM commits "golden_apple"
    { x: 40, y: 64, z: 30, item: 'iron_ingot'   }, // a decoy, for richer demo (wrong claims)
];

const bot = mineflayer.createBot({
    host: settings.host,
    port: settings.port,
    username: 'seeder',
    auth: settings.auth === 'microsoft' ? 'microsoft' : 'offline',
    version: settings.minecraft_version === 'auto' ? false : settings.minecraft_version,
});

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

bot.once('spawn', async () => {
    console.log('[seed] connected; placing relic chests...');
    for (const c of CHESTS) {
        bot.chat(`/setblock ${c.x} ${c.y} ${c.z} minecraft:chest`);
        await sleep(400);
        bot.chat(`/item replace block ${c.x} ${c.y} ${c.z} container.0 with minecraft:${c.item} 1`);
        await sleep(400);
        console.log(`[seed] chest @ (${c.x}, ${c.y}, ${c.z}) -> 1 ${c.item}`);
    }
    await sleep(800);
    console.log('[seed] done. If the chests are empty, the seeder is not op — run /op seeder or use the server console.');
    bot.quit();
    process.exit(0);
});

bot.on('kicked', (reason) => { console.error('[seed] kicked:', reason); process.exit(1); });
bot.on('error', (err) => { console.error('[seed] error:', err.message); process.exit(1); });
