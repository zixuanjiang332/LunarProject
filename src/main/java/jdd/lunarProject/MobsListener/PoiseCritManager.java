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
    private static final long PENDING_CRIT_TTL_MS = 2_000L;
    private static final Map<UUID, Long> poiseCountCooldowns = new HashMap<>();
    private static final Map<UUID, PendingCrit> pendingCrits = new HashMap<>();

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
            var profile = MythicBukkit.inst().getPlayerManager().getProfile(player);
            if (profile == null) {
                return;
            }

            variables = profile.getVariables();
            count = variables.getInt(CombatVariableUtil.VAR_POISE_COUNT);
            intensity = variables.getInt(CombatVariableUtil.VAR_POISE_INTENSITY);
            if (count <= 0 || intensity <= 0) {
                clearPendingCrit(attacker);
                return;
            }

            critRate = Math.min(1.0, intensity * 0.025);
            // We keep the full crit multiplier here (for example 1.30), and the
            // regular damage formula later only reads the extra part above 1.00.
            critDamage = 1.30;
            if (critRate >= 1.0 && intensity > 40) {
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
                clearPendingCrit(attacker);
                return;
            }

            critRate = Math.min(0.8, intensity * 0.04);
            // Same rule for mobs: store the full multiplier here and let the
            // damage formula convert it into the first-category extra bonus.
            critDamage = 1.20;
            if (critRate >= 0.8 && intensity > 20) {
                critDamage += ((intensity - 20) * 0.01);
            }
        }

        variables.putDouble(CombatVariableUtil.VAR_LAST_CRIT_RATE, critRate);
        variables.putDouble(CombatVariableUtil.VAR_LAST_CRIT_DAMAGE_MULTIPLIER, critDamage);

        boolean critSuccess = Math.random() < critRate;
        pendingCrits.put(attacker.getUniqueId(), new PendingCrit(critSuccess, critSuccess ? critDamage : 1.0, System.currentTimeMillis() + PENDING_CRIT_TTL_MS));

        if (!critSuccess) {
            return;
        }

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

    public static PendingCrit consumePendingCrit(Entity attacker) {
        PendingCrit pendingCrit = pendingCrits.remove(attacker.getUniqueId());
        if (pendingCrit == null) {
            return PendingCrit.NONE;
        }
        if (pendingCrit.expiresAt < System.currentTimeMillis()) {
            return PendingCrit.NONE;
        }
        return pendingCrit;
    }

    public static void clearPendingCrit(Entity attacker) {
        pendingCrits.remove(attacker.getUniqueId());
    }

    public static final class PendingCrit {
        public static final PendingCrit NONE = new PendingCrit(false, 1.0, 0L);

        private final boolean critical;
        private final double multiplier;
        private final long expiresAt;

        private PendingCrit(boolean critical, double multiplier, long expiresAt) {
            this.critical = critical;
            this.multiplier = multiplier;
            this.expiresAt = expiresAt;
        }

        public boolean critical() {
            return critical;
        }

        public double multiplier() {
            return multiplier;
        }
    }
}
