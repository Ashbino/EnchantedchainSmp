package com.thegildedking.enchain;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.view.AnvilView;

import java.util.Map;

/**
 * Lets the anvil put ANY enchant on ANY item: wrong item types, conflicting
 * enchants, and levels past vanilla max are all allowed.
 */
public final class AnvilListener implements Listener {

    private final EnchainPlugin plugin;

    public AnvilListener(EnchainPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepare(PrepareAnvilEvent event) {
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.getBoolean("anvil.enabled", true)) {
            return;
        }

        AnvilView view = event.getView();
        if (!view.getPlayer().hasPermission("enchain.anvil")) {
            return;
        }

        AnvilInventory inv = event.getInventory();
        ItemStack first = inv.getItem(0);
        ItemStack second = inv.getItem(1);
        if (isEmpty(first) || isEmpty(second)) {
            return;
        }
        if (first.getType() == Material.BOOK) {
            return;
        }

        boolean secondIsBook = second.getType() == Material.ENCHANTED_BOOK;
        if (!secondIsBook && !cfg.getBoolean("anvil.allow-item-transfer", true)) {
            return;
        }

        Map<Enchantment, Integer> incoming = EnchantUtil.read(second);
        if (incoming.isEmpty()) {
            return;
        }

        ItemStack vanilla = event.getResult();
        boolean hasVanilla = vanilla != null && !vanilla.getType().isAir();
        if (!hasVanilla && first.getAmount() != 1) {
            return;
        }

        ItemStack result = hasVanilla ? vanilla.clone() : first.clone();
        ItemMeta meta = result.getItemMeta();
        if (meta == null) {
            return;
        }

        boolean allowAbove = cfg.getBoolean("levels.allow-above-vanilla-max", true);
        int hardMax = Math.max(1, Math.min(255, cfg.getInt("levels.max-level", 10)));
        boolean combine = cfg.getBoolean("levels.combine-same-level", true);
        int perLevel = Math.max(0, cfg.getInt("anvil.cost-per-level", 2));

        int cost = 0;
        boolean changed = false;

        for (Map.Entry<Enchantment, Integer> entry : incoming.entrySet()) {
            Enchantment ench = entry.getKey();
            int add = entry.getValue();
            int cap = allowAbove ? Math.max(hardMax, ench.getMaxLevel()) : ench.getMaxLevel();
            int current = EnchantUtil.level(meta, ench);

            int target;
            if (current == 0) {
                target = add;
            } else if (current == add && combine) {
                target = add + 1;
            } else {
                target = Math.max(current, add);
            }
            target = Math.min(target, cap);

            if (target > current) {
                EnchantUtil.apply(meta, ench, target);
                cost += target * perLevel;
                changed = true;
            }
        }

        if (!changed) {
            return;
        }

        result.setItemMeta(meta);
        event.setResult(result);

        int base = hasVanilla ? view.getRepairCost() : 0;
        view.setMaximumRepairCost(Math.max(40, cfg.getInt("anvil.max-cost", 200)));
        view.setRepairCost(Math.max(1, base + cost));
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir();
    }
}
