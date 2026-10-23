package dev.strengthsmp;

import java.util.UUID;

public final class PlayerProfile {
    private final UUID id;
    private int strength;
    private WeaponClass weaponClass;
    private boolean protectionRemoved;
    public PlayerProfile(UUID id, int strength, WeaponClass weaponClass, boolean protectionRemoved) {
        this.id = id; this.strength = strength; this.weaponClass = weaponClass; this.protectionRemoved = protectionRemoved;
    }
    public UUID id() { return id; }
    public int strength() { return strength; }
    public void strength(int value) { strength = value; }
    public WeaponClass weaponClass() { return weaponClass; }
    public void weaponClass(WeaponClass value) { weaponClass = value; }
    public boolean protectionRemoved() { return protectionRemoved; }
    public void protectionRemoved(boolean value) { protectionRemoved = value; }
}
