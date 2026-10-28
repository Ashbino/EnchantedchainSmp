package com.thegildedking.enchain;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.ThrowableProjectile;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Makes enchants actually DO something on items they don't normally work on:
 * Density = mace smash on anything, Blast Protection = explosive throwables /
 * tools / weapons, Loyalty = throw any item and it flies back.
 */
public final class EmulationListener implements Listener {

    private final EnchainPlugin plugin;
    private final NamespacedKey thrownKey;
    private final Map<UUID, Long> lastThrow = new HashMap<>();

    public EmulationListener(EnchainPlugin plugin) {
        this.plugin = plugin;
        this.thrownKey = new NamespacedKey(plugin, "thrown");
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    // ------------------------------------------------------------------
    // Explosion damage protection for the player who caused the blast
    // ------------------------------------------------------------------
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplosionDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) {
            return;
        }
        DamageCause cause = e.getCause();
        if ((cause == DamageCause.ENTITY_EXPLOSION || cause == DamageCause.BLOCK_EXPLOSION)
                && plugin.booms().isSafe(p.getUniqueId())) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------
    // Melee: Density (mace smash) + Blast Protection (explosive hits)
    // ------------------------------------------------------------------
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMelee(EntityDamageByEntityEvent e) {
        if (e.getCause() != DamageCause.ENTITY_ATTACK) {
            return;
        }
        if (!(e.getDamager() instanceof Player p) || !p.hasPermission("enchain.emulate")) {
            return;
        }
        ItemStack item = p.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            return;
        }
        Entity victim = e.getEntity();

        int density = EnchantUtil.held(item, "density");
        if (density > 0 && !item.getType().name().equals("MACE")
                && cfg().getBoolean("emulation.density.enabled", true)) {
            double fall = p.getFallDistance();
            if (fall > 1.5 && !p.isGliding() && !p.isFlying()) {
                double smash = Math.min(fall, 3) * 4
                        + Math.max(0, Math.min(fall - 3, 5)) * 2
                        + Math.max(0, fall - 8);
                double bonus = smash + fall * 0.5 * density;
                e.setDamage(e.getDamage() + bonus);

                World w = victim.getWorld();
                w.spawnParticle(Particle.CRIT, victim.getLocation().add(0, 1, 0), 40, 0.6, 0.6, 0.6, 0.3);
                w.playSound(victim.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.2f, 0.6f);

                int wind = EnchantUtil.held(item, "wind_burst");
                if (wind > 0) {
                    p.setVelocity(p.getVelocity().setY(0.7 + 0.4 * wind));
                }
                p.setFallDistance(0f);
            }
        }

