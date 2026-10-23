package dev.strengthsmp;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

public final class ItemListener implements Listener {
    private final StrengthSMP plugin;
    ItemListener(StrengthSMP plugin) { this.plugin = plugin; }

    @EventHandler public void use(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        String kind = plugin.items().kind(event.getItem());
        if (kind == null) return;
        Player player = event.getPlayer();
        switch (kind) {
            case "strength" -> {
                event.setCancelled(true);
                int before = plugin.data().get(player.getUniqueId()).strength();
                int max = plugin.getConfig().getInt("strength.maximum", 5);
                if (before >= max) { player.sendMessage(plugin.mini().deserialize("<red>You are already at maximum strength.")); return; }
                plugin.combat().setStrength(player, before + 1, true); consume(event);
                player.sendMessage(plugin.mini().deserialize("<green>Strength shard consumed. Strength: " + (before + 1)));
            }
            case "reroll" -> {
                event.setCancelled(true); consume(event);
                plugin.abilities().reroll(player);
                player.sendMessage(plugin.mini().deserialize("<green>Your combat class has been rerolled."));
            }
            case "death" -> {
                event.setCancelled(true);
                Player target = Bukkit.getOnlinePlayers().stream().filter(p -> p.getGameMode() == GameMode.SPECTATOR && !p.equals(player)).findFirst().orElse(null);
                if (target == null) { player.sendMessage(plugin.mini().deserialize("<red>No spectator is available to revive.")); return; }
                plugin.combat().revive(target); consume(event);
                player.sendMessage(plugin.mini().deserialize("<green>Revived " + target.getName() + "."));
            }
        }
    }

    @EventHandler public void place(BlockPlaceEvent event) {
        if (!"death".equals(plugin.items().kind(event.getItemInHand()))) return;
        event.setCancelled(true);
        Player target = Bukkit.getOnlinePlayers().stream().filter(p -> p.getGameMode() == GameMode.SPECTATOR).findFirst().orElse(null);
        if (target == null) { event.getPlayer().sendMessage(plugin.mini().deserialize("<red>No spectator is available to revive.")); return; }
        plugin.combat().revive(target);
        var item = event.getItemInHand().clone();
        if (item.getAmount() <= 1) item = new org.bukkit.inventory.ItemStack(Material.AIR);
        else item.setAmount(item.getAmount() - 1);
        event.getPlayer().getInventory().setItem(event.getHand(), item);
    }

    private void consume(PlayerInteractEvent event) {
        var item = event.getItem(); if (item == null) return;
        if (item.getAmount() <= 1) event.getPlayer().getInventory().setItemInMainHand(new org.bukkit.inventory.ItemStack(Material.AIR));
        else item.setAmount(item.getAmount() - 1);
    }
}
