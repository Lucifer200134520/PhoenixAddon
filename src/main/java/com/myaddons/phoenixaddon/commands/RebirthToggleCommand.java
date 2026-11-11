package com.myaddons.phoenixaddon.commands;

import com.myaddons.phoenixaddon.Rebirth;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RebirthToggleCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Эта команда доступна только игрокам.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("projectkorra.command.rebirthtoggle")) {
            player.sendMessage(ChatColor.RED + "У вас нет прав для использования этой команды.");
            return true;
        }

        boolean enabled = Rebirth.toggleEnabled(player.getUniqueId());
        if (enabled) {
            player.sendMessage(ChatColor.GOLD + "Rebirth " + ChatColor.GREEN + "включён.");
        } else {
            player.sendMessage(ChatColor.GOLD + "Rebirth " + ChatColor.RED + "отключён.");
        }

        return true;
    }
}
