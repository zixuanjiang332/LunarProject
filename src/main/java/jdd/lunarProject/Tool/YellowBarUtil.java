package jdd.lunarProject.Tool;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public final class YellowBarUtil {
    public static final String VAR_YELLOW_BAR_CURRENT = "yellow_bar_current";
    public static final String VAR_YELLOW_BAR_MAX = "yellow_bar_max";
    public static final String VAR_STAGGER_BAR_CURRENT = "stagger_bar_current";
    public static final String VAR_STAGGER_BAR_MAX = "stagger_bar_max";
    public static final String VAR_YELLOW_BAR_RESISTANCE = "yellow_bar_resistance";
    public static final String VAR_STAGGER_RESISTANCE = "stagger_resistance";
    public static final String VAR_STAGGER_RESISTANCE_MULTIPLIER = "stagger_resistance_multiplier";

    private YellowBarUtil() {
    }

    public static DamageResult applyDamage(Entity entity, double damage) {
        if (!(entity instanceof LivingEntity livingEntity) || damage <= 0.0) {
            return new DamageResult(0.0, 0.0, 0.0, false);
        }

        double maxValue = resolveMaxValue(livingEntity);
        double currentValue = resolveCurrentValue(livingEntity, maxValue);
        // Yellow-bar damage has its own resistance multiplier, similar to sin/type
        // resistances. Tremor and future mechanics can raise this value through
        // Mythic variables without touching Java damage routing again.
        double adjustedDamage = Math.max(0.0, damage * resolveResistanceMultiplier(livingEntity));
        double updatedValue = Math.max(0.0, currentValue - adjustedDamage);
        setValues(livingEntity, updatedValue, maxValue);
        boolean broken = currentValue > 0.0 && updatedValue <= 0.0;
        return new DamageResult(currentValue, updatedValue, maxValue, broken);
    }

    public static void refill(Entity entity) {
        if (!(entity instanceof LivingEntity livingEntity)) {
            return;
        }

        double maxValue = resolveMaxValue(livingEntity);
        setValues(livingEntity, maxValue, maxValue);
    }

    public static void setValues(Entity entity, double currentValue, double maxValue) {
        if (!(entity instanceof LivingEntity livingEntity)) {
            return;
        }
        setValues(livingEntity, currentValue, maxValue);
    }

    public static void restorePostStagger(Entity entity) {
        refill(entity);
        resetBreakState(entity);
    }

    public static double getCurrent(Entity entity) {
        return CombatVariableUtil.getDouble(entity, VAR_YELLOW_BAR_CURRENT, 0.0);
    }

    public static double getMax(Entity entity) {
        return CombatVariableUtil.getDouble(entity, VAR_YELLOW_BAR_MAX, 0.0);
    }

    public static boolean hasYellowBar(Entity entity) {
        return getMax(entity) > 0.0;
    }

    public static double getResistanceMultiplier(Entity entity) {
        return resolveResistanceMultiplier(entity);
    }

    public static void resetBreakState(Entity entity) {
        CombatVariableUtil.setInt(entity, "stagger_stage", 0);
        CombatVariableUtil.setInt(entity, "stagger_silence_lock", 0);
        CombatVariableUtil.setInt(entity, "stagger_t1_lock", 0);
        CombatVariableUtil.setInt(entity, "stagger_t2_lock", 0);
    }

    private static double resolveResistanceMultiplier(Entity entity) {
        double value = CombatVariableUtil.getDouble(entity, VAR_YELLOW_BAR_RESISTANCE, Double.NaN);
        if (Double.isNaN(value)) {
            value = CombatVariableUtil.getDouble(entity, VAR_STAGGER_RESISTANCE, Double.NaN);
        }
        if (Double.isNaN(value)) {
            value = CombatVariableUtil.getDouble(entity, VAR_STAGGER_RESISTANCE_MULTIPLIER, Double.NaN);
        }
        if (Double.isNaN(value) || value <= 0.0) {
            value = 1.0;
        }
        return Math.max(0.1, Math.min(5.0, value));
    }

    private static double resolveMaxValue(LivingEntity entity) {
        double fallback = Math.max(1.0, entity.getHealth());
        AttributeInstance maxHealth = entity.getAttribute(Attribute.MAX_HEALTH);
        double sourceMax = maxHealth == null ? fallback : Math.max(1.0, maxHealth.getValue());

        double storedMax = CombatVariableUtil.getDouble(entity, VAR_YELLOW_BAR_MAX, 0.0);
        double resolved = storedMax > 0.0 ? storedMax : sourceMax;
        if (Math.abs(resolved - storedMax) > 0.001) {
            CombatVariableUtil.setDouble(entity, VAR_YELLOW_BAR_MAX, resolved);
            CombatVariableUtil.setDouble(entity, VAR_STAGGER_BAR_MAX, resolved);
        }
        return resolved;
    }

    private static double resolveCurrentValue(LivingEntity entity, double maxValue) {
        double storedCurrent = CombatVariableUtil.getDouble(entity, VAR_YELLOW_BAR_CURRENT, maxValue);
        if (storedCurrent > maxValue) {
            storedCurrent = maxValue;
        }
        if (storedCurrent < 0.0) {
            storedCurrent = 0.0;
        }
        setValues(entity, storedCurrent, maxValue);
        return storedCurrent;
    }

    private static void setValues(LivingEntity entity, double currentValue, double maxValue) {
        CombatVariableUtil.setDouble(entity, VAR_YELLOW_BAR_CURRENT, currentValue);
        CombatVariableUtil.setDouble(entity, VAR_YELLOW_BAR_MAX, maxValue);
        CombatVariableUtil.setDouble(entity, VAR_STAGGER_BAR_CURRENT, currentValue);
        CombatVariableUtil.setDouble(entity, VAR_STAGGER_BAR_MAX, maxValue);
    }

    public record DamageResult(double previousValue, double currentValue, double maxValue, boolean broken) {
    }
}
