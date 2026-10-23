package dev.strengthsmp;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Arrow;
import org.bukkit.inventory.EquipmentSlotGroup;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CombatListener implements Listener {
    private final StrengthSMP plugin;
    private final Map<UUID, Long> protectionUntil = new HashMap<>();
    private final Map<UUID, Long> lastHit = new HashMap<>();
    private final org.bukkit.NamespacedKey strengthKey;
    private final Map<UUID, AttributeModifier> strengthModifiers = new HashMap<>();
    CombatListener(StrengthSMP plugin) { this.plugin = plugin; this.strengthKey = plugin.key("strength_damage"); }

    @EventHandler public void join(PlayerJoinEvent event) {
        Player player = event.getPlayer(); applyDamage(player);
        if (plugin.getConfig().getBoolean("strength.minimum-spectator", true)
                && plugin.data().get(player.getUniqueId()).strength() <= plugin.getConfig().getInt("strength.minimum", 0))
            player.setGameMode(GameMode.SPECTATOR);
    }
    @EventHandler public void quit(PlayerQuitEvent event) { plugin.data().saveAsync(); }
    @EventHandler public void respawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = event.getPlayer();
            int min = plugin.getConfig().getInt("strength.minimum", 0);
            if (plugin.data().get(player.getUniqueId()).strength() <= min && plugin.getConfig().getBoolean("strength.minimum-spectator", true)) player.setGameMode(GameMode.SPECTATOR);
            applyDamage(player);
        });
    }

    @EventHandler public void death(PlayerDeathEvent event) {
        Player dead = event.getEntity();
        if (plugin.maintenance()) return;
        var profile = plugin.data().get(dead.getUniqueId());
        setStrength(dead, profile.strength() - plugin.getConfig().getInt("strength.death-penalty", 1), true);
        protectionUntil.put(dead.getUniqueId(), System.currentTimeMillis() + plugin.getConfig().getLong("strength.protection-seconds", 30) * 1000L);
        Player killer = dead.getKiller();
        if (killer != null) {
            var killerProfile = plugin.data().get(killer.getUniqueId());
            setStrength(killer, killerProfile.strength() + plugin.getConfig().getInt("strength.kill-reward", 1), true);
            Integer bounty = bounty(dead);
            if (bounty != null) {
                setStrength(killer, plugin.data().get(killer.getUniqueId()).strength() + bounty, true);
                clearBounty(dead);
                killer.sendMessage(plugin.mini().deserialize("<gold>Bounty claimed: +" + bounty + " strength."));
            }
        }
        int min = plugin.getConfig().getInt("strength.minimum", 0);
        if (profile.strength() - plugin.getConfig().getInt("strength.death-penalty", 1) <= min && plugin.getConfig().getBoolean("strength.minimum-spectator", true))
            Bukkit.getScheduler().runTask(plugin, () -> dead.setGameMode(GameMode.SPECTATOR));
    }

    @EventHandler public void damage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (event.getEntity() instanceof Player victim) {
            if (!plugin.getConfig().getBoolean("combat.pvp-enabled", true) || protectedPlayer(attacker) || protectedPlayer(victim)) { event.setCancelled(true); return; }
            lastHit.put(attacker.getUniqueId(), System.currentTimeMillis());
            lastHit.put(victim.getUniqueId(), System.currentTimeMillis());
        }
        WeaponClass attackerClass = plugin.data().get(attacker.getUniqueId()).weaponClass();
        if (attackerClass == WeaponClass.ASSASSIN && attacker.getInventory().getItemInMainHand().getType().name().endsWith("_SWORD") && plugin.abilities().assassinCombo(attacker)) event.setDamage(event.getDamage() * 1.5);
        if (attackerClass == WeaponClass.JUGGERNAUT && event.getEntity() instanceof Player victim && victim.isBlocking()) {
            victim.setCooldown(org.bukkit.Material.SHIELD, 100);
        }
        if (event.getEntity() instanceof Player victim && plugin.data().get(victim.getUniqueId()).weaponClass() == WeaponClass.TANK) event.setDamage(event.getDamage() * 0.5);
    }

    @EventHandler public void sneak(PlayerToggleSneakEvent event) {
        // Shift+F is handled through the offhand swap event below.
    }

    @EventHandler public void swap(PlayerSwapHandItemsEvent event) {
        if (event.getPlayer().isSneaking()) { event.setCancelled(true); plugin.abilities().activate(event.getPlayer()); }
    }

    @EventHandler public void rightClick(PlayerInteractEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND || event.getItem() == null) return;
        WeaponClass clazz = plugin.data().get(event.getPlayer().getUniqueId()).weaponClass();
        if (clazz == null) return;
        org.bukkit.Material material = event.getItem().getType();
        boolean matching = switch (clazz) {
            case ASSASSIN -> material.name().endsWith("_SWORD");
            case JUGGERNAUT -> material.name().endsWith("_AXE");
            case TANK -> material == org.bukkit.Material.SHIELD;
            case ARCHER -> material == org.bukkit.Material.BOW;
            case SCORPION -> material == org.bukkit.Material.CROSSBOW;
            case GLADIATOR -> material == org.bukkit.Material.TRIDENT;
        };
        if (matching && (event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_AIR || event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK))
            plugin.abilities().activate(event.getPlayer());
    }

    @EventHandler public void projectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof Player shooter)) return;
        if (projectile.getPersistentDataContainer().has(plugin.key("gravity_arrow"))) {
            var center = projectile.getLocation();
            for (int tick = 0; tick < 40; tick++) {
                final int t = tick;
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!center.getChunk().isLoaded()) return;
                    center.getWorld().spawnParticle(org.bukkit.Particle.PORTAL, center, 20, 2.2, 1.5, 2.2, .03);
                    for (var entity : center.getWorld().getNearbyEntities(center, 8, 8, 8)) if (entity instanceof org.bukkit.entity.LivingEntity target && !target.equals(shooter)) {
                        var pull = center.toVector().subtract(target.getLocation().toVector()).normalize().multiply(.25);
                        target.setVelocity(pull);
                    }
                }, t);
            }
            projectile.remove();
        } else if (projectile.getPersistentDataContainer().has(plugin.key("scorpion_hook"))) {
            if (event.getHitEntity() instanceof org.bukkit.entity.LivingEntity target) {
                var pull = shooter.getLocation().toVector().subtract(target.getLocation().toVector()).normalize().multiply(2.0).setY(.35);
                target.setVelocity(pull);
            } else shooter.setVelocity(projectile.getLocation().toVector().subtract(shooter.getLocation().toVector()).normalize().multiply(1.5));
            projectile.remove();
        }
    }

    @EventHandler public void trident(org.bukkit.event.entity.ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.Trident trident && trident.getShooter() instanceof Player player
                && plugin.data().get(player.getUniqueId()).weaponClass() == WeaponClass.GLADIATOR) event.setCancelled(true);
    }

    public void setStrength(Player player, int value, boolean announce) {
        int min = plugin.getConfig().getInt("strength.minimum", 0), max = plugin.getConfig().getInt("strength.maximum", 5);
        var profile = plugin.data().get(player.getUniqueId()); int previous = profile.strength(); profile.strength(Math.max(min, Math.min(max, value)));
        applyDamage(player); plugin.data().saveAsync();
        if (announce) player.sendActionBar(plugin.mini().deserialize("<gold>Strength <white>" + profile.strength()));
        if (profile.strength() <= min && plugin.getConfig().getBoolean("strength.minimum-spectator", true)) player.setGameMode(GameMode.SPECTATOR);
        else if (previous <= min && profile.strength() > min && player.getGameMode() == GameMode.SPECTATOR) player.setGameMode(GameMode.SURVIVAL);
    }
    private void applyDamage(Player player) {
        var attribute = player.getAttribute(Attribute.ATTACK_DAMAGE); if (attribute == null) return;
        AttributeModifier old = strengthModifiers.remove(player.getUniqueId());
        if (old != null) attribute.removeModifier(old);
        double amount = plugin.data().get(player.getUniqueId()).strength() * plugin.getConfig().getDouble("strength.damage-per-point", 1.0);
        if (amount != 0) {
            AttributeModifier modifier = new AttributeModifier(strengthKey, amount, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY);
            attribute.addModifier(modifier); strengthModifiers.put(player.getUniqueId(), modifier);
        }
    }
    public boolean protectedPlayer(Player p) { return protectionUntil.getOrDefault(p.getUniqueId(), 0L) > System.currentTimeMillis(); }
    public void removeProtection(Player p) { protectionUntil.remove(p.getUniqueId()); plugin.data().get(p.getUniqueId()).protectionRemoved(true); }
    public void revive(Player p) { p.setGameMode(GameMode.SURVIVAL); setStrength(p, Math.max(0, plugin.getConfig().getInt("strength.default", 0)), false); removeProtection(p); p.teleport(p.getWorld().getSpawnLocation()); }
    private Integer bounty(Player p) { return p.getPersistentDataContainer().get(plugin.key("bounty"), org.bukkit.persistence.PersistentDataType.INTEGER); }
    private void clearBounty(Player p) { p.getPersistentDataContainer().remove(plugin.key("bounty")); }
}
