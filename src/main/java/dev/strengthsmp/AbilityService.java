package dev.strengthsmp;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AbilityService {
    private final StrengthSMP plugin;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> combos = new ConcurrentHashMap<>();
    private final Map<UUID, Long> comboExpiry = new ConcurrentHashMap<>();
    AbilityService(StrengthSMP plugin) { this.plugin = plugin; }

    public void activate(Player player) {
        WeaponClass clazz = plugin.data().get(player.getUniqueId()).weaponClass();
        if (clazz == null) { player.sendMessage(plugin.mini().deserialize("<red>Bind a class first with /bind.")); return; }
        long now = System.currentTimeMillis(), until = cooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (until > now) { player.sendActionBar(plugin.mini().deserialize("<gray>Ability cooldown: <white>" + ((until - now + 999) / 1000) + "s")); return; }
        long seconds = plugin.getConfig().getLong("cooldowns.ability-seconds", 15);
        cooldowns.put(player.getUniqueId(), now + Math.max(0, seconds) * 1000L);
        switch (clazz) {
            case ASSASSIN -> beam(player);
            case JUGGERNAUT -> cleave(player);
            case TANK -> forcefield(player);
            case ARCHER -> gravityArrow(player);
            case SCORPION -> hook(player);
            case GLADIATOR -> riptide(player);
        }
    }

    public boolean assassinCombo(Player attacker) {
        long now = System.currentTimeMillis();
        int count = comboExpiry.getOrDefault(attacker.getUniqueId(), 0L) < now ? 0 : combos.getOrDefault(attacker.getUniqueId(), 0);
        count++;
        combos.put(attacker.getUniqueId(), count % 3);
        comboExpiry.put(attacker.getUniqueId(), now + 2500);
        return count >= 3;
    }

    public void reroll(Player p) {
        WeaponClass[] choices = WeaponClass.values();
        WeaponClass next = choices[java.util.concurrent.ThreadLocalRandom.current().nextInt(choices.length)];
        plugin.data().get(p.getUniqueId()).weaponClass(next); plugin.data().saveAsync();
        p.sendMessage(plugin.mini().deserialize("<gold>New class: <yellow>" + next.display()));
    }

    private void beam(Player p) {
        Location start = p.getEyeLocation(); Vector dir = start.getDirection().normalize();
        for (double d = 0; d <= 12; d += .35) {
            Location point = start.clone().add(dir.clone().multiply(d));
            point.getWorld().spawnParticle(Particle.END_ROD, point, 2, 0, 0, 0, 0);
            point.getWorld().spawnParticle(Particle.WAX_ON, point, 1, 0, 0, 0, 0);
            for (Entity e : point.getWorld().getNearbyEntities(point, .6, .6, .6)) {
                if (e instanceof LivingEntity target && !e.equals(p)) { target.damage(6, p); target.setVelocity(target.getVelocity().add(new Vector(0, .8, 0))); }
            }
        }
        p.getWorld().playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1, .7f);
    }

    private void cleave(Player p) {
        Vector forward = p.getLocation().getDirection().setY(0).normalize();
        final int[] ticks = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            ticks[0]++;
            for (Entity e : p.getNearbyEntities(3.5, 2, 3.5)) if (e instanceof LivingEntity target && !target.equals(p)) {
                Vector pull = p.getLocation().toVector().subtract(target.getLocation().toVector()).normalize().multiply(.45);
                target.setVelocity(pull);
            }
            p.getWorld().spawnParticle(Particle.PORTAL, p.getLocation().add(forward.clone().multiply(2)), 18, 1.2, .5, 1.2, .05);
            if (ticks[0] >= 40) {
                for (Entity e : p.getNearbyEntities(4, 3, 4)) if (e instanceof LivingEntity target && !target.equals(p)) target.damage(8, p);
                p.getWorld().createExplosion(p.getLocation(), 0F, false, false, p); task.cancel();
            }
        }, 0L, 1L);
    }

    private void forcefield(Player p) {
        Location center = p.getLocation(); p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, center, 100, 2.5, 1, 2.5, .15);
        for (Entity e : p.getNearbyEntities(6, 4, 6)) if (e instanceof LivingEntity target && !target.equals(p)) {
            Vector push = target.getLocation().toVector().subtract(center.toVector()).normalize().multiply(1.5).setY(.5);
            target.setVelocity(push); target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1)); target.damage(2, p);
        }
        p.getWorld().playSound(center, org.bukkit.Sound.ENTITY_LIGHTNING_BOLT_IMPACT, .7f, 1.5f);
    }

    private void gravityArrow(Player p) {
        Arrow arrow = p.launchProjectile(Arrow.class); arrow.setVelocity(p.getEyeLocation().getDirection().multiply(2.3));
        arrow.getPersistentDataContainer().set(plugin.key("gravity_arrow"), org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
    }

    private void hook(Player p) {
        Arrow hook = p.launchProjectile(Arrow.class); hook.setVelocity(p.getEyeLocation().getDirection().multiply(3.2));
        hook.setPickupStatus(Arrow.PickupStatus.DISALLOWED);
        hook.getPersistentDataContainer().set(plugin.key("scorpion_hook"), org.bukkit.persistence.PersistentDataType.STRING, p.getUniqueId().toString());
    }

    private void riptide(Player p) {
        Vector impulse = p.getEyeLocation().getDirection().normalize().multiply(2.5).setY(Math.max(.35, p.getEyeLocation().getDirection().getY() * 1.8));
        p.setVelocity(impulse); p.setFallDistance(0);
        final Location[] previous = {p.getLocation()}; final int[] ticks = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!p.isOnline() || ++ticks[0] > 12) { task.cancel(); return; }
            p.getWorld().spawnParticle(Particle.SPLASH, p.getLocation(), 14, .4, .4, .4, .1);
            for (Entity e : p.getNearbyEntities(1.5, 1.5, 1.5)) if (e instanceof LivingEntity target && !target.equals(p)) target.damage(4, p);
            previous[0] = p.getLocation();
        }, 0L, 1L);
    }
}
