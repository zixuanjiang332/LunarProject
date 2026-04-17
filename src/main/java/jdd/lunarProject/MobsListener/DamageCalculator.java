package jdd.lunarProject.MobsListener;

import io.lumine.mythic.bukkit.MythicBukkit;
import jdd.lunarProject.Build.BuildModifierType;
import jdd.lunarProject.Game.Game;
import jdd.lunarProject.Game.GameManager;
import jdd.lunarProject.LunarProject;
import jdd.lunarProject.Tool.CombatVariableUtil;
import jdd.lunarProject.Tool.DamageIndicatorUtil;
import jdd.lunarProject.Tool.YellowBarUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

public class DamageCalculator implements Listener {
    private static final double FIRST_MULTIPLIER_FLOOR = 0.05;
    private static final double SECOND_MULTIPLIER_FLOOR = 0.0;

    @EventHandler(priority = EventPriority.LOWEST)
    public void onGlobalVanillaAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        if (CombatVariableUtil.isStatusDamage(player)) {
            return;
        }

        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK &&
                event.getCause() != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            return;
        }

        boolean isMythicSkill = false;
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            if (element.getClassName().contains("io.lumine.mythic")) {
                isMythicSkill = true;
                break;
            }
        }

        if (isMythicSkill) {
            return;
        }

        float attackStrength = player.getCooledAttackStrength(0.0F);
        if (attackStrength < 0.9F) {
            event.setCancelled(true);
            return;
        }

        event.setDamage(0.0);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Entity attacker = event.getDamager();
        Entity victim = event.getEntity();
        CombatVariableUtil.PendingSelfStatusHit pendingSelfStatusHit =
                attacker.getUniqueId().equals(victim.getUniqueId())
                        ? CombatVariableUtil.getPendingSelfStatusHit(attacker)
                        : null;

        String attackSin = pendingSelfStatusHit != null
                ? pendingSelfStatusHit.attackSin()
                : getAttackSin(attacker);
        String attackType = pendingSelfStatusHit != null
                ? pendingSelfStatusHit.attackType()
                : getAttackType(attacker);
        boolean statusDamage = pendingSelfStatusHit != null || CombatVariableUtil.isStatusDamage(attacker);

        if (attackSin == null || attackType == null) {
            return;
        }

        double baseDamage = pendingSelfStatusHit != null
                ? pendingSelfStatusHit.amount()
                : event.getDamage();
        if (CombatVariableUtil.isIndependentDot(attackType)) {
            double independentDamage = adjustIndependentDotDamage(victim, attackType, baseDamage);
            event.setDamage(independentDamage);
            YellowBarUtil.DamageResult yellowResult = YellowBarUtil.applyDamage(victim, independentDamage);
            if (yellowResult.broken()) {
                LunarProject.getInstance().getStaggerManager().triggerYellowBarBreak(victim);
            }
            DamageIndicatorUtil.spawnIndicator(targetLocation(victim), independentDamage, false, attackType, statusDamage, yellowResult.broken());
            clearAttackTags(attacker);
            logCombat("dot damage=" + independentDamage + " yellow=" + String.format("%.2f", yellowResult.currentValue()));
            return;
        }

        PoiseCritManager.PendingCrit pendingCrit = PoiseCritManager.consumePendingCrit(attacker);
        boolean isCrit = pendingCrit.critical();
        double critMultiplier = pendingCrit.multiplier();
        DamageBreakdown breakdown = buildDamageBreakdown(
                attacker,
                victim,
                attackType,
                attackSin,
                statusDamage,
                isCrit,
                critMultiplier
        );

        double finalDamage = baseDamage * breakdown.firstCategoryMultiplier() * breakdown.secondCategoryMultiplier();
        if (isCrit && !statusDamage) {
            MythicDamageListener.playCriticalFeedback(victim);
        }

        Location targetLocation = victim.getLocation();
        if (finalDamage != 0.0) {
            DamageIndicatorUtil.spawnIndicator(targetLocation, finalDamage, isCrit, attackType, statusDamage, false);
        }

        event.setDamage(finalDamage);
        YellowBarUtil.DamageResult yellowResult = YellowBarUtil.applyDamage(victim, finalDamage);
        if (yellowResult.broken()) {
            LunarProject.getInstance().getStaggerManager().triggerYellowBarBreak(victim);
            DamageIndicatorUtil.spawnIndicator(targetLocation, 0.0, false, attackType, true, true);
        }
        clearAttackTags(attacker);
        logCombat(
                        "base=" + baseDamage +
                        " firstCategoryMultiplier=" + String.format("%.2f", breakdown.firstCategoryMultiplier()) +
                        " secondCategoryMultiplier=" + String.format("%.2f", breakdown.secondCategoryMultiplier()) +
                        " criticalDamageBonus=" + String.format("%.2f", breakdown.criticalDamageBonus()) +
                        " typeResistanceBonus=" + String.format("%.2f", breakdown.typeResistanceBonus()) +
                        " sinResistanceBonus=" + String.format("%.2f", breakdown.sinResistanceBonus()) +
                        " victimMultiplierBonus=" + String.format("%.2f", breakdown.victimMultiplierBonus()) +
                        " final=" + String.format("%.2f", finalDamage) +
                        " yellow=" + String.format("%.2f", yellowResult.currentValue())
        );
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPendingSelfStatusDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent) {
            return;
        }

        Entity victim = event.getEntity();
        CombatVariableUtil.PendingSelfStatusHit pendingSelfStatusHit = CombatVariableUtil.getPendingSelfStatusHit(victim);
        if (pendingSelfStatusHit == null) {
            return;
        }

        String attackSin = pendingSelfStatusHit.attackSin();
        String attackType = pendingSelfStatusHit.attackType();
        boolean statusDamage = true;
        double baseDamage = pendingSelfStatusHit.amount();

        if (attackSin == null || attackType == null) {
            return;
        }

        if (CombatVariableUtil.isIndependentDot(attackType)) {
            double independentDamage = adjustIndependentDotDamage(victim, attackType, baseDamage);
            event.setDamage(independentDamage);
            YellowBarUtil.DamageResult yellowResult = YellowBarUtil.applyDamage(victim, independentDamage);
            if (yellowResult.broken()) {
                LunarProject.getInstance().getStaggerManager().triggerYellowBarBreak(victim);
            }
            DamageIndicatorUtil.spawnIndicator(targetLocation(victim), independentDamage, false, attackType, statusDamage, yellowResult.broken());
            logCombat("dot damage=" + independentDamage + " yellow=" + String.format("%.2f", yellowResult.currentValue()));
            return;
        }

        DamageBreakdown breakdown = buildDamageBreakdown(
                victim,
                victim,
                attackType,
                attackSin,
                statusDamage,
                false,
                1.0
        );

        double finalDamage = baseDamage * breakdown.firstCategoryMultiplier() * breakdown.secondCategoryMultiplier();
        Location targetLocation = victim.getLocation();
        DamageIndicatorUtil.spawnIndicator(targetLocation, finalDamage, false, attackType, statusDamage, false);
        event.setDamage(finalDamage);
        YellowBarUtil.DamageResult yellowResult = YellowBarUtil.applyDamage(victim, finalDamage);
        if (yellowResult.broken()) {
            LunarProject.getInstance().getStaggerManager().triggerYellowBarBreak(victim);
            DamageIndicatorUtil.spawnIndicator(targetLocation, 0.0, false, attackType, true, true);
        }
        logCombat(
                "base=" + baseDamage +
                        " firstCategoryMultiplier=" + String.format("%.2f", breakdown.firstCategoryMultiplier()) +
                        " secondCategoryMultiplier=" + String.format("%.2f", breakdown.secondCategoryMultiplier()) +
                        " criticalDamageBonus=" + String.format("%.2f", breakdown.criticalDamageBonus()) +
                        " typeResistanceBonus=" + String.format("%.2f", breakdown.typeResistanceBonus()) +
                        " sinResistanceBonus=" + String.format("%.2f", breakdown.sinResistanceBonus()) +
                        " victimMultiplierBonus=" + String.format("%.2f", breakdown.victimMultiplierBonus()) +
                        " final=" + String.format("%.2f", finalDamage) +
                        " yellow=" + String.format("%.2f", yellowResult.currentValue())
        );
    }

    private double adjustIndependentDotDamage(Entity victim, String attackType, double baseDamage) {
        // Player bleed keeps the Mirror Dungeon style rule: if the victim has not
        // moved recently, the bleed tick is halved and rounded up.
        if (victim instanceof Player player
                && CombatVariableUtil.ATTACK_TYPE_BLEED_DOT.equalsIgnoreCase(attackType)
                && !StatusMechanicManager.isPlayerMoving(player)) {
            return Math.ceil(baseDamage / 2.0);
        }
        return baseDamage;
    }

    private Location targetLocation(Entity entity) {
        return entity.getLocation();
    }

    private double clampResistance(double value) {
        return Math.max(0.5, Math.min(2.0, value));
    }

    private DamageBreakdown buildDamageBreakdown(
            Entity attacker,
            Entity victim,
            String attackType,
            String attackSin,
            boolean statusDamage,
            boolean isCrit,
            double critMultiplier
    ) {
        double sinResistanceValue = clampResistance(getEntityResistance(victim, attackSin));
        double typeResistanceValue = clampResistance(getEntityResistance(victim, attackType));

        int staggerStage = getStaggerStage(victim);
        if (staggerStage == 1) {
            sinResistanceValue = Math.max(sinResistanceValue, 1.5);
            typeResistanceValue = Math.max(typeResistanceValue, 1.5);
        } else if (staggerStage == 2) {
            sinResistanceValue = Math.max(sinResistanceValue, 2.0);
            typeResistanceValue = Math.max(typeResistanceValue, 2.0);
        }

        int pureFragility = getEntityFragility(victim, "pure_fragility");
        int typeFragility = getEntityFragility(victim, attackType + "_fragility");
        int sinFragility = getEntityFragility(victim, attackSin + "_fragility");
        pureFragility += resolveFragilityBonus(attacker);

        // The first category only uses the extra value above 100% of the locked
        // crit/resistance multipliers. For example, a 2.0 resistance contributes
        // +1.0 here rather than +2.0.
        double criticalDamageBonus = (!statusDamage && isCrit) ? Math.max(0.0, critMultiplier - 1.0) : 0.0;
        double typeResistanceBonus = typeResistanceValue - 1.0;
        double sinResistanceBonus = sinResistanceValue - 1.0;

        // Fragility stacks are a second-category additive bonus source. Ten total
        // stacks contribute +1.0 here, not +10.
        double victimMultiplierBonus = (pureFragility + typeFragility + sinFragility) * 0.1;

        double firstCategoryMultiplier = Math.max(
                FIRST_MULTIPLIER_FLOOR,
                1.0 + criticalDamageBonus + typeResistanceBonus + sinResistanceBonus
        );
        double secondCategoryMultiplier = Math.max(SECOND_MULTIPLIER_FLOOR, 1.0 + victimMultiplierBonus);
        return new DamageBreakdown(
                firstCategoryMultiplier,
                secondCategoryMultiplier,
                criticalDamageBonus,
                typeResistanceBonus,
                sinResistanceBonus,
                victimMultiplierBonus
        );
    }

    private int resolveFragilityBonus(Entity attacker) {
        if (!(attacker instanceof Player playerAttacker)) {
            return 0;
        }

        Game attackerGame = GameManager.getPlayerGame(playerAttacker.getUniqueId());
        if (attackerGame == null || !attackerGame.isRunning()) {
            return 0;
        }

        return (int) Math.round(attackerGame.getModifier(playerAttacker.getUniqueId(), BuildModifierType.FRAGILITY_BONUS));
    }

    private int getStaggerStage(Entity entity) {
        if (entity instanceof Player player) {
            var variables = MythicBukkit.inst().getPlayerManager().getProfile(player).getVariables();
            return variables.has("stagger_stage") ? variables.getInt("stagger_stage") : 0;
        }

        var activeMob = MythicBukkit.inst().getMobManager().getActiveMob(entity.getUniqueId());
        if (activeMob.isPresent()) {
            var variables = activeMob.get().getVariables();
            return variables.has("stagger_stage") ? variables.getInt("stagger_stage") : 0;
        }
        return 0;
    }

    private void clearAttackTags(Entity attacker) {
        CombatVariableUtil.clearAttackPayload(attacker);
        PoiseCritManager.clearPendingCrit(attacker);
    }

    private int getEntityFragility(Entity entity, String variableName) {
        if (entity instanceof Player player) {
            var variables = MythicBukkit.inst().getPlayerManager().getProfile(player).getVariables();
            return variables.has(variableName) ? variables.getInt(variableName) : 0;
        }

        var activeMob = MythicBukkit.inst().getMobManager().getActiveMob(entity.getUniqueId());
        if (activeMob.isPresent()) {
            var variables = activeMob.get().getVariables();
            return variables.has(variableName) ? variables.getInt(variableName) : 0;
        }
        return 0;
    }

    private double getEntityResistance(Entity entity, String resistanceName) {
        if (entity instanceof Player player) {
            var variables = MythicBukkit.inst().getPlayerManager().getProfile(player).getVariables();
            return variables.has(resistanceName) ? variables.getDouble(resistanceName) : 1.0;
        }

        var activeMob = MythicBukkit.inst().getMobManager().getActiveMob(entity.getUniqueId());
        if (activeMob.isPresent()) {
            var variables = activeMob.get().getVariables();
            return variables.has(resistanceName) ? variables.getDouble(resistanceName) : 1.0;
        }
        return 1.0;
    }

    private String getAttackSin(Entity attacker) {
        return CombatVariableUtil.getString(attacker, CombatVariableUtil.VAR_ATTACK_SIN, "none");
    }

    private String getAttackType(Entity attacker) {
        return CombatVariableUtil.getString(attacker, CombatVariableUtil.VAR_ATTACK_TYPE, "none");
    }

    private void logCombat(String message) {
        if (LunarProject.getInstance().isCombatLogEnabled()) {
            Bukkit.getLogger().info("[LunarProject] " + message);
        }
    }

    private record DamageBreakdown(
            double firstCategoryMultiplier,
            double secondCategoryMultiplier,
            double criticalDamageBonus,
            double typeResistanceBonus,
            double sinResistanceBonus,
            double victimMultiplierBonus
    ) {
    }
}
