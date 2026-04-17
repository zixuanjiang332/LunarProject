package jdd.lunarProject.MobsListener;

import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.bukkit.events.MythicSkillEvent;
import jdd.lunarProject.Tool.CombatVariableUtil;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MythicSkillGateListener implements Listener {
    private static final long OUTGOING_ATTACK_WINDOW_MS = 350L;
    private final Map<UUID, Long> recentOutgoingAttacks = new HashMap<>();

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerOutgoingAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        if (CombatVariableUtil.isStatusDamage(player)) {
            return;
        }
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                && event.getCause() != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            return;
        }
        recentOutgoingAttacks.put(player.getUniqueId(), System.currentTimeMillis() + OUTGOING_ATTACK_WINDOW_MS);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMythicSkill(MythicSkillEvent event) {
        Entity caster = resolveCasterEntity(event.getSkillMetadata());
        if (caster == null) {
            return;
        }

        String internalName = event.getSkill() == null ? "" : event.getSkill().getInternalName();
        if (caster instanceof Player player && isAttackTriggeredSkill(internalName) && !hasRecentOutgoingAttack(player)) {
            event.setCancelled(true);
            return;
        }

        if (!isSkillLocked(caster)) {
            return;
        }

        if (internalName.startsWith("System_")) {
            return;
        }

        event.setCancelled(true);
    }

    private boolean isSkillLocked(Entity caster) {
        return CombatVariableUtil.getInt(caster, "stagger_stage", 0) > 0
                || CombatVariableUtil.getInt(caster, "silence_stack", 0) > 0
                || CombatVariableUtil.getInt(caster, "stagger_silence_lock", 0) > 0;
    }

    private boolean isAttackTriggeredSkill(String internalName) {
        if (internalName == null || internalName.isBlank()) {
            return false;
        }
        return internalName.endsWith("_OnAttack")
                || (internalName.startsWith("Skill_Weapon_") && internalName.endsWith("_Hit"));
    }

    private boolean hasRecentOutgoingAttack(Player player) {
        long expiresAt = recentOutgoingAttacks.getOrDefault(player.getUniqueId(), 0L);
        if (expiresAt < System.currentTimeMillis()) {
            recentOutgoingAttacks.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    private Entity resolveCasterEntity(SkillMetadata skillMetadata) {
        if (skillMetadata == null || skillMetadata.getCaster() == null || skillMetadata.getCaster().getEntity() == null) {
            return null;
        }

        Object abstractEntity = skillMetadata.getCaster().getEntity();
        try {
            Method method = abstractEntity.getClass().getMethod("getBukkitEntity");
            Object result = method.invoke(abstractEntity);
            if (result instanceof Entity entity) {
                return entity;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }
}
