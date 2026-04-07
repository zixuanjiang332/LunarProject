package jdd.lunarProject.Tool;

import jdd.lunarProject.LunarProject;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class DamageIndicatorUtil {

    /**
     * 在指定位置生成伤害飘字
     * @param targetLoc 目标实体的位置
     * @param damage 造成的最终伤害数值
     * @param isCrit 是否为暴击（用于改变颜色和大小）
     */
    public static void spawnIndicator(Location targetLoc, double damage, boolean isCrit) {
        double offsetX = (ThreadLocalRandom.current().nextDouble() - 0.5) * 1.5;
        double offsetY = ThreadLocalRandom.current().nextDouble() * 1.0 + 1.0;
        double offsetZ = (ThreadLocalRandom.current().nextDouble() - 0.5) * 1.5;
        Location spawnLoc = targetLoc.clone().add(offsetX, offsetY, offsetZ);
        int displayDamage = (int) Math.round(damage);
        String text = isCrit ? "§c§l✩ " + displayDamage + " ✩" : "§7" + displayDamage;

        // 3. 生成 TextDisplay 实体
        spawnLoc.getWorld().spawn(spawnLoc, TextDisplay.class, display -> {
            display.setText(text);
            display.setBillboard(Display.Billboard.CENTER); // 永远面向看它的玩家
            display.setDefaultBackground(false); // 取消丑陋的黑色背景框
            display.setShadowed(true); // 开启文字阴影，更有层次感
            // 如果是暴击，还可以让文字稍微变大一点 (通过矩阵缩放)
            if (isCrit) {
                Transformation transform = display.getTransformation();
                // 放大 1.5 倍，视觉冲击力更强
                transform.getScale().set(1.5f, 1.5f, 1.5f);
                display.setTransformation(transform);
            }
            // 4. 利用 BukkitRunnable 制作向上飘动的动画并销毁
            new BukkitRunnable() {
                int ticks = 0;
                final int MAX_TICKS = 20; // 存在 20 tick (1秒)

                @Override
                public void run() {
                    if (display.isDead() || !display.isValid()) {
                        this.cancel();
                        return;
                    }

                    if (ticks >= MAX_TICKS) {
                        display.remove(); // 1秒后删除实体
                        this.cancel();
                        return;
                    }

                    // 每一 Tick 稍微向上移动一点点
                    display.teleport(display.getLocation().add(0, 0.05, 0));
                    if (ticks > 10) {
                        int alpha = (int) (255 * (1.0 - ((double)(ticks - 10) / 10.0)));
                        display.setTextOpacity((byte) Math.max(0, alpha));
                    }

                    ticks++;
                }
            }.runTaskTimer(LunarProject.getInstance(), 0L, 1L); // 延迟 0 tick 执行，每 1 tick 执行一次
        });
    }
}