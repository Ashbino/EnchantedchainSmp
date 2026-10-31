package com.thegildedking.enchain;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class EnchainPlugin extends JavaPlugin {

    private Booms booms;
    private SmpManager smp;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        booms = new Booms(this);
        smp = new SmpManager(this);

        getServer().getPluginManager().registerEvents(new AnvilListener(this), this);
        getServer().getPluginManager().registerEvents(new EmulationListener(this), this);

        EnchainCommand handler = new EnchainCommand(this);
        PluginCommand command = getCommand("enchain");
        if (command != null) {
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        }
        getLogger().info("Enchain enabled - any item, any enchant.");
    }

    @Override
    public void onDisable() {
        if (smp != null) {
            smp.stop();
        }
    }

    public Booms booms() {
        return booms;
    }

    public SmpManager smp() {
        return smp;
    }
}
