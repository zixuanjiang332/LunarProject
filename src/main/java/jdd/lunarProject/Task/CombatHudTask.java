package jdd.lunarProject.Task;

import jdd.lunarProject.Tool.CombatVariableUtil;
import jdd.lunarProject.Tool.YellowBarUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class CombatHudTask extends BukkitRunnable {
    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!CombatVariableUtil.isManaged(player) || !YellowBarUtil.hasYellowBar(player)) {
                continue;
            }

            player.sendActionBar(Component.text(buildHudLine(player)));
        }
    }

    private String buildHudLine(Player player) {
        double currentHealth = player.getHealth();
        double maxHealth = player.getAttribute(Attribute.MAX_HEALTH) == null
                ? currentHealth
                : player.getAttribute(Attribute.MAX_HEALTH).getValue();
        double currentYellow = YellowBarUtil.getCurrent(player);
        double maxYellow = Math.max(1.0, YellowBarUtil.getMax(player));
        int sanity = CombatVariableUtil.getInt(player, "sanity", 50);

        return "§c生命 " + compactValue(currentHealth) + "/" + compactValue(maxHealth)
                + "  §b理智 " + sanity
                + "  §e混乱抗性 " + compactValue(currentYellow) + "/" + compactValue(maxYellow);
    }

    private String compactValue(double value) {
        int rounded = (int) Math.round(value);
        if (rounded >= 1000) {
            return String.format("%.1fk", rounded / 1000.0);
        }
        return Integer.toString(rounded);
    }
}
