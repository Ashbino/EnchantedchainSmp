package com.thegildedking.enchain;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class EnchainPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(new AnvilListener(this), this);

        EnchainCommand handler = new EnchainCommand(this);
        PluginCommand command = getCommand("enchain");
        if (command != null) {
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        }
        getLogger().info("Enchain enabled - any item, any enchant.");
    }
}
