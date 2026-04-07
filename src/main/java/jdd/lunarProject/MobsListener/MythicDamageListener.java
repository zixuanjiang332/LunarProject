package jdd.lunarProject.MobsListener;

import io.lumine.mythic.bukkit.events.MythicDamageEvent;
import jdd.lunarProject.LunarProject;
import jdd.lunarProject.Tool.DamageIndicatorUtil;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.Particle;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.concurrent.ThreadLocalRandom;

public class MythicDamageListener implements Listener {
    private final DamageIndicatorUtil indicatorUtil = new DamageIndicatorUtil();
    public static final double CRIT_CHANCE = 0.00; // 20% 暴击率
    public static final double CRIT_DAMAGE_MULTIPLIER = 2.0;
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMythicDamage(MythicDamageEvent event) {
        double baseDamage = event.getDamage();
        double finalDamage = baseDamage * 1000;
        event.getDamageMetadata().setAmount(finalDamage);
    }
    public static void playCriticalFeedback(Entity target) {
        if (target == null) return;
        target.getWorld().spawnParticle(
                Particle.ENCHANTED_HIT, // <--- 1.21+ 必须使用这个名字
                target.getLocation().add(0, 1, 0),
                15, 0.5, 0.5, 0.5, 0.1
        );
        target.getWorld().playSound(
                target.getLocation(),
                Sound.ENTITY_PLAYER_ATTACK_CRIT,
                1.0f, 1.2f
        );
    }
}

