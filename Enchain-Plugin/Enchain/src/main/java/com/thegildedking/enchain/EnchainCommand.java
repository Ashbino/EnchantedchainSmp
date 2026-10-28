package com.thegildedking.enchain;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class EnchainCommand implements CommandExecutor, TabCompleter {

    private static final Component PREFIX = MiniMessage.miniMessage().deserialize(
            "<gradient:#f5c542:#b8860b><bold>Enchain</bold></gradient> <dark_gray>\u00bb</dark_gray> ");
    private static final List<String> SUBS = List.of("add", "remove", "book", "list", "reload");

    private final EnchainPlugin plugin;

    public EnchainCommand(EnchainPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("enchain.admin")) {
            send(sender, err("You don't have permission."));
            return true;
        }
        if (args.length == 0) {
            send(sender, gray("/enchain add <enchant> [level]  |  remove <enchant>  |  book <enchant> [level]  |  list  |  reload"));
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "add" -> {
                Player p = player(sender);
                if (p != null) add(p, args);
            }
            case "remove" -> {
                Player p = player(sender);
                if (p != null) remove(p, args);
            }
            case "book" -> {
                Player p = player(sender);
                if (p != null) book(p, args);
            }
            case "list" -> send(sender, gray("Enchantments: ")
                    .append(Component.text(String.join(", ", EnchantUtil.names()), NamedTextColor.GOLD)));
            case "reload" -> {
                plugin.reloadConfig();
                send(sender, gray("Config reloaded."));
            }
            default -> send(sender, err("Unknown subcommand."));
        }
        return true;
    }

    private void add(Player p, String[] args) {
        if (args.length < 2) {
            send(p, err("Usage: /enchain add <enchant> [level]"));
            return;
        }
        Enchantment ench = EnchantUtil.find(args[1]);
        if (ench == null) {
            send(p, err("Unknown enchantment."));
            return;
        }
        int level = parseLevel(args, 2);
        if (level < 1) {
            send(p, err("Level must be a number from 1 to 255."));
            return;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            send(p, err("Hold an item first."));
            return;
        }
        ItemMeta meta = hand.getItemMeta();
        if (meta == null) {
            send(p, err("That item can't be enchanted."));
            return;
        }
        EnchantUtil.apply(meta, ench, level);
        hand.setItemMeta(meta);
        p.getInventory().setItemInMainHand(hand);
        send(p, gray("Applied ").append(ench.displayName(level)).append(gray(" to your held item.")));
    }

    private void remove(Player p, String[] args) {
        if (args.length < 2) {
            send(p, err("Usage: /enchain remove <enchant>"));
            return;
        }
        Enchantment ench = EnchantUtil.find(args[1]);
        if (ench == null) {
            send(p, err("Unknown enchantment."));
            return;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        ItemMeta meta = hand.getItemMeta();
        if (hand.getType().isAir() || meta == null) {
            send(p, err("Hold an item first."));
            return;
        }
        EnchantUtil.remove(meta, ench);
        hand.setItemMeta(meta);
        p.getInventory().setItemInMainHand(hand);
        send(p, gray("Removed ").append(ench.displayName(1)).append(gray(" from your held item.")));
    }

    private void book(Player p, String[] args) {
        if (args.length < 2) {
            send(p, err("Usage: /enchain book <enchant> [level]"));
            return;
        }
        Enchantment ench = EnchantUtil.find(args[1]);
        if (ench == null) {
            send(p, err("Unknown enchantment."));
            return;
        }
        int level = parseLevel(args, 2);
        if (level < 1) {
            send(p, err("Level must be a number from 1 to 255."));
            return;
        }
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
        meta.addStoredEnchant(ench, level, true);
        book.setItemMeta(meta);

        Map<Integer, ItemStack> leftover = p.getInventory().addItem(book);
        for (ItemStack extra : leftover.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), extra);
        }
        send(p, gray("Gave you a book with ").append(ench.displayName(level)).append(gray(".")));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("enchain.admin")) {
            return out;
        }
        if (args.length == 1) {
            filter(SUBS, args[0], out);
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("add") || sub.equals("remove") || sub.equals("book")) {
                filter(EnchantUtil.names(), args[1], out);
            }
        } else if (args.length == 3) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("add") || sub.equals("book")) {
                filter(List.of("1", "2", "3", "4", "5", "10"), args[2], out);
            }
        }
        return out;
    }

    private static void filter(List<String> options, String typed, List<String> out) {
        String t = typed.toLowerCase(Locale.ROOT);
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(t)) {
                out.add(option);
            }
        }
    }

    /** Returns 1 when omitted, -1 when invalid, otherwise 1-255. */
    private static int parseLevel(String[] args, int index) {
        if (args.length <= index) {
            return 1;
        }
        try {
            int level = Integer.parseInt(args[index]);
            return (level < 1 || level > 255) ? -1 : level;
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private Player player(CommandSender sender) {
        if (sender instanceof Player p) {
            return p;
        }
        send(sender, err("Players only."));
        return null;
    }

    private static void send(CommandSender to, Component body) {
        to.sendMessage(PREFIX.append(body));
    }

    private static Component gray(String s) {
        return Component.text(s, NamedTextColor.GRAY);
    }

    private static Component err(String s) {
        return Component.text(s, NamedTextColor.RED);
    }
}
