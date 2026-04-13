package jdd.lunarProject.MobsListener;

import io.lumine.mythic.bukkit.events.MythicMobSpawnEvent;
import jdd.lunarProject.LunarProject;
import jdd.lunarProject.Tool.YellowBarUtil;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class TestMobListener implements Listener {
    private static final double PLAYER_VISIBLE_HEARTS = 20.0;
    private static final double PLAYER_TRUE_MAX_HEALTH = 20_000.0;

    @EventHandler
    public void onMobSpawn(MythicMobSpawnEvent event) {
        if (!LunarProject.getInstance().isDevMobScalingEnabled()) {
            return;
        }

        org.bukkit.entity.Entity bukkitEntity = event.getMob().getEntity().getBukkitEntity();
        if (!(bukkitEntity instanceof org.bukkit.entity.LivingEntity livingEntity)) {
            return;
        }

        AttributeInstance maxHealth = livingEntity.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }

        double scaledHealth = livingEntity.getHealth() * 1000.0;
        maxHealth.setBaseValue(scaledHealth);
        livingEntity.setHealth(scaledHealth);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        applyPlayerHealthModel(event.getPlayer());
    }

    public static void applyPlayerHealthModel(Player player) {
        player.setHealthScaled(true);

        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            // Players keep an inflated real HP pool for integer-friendly balance tuning,
            // while the client still renders the normal 20-heart UI.
            maxHealth.setBaseValue(PLAYER_TRUE_MAX_HEALTH);
            player.setHealth(maxHealth.getValue());
        }

        player.setHealthScale(PLAYER_VISIBLE_HEARTS);
        YellowBarUtil.refill(player);
    }
}
