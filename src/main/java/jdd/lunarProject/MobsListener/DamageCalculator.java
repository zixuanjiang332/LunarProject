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

import java.util.concurrent.ThreadLocalRandom;

import static jdd.lunarProject.MobsListener.MythicDamageListener.CRIT_CHANCE;
import static jdd.lunarProject.MobsListener.MythicDamageListener.CRIT_DAMAGE_MULTIPLIER;

public class DamageCalculator implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void onGlobalVanillaAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }

        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK &&
                event.getCause() != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            return;
        }

        if (CombatVariableUtil.isStatusDamage(player)) {
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
        if (attackStrength < 0.8F) {
            event.setCancelled(true);
            return;
        }

        event.setDamage(0.0);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Entity attacker = event.getDamager();
        Entity victim = event.getEntity();
        String attackSin = getAttackSin(attacker);
        String attackType = getAttackType(attacker);
        boolean statusDamage = CombatVariableUtil.isStatusDamage(attacker);

        if (attackSin == null || attackType == null) {
            return;
        }

        double sinRes = clampResistance(getEntityResistance(victim, attackSin));
        double typeRes = clampResistance(getEntityResistance(victim, attackType));

        int staggerStage = getStaggerStage(victim);
        if (staggerStage == 1) {
            sinRes = Math.max(sinRes, 1.5);
            typeRes = Math.max(typeRes, 1.5);
        } else if (staggerStage == 2) {
            sinRes = Math.max(sinRes, 2.0);
            typeRes = Math.max(typeRes, 2.0);
        }

        double resMultiplier = Math.max(0.1, 1.0 + (sinRes - 1.0) + (typeRes - 1.0));
        int pureFragility = getEntityFragility(victim, "pure_fragility");
        int typeFragility = getEntityFragility(victim, attackType + "_fragility");
        int sinFragility = getEntityFragility(victim, attackSin + "_fragility");

        if (attacker instanceof Player playerAttacker) {
            Game attackerGame = GameManager.getPlayerGame(playerAttacker.getUniqueId());
            if (attackerGame != null && attackerGame.isRunning()) {
                pureFragility += (int) Math.round(attackerGame.getModifier(playerAttacker.getUniqueId(), BuildModifierType.FRAGILITY_BONUS));
            }
        }

        double fragilityMultiplier = 1.0 + (pureFragility * 0.1) + (typeFragility * 0.1) + (sinFragility * 0.1);

        double baseDamage = event.getDamage();
        if (CombatVariableUtil.isIndependentDot(attackType)) {
            event.setDamage(baseDamage);
            double yellowRemaining = YellowBarUtil.applyDamage(victim, baseDamage);
            clearAttackTags(attacker);
            logCombat("dot damage=" + baseDamage + " yellow=" + String.format("%.2f", yellowRemaining));
            return;
        }

        double finalDamage = baseDamage * resMultiplier * fragilityMultiplier;
        if (attacker instanceof Player playerAttacker) {
            Game attackerGame = GameManager.getPlayerGame(playerAttacker.getUniqueId());
            if (attackerGame != null && attackerGame.isRunning()) {
                finalDamage *= Math.max(0.1, 1.0 + attackerGame.getModifier(playerAttacker.getUniqueId(), BuildModifierType.DAMAGE_DEALT_MULT));
            }
        }
        boolean isCrit = !statusDamage && ThreadLocalRandom.current().nextDouble() < CRIT_CHANCE;
        if (isCrit) {
            finalDamage *= CRIT_DAMAGE_MULTIPLIER;
            MythicDamageListener.playCriticalFeedback(victim);
        }

        Location targetLocation = victim.getLocation();
        if (finalDamage != 0.0) {
            DamageIndicatorUtil.spawnIndicator(targetLocation, finalDamage, isCrit);
        }

        if (victim instanceof Player playerVictim) {
            Game victimGame = GameManager.getPlayerGame(playerVictim.getUniqueId());
            if (victimGame != null && victimGame.isRunning()) {
                finalDamage *= Math.max(0.1, 1.0 + victimGame.getModifier(playerVictim.getUniqueId(), BuildModifierType.DAMAGE_TAKEN_MULT));
            }
        }

        event.setDamage(finalDamage);
        double yellowRemaining = YellowBarUtil.applyDamage(victim, finalDamage);
        clearAttackTags(attacker);
        logCombat(
                "base=" + baseDamage +
                        " res=" + String.format("%.2f", resMultiplier) +
                        " fragility=" + String.format("%.2f", fragilityMultiplier) +
                        " final=" + String.format("%.2f", finalDamage) +
                        " yellow=" + String.format("%.2f", yellowRemaining)
        );
    }

    private double clampResistance(double value) {
        return Math.max(0.5, Math.min(2.0, value));
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
}
