package jdd.lunarProject.Build;

public enum BuildModifierType {
    DAMAGE_DEALT_MULT,
    DAMAGE_TAKEN_MULT,
    POST_COMBAT_HEAL_BONUS,
    SANITY_GAIN_BONUS,
    FRAGILITY_BONUS;

    public static BuildModifierType fromConfig(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return BuildModifierType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
