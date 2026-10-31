# Enchain

Any item. Any enchant. No limits. A Paper 1.21.11 plugin by TheGildedKing, inspired by Enchain SMP.

## What it does
- Put any enchantment on any item at an anvil (Sharpness on a pickaxe, Efficiency on a sword, anything).
- Conflicting enchants are allowed (Sharpness + Smite, Mending + Infinity, etc).
- Enchanted items work as the sacrifice, not just books: their enchants move onto the left-slot item.
- Same-level enchants combine up to the vanilla max, never past it.
- Raised "Too Expensive" limit.

Enchants still follow Minecraft's own slot rules once applied: weapon/tool enchants work on any held item, armor enchants only work on worn armor.

## SMP rolls
Every roll picks a random enchantment with a random level (never above the vanilla max) and puts it on whatever each player is holding: a fish, a block, an enchanted golden apple, anything. An empty hand gets an enchanted book instead.

- `/enchain start`: visible countdown (boss bar, warnings at 60/30/10 seconds, then every second from 5). Rolls once after 5 minutes.
- `/enchain loop`: silent. First roll after 5 minutes, then it keeps rolling every 5 minutes. Nobody is told anything until the last 5 seconds ("5 seconds", "4 seconds", ... "1 second").

Everything is in `config.yml` under `smp`.

## Commands (permission: `enchain.admin`, default op)
- `/enchain start` / `loop` / `stop` / `status` - run the SMP roll countdown
- `/enchain roll` - roll right now
- `/enchain add <enchant> [level]` - enchant the held item
- `/enchain remove <enchant>` - remove from the held item
- `/enchain book <enchant> [level]` - give yourself an enchanted book
- `/enchain list` - list all enchantments
- `/enchain reload` - reload config.yml

Anvil use needs `enchain.anvil` (default: everyone).

## Build on GitHub
1. Create a repo and push this folder (the `.github/workflows/build.yml` must be included).
2. Open the Actions tab, wait for "Build Enchain" to finish.
3. Download the `Enchain-plugin` artifact, unzip, drop `Enchain-1.3.0.jar` in your server's `plugins` folder.

Requires Paper 1.21.11 and Java 21.
