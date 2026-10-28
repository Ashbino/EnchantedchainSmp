package com.thegildedking.enchain;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Creates explosions on behalf of a player, optionally sparing that player from the damage. */
public final class Booms {

    private final EnchainPlugin plugin;
    private final Set<UUID> safe = ConcurrentHashMap.newKeySet();

    public Booms(EnchainPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isSafe(UUID id) {
        return safe.contains(id);
    }

    public void explode(Location loc, double power, Player source, boolean breakBlocks) {
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        float capped = (float) Math.max(0.5, Math.min(plugin.getConfig().getDouble("emulation.blast-protection.max-power", 8.0), power));
        boolean protect = source != null && plugin.getConfig().getBoolean("emulation.blast-protection.protect-owner", true);
        UUID id = source == null ? null : source.getUniqueId();
        if (protect) {
            safe.add(id);
        }
        try {
            world.createExplosion(loc, capped, false, breakBlocks, source);
        } finally {
            if (protect) {
                safe.remove(id);
            }
        }
    }
}
