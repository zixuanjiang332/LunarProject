package jdd.lunarProject.Task;
import io.lumine.mythic.bukkit.MythicBukkit;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class SinkingSpeedManager extends BukkitRunnable {
    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            var variables = MythicBukkit.inst().getPlayerManager().getProfile(player).getVariables();
            int sinking = variables.has("sinking_intensity") ? variables.getInt("sinking_intensity") : 0;
            double modifier = variables.has("sinking_modifier") ? variables.getDouble("sinking_modifier") : -1.0;
            double intensitySpeedChange = (sinking * 0.01) * modifier;
            double punishSlow = variables.has("sinking_punish_slow") ? variables.getDouble("sinking_punish_slow") : 0.0;
            float finalSpeed = (float) (0.2 + (0.2 * intensitySpeedChange) - (0.2 * punishSlow));
            finalSpeed = Math.max(0.02f, Math.min(1.0f, finalSpeed));
            player.setWalkSpeed(finalSpeed);
        }
    }
}