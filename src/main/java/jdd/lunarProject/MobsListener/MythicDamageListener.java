package jdd.lunarProject.MobsListener;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.event.Listener;

public class MythicDamageListener implements Listener {
    public static final double CRIT_CHANCE = 0.00;
    public static final double CRIT_DAMAGE_MULTIPLIER = 2.0;

    public MythicDamageListener() {
    }

    public static void playCriticalFeedback(Entity target) {
        if (target == null || target.getWorld() == null) {
            return;
        }

        target.getWorld().spawnParticle(
                Particle.ENCHANTED_HIT,
                target.getLocation().add(0.0, 1.0, 0.0),
                15,
                0.5,
                0.5,
                0.5,
                0.1
        );
        target.getWorld().playSound(
                target.getLocation(),
                Sound.ENTITY_PLAYER_ATTACK_CRIT,
                1.0f,
                1.2f
        );
    }
}
