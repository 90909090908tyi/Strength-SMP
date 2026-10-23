package dev.strengthsmp;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class BindMenu implements Listener {
    private final StrengthSMP plugin;
    BindMenu(StrengthSMP plugin) { this.plugin = plugin; }
    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, Component.text("Bind a Combat Class"));
        WeaponClass[] classes = WeaponClass.values();
        for (int i = 0; i < classes.length; i++) {
            WeaponClass selected = classes[i];
            ItemStack icon = new ItemStack(switch (selected) {
                case ASSASSIN -> Material.DIAMOND_SWORD; case JUGGERNAUT -> Material.NETHERITE_AXE;
                case TANK -> Material.SHIELD; case ARCHER -> Material.BOW; case SCORPION -> Material.CROSSBOW; case GLADIATOR -> Material.TRIDENT;
            });
            icon.editMeta(meta -> { meta.displayName(Component.text(selected.display())); meta.setCustomModelData(selected.ordinal() + 1); });
            inv.setItem(i, icon);
        }
        player.openInventory(inv);
    }

    @EventHandler public void click(InventoryClickEvent event) {
        if (!event.getView().title().equals(Component.text("Bind a Combat Class"))) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() < 0 || event.getRawSlot() >= 6) return;
        WeaponClass selected = WeaponClass.values()[event.getRawSlot()];
        var profile = plugin.data().get(player.getUniqueId());
        int cost = profile.weaponClass() == null ? plugin.getConfig().getInt("bind.initial-cost", 3) : plugin.getConfig().getInt("bind.swap-cost", 1);
        if (profile.strength() < cost) { player.sendMessage(plugin.mini().deserialize("<red>Binding costs " + cost + " strength.")); player.closeInventory(); return; }
        plugin.combat().setStrength(player, profile.strength() - cost, true);
        profile.weaponClass(selected); plugin.data().saveAsync();
        player.sendMessage(plugin.mini().deserialize("<green>Bound class: " + selected.display() + ".")); player.closeInventory();
    }
}
