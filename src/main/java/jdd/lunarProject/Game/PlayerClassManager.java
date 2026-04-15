package jdd.lunarProject.Game;

import jdd.lunarProject.LunarProject;
import jdd.lunarProject.Tool.CombatVariableUtil;
import jdd.lunarProject.Tool.YellowBarUtil;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public final class PlayerClassManager {
    public static final String TEST_CLASS_ID = "测试职业";
    private static final double DEFAULT_MAX_HEALTH = 20.0;
    private static final double DEFAULT_YELLOW_BAR = 0.0;
    private static final int DEFAULT_SANITY = 0;

    private PlayerClassManager() {
    }

    public static boolean hasSelectedClass(Player player) {
        return player != null && !getSelectedClass(player).isBlank();
    }

    public static String getSelectedClass(Player player) {
        if (player == null) {
            return "";
        }
        return CombatVariableUtil.getString(player, "class_name", "").trim();
    }

    public static boolean applyClass(Player player, String classId) {
        if (player == null || classId == null || classId.isBlank()) {
            return false;
        }

        if (getClassSection(classId) == null) {
            return false;
        }

        CombatVariableUtil.setString(player, "class_name", classId);
        return restoreClassBaseline(player, true, true, true);
    }

    public static boolean restoreClassBaseline(Player player, boolean restoreHealth, boolean restoreYellowBar, boolean restoreSanity) {
        if (player == null) {
            return false;
        }

        String classId = getSelectedClass(player);
        if (classId.isBlank()) {
            return false;
        }

        ConfigurationSection section = getClassSection(classId);
        if (section == null) {
            return false;
        }

        double maxHealth = Math.max(1.0, section.getDouble("max-health", DEFAULT_MAX_HEALTH));
        double yellowBar = Math.max(0.0, section.getDouble("yellow-bar", maxHealth));
        int baseSanity = section.getInt("base-sanity", DEFAULT_SANITY);

        player.setHealthScaled(true);
        player.setHealthScale(20.0);

        AttributeInstance maxHealthAttribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttribute != null) {
            maxHealthAttribute.setBaseValue(maxHealth);
            if (restoreHealth) {
                player.setHealth(Math.min(maxHealth, maxHealthAttribute.getValue()));
            }
        }

        if (restoreYellowBar) {
            YellowBarUtil.setValues(player, yellowBar, yellowBar);
            YellowBarUtil.resetBreakState(player);
        }

        if (restoreSanity) {
            CombatVariableUtil.setInt(player, "sanity", baseSanity);
        }

        CombatVariableUtil.setDouble(player, "sinking_punish_slow", 0.0);
        player.setWalkSpeed(0.2f);
        return true;
    }

    public static void clearClassState(Player player) {
        if (player == null) {
            return;
        }

        TemporaryCombatStateCleaner.clearTemporaryCombatState(player);

        player.setHealthScaled(true);
        player.setHealthScale(20.0);

        AttributeInstance maxHealthAttribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttribute != null) {
            maxHealthAttribute.setBaseValue(DEFAULT_MAX_HEALTH);
            player.setHealth(Math.min(DEFAULT_MAX_HEALTH, maxHealthAttribute.getValue()));
        }

        CombatVariableUtil.setString(player, "class_name", "");
        CombatVariableUtil.setInt(player, "sanity", DEFAULT_SANITY);
        YellowBarUtil.setValues(player, DEFAULT_YELLOW_BAR, DEFAULT_YELLOW_BAR);
        YellowBarUtil.resetBreakState(player);
        CombatVariableUtil.setDouble(player, "sinking_punish_slow", 0.0);
        player.setWalkSpeed(0.2f);
    }

    private static ConfigurationSection getClassSection(String classId) {
        return LunarProject.getInstance().getConfig().getConfigurationSection("classes." + classId);
    }
}
