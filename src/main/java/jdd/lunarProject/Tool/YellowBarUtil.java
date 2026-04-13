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

    private YellowBarUtil() {
    }

    public static double applyDamage(Entity entity, double damage) {
        if (!(entity instanceof LivingEntity livingEntity) || damage <= 0.0) {
            return 0.0;
        }

        double maxValue = resolveMaxValue(livingEntity);
        double currentValue = resolveCurrentValue(livingEntity, maxValue);
        double updatedValue = Math.max(0.0, currentValue - damage);
        setValues(livingEntity, updatedValue, maxValue);
        return updatedValue;
    }

    public static void refill(Entity entity) {
        if (!(entity instanceof LivingEntity livingEntity)) {
            return;
        }

        double maxValue = resolveMaxValue(livingEntity);
        setValues(livingEntity, maxValue, maxValue);
    }

    public static double getCurrent(Entity entity) {
        return CombatVariableUtil.getDouble(entity, VAR_YELLOW_BAR_CURRENT, 0.0);
    }

    public static double getMax(Entity entity) {
        return CombatVariableUtil.getDouble(entity, VAR_YELLOW_BAR_MAX, 0.0);
    }

    private static double resolveMaxValue(LivingEntity entity) {
        double fallback = Math.max(1.0, entity.getHealth());
        AttributeInstance maxHealth = entity.getAttribute(Attribute.MAX_HEALTH);
        double sourceMax = maxHealth == null ? fallback : Math.max(1.0, maxHealth.getValue());

        double storedMax = CombatVariableUtil.getDouble(entity, VAR_YELLOW_BAR_MAX, sourceMax);
        double resolved = entity instanceof Player ? sourceMax : Math.max(sourceMax, storedMax);
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
}
