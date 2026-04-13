package jdd.lunarProject.MobsListener;

import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.skills.variables.VariableRegistry;
import jdd.lunarProject.Tool.CombatVariableUtil;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PoiseCritManager implements Listener {
    private static final Map<UUID, Long> poiseCountCooldowns = new HashMap<>();

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDealDamage(EntityDamageByEntityEvent event) {
        Entity attacker = event.getDamager();
        if (CombatVariableUtil.isStatusDamage(attacker)) {
            return;
        }

        // Breath still uses the live Mythic poise variables so existing item skills
        // do not need to be renamed. We simply mirror them to breath_* aliases.
        CombatVariableUtil.settleBreath(attacker);

        boolean playerAttacker = attacker instanceof Player;
        VariableRegistry variables;
        int count;
        int intensity;
        double critRate;
        double critDamage;

        if (playerAttacker) {
            Player player = (Player) attacker;
            variables = MythicBukkit.inst().getPlayerManager().getProfile(player).getVariables();
            count = variables.getInt(CombatVariableUtil.VAR_POISE_COUNT);
            intensity = variables.getInt(CombatVariableUtil.VAR_POISE_INTENSITY);
            if (count <= 0 || intensity <= 0) {
                return;
            }

            critRate = Math.min(1.0, intensity * 0.025);
            critDamage = 1.30;
            if (intensity > 40) {
                critDamage += ((intensity - 40) * 0.01);
            }
        } else {
            var activeMob = MythicBukkit.inst().getMobManager().getActiveMob(attacker.getUniqueId());
            if (activeMob.isEmpty()) {
                return;
            }

            variables = activeMob.get().getVariables();
            count = variables.getInt(CombatVariableUtil.VAR_POISE_COUNT);
            intensity = variables.getInt(CombatVariableUtil.VAR_POISE_INTENSITY);
            if (count <= 0 || intensity <= 0) {
                return;
            }

            critRate = Math.min(0.8, intensity * 0.04);
            critDamage = 1.20;
            if (intensity > 20) {
                critDamage += ((intensity - 20) * 0.01);
            }
        }

        if (Math.random() >= critRate) {
            return;
        }

        event.setDamage(event.getDamage() * critDamage);

        // A successful crit only consumes one breath layer every 0.3 seconds.
        UUID attackerId = attacker.getUniqueId();
        long currentTime = System.currentTimeMillis();
        long nextAllowedTime = poiseCountCooldowns.getOrDefault(attackerId, 0L);
        if (currentTime >= nextAllowedTime) {
            int newCount = count - 1;
            variables.putInt(CombatVariableUtil.VAR_POISE_COUNT, newCount);
            if (newCount <= 0) {
                variables.putInt(CombatVariableUtil.VAR_POISE_INTENSITY, 0);
            }

            poiseCountCooldowns.put(attackerId, currentTime + 300L);
            CombatVariableUtil.settleBreath(attacker);
            MythicBukkit.inst().getAPIHelper().castSkill(attacker, "System_Settle_Poise");
        }

        MythicBukkit.inst().getAPIHelper().castSkill(attacker, "System_Apply_Poise_Crit_Effect");
    }
}
