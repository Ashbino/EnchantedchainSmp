package com.thegildedking.enchain;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

final class Msg {

    static final Component PREFIX = MiniMessage.miniMessage().deserialize(
            "<gradient:#f5c542:#b8860b><bold>Enchain</bold></gradient> <dark_gray>\u00bb</dark_gray> ");

    private Msg() {
    }

    static void send(CommandSender to, Component body) {
        to.sendMessage(PREFIX.append(body));
    }

    static void broadcast(Component body) {
        Bukkit.broadcast(PREFIX.append(body));
    }

    static Component gray(String s) {
        return Component.text(s, NamedTextColor.GRAY);
    }

    static Component gold(String s) {
        return Component.text(s, NamedTextColor.GOLD);
    }

    static Component err(String s) {
        return Component.text(s, NamedTextColor.RED);
    }
}
