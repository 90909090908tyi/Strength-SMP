package dev.strengthsmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public final class ItemService {
    private final StrengthSMP plugin;
    ItemService(StrengthSMP plugin) { this.plugin = plugin; }

    public ItemStack create(String kind, int amount) {
        Material material = switch (kind) {
            case "strength" -> Material.AMETHYST_SHARD;
            case "reroll" -> Material.ENCHANTED_BOOK;
            case "death" -> Material.BEACON;
            default -> Material.PAPER;
        };
        String label = switch (kind) { case "strength" -> "Strength Shard"; case "reroll" -> "Reroll Grimoire"; case "death" -> "Revive Beacon"; default -> "Unknown"; };
        ItemStack item = new ItemStack(material, Math.max(1, amount));
        item.editMeta(meta -> {
            meta.displayName(Component.text(label, NamedTextColor.GOLD));
            meta.lore(java.util.List.of(Component.text(switch (kind) {
                case "strength" -> "Consume to gain 1 strength";
                case "reroll" -> "Consume to reroll your class";
                case "death" -> "Consume to revive a strength-banned player";
                default -> "";
            }, NamedTextColor.GRAY)));
            meta.getPersistentDataContainer().set(plugin.key("item_kind"), PersistentDataType.STRING, kind);
        });
        return item;
    }

    public String kind(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(plugin.key("item_kind"), PersistentDataType.STRING);
    }
}
