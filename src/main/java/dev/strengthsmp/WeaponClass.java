package dev.strengthsmp;

import java.util.Locale;

public enum WeaponClass {
    ASSASSIN("Assassin", "Sword"), JUGGERNAUT("Juggernaut", "Axe"), TANK("Tank", "Shield"),
    ARCHER("Archer", "Bow"), SCORPION("Scorpion", "Crossbow"), GLADIATOR("Gladiator", "Trident");

    private final String display;
    private final String weapon;
    WeaponClass(String display, String weapon) { this.display = display; this.weapon = weapon; }
    public String display() { return display; }
    public String weapon() { return weapon; }
    public static WeaponClass parse(String input) {
        if (input == null) return null;
        try { return valueOf(input.trim().toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException ex) { return null; }
    }
}
