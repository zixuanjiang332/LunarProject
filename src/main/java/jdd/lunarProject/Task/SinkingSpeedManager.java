package jdd.lunarProject.Task;
import io.lumine.mythic.bukkit.MythicBukkit;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import jdd.lunarProject.Game.GameManager;

public class SinkingSpeedManager extends BukkitRunnable {
    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            var profile = MythicBukkit.inst().getPlayerManager().getProfile(player);
            if (profile == null) {
                continue;
            }

            if (GameManager.getPlayerGame(player.getUniqueId()) == null) {
                // Outside a run we only clear the temporary punish slow and restore
                // vanilla walk speed. Sinking intensity itself stays owned by Mythic/YAML.
                player.setWalkSpeed(0.2f);
                var outsideVariables = profile.getVariables();
                outsideVariables.putDouble("sinking_punish_slow", 0.0);
                continue;
            }

            var variables = profile.getVariables();
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
