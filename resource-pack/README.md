# silk-monad resource pack

Resource pack that pairs with the SilkMonad plugin. The plugin tags items with `custom-model-data` values; this pack maps those values to custom textures/models.

## Structure

```
resource-pack/
├── pack.mcmeta
└── assets/
    └── silk/
        ├── models/item/      # custom model JSONs
        └── textures/item/    # 16x16 / 32x32 PNG textures
```

`pack_format: 46` corresponds to Minecraft 1.21.6 — bump it when the server updates.

## Adding a new textured item

1. Drop `your_texture.png` into `assets/silk/textures/item/`.
2. Create `assets/silk/models/item/your_item.json`:
   ```json
   {
     "parent": "minecraft:item/handheld",
     "textures": { "layer0": "silk:item/your_texture" }
   }
   ```
3. Override the base material in `assets/minecraft/models/item/<material>.json` using `overrides` keyed on `custom_model_data` — see Minecraft wiki "Custom model data".
4. Reference the same `custom-model-data` value in the matching cosmetic YAML in `silk-monad/plugin/src/main/resources/cosmetics/`.

## Hosting (TODO)

The server needs to serve this pack to clients via the `resource-pack` setting in `server.properties` (URL + SHA-1). Options being considered:
- Zip + GitHub release asset
- Self-hosted via a tiny static server alongside Paper
- A plugin like ResourcePackHost
