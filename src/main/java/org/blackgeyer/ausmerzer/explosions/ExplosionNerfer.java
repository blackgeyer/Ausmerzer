package org.blackgeyer.ausmerzer.explosions;

import org.blackgeyer.ausmerzer.config.ConfigLoader;
import org.blackgeyer.ausmerzer.explosions.ExplosiveItems.Type;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Map;

public final class ExplosionNerfer implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!ConfigLoader.EXPLOSIVE_NERFS_ENABLED) return;

        Double cap = resolveCap(event);
        if (cap == null) return;

        if (ConfigLoader.EXPLOSIVE_NERFS_RAW) {
            if (event.getDamage() > cap) event.setDamage(cap);
            return;
        }

        for (int i = 0; i < 6 && event.getFinalDamage() > cap + 0.01; i++) {
            double fin = event.getFinalDamage();
            event.setDamage(event.getDamage() * cap / fin);
        }
    }

    private static Double resolveCap(EntityDamageEvent event) {
        Map<Type, Double> caps = ConfigLoader.EXPLOSIVE_NERFS;
        if (caps.isEmpty()) return null;

        DamageSource source = event.getDamageSource();

        Type type = typeOfEntity(source.getDirectEntity());
        if (type == null && event instanceof EntityDamageByEntityEvent byEntity) {
            type = typeOfEntity(byEntity.getDamager());
        }
        if (type != null) return caps.get(type);

        if (DamageType.BAD_RESPAWN_POINT.equals(source.getDamageType())) {
            Material material = damagerMaterial(event);
            if (material != null && !material.isAir()) {
                Type blockType = ExplosiveItems.typeOf(material);
                if (blockType == Type.RESPAWN_ANCHOR || blockType == Type.BED) return caps.get(blockType);
                return null;
            }
            return min(caps.get(Type.RESPAWN_ANCHOR), caps.get(Type.BED));
        }
        return null;
    }

    private static Type typeOfEntity(Entity entity) {
        if (entity instanceof EnderCrystal) return Type.END_CRYSTAL;
        if (entity instanceof TNTPrimed) return Type.TNT;
        if (entity instanceof ExplosiveMinecart) return Type.TNT_MINECART;
        if (entity instanceof Firework) return Type.FIREWORK_ROCKET;
        return null;
    }

    private static Material damagerMaterial(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByBlockEvent byBlock)) return null;

        BlockState state = byBlock.getDamagerBlockState();
        if (state != null) return state.getType();

        Block block = byBlock.getDamager();
        return block != null ? block.getType() : null;
    }

    private static Double min(Double a, Double b) {
        if (a == null) return b;
        if (b == null) return a;
        return Math.min(a, b);
    }
}