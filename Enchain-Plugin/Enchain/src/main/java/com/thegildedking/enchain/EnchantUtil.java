package com.thegildedking.enchain;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class EnchantUtil {

    private EnchantUtil() {
    }

    static Enchantment find(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String s = input.toLowerCase(Locale.ROOT).trim();
        try {
            NamespacedKey key = s.contains(":") ? NamespacedKey.fromString(s) : NamespacedKey.minecraft(s);
            return key == null ? null : Registry.ENCHANTMENT.get(key);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    static List<String> names() {
        List<String> out = new ArrayList<>();
        for (Enchantment e : Registry.ENCHANTMENT) {
            out.add(e.getKey().getKey());
        }
        Collections.sort(out);
        return out;
    }

    /** Enchants on an item, or the stored enchants if it is an enchanted book. */
    static Map<Enchantment, Integer> read(ItemStack item) {
        Map<Enchantment, Integer> out = new LinkedHashMap<>();
        if (item == null) {
            return out;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta storage) {
            out.putAll(storage.getStoredEnchants());
        } else if (meta != null) {
            out.putAll(meta.getEnchants());
        }
        return out;
    }

    static int level(ItemMeta meta, Enchantment ench) {
        if (meta instanceof EnchantmentStorageMeta storage) {
            return storage.getStoredEnchantLevel(ench);
        }
        return meta.getEnchantLevel(ench);
    }

    /** Applies an enchant ignoring every vanilla restriction (item type, conflicts, max level). */
    static void apply(ItemMeta meta, Enchantment ench, int level) {
        if (meta instanceof EnchantmentStorageMeta storage) {
            storage.addStoredEnchant(ench, level, true);
        } else {
            meta.addEnchant(ench, level, true);
        }
    }

    static void remove(ItemMeta meta, Enchantment ench) {
        if (meta instanceof EnchantmentStorageMeta storage) {
            storage.removeStoredEnchant(ench);
        } else {
            meta.removeEnchant(ench);
        }
    }
}
