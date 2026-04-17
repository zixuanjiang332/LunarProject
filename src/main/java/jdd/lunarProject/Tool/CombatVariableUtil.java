package jdd.lunarProject.Tool;

import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.skills.variables.VariableRegistry;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class CombatVariableUtil {
    public static final String VAR_ATTACK_TYPE = "attack_type";
    public static final String VAR_ATTACK_SIN = "attack_sin";
    public static final String VAR_DAMAGE_MODE = "damage_mode";

    public static final String DAMAGE_MODE_NORMAL = "normal";
    public static final String DAMAGE_MODE_STATUS = "status";

    public static final String ATTACK_TYPE_NONE = "none";
    public static final String ATTACK_TYPE_DOT = "dot";
    public static final String ATTACK_TYPE_BLEED = "bleed";
    public static final String ATTACK_TYPE_BLEED_DOT = "bleed_dot";
    public static final String ATTACK_TYPE_RUPTURE = "rupture";
    public static final String ATTACK_TYPE_RUPTURE_DOT = "rupture_dot";

    public static final String ATTACK_SIN_NONE = "none";
    public static final String ATTACK_SIN_LUST = "lust";
    public static final String ATTACK_SIN_GLUTTONY = "gluttony";

    public static final String VAR_BLEED_INTENSITY = "bleed_intensity";
    public static final String VAR_RUPTURE_INTENSITY = "rupture_intensity";
    public static final String VAR_RUPTURE_APPLICATION_MARKER = "rupture_application_marker";
    public static final String VAR_POISE_INTENSITY = "poise_intensity";
    public static final String VAR_POISE_COUNT = "poise_count";
    public static final String VAR_BREATH_INTENSITY = "breath_intensity";
    public static final String VAR_BREATH_COUNT = "breath_count";
    public static final String VAR_PENDING_CRIT_MULTIPLIER = "pending_crit_multiplier";
    public static final String VAR_PENDING_CRIT_RESULT = "pending_crit_result";
    public static final String VAR_LAST_CRIT_RATE = "last_crit_rate";
    public static final String VAR_LAST_CRIT_DAMAGE_MULTIPLIER = "last_crit_damage_multiplier";
    private static final Map<UUID, PendingSelfStatusHit> pendingSelfStatusHits = new HashMap<>();

    private CombatVariableUtil() {
    }

    public static Optional<VariableRegistry> getVariables(Entity entity) {
        if (entity instanceof Player player) {
            var profile = MythicBukkit.inst().getPlayerManager().getProfile(player);
            if (profile == null) {
                return Optional.empty();
            }
            return Optional.of(profile.getVariables());
        }

        return MythicBukkit.inst().getMobManager().getActiveMob(entity.getUniqueId())
                .map(activeMob -> activeMob.getVariables());
    }

    public static boolean isManaged(Entity entity) {
        return getVariables(entity).isPresent();
    }

    public static boolean hasVariable(Entity entity, String variableName) {
        return getVariables(entity).map(variables -> variables.has(variableName)).orElse(false);
    }

    public static int getInt(Entity entity, String variableName, int defaultValue) {
        return getVariables(entity)
                .filter(variables -> variables.has(variableName))
                .map(variables -> variables.getInt(variableName))
                .orElse(defaultValue);
    }

    public static double getDouble(Entity entity, String variableName, double defaultValue) {
        return getVariables(entity)
                .filter(variables -> variables.has(variableName))
                .map(variables -> variables.getDouble(variableName))
                .orElse(defaultValue);
    }

    public static String getString(Entity entity, String variableName, String defaultValue) {
        return getVariables(entity)
                .filter(variables -> variables.has(variableName))
                .map(variables -> variables.getString(variableName))
                .orElse(defaultValue);
    }

    public static void setInt(Entity entity, String variableName, int value) {
        getVariables(entity).ifPresent(variables -> variables.putInt(variableName, value));
    }

    public static void setDouble(Entity entity, String variableName, double value) {
        getVariables(entity).ifPresent(variables -> variables.putDouble(variableName, value));
    }

    public static void setString(Entity entity, String variableName, String value) {
        getVariables(entity).ifPresent(variables -> variables.putString(variableName, value));
    }

    public static int clampInt(Entity entity, String variableName, int defaultValue, int min, int max) {
        int value = getInt(entity, variableName, defaultValue);
        int clamped = Math.max(min, Math.min(max, value));
        if (!hasVariable(entity, variableName) || clamped != value) {
            setInt(entity, variableName, clamped);
        }
        return clamped;
    }

    public static void setAttackPayload(Entity entity, String attackType, String attackSin, String damageMode) {
        setString(entity, VAR_ATTACK_TYPE, attackType == null ? ATTACK_TYPE_NONE : attackType);
        setString(entity, VAR_ATTACK_SIN, attackSin == null ? ATTACK_SIN_NONE : attackSin);
        setString(entity, VAR_DAMAGE_MODE, damageMode == null ? DAMAGE_MODE_NORMAL : damageMode);
    }

    public static void clearAttackPayload(Entity entity) {
        setString(entity, VAR_ATTACK_TYPE, ATTACK_TYPE_NONE);
        setString(entity, VAR_ATTACK_SIN, ATTACK_SIN_NONE);
        setString(entity, VAR_DAMAGE_MODE, DAMAGE_MODE_NORMAL);
    }

    public static boolean isStatusDamage(Entity entity) {
        return DAMAGE_MODE_STATUS.equalsIgnoreCase(getString(entity, VAR_DAMAGE_MODE, DAMAGE_MODE_NORMAL));
    }

    public static boolean isIndependentDot(String attackType) {
        return attackType != null
                && (ATTACK_TYPE_DOT.equalsIgnoreCase(attackType) || attackType.toLowerCase().endsWith("_dot"));
    }

    public static boolean applySelfStatusDamage(LivingEntity target, double amount, String attackType, String attackSin) {
        if (!target.isValid() || target.isDead() || amount <= 0.0) {
            return false;
        }

        pendingSelfStatusHits.put(
                target.getUniqueId(),
                new PendingSelfStatusHit(amount, attackType, attackSin)
        );
        setAttackPayload(target, attackType, attackSin, DAMAGE_MODE_STATUS);
        try {
            target.damage(amount);
            return true;
        } finally {
            pendingSelfStatusHits.remove(target.getUniqueId());
            clearAttackPayload(target);
        }
    }

    public static PendingSelfStatusHit getPendingSelfStatusHit(Entity entity) {
        if (entity == null) {
            return null;
        }
        return pendingSelfStatusHits.get(entity.getUniqueId());
    }

    // Poise is still the live Mythic variable name, but we also mirror a breath alias
    // so the mechanic is easier to inspect from placeholders or scripts later.
    public static void settleBreath(Entity entity) {
        int intensity = clampInt(entity, VAR_POISE_INTENSITY, 0, 0, 100);
        int maxCount = entity instanceof Player ? 50 : 20;
        int count = clampInt(entity, VAR_POISE_COUNT, 0, 0, maxCount);

        if (intensity > 0 && count <= 0) {
            count = 1;
            setInt(entity, VAR_POISE_COUNT, count);
        }

        if (count <= 0) {
            intensity = 0;
            setInt(entity, VAR_POISE_INTENSITY, 0);
        }

        setInt(entity, VAR_BREATH_INTENSITY, intensity);
        setInt(entity, VAR_BREATH_COUNT, count);
    }

    public record PendingSelfStatusHit(double amount, String attackType, String attackSin) {
    }
}
