package dev.strengthsmp;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Mutable profile data is only touched from the server thread; immutable snapshots are serialized off-thread. */
public final class PlayerDataStore {
    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, PlayerProfile> profiles = new ConcurrentHashMap<>();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "StrengthSMP-data-writer"); thread.setDaemon(true); return thread;
    });

    public PlayerDataStore(JavaPlugin plugin) { this.plugin = plugin; this.file = new File(plugin.getDataFolder(), "players.yml"); }

    public void load() {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("players");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                String className = root.getString(key + ".class", "");
                profiles.put(id, new PlayerProfile(id, root.getInt(key + ".strength", 0), WeaponClass.parse(className),
                        root.getBoolean(key + ".protection-removed", false)));
            } catch (IllegalArgumentException ex) { plugin.getLogger().warning("Ignoring invalid player data key: " + key); }
        }
    }

    public PlayerProfile get(UUID id) {
        return profiles.computeIfAbsent(id, uuid -> new PlayerProfile(uuid, plugin.getConfig().getInt("strength.default", 0), null, false));
    }

    public void saveAsync() {
        Map<UUID, PlayerProfile> snapshot = profiles.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey, e -> new PlayerProfile(e.getKey(), e.getValue().strength(), e.getValue().weaponClass(), e.getValue().protectionRemoved())));
        writer.execute(() -> {
            YamlConfiguration yaml = new YamlConfiguration();
            snapshot.forEach((id, profile) -> {
                String path = "players." + id;
                yaml.set(path + ".strength", profile.strength());
                yaml.set(path + ".class", profile.weaponClass() == null ? "" : profile.weaponClass().name());
                yaml.set(path + ".protection-removed", profile.protectionRemoved());
            });
            try { plugin.getDataFolder().mkdirs(); yaml.save(file); }
            catch (IOException ex) { plugin.getLogger().severe("Could not save players.yml: " + ex.getMessage()); }
        });
    }

    public void shutdown() {
        saveAsync(); writer.shutdown();
        try { if (!writer.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) writer.shutdownNow(); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); writer.shutdownNow(); }
    }
}
