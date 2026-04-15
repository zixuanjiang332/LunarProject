package jdd.lunarProject.Task;

import jdd.lunarProject.Tool.CombatVariableUtil;
import jdd.lunarProject.Tool.YellowBarUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
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
        double currentYellow = YellowBarUtil.getCurrent(player);
        double maxYellow = Math.max(1.0, YellowBarUtil.getMax(player));
        int sanity = CombatVariableUtil.getInt(player, "sanity", 50);

        // Keep the always-on ActionBar focused on the two values players need
        // during every room: sanity and the current yellow bar.
        return "§eYELLOW " + compactValue(currentYellow) + "/" + compactValue(maxYellow)
                + "  §bSANITY " + sanity;
    }

    private String compactValue(double value) {
        int rounded = (int) Math.round(value);
        if (rounded >= 1000) {
            return String.format("%.1fk", rounded / 1000.0);
        }
        return Integer.toString(rounded);
    }
}
