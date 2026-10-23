package dev.strengthsmp;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public final class StrengthCommand implements CommandExecutor, TabCompleter {
    private final StrengthSMP plugin;
    StrengthCommand(StrengthSMP plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String root = command.getName().toLowerCase(java.util.Locale.ROOT);
        if (root.equals("strength")) {
            if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
            var profile = plugin.data().get(p.getUniqueId());
            String clazz = profile.weaponClass() == null ? "<gray>Unbound" : "<aqua>" + profile.weaponClass().display();
            double damage = p.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE) == null ? 0 : p.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE).getValue();
            p.sendMessage(plugin.mini().deserialize("<gold><bold>STRENGTH SMP</bold></gold> <dark_gray>━━━━━━━━━━━━</dark_gray>"));
            p.sendMessage(plugin.mini().deserialize("<yellow>Strength:</yellow> <white>" + profile.strength() + "</white> <dark_gray>|</dark_gray> <yellow>Class:</yellow> " + clazz));
            p.sendMessage(plugin.mini().deserialize("<yellow>Attack damage:</yellow> <white>" + String.format(java.util.Locale.ROOT, "%.1f", damage) + "</white>"));
            return true;
        }
        if (root.equals("bind")) { if (sender instanceof Player p) new BindMenu(plugin).open(p); else sender.sendMessage("Players only."); return true; }
        if (root.equals("ability")) { if (sender instanceof Player p) plugin.abilities().activate(p); else sender.sendMessage("Players only."); return true; }
        if (root.equals("removeprotection")) {
            if (sender instanceof Player p) { plugin.combat().removeProtection(p); p.sendMessage(plugin.mini().deserialize("<green>Your protection has ended.")); }
            else sender.sendMessage("Players only."); return true;
        }
        if (root.equals("withdraw")) return withdraw(sender, args);
        if (root.equals("bounty")) return bounty(sender, args);
        if (!root.equals("strengthsmp")) return false;
        if (!sender.hasPermission("strengthsmp.admin")) { sender.sendMessage(plugin.mini().deserialize("<red>You lack permission.")); return true; }
        if (args.length == 0) return usage(sender);
        return switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "reload" -> { plugin.reloadConfig(); sender.sendMessage(plugin.mini().deserialize("<green>StrengthSMP config reloaded.")); yield true; }
            case "setstrength" -> setStrength(sender, args);
            case "setweapon" -> setWeapon(sender, args);
            case "reroll" -> reroll(sender, args);
            case "giveitem" -> giveItem(sender, args);
            case "maintenance" -> maintenance(sender, args);
            case "resetplayer" -> reset(sender, args);
            default -> usage(sender);
        };
    }

    private boolean withdraw(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p) || args.length != 1) { sender.sendMessage("Usage: /withdraw <amount>"); return true; }
        int amount; try { amount = Integer.parseInt(args[0]); } catch (NumberFormatException ex) { p.sendMessage(plugin.mini().deserialize("<red>Amount must be a positive integer.")); return true; }
        var profile = plugin.data().get(p.getUniqueId());
        if (amount <= 0 || amount > profile.strength()) { p.sendMessage(plugin.mini().deserialize("<red>You do not have enough withdrawable strength.")); return true; }
        plugin.combat().setStrength(p, profile.strength() - amount, true);
        while (amount > 0) { int stack = Math.min(amount, 64); p.getInventory().addItem(plugin.items().create("strength", stack)).values().forEach(item -> p.getWorld().dropItemNaturally(p.getLocation(), item)); amount -= stack; }
        return true;
    }

    private boolean bounty(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p) || args.length != 2) { sender.sendMessage("Usage: /bounty <player> <amount>"); return true; }
        Player target = Bukkit.getPlayerExact(args[0]); int amount;
        try { amount = Integer.parseInt(args[1]); } catch (NumberFormatException ex) { p.sendMessage(plugin.mini().deserialize("<red>Amount must be an integer.")); return true; }
        if (target == null || target.equals(p) || amount <= 0 || plugin.data().get(p.getUniqueId()).strength() < amount) { p.sendMessage(plugin.mini().deserialize("<red>Invalid target or amount.")); return true; }
        if (target.getPersistentDataContainer().has(plugin.key("bounty"))) { p.sendMessage(plugin.mini().deserialize("<red>That player already has a bounty.")); return true; }
        plugin.combat().setStrength(p, plugin.data().get(p.getUniqueId()).strength() - amount, true);
        target.getPersistentDataContainer().set(plugin.key("bounty"), PersistentDataType.INTEGER, amount);
        p.sendMessage(plugin.mini().deserialize("<gold>Bounty of " + amount + " strength placed on " + target.getName() + ".")); return true;
    }

    private boolean setStrength(CommandSender sender, String[] args) {
        if (args.length != 3) return usage(sender);
        Player p = Bukkit.getPlayerExact(args[1]);
        try { if (p == null) throw new IllegalArgumentException(); int value = Integer.parseInt(args[2]); plugin.combat().setStrength(p, value, true); sender.sendMessage("Strength updated."); }
        catch (IllegalArgumentException ex) { sender.sendMessage(plugin.mini().deserialize("<red>Use an online player and integer amount.")); }
        return true;
    }
    private boolean setWeapon(CommandSender sender, String[] args) {
        if (args.length != 3) return usage(sender);
        Player p = Bukkit.getPlayerExact(args[1]); WeaponClass clazz = WeaponClass.parse(args[2]);
        if (p == null || clazz == null) { sender.sendMessage(plugin.mini().deserialize("<red>Use an online player and class: " + String.join(", ", java.util.Arrays.stream(WeaponClass.values()).map(WeaponClass::name).toList()))); return true; }
        plugin.data().get(p.getUniqueId()).weaponClass(clazz); plugin.data().saveAsync(); sender.sendMessage("Class updated."); return true;
    }
    private boolean reroll(CommandSender sender, String[] args) { if (args.length != 2) return usage(sender); Player p = Bukkit.getPlayerExact(args[1]); if (p == null) sender.sendMessage("Player must be online."); else { plugin.abilities().reroll(p); sender.sendMessage("Class rerolled."); } return true; }
    private boolean giveItem(CommandSender sender, String[] args) {
        if (args.length != 3) return usage(sender);
        Player p = Bukkit.getPlayerExact(args[1]); String kind = args[2].toLowerCase(java.util.Locale.ROOT);
        if (p == null || !List.of("strength", "reroll", "death").contains(kind)) { sender.sendMessage("Usage: /strengthsmp giveitem <player> <strength|reroll|death>"); return true; }
        p.getInventory().addItem(plugin.items().create(kind, 1)).values().forEach(item -> p.getWorld().dropItemNaturally(p.getLocation(), item)); sender.sendMessage("Item given."); return true;
    }
    private boolean maintenance(CommandSender sender, String[] args) { if (args.length != 2 || !List.of("on", "off").contains(args[1].toLowerCase())) return usage(sender); plugin.maintenance(args[1].equalsIgnoreCase("on")); sender.sendMessage("Progression maintenance " + args[1] + "."); return true; }
    private boolean reset(CommandSender sender, String[] args) {
        if (args.length != 2) return usage(sender); Player p = Bukkit.getPlayerExact(args[1]);
        if (p == null) { sender.sendMessage("Player must be online."); return true; }
        var profile = plugin.data().get(p.getUniqueId()); profile.strength(plugin.getConfig().getInt("strength.default", 0)); profile.weaponClass(null); profile.protectionRemoved(false);
        p.setGameMode(GameMode.SURVIVAL); plugin.combat().setStrength(p, profile.strength(), true); plugin.data().saveAsync(); sender.sendMessage("Player reset."); return true;
    }
    private boolean usage(CommandSender sender) { sender.sendMessage(plugin.mini().deserialize("<yellow>Use /strengthsmp <reload|setstrength|setweapon|reroll|giveitem|maintenance|resetplayer>")); return true; }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String root = command.getName().toLowerCase(java.util.Locale.ROOT);
        if (root.equals("strengthsmp")) {
            if (args.length == 1) return match(args[0], List.of("reload", "setstrength", "setweapon", "reroll", "giveitem", "maintenance", "resetplayer"));
            if (args.length == 2 && List.of("setstrength", "setweapon", "reroll", "giveitem", "resetplayer").contains(args[0].toLowerCase())) return match(args[1], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
            if (args.length == 3 && args[0].equalsIgnoreCase("setweapon")) return match(args[2], java.util.Arrays.stream(WeaponClass.values()).map(WeaponClass::name).toList());
            if (args.length == 3 && args[0].equalsIgnoreCase("giveitem")) return match(args[2], List.of("strength", "reroll", "death"));
            if (args.length == 2 && args[0].equalsIgnoreCase("maintenance")) return match(args[1], List.of("on", "off"));
        }
        if (root.equals("bounty") && args.length == 1) return match(args[0], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        return List.of();
    }
    private List<String> match(String prefix, List<String> options) { return new ArrayList<>(options.stream().filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase())).toList()); }
}
