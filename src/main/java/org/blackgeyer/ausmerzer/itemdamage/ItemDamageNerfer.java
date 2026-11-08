package org.blackgeyer.ausmerzer.itemdamage;

import org.blackgeyer.ausmerzer.config.ConfigLoader;
import org.bukkit.Material;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public final class ItemDamageNerfer implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!ConfigLoader.ITEM_NERF_ENABLED) return;

        Map<Material, Double> caps = ConfigLoader.ITEM_NERFS;
        if (caps.isEmpty()) return;

        Material weapon = resolveWeapon(event);
        if (weapon == null) return;

        Double cap = caps.get(weapon);
        if (cap == null) return;

        if (ConfigLoader.ITEM_NERF_RAW) {
            if (event.getDamage() > cap) event.setDamage(cap);
            return;
        }

        for (int i = 0; i < 6 && event.getFinalDamage() > cap + 0.01; i++) {
            double fin = event.getFinalDamage();
            event.setDamage(event.getDamage() * cap / fin);
        }
    }

    private static Material resolveWeapon(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        DamageCause cause = event.getCause();

        if (cause == DamageCause.ENTITY_ATTACK || cause == DamageCause.ENTITY_SWEEP_ATTACK) {
            if (damager instanceof Player player) {
                ItemStack hand = player.getInventory().getItemInMainHand();
                return hand == null ? Material.AIR : hand.getType();
            }
            return null;
        }

        if (cause == DamageCause.PROJECTILE && damager instanceof AbstractArrow arrow
                && arrow.getShooter() instanceof Player) {
            ItemStack item = arrow instanceof Trident trident ? trident.getItemStack() : arrow.getWeapon();
            return item == null ? null : item.getType();
        }

        return null;
    }
}
