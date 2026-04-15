package jdd.lunarProject.Game;

import jdd.lunarProject.Tool.CombatVariableUtil;
import jdd.lunarProject.Tool.YellowBarUtil;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

import java.util.List;

public final class TemporaryCombatStateCleaner {
    private static final List<String> ZERO_INT_VARIABLES = List.of(
            "sinking_intensity",
            "burn_intensity",
            "bleed_intensity",
            "poisoning_intensity",
            "charge_intensity",
            "corrosion_intensity",
            "rupture_intensity",
            "tremor_intensity",
            "breath_intensity",
            "breath_count",
            "poise_intensity",
            "poise_count",
            "pure_fragility",
            "slash_fragility",
            "pierce_fragility",
            "blunt_fragility",
            "dot_fragility",
            "bleed_fragility",
            "bleed_dot_fragility",
            "rupture_fragility",
            "rupture_dot_fragility",
            "burn_fragility",
            "tremor_fragility",
            "poisoning_fragility",
            "corrosion_fragility",
            "wrath_fragility",
            "lust_fragility",
            "sloth_fragility",
            "gluttony_fragility",
            "gloom_fragility",
            "pride_fragility",
            "envy_fragility",
            "stagger_stage",
            "stagger_silence_lock",
            "stagger_t1_lock",
            "stagger_t2_lock"
    );

    private static final List<String> ZERO_DOUBLE_VARIABLES = List.of(
            "sinking_punish_slow"
    );

    private TemporaryCombatStateCleaner() {
    }

    public static void clearTemporaryCombatState(Player player) {
        if (player == null) {
            return;
        }

        // Room transitions should wipe every temporary vanilla buff/debuff so
        // combat state never leaks into the next node.
        for (PotionEffect potionEffect : player.getActivePotionEffects()) {
            player.removePotionEffect(potionEffect.getType());
        }

        // We intentionally reset only a white-listed set of combat variables so
        // long-term build data and class data remain untouched.
        for (String variableName : ZERO_INT_VARIABLES) {
            CombatVariableUtil.setInt(player, variableName, 0);
        }
        for (String variableName : ZERO_DOUBLE_VARIABLES) {
            CombatVariableUtil.setDouble(player, variableName, 0.0);
        }

        CombatVariableUtil.clearAttackPayload(player);
        CombatVariableUtil.setDouble(player, YellowBarUtil.VAR_YELLOW_BAR_RESISTANCE, 1.0);
        CombatVariableUtil.setDouble(player, YellowBarUtil.VAR_STAGGER_RESISTANCE, 1.0);
        CombatVariableUtil.setDouble(player, YellowBarUtil.VAR_STAGGER_RESISTANCE_MULTIPLIER, 1.0);
        YellowBarUtil.resetBreakState(player);
        player.setWalkSpeed(0.2f);
    }
}
