package org.blackgeyer.ausmerzer.explosions;

import org.bukkit.Material;
import org.bukkit.Tag;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

public final class ExplosiveItems {

    public enum Type {
        TNT(Material.TNT),
        TNT_MINECART(Material.TNT_MINECART),
        END_CRYSTAL(Material.END_CRYSTAL),
        RESPAWN_ANCHOR(Material.RESPAWN_ANCHOR),
        BED(null), 
        FIREWORK_ROCKET(Material.FIREWORK_ROCKET);

        private final Material material;

        Type(Material material) {
            this.material = material;
        }
    }

    public static final Set<Material> ALL;

    static {
        Set<Material> set = EnumSet.noneOf(Material.class);
        for (Type type : Type.values()) {
            if (type.material != null) set.add(type.material);
        }
        set.addAll(Tag.BEDS.getValues());
        ALL = Set.copyOf(set);
    }

    private ExplosiveItems() {}

    public static Type typeOf(Material material) {
        if (material == null) return null;
        if (Tag.BEDS.isTagged(material)) return Type.BED;
        for (Type type : Type.values()) {
            if (type.material == material) return type;
        }
        return null;
    }

    public static Type parse(String name) {
        if (name == null) return null;
        String n = name.trim().toUpperCase(Locale.ROOT);
        if (n.isEmpty()) return null;

        switch (n) {
            case "BED" -> { return Type.BED; }
            case "ENDER_CRYSTAL" -> n = "END_CRYSTAL";
            case "FIREWORK" -> n = "FIREWORK_ROCKET";
            case "MINECART_TNT" -> n = "TNT_MINECART";
            default -> { }
        }
        return typeOf(Material.matchMaterial(n));
    }

    public static String supportedNames() {
        return "TNT, TNT_MINECART, END_CRYSTAL (ENDER_CRYSTAL), RESPAWN_ANCHOR, BED (or any *_BED), FIREWORK_ROCKET";
    }
}