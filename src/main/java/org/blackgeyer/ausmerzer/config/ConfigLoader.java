package org.blackgeyer.ausmerzer.config;

import org.blackgeyer.ausmerzer.explosions.ExplosiveItems;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public final class ConfigLoader {

    public enum Result { SUCCESS, WITH_ERRORS, FAILED }

    // explosive-nerfs
    public static volatile boolean EXPLOSIVE_NERFS_ENABLED = true;
    public static volatile Map<ExplosiveItems.Type, Double> EXPLOSIVE_NERFS = Map.of();
    public static volatile boolean EXPLOSIVE_NERFS_RAW = false;

    // nerf-item-damage
    public static volatile boolean ITEM_NERF_ENABLED = true;
    public static volatile Map<Material, Double> ITEM_NERFS = Map.of();
    public static volatile boolean ITEM_NERF_RAW = false;

    // itemban
    public static volatile boolean ITEMBAN_ENABLED = true;
    public static volatile Set<Material> BANNED_ITEMS = Set.of();

    // recipeban
    public static volatile boolean RECIPEBAN_ENABLED = true;
    public static volatile Set<Material> BANNED_RECIPE_RESULTS = Set.of();
    public static volatile Set<NamespacedKey> BANNED_RECIPE_KEYS = Set.of();

    // item-clear-notify
    public static volatile boolean ITEM_CLEAR_NOTIFY_ENABLED = true;
    public static volatile String ITEM_CLEAR_NOTIFY_MSG = "<red>%banned% item is banned.";

    // messages
    public static volatile String MSG_NO_PERMISSION = "<red>Access denied.";
    public static volatile String MSG_INVALID = "<yellow>Invalid command. Usage: /asm reload";
    public static volatile String MSG_RELOAD_SUCCESS = "<green>Configuration reload was successful.";
    public static volatile String MSG_RELOAD_FAIL = "<red>Configuration reload failed. Check the console for details.";
    public static volatile String MSG_RELOAD_WITH_ERRORS = "<yellow>Configuration reload executed but with errors. Check the console for details.";

    private ConfigLoader() {}

    public static Result load(JavaPlugin plugin) {
        Logger log = plugin.getLogger();
        File file = new File(plugin.getDataFolder(), "config.yml");
        YamlConfiguration cfg = new YamlConfiguration();

        try {
            cfg.load(file);
        } catch (Exception ex) {
            log.severe("Cannot read config.yml: " + ex.getMessage());
            return Result.FAILED;
        }

        int errors = 0;

        Map<ExplosiveItems.Type, Double> nerfs = new EnumMap<>(ExplosiveItems.Type.class);
        for (String raw : cfg.getStringList("explosive-nerfs.nerfed")) {
            int idx = raw.lastIndexOf(':');
            if (idx <= 0 || idx == raw.length() - 1) {
                log.warning("[explosive-nerfs] Invalid entry '" + raw + "', expected TYPE:DAMAGE.");
                errors++;
                continue;
            }
            String name = raw.substring(0, idx).trim();
            ExplosiveItems.Type target = ExplosiveItems.parse(name);
            if (target == null) {
                log.warning("[explosive-nerfs] Unsupported type '" + name + "' in '" + raw
                        + "'. Supported: " + ExplosiveItems.supportedNames());
                errors++;
                continue;
            }
            double value;
            try {
                value = Double.parseDouble(raw.substring(idx + 1).trim());
            } catch (NumberFormatException ex) {
                log.warning("[explosive-nerfs] Invalid number in '" + raw + "'.");
                errors++;
                continue;
            }
            if (!Double.isFinite(value) || value < 0) {
                log.warning("[explosive-nerfs] Damage must be a non-negative number in '" + raw + "'.");
                errors++;
                continue;
            }
            nerfs.put(target, value);
        }

        Set<Material> bannedItems = EnumSet.noneOf(Material.class);
        for (String raw : cfg.getStringList("itemban.banned-items")) {
            if (raw == null || raw.isBlank()) continue;
            Material m = Material.matchMaterial(raw.trim());
            if (m == null || !m.isItem()) {
                log.warning("[itemban] Unknown item '" + raw + "'.");
                errors++;
                continue;
            }
            bannedItems.add(m);
        }

        Set<Material> recipeResults = EnumSet.noneOf(Material.class);
        Set<NamespacedKey> recipeKeys = new HashSet<>();
        for (String raw : cfg.getStringList("recipeban.banned-recipes")) {
            if (raw == null || raw.isBlank()) continue;
            String entry = raw.trim();
            if (entry.contains(":")) {
                NamespacedKey key = NamespacedKey.fromString(entry.toLowerCase(Locale.ROOT));
                if (key == null) {
                    log.warning("[recipeban] Invalid recipe key '" + raw + "'.");
                    errors++;
                    continue;
                }
                recipeKeys.add(key);
            } else {
                Material m = Material.matchMaterial(entry);
                if (m == null || !m.isItem()) {
                    log.warning("[recipeban] Unknown item '" + raw + "'.");
                    errors++;
                    continue;
                }
                recipeResults.add(m);
            }
        }

        Map<Material, Double> explicitItemNerfs = new EnumMap<>(Material.class);
        Map<Material, Double> wildcardItemNerfs = new EnumMap<>(Material.class);
        for (String entry : cfg.getStringList("nerf-item-damage.nerfed-items")) {
            if (entry == null || entry.isBlank()) continue;
            int idx = entry.lastIndexOf(':');
            if (idx <= 0 || idx == entry.length() - 1) {
                log.warning("[nerf-item-damage] Invalid entry '" + entry + "', expected ITEM:DAMAGE.");
                errors++;
                continue;
            }
            String name = entry.substring(0, idx).trim();
            double value;
            try {
                value = Double.parseDouble(entry.substring(idx + 1).trim());
            } catch (NumberFormatException ex) {
                log.warning("[nerf-item-damage] Invalid number in '" + entry + "'.");
                errors++;
                continue;
            }
            if (!Double.isFinite(value) || value < 0) {
                log.warning("[nerf-item-damage] Damage must be a non-negative number in '" + entry + "'.");
                errors++;
                continue;
            }

            if (name.equalsIgnoreCase("FIST") || name.equalsIgnoreCase("HAND")) {
                explicitItemNerfs.put(Material.AIR, value);
                continue;
            }

            if (name.startsWith("*") || name.endsWith("*")) {
                boolean starts = name.startsWith("*");
                boolean ends = name.endsWith("*");
                String core = name.replace("*", "").toUpperCase(Locale.ROOT);
                int matched = 0;
                if (!core.isEmpty()) {
                    for (Material m : Material.values()) {
                        if (m.isLegacy() || !m.isItem()) continue;
                        String mn = m.name();
                        boolean hit = starts && ends ? mn.contains(core)
                                : starts ? mn.endsWith(core)
                                : mn.startsWith(core);
                        if (hit) {
                            wildcardItemNerfs.put(m, value);
                            matched++;
                        }
                    }
                }
                if (matched == 0) {
                    log.warning("[nerf-item-damage] Pattern '" + name + "' matched no items.");
                    errors++;
                }
                continue;
            }

            Material m = Material.matchMaterial(name);
            if (m == null || !m.isItem()) {
                log.warning("[nerf-item-damage] Unknown item '" + name + "' in '" + entry + "'.");
                errors++;
                continue;
            }
            explicitItemNerfs.put(m, value);
        }
        Map<Material, Double> itemNerfs = new EnumMap<>(Material.class);
        itemNerfs.putAll(wildcardItemNerfs);
        itemNerfs.putAll(explicitItemNerfs);

        String itemMode = cfg.getString("nerf-item-damage.damage-mode", "FINAL").trim().toUpperCase(Locale.ROOT);
        boolean itemRaw = false;
        if (itemMode.equals("RAW")) {
            itemRaw = true;
        } else if (!itemMode.equals("FINAL")) {
            log.warning("[nerf-item-damage] Invalid damage-mode '" + itemMode + "', expected RAW or FINAL. Using FINAL.");
            errors++;
        }

        String mode = cfg.getString("explosive-nerfs.damage-mode", "FINAL").trim().toUpperCase(Locale.ROOT);
        boolean raw = false;
        if (mode.equals("RAW")) {
            raw = true;
        } else if (!mode.equals("FINAL")) {
            log.warning("[explosive-nerfs] Invalid damage-mode '" + mode + "', expected RAW or FINAL. Using FINAL.");
            errors++;
        }

        EXPLOSIVE_NERFS_ENABLED = cfg.getBoolean("explosive-nerfs.is-enabled", true);
        EXPLOSIVE_NERFS = Map.copyOf(nerfs);
        EXPLOSIVE_NERFS_RAW = raw;

        ITEM_NERF_ENABLED = cfg.getBoolean("nerf-item-damage.is-enabled", true);
        ITEM_NERFS = Map.copyOf(itemNerfs);
        ITEM_NERF_RAW = itemRaw;

        ITEMBAN_ENABLED = cfg.getBoolean("itemban.is-enabled", true);
        BANNED_ITEMS = Set.copyOf(bannedItems);

        RECIPEBAN_ENABLED = cfg.getBoolean("recipeban.is-enabled", true);
        BANNED_RECIPE_RESULTS = Set.copyOf(recipeResults);
        BANNED_RECIPE_KEYS = Set.copyOf(recipeKeys);

        ITEM_CLEAR_NOTIFY_ENABLED = cfg.getBoolean("item-clear-notify.is-enabled", true);
        ITEM_CLEAR_NOTIFY_MSG = cfg.getString("item-clear-notify.notify-msg", ITEM_CLEAR_NOTIFY_MSG);

        MSG_NO_PERMISSION = cfg.getString("messages.no-permission", MSG_NO_PERMISSION);
        MSG_INVALID = cfg.getString("messages.invalid", MSG_INVALID);
        MSG_RELOAD_SUCCESS = cfg.getString("messages.reload-scf", MSG_RELOAD_SUCCESS);
        MSG_RELOAD_FAIL = cfg.getString("messages.reload-fail", MSG_RELOAD_FAIL);
        MSG_RELOAD_WITH_ERRORS = cfg.getString("messages.reload-with-errors", MSG_RELOAD_WITH_ERRORS);

        return errors == 0 ? Result.SUCCESS : Result.WITH_ERRORS;
    }
}