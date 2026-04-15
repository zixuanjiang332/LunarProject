package jdd.lunarProject.MobsListener;

import io.lumine.mythic.bukkit.events.MythicMobSpawnEvent;
import jdd.lunarProject.Game.PlayerClassManager;
import jdd.lunarProject.LunarProject;
import jdd.lunarProject.Tool.YellowBarUtil;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class TestMobListener implements Listener {

    @EventHandler
    public void onMobSpawn(MythicMobSpawnEvent event) {
        org.bukkit.entity.Entity bukkitEntity = event.getMob().getEntity().getBukkitEntity();
        if (!(bukkitEntity instanceof org.bukkit.entity.LivingEntity livingEntity)) {
            return;
        }

        if (LunarProject.getInstance().isDevMobScalingEnabled()) {
            AttributeInstance maxHealth = livingEntity.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealth != null) {
                double scaledHealth = livingEntity.getHealth() * 1000.0;
                maxHealth.setBaseValue(scaledHealth);
                livingEntity.setHealth(scaledHealth);
            }
        }

        YellowBarUtil.refill(livingEntity);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        applyPlayerHealthModel(event.getPlayer());
    }

    public static void applyPlayerHealthModel(Player player) {
        PlayerClassManager.clearClassState(player);
    }
}
