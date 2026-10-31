package com.thegildedking.enchain;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The enchant roll. Every roll picks a random enchantment with a random level
 * (never above the vanilla max) and puts it on whatever each player is holding.
 *
 * /enchain start: visible countdown (boss bar + warnings), rolls once.
 * /enchain loop : silent, keeps rolling forever, only counts down the last 5 seconds.
 */
public final class SmpManager {

    private record Roll(Enchantment enchantment, int level) {
    }

    private final EnchainPlugin plugin;
    private final BossBar bar = BossBar.bossBar(Component.text("Enchain"), 1f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
    private BukkitTask task;
    private boolean loop;
    private int secondsLeft;
    private int intervalSeconds;

    public SmpManager(EnchainPlugin plugin) {
        this.plugin = plugin;
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    public boolean isRunning() {
        return task != null;
    }

    public boolean isLoop() {
        return loop;
    }

    public int secondsLeft() {
        return secondsLeft;
    }

    private int minutesToSeconds(String path, double fallback) {
        return Math.max(10, (int) Math.round(cfg().getDouble(path, fallback) * 60));
    }

    public void start(boolean loopMode) {
        loop = loopMode;
        intervalSeconds = minutesToSeconds("smp.interval-minutes", 5.0);
        secondsLeft = intervalSeconds;

        if (!loop) {
            updateBar();
            Msg.broadcast(Msg.gold("The SMP has started!").append(
                    Msg.gray(" Enchant roll in " + format(secondsLeft) + ".")));
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            }
        }

        task = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        loop = false;
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.hideBossBar(bar);
        }
    }

    private void tick() {
        secondsLeft--;

        if (secondsLeft <= 0) {
            roll();
            if (loop) {
                intervalSeconds = minutesToSeconds("smp.loop-interval-minutes", 5.0);
                secondsLeft = intervalSeconds;
            } else {
                stop();
                Msg.broadcast(Msg.gray("The enchant roll is done. Use /enchain start or /enchain loop to run it again."));
            }
            return;
        }

        if (secondsLeft <= 5) {
            countdown(secondsLeft + (secondsLeft == 1 ? " second" : " seconds"), secondsLeft);
        } else if (!loop && (secondsLeft == 60 || secondsLeft == 30 || secondsLeft == 10)) {
            countdown(format(secondsLeft), secondsLeft);
        }

        if (!loop) {
            updateBar();
        }
    }

    private void countdown(String text, int seconds) {
        Msg.broadcast(Msg.gold(text));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, seconds <= 5 ? 1.6f : 1.0f);
        }
    }

    private void updateBar() {
        bar.name(Component.text("Next enchant roll in " + format(secondsLeft), NamedTextColor.GOLD));
        bar.progress(Math.max(0f, Math.min(1f, secondsLeft / (float) Math.max(1, intervalSeconds))));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showBossBar(bar);
        }
    }

    /** Rolls now: random enchantment, random level from 1 to its vanilla max, onto each player's held item. */
    public void roll() {
        List<Enchantment> pool = new ArrayList<>();
        boolean noCurses = cfg().getBoolean("smp.exclude-curses", true);
        for (Enchantment e : Registry.ENCHANTMENT) {
            if (noCurses && e.getKey().getKey().endsWith("_curse")) {
                continue;
            }
            pool.add(e);
        }
        if (pool.isEmpty() || Bukkit.getOnlinePlayers().isEmpty()) {
            return;
        }

        boolean same = cfg().getBoolean("smp.same-for-everyone", true);
        boolean bookIfEmpty = cfg().getBoolean("smp.empty-hand-gives-book", true);
        Roll shared = same ? pick(pool) : null;
        if (same) {
            Msg.broadcast(Msg.gray("Rolled ").append(shared.enchantment().displayName(shared.level()))
                    .append(Msg.gray(" onto everyone's held item!")));
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            Roll roll = same ? shared : pick(pool);

            ItemStack hand = p.getInventory().getItemInMainHand();
            ItemMeta meta = hand.getType().isAir() ? null : hand.getItemMeta();
            if (meta != null) {
                EnchantUtil.apply(meta, roll.enchantment(), roll.level());
                hand.setItemMeta(meta);
                p.getInventory().setItemInMainHand(hand);
            } else if (bookIfEmpty) {
                Map<Integer, ItemStack> leftover = p.getInventory().addItem(EnchantUtil.book(roll.enchantment(), roll.level()));
                for (ItemStack extra : leftover.values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), extra);
                }
            } else {
                continue;
            }

            p.showTitle(Title.title(
                    Component.text("Enchant Roll!", NamedTextColor.GOLD),
                    roll.enchantment().displayName(roll.level())));
            if (!same) {
                Msg.send(p, Msg.gray("You rolled ").append(roll.enchantment().displayName(roll.level())).append(Msg.gray(".")));
            }
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        }
    }

    private static Roll pick(List<Enchantment> pool) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        Enchantment ench = pool.get(rng.nextInt(pool.size()));
        int level = rng.nextInt(1, Math.max(1, ench.getMaxLevel()) + 1);
        return new Roll(ench, level);
    }

    static String format(int seconds) {
        return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
    }
}
