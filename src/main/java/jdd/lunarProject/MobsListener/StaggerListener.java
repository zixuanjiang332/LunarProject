package jdd.lunarProject.MobsListener;

import io.lumine.mythic.bukkit.MythicBukkit;
import jdd.lunarProject.Game.StaggerManager;
import jdd.lunarProject.Tool.YellowBarUtil;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

public class StaggerListener implements Listener {
    private final StaggerManager staggerManager;

    public StaggerListener(StaggerManager staggerManager) {
        this.staggerManager = staggerManager;
    }

    private StaggerManager.StaggerConfig getPlayerStaggerConfig(Player player) {
        return staggerManager.getConfigForClass(getPlayerClass(player));
    }

    private String getPlayerClass(Player player) {
        var variables = MythicBukkit.inst().getPlayerManager().getProfile(player).getVariables();
        if (variables.has("class_name")) {
            return variables.getString("class_name");
        }
        return "None";
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        // Yellow bar is now the primary stagger source.
        // Legacy health-threshold stagger only remains as a fallback if the bar
        // has not been initialized for this player yet.
        if (YellowBarUtil.hasYellowBar(player)) {
            return;
        }

        StaggerManager.StaggerConfig config = getPlayerStaggerConfig(player);
        if (config == null || player.getAttribute(Attribute.MAX_HEALTH) == null) {
            return;
        }

        double maxHealth = player.getAttribute(Attribute.MAX_HEALTH).getValue();
        double currentHealth = player.getHealth();
        double expectedHealth = currentHealth - event.getFinalDamage();
        double currentPct = currentHealth / maxHealth;
        double expectedPct = expectedHealth / maxHealth;

        if (config.t2 > 0 && currentPct > config.t2 && expectedPct <= config.t2) {
            MythicBukkit.inst().getAPIHelper().castSkill(player, "System_Apply_Stagger_T2", player, player.getLocation(), null, null, 1.0f);
            return;
        }

        if (config.t1 > 0 && currentPct > config.t1 && expectedPct <= config.t1) {
            MythicBukkit.inst().getAPIHelper().castSkill(player, "System_Apply_Stagger_T1", player, player.getLocation(), null, null, 1.0f);
        }
    }
}
