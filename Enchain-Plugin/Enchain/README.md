# Enchain

Any item. Any enchant. No limits. A Paper 1.21.11 plugin by TheGildedKing, inspired by Enchain SMP.

## What it does
- Put any enchantment on any item at an anvil (Sharpness on a pickaxe, Efficiency on a sword, anything).
- Conflicting enchants are allowed (Sharpness + Smite, Mending + Infinity, etc).
- Enchanted items work as the sacrifice, not just books: their enchants move onto the left-slot item.
- Same-level enchants combine past vanilla max (configurable cap, up to 255).
- Raised "Too Expensive" limit.

Enchants still follow Minecraft's own slot rules once applied: weapon/tool enchants work on any held item, armor enchants only work on worn armor.

## Commands (permission: `enchain.admin`, default op)
- `/enchain add <enchant> [level]` - enchant the held item
- `/enchain remove <enchant>` - remove from the held item
- `/enchain book <enchant> [level]` - give yourself an enchanted book
- `/enchain list` - list all enchantments
- `/enchain reload` - reload config.yml

Anvil use needs `enchain.anvil` (default: everyone).

## Build on GitHub
1. Create a repo and push this folder (the `.github/workflows/build.yml` must be included).
2. Open the Actions tab, wait for "Build Enchain" to finish.
3. Download the `Enchain-plugin` artifact, unzip, drop `Enchain-1.0.0.jar` in your server's `plugins` folder.

Requires Paper 1.21.11 and Java 21.
