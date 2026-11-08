package org.blackgeyer.ausmerzer.itemban;

import com.tcoded.folialib.wrapper.task.WrappedTask;
import org.blackgeyer.ausmerzer.config.ConfigLoader;
import org.blackgeyer.ausmerzer.config.MessageUtil;
import org.blackgeyer.ausmerzer.engine.ScheduleManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ItemBan implements Listener {

    private static final long SWEEP_PERIOD_TICKS = 100L;

    private final Map<UUID, WrappedTask> sweepTasks = new ConcurrentHashMap<>();

    public static boolean isBanned(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        return ConfigLoader.BANNED_ITEMS.contains(item.getType());
    }

    public void sweep(Player player) {
        if (!ConfigLoader.ITEMBAN_ENABLED) return;

        if (player.hasPermission("asm.bypass")) return;

        Set<Material> banned = ConfigLoader.BANNED_ITEMS;
        if (banned.isEmpty()) return;

        Set<Material> removed = EnumSet.noneOf(Material.class);

        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack != null && banned.contains(stack.getType())) {
                inv.setItem(i, null);
                removed.add(stack.getType());
            }
        }

        ItemStack cursor = player.getItemOnCursor();
        if (cursor != null && banned.contains(cursor.getType())) {
            player.setItemOnCursor(null);
            removed.add(cursor.getType());
        }

        for (Material material : removed) {
            notifyBanned(player, material);
        }
    }

    public void track(Player player) {
        WrappedTask task = ScheduleManager.get().runAtEntityTimer(
                player, () -> sweep(player), SWEEP_PERIOD_TICKS, SWEEP_PERIOD_TICKS);
        WrappedTask old = sweepTasks.put(player.getUniqueId(), task);
        if (old != null) old.cancel();
    }

    public void untrack(Player player) {
        WrappedTask task = sweepTasks.remove(player.getUniqueId());
        if (task != null) task.cancel();
    }

    private void sweepNowAndNextTick(Player player) {
        if (!ConfigLoader.ITEMBAN_ENABLED || ConfigLoader.BANNED_ITEMS.isEmpty()) return;
        sweep(player);
        ScheduleManager.get().runAtEntity(player, () -> sweep(player));
    }

    private void notifyBanned(Player player, Material material) {
        if (!ConfigLoader.ITEM_CLEAR_NOTIFY_ENABLED) return;
        MessageUtil.send(player, ConfigLoader.ITEM_CLEAR_NOTIFY_MSG.replace("%banned%", prettyName(material)));
    }

    private static String prettyName(Material material) {
        StringBuilder sb = new StringBuilder();
        for (String part : material.name().toLowerCase(Locale.ROOT).split("_")) {
            if (part.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!ConfigLoader.ITEMBAN_ENABLED) return;
        if (!(event.getEntity() instanceof Player player)) return;

        ItemStack stack = event.getItem().getItemStack();
        if (!isBanned(stack)) return;

        if (player.hasPermission("asm.bypass")) {
            return;
        }

        event.setCancelled(true);
        event.getItem().remove();
        notifyBanned(player, stack.getType());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!ConfigLoader.ITEMBAN_ENABLED) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack currentItem = event.getCurrentItem();
        ItemStack cursorItem = event.getCursor();

        boolean bannedFound = isBanned(currentItem) || isBanned(cursorItem);
        if (!bannedFound) {
            sweepNowAndNextTick(player);
            return;
        }

        if (player.hasPermission("asm.bypass")) {
            sweepNowAndNextTick(player);
            return;
        }

        event.setCancelled(true);
        if (isBanned(currentItem)) {
            event.setCurrentItem(null);
            notifyBanned(player, currentItem.getType());
        }
        if (isBanned(cursorItem)) {
            player.setItemOnCursor(null);
            notifyBanned(player, cursorItem.getType());
        }
        
        sweepNowAndNextTick(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) sweepNowAndNextTick(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreative(InventoryCreativeEvent event) {
        if (!ConfigLoader.ITEMBAN_ENABLED) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack cursor = event.getCursor();
        if (isBanned(cursor)) {
            if (player.hasPermission("asm.bypass")) {
                sweepNowAndNextTick(player);
                return;
            }
            event.setCancelled(true);
            notifyBanned(player, cursor.getType());
        }
        sweepNowAndNextTick(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        sweep(event.getPlayer());
        track(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        untrack(event.getPlayer());
    }
}