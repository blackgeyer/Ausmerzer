package org.blackgeyer.ausmerzer.itemban;

import org.blackgeyer.ausmerzer.config.ConfigLoader;
import org.blackgeyer.ausmerzer.engine.ScheduleManager;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRecipeDiscoverEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public final class RecipeBan implements Listener {

    private volatile Set<NamespacedKey> hiddenKeys = Set.of();

    public void rebuild() {
        Set<NamespacedKey> keys = new HashSet<>(ConfigLoader.BANNED_RECIPE_KEYS);
        Set<Material> results = ConfigLoader.BANNED_RECIPE_RESULTS;

        if (!results.isEmpty()) {
            Iterator<Recipe> it = Bukkit.recipeIterator();
            while (it.hasNext()) {
                try {
                    Recipe recipe = it.next();
                    if (recipe instanceof Keyed keyed && results.contains(recipe.getResult().getType())) {
                        keys.add(keyed.getKey());
                    }
                } catch (Exception ignored) {
                }
            }
        }
        hiddenKeys = Set.copyOf(keys);
    }

    public void refresh() {
        rebuild();
        for (Player player : Bukkit.getOnlinePlayers()) {
            ScheduleManager.get().runAtEntity(player, () -> hideFrom(player));
        }
    }

    public void hideFrom(Player player) {
        if (!ConfigLoader.RECIPEBAN_ENABLED) return;
        
        if (player.hasPermission("asm.bypass")) return;

        Set<NamespacedKey> keys = hiddenKeys;
        if (!keys.isEmpty()) player.undiscoverRecipes(keys);
    }

    private static boolean isBannedResult(ItemStack result) {
        return result != null && ConfigLoader.BANNED_RECIPE_RESULTS.contains(result.getType());
    }

    private static boolean isBanned(Recipe recipe) {
        if (!ConfigLoader.RECIPEBAN_ENABLED || recipe == null) return false;
        if (isBannedResult(recipe.getResult())) return true;
        return recipe instanceof Keyed keyed && ConfigLoader.BANNED_RECIPE_KEYS.contains(keyed.getKey());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (!isBanned(event.getRecipe())) return;
        
        if (event.getView().getPlayer() instanceof Player player && player.hasPermission("asm.bypass")) {
            return;
        }

        event.getInventory().setResult(null);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCraft(CraftItemEvent event) {
        if (!isBanned(event.getRecipe())) return;

        if (event.getWhoClicked() instanceof Player player && player.hasPermission("asm.bypass")) {
            return;
        }

        event.setCancelled(true);
        event.getInventory().setResult(null);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        if (!ConfigLoader.RECIPEBAN_ENABLED) return;
        if (!isBannedResult(event.getResult()) && !isBanned(event.getInventory().getRecipe())) return;

        if (event.getView().getPlayer() instanceof Player player && player.hasPermission("asm.bypass")) {
            return;
        }

        event.setResult(null);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDiscover(PlayerRecipeDiscoverEvent event) {
        if (!ConfigLoader.RECIPEBAN_ENABLED) return;
        
        Player player = event.getPlayer();
        if (player.hasPermission("asm.bypass")) return;

        if (hiddenKeys.contains(event.getRecipe())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        hideFrom(event.getPlayer());
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        rebuild();
    }
}