        int blast = EnchantUtil.held(item, "blast_protection");
        if (blast > 0 && cfg().getBoolean("emulation.blast-protection.enabled", true)
                && cfg().getBoolean("emulation.blast-protection.weapons", true)) {
            Location at = victim.getLocation().add(0, 0.5, 0);
            double power = 1.0 + 0.5 * blast;
            boolean breaks = cfg().getBoolean("emulation.blast-protection.weapons-break-blocks", false);
            Bukkit.getScheduler().runTask(plugin, () -> plugin.booms().explode(at, power, p, breaks));
        }
    }

    // ------------------------------------------------------------------
    // Tools: Blast Protection blows up the block you break
    // ------------------------------------------------------------------
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (!p.hasPermission("enchain.emulate")) {
            return;
        }
        if (!cfg().getBoolean("emulation.blast-protection.enabled", true)
                || !cfg().getBoolean("emulation.blast-protection.tools", true)) {
            return;
        }
        ItemStack tool = p.getInventory().getItemInMainHand();
        int blast = EnchantUtil.held(tool, "blast_protection");
        if (blast <= 0) {
            return;
        }
        Location at = e.getBlock().getLocation().add(0.5, 0.5, 0.5);
        double power = 1.0 + 0.75 * blast;
        boolean breaks = cfg().getBoolean("emulation.blast-protection.break-blocks", true);
        plugin.booms().explode(at, power, p, breaks);
    }

    // ------------------------------------------------------------------
    // Loyalty: right-click to throw ANY item, it flies back to you
    // ------------------------------------------------------------------
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (!cfg().getBoolean("emulation.loyalty.enabled", true)) {
            return;
        }
        if (e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player p = e.getPlayer();
        if (!p.hasPermission("enchain.emulate")) {
            return;
        }
        Action action = e.getAction();
        boolean air = action == Action.RIGHT_CLICK_AIR;
        boolean block = action == Action.RIGHT_CLICK_BLOCK && p.isSneaking();
        if (!air && !block) {
            return;
        }
        ItemStack held = p.getInventory().getItemInMainHand();
        if (held.getType().isAir() || held.getType() == Material.TRIDENT) {
            return;
        }
        int loyalty = EnchantUtil.held(held, "loyalty");
        if (loyalty <= 0) {
            return;
        }

        long now = System.currentTimeMillis();
        long cooldown = cfg().getLong("emulation.loyalty.cooldown-ms", 400L);
        Long last = lastThrow.get(p.getUniqueId());
        e.setCancelled(true);
        if (last != null && now - last < cooldown) {
            return;
        }
        lastThrow.put(p.getUniqueId(), now);

        ItemStack thrown = held.clone();
        thrown.setAmount(1);
        held.setAmount(held.getAmount() - 1);

        double speed = cfg().getDouble("emulation.loyalty.throw-speed", 1.8);
        Snowball ball = p.launchProjectile(Snowball.class, p.getEyeLocation().getDirection().multiply(speed));
        ball.setItem(thrown);
        ball.getPersistentDataContainer().set(thrownKey, PersistentDataType.BYTE, (byte) 1);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 1.0f, 0.7f);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastThrow.remove(e.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------
    // Projectile hits: thrown-item damage/return, Blast Protection, Channeling
    // ------------------------------------------------------------------
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(ProjectileHitEvent e) {
        Projectile proj = e.getEntity();
        ItemStack item = projectileItem(proj);
        if (item == null || item.getType().isAir()) {
            return;
        }
        Player owner = proj.getShooter() instanceof Player pl ? pl : null;
        Location at = proj.getLocation();

        // Blast Protection on anything that flies
        int blast = EnchantUtil.held(item, "blast_protection");
        if (blast > 0 && cfg().getBoolean("emulation.blast-protection.enabled", true)
                && cfg().getBoolean("emulation.blast-protection.projectiles", true)
                && (owner == null || owner.hasPermission("enchain.emulate"))) {
            double power = 1.5 + 0.75 * blast;
            boolean breaks = cfg().getBoolean("emulation.blast-protection.break-blocks", true);
            plugin.booms().explode(at, power, owner, breaks);
        }

        boolean ourThrow = proj.getPersistentDataContainer().has(thrownKey, PersistentDataType.BYTE);

        // Channeling on thrown things (tridents already handle themselves)
        if (!(proj instanceof Trident) && EnchantUtil.held(item, "channeling") > 0
                && cfg().getBoolean("emulation.loyalty.channeling-lightning", true)
                && (owner == null || owner.hasPermission("enchain.emulate"))) {
            Entity target = e.getHitEntity();
            at.getWorld().strikeLightning(target != null ? target.getLocation() : at);
        }

        if (!ourThrow) {
            return;
        }

        // Damage whatever the thrown item hit
        Entity hit = e.getHitEntity();
        if (hit instanceof LivingEntity living && !living.equals(owner)) {
            double damage = cfg().getDouble("emulation.loyalty.base-damage", 6.0);
            int sharp = EnchantUtil.held(item, "sharpness");
            if (sharp > 0) {
                damage += 0.5 * sharp + 0.5;
            }
            if (owner != null) {
                living.damage(damage, owner);
            } else {
                living.damage(damage);
            }
            int fire = EnchantUtil.held(item, "fire_aspect");
            if (fire > 0) {
                living.setFireTicks(80 * fire);
            }
            int knock = EnchantUtil.held(item, "knockback");
            if (knock > 0) {
                Vector push = proj.getVelocity().clone().setY(0);
                if (push.lengthSquared() > 0) {
                    living.setVelocity(living.getVelocity().add(push.normalize().multiply(0.6 * knock)));
                }
            }
        }

        // Wear the item down, then send it home
        ItemStack back = item.clone();
        int loss = cfg().getInt("emulation.loyalty.durability-loss", 1);
        int max = back.getType().getMaxDurability();
        if (loss > 0 && max > 0) {
            ItemMeta meta = back.getItemMeta();
            if (meta instanceof Damageable dmg) {
                int next = dmg.getDamage() + loss;
                if (next >= max) {
                    return; // it broke
                }
                dmg.setDamage(next);
                back.setItemMeta(meta);
            }
        }
        int loyalty = Math.max(1, EnchantUtil.held(item, "loyalty"));
        startReturn(owner, back, at, loyalty);
    }

    private static ItemStack projectileItem(Projectile proj) {
        if (proj instanceof ThrowableProjectile throwable) {
            return throwable.getItem();
        }
        if (proj instanceof AbstractArrow arrow) {
            return arrow.getItemStack();
        }
        return null;
    }

    private void startReturn(Player owner, ItemStack stack, Location from, int loyalty) {
        World world = from.getWorld();
        if (world == null) {
            return;
        }
        if (owner == null || !owner.isOnline()) {
            if (owner == null) {
                world.dropItemNaturally(from, stack);
            } else {
                world.dropItemNaturally(from, stack);
            }
            return;
        }

        UUID ownerId = owner.getUniqueId();
        Item drop = world.dropItem(from, stack, it -> {
            it.setGravity(false);
            it.setVelocity(new Vector());
            it.setPickupDelay(32767);
            it.setInvulnerable(true);
            it.setUnlimitedLifetime(true);
        });

        double speed = Math.min(2.0, 0.6 + 0.2 * loyalty);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks++;
                Player p = Bukkit.getPlayer(ownerId);

                if (!drop.isValid()) {
                    cancel();
                    if (p != null) {
                        give(p, stack);
                    }
                    return;
                }
                if (p == null || !p.isOnline() || p.isDead()) {
                    drop.setPickupDelay(0);
                    drop.setGravity(true);
                    drop.setUnlimitedLifetime(false);
                    drop.setInvulnerable(false);
                    cancel();
                    return;
                }

                Location here = drop.getLocation();
                if (!here.getWorld().equals(p.getWorld())) {
                    drop.remove();
                    give(p, stack);
                    cancel();
                    return;
                }

                Vector toPlayer = p.getLocation().add(0, 1.0, 0).toVector().subtract(here.toVector());
                double dist = toPlayer.length();
                if (dist < 1.6 || ticks > 300) {
                    drop.remove();
                    give(p, stack);
                    cancel();
                    return;
                }
                Vector step = toPlayer.normalize().multiply(Math.min(dist, speed));
                drop.teleport(here.add(step));
            }
        }.runTaskTimer(plugin, 6L, 1L);
    }

    private static void give(Player p, ItemStack stack) {
        Map<Integer, ItemStack> leftover = p.getInventory().addItem(stack);
        for (ItemStack extra : leftover.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), extra);
        }
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
    }
}
