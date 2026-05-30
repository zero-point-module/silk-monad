# SilkMonad Plugin

Custom cosmetics framework for the silk-monad Paper 1.21.6 server.

## Architecture

- `Cosmetic` — interface every cosmetic implements (`id`, `displayName`, `type`, `apply`, `remove`)
- `CosmeticType` — `ITEM`, `HAT`, `TRAIL`, `CAPE`, `EMOTE` (extend as needed)
- `CosmeticRegistry` — central lookup. Plugin loads all `*.yml` files under `plugins/SilkMonad/cosmetics/` on startup
- `ItemCosmetic` — first concrete cosmetic type. Wraps an `ItemStack` and tags it via `PersistentDataContainer` with `silk:cosmetic_id`, so bots/code can reliably identify cosmetic items via the Bukkit API.

Adding a new cosmetic type (e.g. hat): create `cosmetic/hat/HatCosmetic.java` + `HatCosmeticLoader.java`, register it in `SilkMonadPlugin#reloadCosmetics`.

## Authoring cosmetics

Drop YAML files under `silk-monad/minecraft/server/data/plugins/SilkMonad/cosmetics/`. Example (`example_sword.yml` ships as default):

```yaml
id: silk_blade
type: item
display-name: "<gradient:#c084fc:#f0abfc>Silk Blade</gradient>"
item:
  material: NETHERITE_SWORD
  custom-model-data: 1001
  unbreakable: true
  lore:
    - "<gray>A blade woven from monad silk.</gray>"
  enchantments:
    SHARPNESS: 5
```

Display names and lore use [MiniMessage](https://docs.advntr.dev/minimessage/format.html).

After editing, run `/silk reload` in-game or from console.

## Commands

| Command | What it does |
|---|---|
| `/silk give <id> [player]` | Add the cosmetic to the player (items go to inventory). |
| `/silk apply <id> [player]` | Alias for give — semantics differ once non-item cosmetics exist. |
| `/silk remove <id> [player]` | Remove the cosmetic from the player. |
| `/silk list [type]` | List all registered cosmetics, optionally filtered by type. |
| `/silk reload` | Reload YAML configs without restarting. |

## Build

No local Java/Gradle required — the build runs in Docker:

```bash
./build.sh             # builds build/libs/silkmonad-plugin-<version>.jar
./build.sh --install   # also copies into ../server/data/plugins/SilkMonad.jar
```

Then restart the server:

```bash
(cd ../server && docker compose restart minecraft)
```

## Resource pack

Custom textures live in `../resource-pack/`. The plugin tags items with `custom-model-data` (e.g. `1001`); the resource pack maps those values to custom models. Pack hosting is not yet wired up — for now players need the pack installed locally, or we'll add a server-side `resource-pack` URL in a follow-up.
