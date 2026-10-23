package dev.strengthsmp;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public final class StrengthSMP extends JavaPlugin {
    private static StrengthSMP instance;
    private final MiniMessage mini = MiniMessage.miniMessage();
    private PlayerDataStore data;
    private ItemService items;
    private AbilityService abilities;
    private CombatListener combat;
    private volatile boolean maintenance;

    @Override public void onEnable() {
        instance = this; saveDefaultConfig();
        data = new PlayerDataStore(this); data.load();
        items = new ItemService(this); abilities = new AbilityService(this); combat = new CombatListener(this);
        Bukkit.getPluginManager().registerEvents(combat, this);
        Bukkit.getPluginManager().registerEvents(new ItemListener(this), this);
        Bukkit.getPluginManager().registerEvents(new BindMenu(this), this);
        StrengthCommand command = new StrengthCommand(this);
        for (String name : new String[]{"strength", "bind", "withdraw", "bounty", "ability", "removeprotection", "strengthsmp"}) {
            var registration = getCommand(name);
            if (registration != null) { registration.setExecutor(command); registration.setTabCompleter(command); }
        }
        Bukkit.getScheduler().runTaskTimer(this, data::saveAsync, 20L * 60, 20L * 60);
    }

    @Override public void onDisable() { if (data != null) data.shutdown(); }
    public static StrengthSMP get() { return instance; }
    public MiniMessage mini() { return mini; }
    public PlayerDataStore data() { return data; }
    public ItemService items() { return items; }
    public AbilityService abilities() { return abilities; }
    public CombatListener combat() { return combat; }
    public NamespacedKey key(String value) { return new NamespacedKey(this, value); }
    public boolean maintenance() { return maintenance; }
    public void maintenance(boolean enabled) { maintenance = enabled; }
}
