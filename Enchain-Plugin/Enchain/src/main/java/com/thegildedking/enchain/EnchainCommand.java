package com.thegildedking.enchain;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class EnchainCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS =
            List.of("start", "loop", "stop", "status", "roll", "add", "remove", "book", "list", "reload");

    private final EnchainPlugin plugin;

    public EnchainCommand(EnchainPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("enchain.admin")) {
            Msg.send(sender, Msg.err("You don't have permission."));
            return true;
        }
        if (args.length == 0) {
            Msg.send(sender, Msg.gray("/enchain start | loop | stop | status | roll | add <enchant> [level] | remove <enchant> | book <enchant> [level] | list | reload"));
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "start" -> {
                if (plugin.smp().isRunning()) {
                    Msg.send(sender, Msg.err("The SMP is already running. Use /enchain stop first."));
                } else {
                    plugin.smp().start(false);
                }
            }
            case "loop" -> {
                if (plugin.smp().isRunning()) {
                    Msg.send(sender, Msg.err("The SMP is already running. Use /enchain stop first."));
                } else {
                    plugin.smp().start(true);
                    Msg.send(sender, Msg.gray("Loop started. Nobody is told anything until the last 5 seconds before each roll."));
                }
            }
            case "stop" -> {
                if (!plugin.smp().isRunning()) {
                    Msg.send(sender, Msg.err("The SMP isn't running."));
                } else {
                    boolean wasLoop = plugin.smp().isLoop();
                    plugin.smp().stop();
                    if (wasLoop) {
                        Msg.send(sender, Msg.gray("Loop stopped."));
                    } else {
                        Msg.broadcast(Msg.gray("The SMP enchant rolls have been stopped."));
                    }
                }
            }
            case "status" -> {
                if (plugin.smp().isRunning()) {
                    Msg.send(sender, Msg.gray((plugin.smp().isLoop() ? "Looping. Next roll in " : "Running. Next roll in "))
                            .append(Msg.gold(SmpManager.format(plugin.smp().secondsLeft()))));
                } else {
                    Msg.send(sender, Msg.gray("Not running. Use /enchain start or /enchain loop."));
                }
            }
            case "roll" -> plugin.smp().roll();
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
            case "list" -> Msg.send(sender, Msg.gray("Enchantments: ")
                    .append(Component.text(String.join(", ", EnchantUtil.names()), NamedTextColor.GOLD)));
            case "reload" -> {
                plugin.reloadConfig();
                Msg.send(sender, Msg.gray("Config reloaded."));
            }
            default -> Msg.send(sender, Msg.err("Unknown subcommand."));
        }
        return true;
    }

    /** Returns the level (1 to vanilla max), or -1 after telling the sender what was wrong. */
    private int checkedLevel(Player p, Enchantment ench, String[] args) {
        int level = 1;
        if (args.length > 2) {
            try {
                level = Integer.parseInt(args[2]);
            } catch (NumberFormatException ex) {
                level = -1;
            }
        }
        if (level < 1) {
            Msg.send(p, Msg.err("Level must be a positive number."));
            return -1;
        }
        if (level > ench.getMaxLevel()) {
            Msg.send(p, Msg.err("That enchantment maxes out at level " + ench.getMaxLevel() + "."));
            return -1;
        }
        return level;
    }

    private void add(Player p, String[] args) {
        if (args.length < 2) {
            Msg.send(p, Msg.err("Usage: /enchain add <enchant> [level]"));
            return;
        }
        Enchantment ench = EnchantUtil.find(args[1]);
        if (ench == null) {
            Msg.send(p, Msg.err("Unknown enchantment."));
            return;
        }
        int level = checkedLevel(p, ench, args);
        if (level < 1) {
            return;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        ItemMeta meta = hand.getItemMeta();
        if (hand.getType().isAir() || meta == null) {
            Msg.send(p, Msg.err("Hold an item first."));
            return;
        }
        EnchantUtil.apply(meta, ench, level);
        hand.setItemMeta(meta);
        p.getInventory().setItemInMainHand(hand);
        Msg.send(p, Msg.gray("Applied ").append(ench.displayName(level)).append(Msg.gray(" to your held item.")));
    }

    private void remove(Player p, String[] args) {
        if (args.length < 2) {
            Msg.send(p, Msg.err("Usage: /enchain remove <enchant>"));
            return;
        }
        Enchantment ench = EnchantUtil.find(args[1]);
        if (ench == null) {
            Msg.send(p, Msg.err("Unknown enchantment."));
            return;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        ItemMeta meta = hand.getItemMeta();
        if (hand.getType().isAir() || meta == null) {
            Msg.send(p, Msg.err("Hold an item first."));
            return;
        }
        EnchantUtil.remove(meta, ench);
        hand.setItemMeta(meta);
        p.getInventory().setItemInMainHand(hand);
        Msg.send(p, Msg.gray("Removed ").append(ench.displayName(1)).append(Msg.gray(" from your held item.")));
    }

    private void book(Player p, String[] args) {
        if (args.length < 2) {
            Msg.send(p, Msg.err("Usage: /enchain book <enchant> [level]"));
            return;
        }
        Enchantment ench = EnchantUtil.find(args[1]);
        if (ench == null) {
            Msg.send(p, Msg.err("Unknown enchantment."));
            return;
        }
        int level = checkedLevel(p, ench, args);
        if (level < 1) {
            return;
        }
        Map<Integer, ItemStack> leftover = p.getInventory().addItem(EnchantUtil.book(ench, level));
        for (ItemStack extra : leftover.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), extra);
        }
        Msg.send(p, Msg.gray("Gave you a book with ").append(ench.displayName(level)).append(Msg.gray(".")));
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
                filter(List.of("1", "2", "3", "4", "5"), args[2], out);
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

    private Player player(CommandSender sender) {
        if (sender instanceof Player p) {
            return p;
        }
        Msg.send(sender, Msg.err("Players only."));
        return null;
    }
}
