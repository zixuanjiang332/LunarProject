package jdd.lunarProject.Tool;

import jdd.lunarProject.LunarProject;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;

import java.util.concurrent.ThreadLocalRandom;

public final class DamageIndicatorUtil {
    private DamageIndicatorUtil() {
    }

    /**
     * Render a floating damage label that also communicates whether the hit was
     * a crit, a status/sin hit, an independent DOT tick, or a yellow-bar break.
     */
    public static void spawnIndicator(
            Location targetLocation,
            double damage,
            boolean isCrit,
            String attackType,
            boolean statusDamage,
            boolean yellowBreak
    ) {
        if (targetLocation == null || targetLocation.getWorld() == null) {
            return;
        }

        double offsetX = (ThreadLocalRandom.current().nextDouble() - 0.5) * 1.5;
        double offsetY = ThreadLocalRandom.current().nextDouble() * 1.0 + 1.0;
        double offsetZ = (ThreadLocalRandom.current().nextDouble() - 0.5) * 1.5;
        Location spawnLocation = targetLocation.clone().add(offsetX, offsetY, offsetZ);

        String text = buildText(damage, isCrit, attackType, statusDamage, yellowBreak);
        float scale = yellowBreak ? 1.45f : (isCrit ? 1.35f : 1.0f);
        int maxTicks = yellowBreak ? 26 : 20;

        spawnLocation.getWorld().spawn(spawnLocation, TextDisplay.class, display -> {
            display.setText(text);
            display.setBillboard(Display.Billboard.CENTER);
            display.setDefaultBackground(false);
            display.setShadowed(true);

            if (scale != 1.0f) {
                Transformation transform = display.getTransformation();
                transform.getScale().set(scale, scale, scale);
                display.setTransformation(transform);
            }

            new BukkitRunnable() {
                int ticks;

                @Override
                public void run() {
                    if (!display.isValid() || display.isDead()) {
                        cancel();
                        return;
                    }

                    if (ticks >= maxTicks) {
                        display.remove();
                        cancel();
                        return;
                    }

                    display.teleport(display.getLocation().add(0.0, yellowBreak ? 0.06 : 0.05, 0.0));
                    if (ticks > maxTicks / 2) {
                        int alpha = (int) (255 * (1.0 - ((double) (ticks - (maxTicks / 2)) / (maxTicks / 2))));
                        display.setTextOpacity((byte) Math.max(0, alpha));
                    }

                    ticks++;
                }
            }.runTaskTimer(LunarProject.getInstance(), 0L, 1L);
        });
    }

    private static String buildText(double damage, boolean isCrit, String attackType, boolean statusDamage, boolean yellowBreak) {
        if (yellowBreak) {
            return "§6§lBREAK";
        }

        int displayDamage = Math.max(0, (int) Math.round(damage));
        if (CombatVariableUtil.isIndependentDot(attackType)) {
            return "§eDOT §6" + displayDamage;
        }
        if (statusDamage) {
            String label = attackType == null || attackType.isBlank() || CombatVariableUtil.ATTACK_TYPE_NONE.equalsIgnoreCase(attackType)
                    ? "SIN"
                    : attackType.toUpperCase();
            return "§b" + label + " §f" + displayDamage;
        }
        if (isCrit) {
            return "§c§l暴击 §f" + displayDamage;
        }
        return "§7" + displayDamage;
    }
}
