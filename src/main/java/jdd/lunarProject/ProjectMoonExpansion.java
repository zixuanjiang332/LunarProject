package jdd.lunarProject;

import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.skills.variables.VariableRegistry;
import jdd.lunarProject.Game.Game;
import jdd.lunarProject.Game.GameManager;
import jdd.lunarProject.Game.PlayerClassManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

public class ProjectMoonExpansion extends PlaceholderExpansion {

    @Override
    public String getIdentifier() {
        return "pm";
    }

    @Override
    public String getAuthor() {
        return "OpenAI";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    private String wholeNumber(double value) {
        return Long.toString(Math.round(value));
    }

    private double resolveMaxHealth(Player player) {
        if (player.getAttribute(Attribute.MAX_HEALTH) != null) {
            return player.getAttribute(Attribute.MAX_HEALTH).getValue();
        }
        return player.getHealth();
    }

    private boolean isCombatUiVisible(Player player) {
        Game game = GameManager.getPlayerGame(player.getUniqueId());
        return game != null && game.isRunning() && PlayerClassManager.hasSelectedClass(player);
    }

    private String readStatusInt(VariableRegistry variables, boolean visible, String variableName) {
        if (!visible) {
            return "0";
        }
        return String.valueOf(variables.getInt(variableName));
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null || !player.isOnline()) {
            return "0";
        }

        Player onlinePlayer = player.getPlayer();
        if (onlinePlayer == null) {
            return "0";
        }

        var profile = MythicBukkit.inst().getPlayerManager().getProfile(onlinePlayer);
        if (profile == null) {
            return "0";
        }

        VariableRegistry variables = profile.getVariables();
        boolean combatUiVisible = isCombatUiVisible(onlinePlayer);

        if (params.equalsIgnoreCase("combat_ui_visible")) {
            return combatUiVisible ? "1" : "0";
        }
        if (params.equalsIgnoreCase("combat_ui_text")) {
            if (!combatUiVisible) {
                return "";
            }
            return "§c生命 §f" + wholeNumber(onlinePlayer.getHealth())
                    + "/" + wholeNumber(resolveMaxHealth(onlinePlayer))
                    + "  §b理智 §f" + variables.getInt("sanity")
                    + "  §e混乱抗性 §f" + wholeNumber(variables.getDouble("yellow_bar_current"))
                    + "/" + wholeNumber(variables.getDouble("yellow_bar_max"));
        }
        if (params.equalsIgnoreCase("health_current")) {
            return combatUiVisible ? wholeNumber(onlinePlayer.getHealth()) : "0";
        }
        if (params.equalsIgnoreCase("health_max")) {
            return combatUiVisible ? wholeNumber(resolveMaxHealth(onlinePlayer)) : "0";
        }
        if (params.equalsIgnoreCase("sanity")) {
            return combatUiVisible ? String.valueOf(variables.getInt("sanity")) : "0";
        }
        if (params.equalsIgnoreCase("sinking_intensity")) {
            return readStatusInt(variables, combatUiVisible, "sinking_intensity");
        }
        if (params.equalsIgnoreCase("burn_intensity")) {
            return readStatusInt(variables, combatUiVisible, "burn_intensity");
        }
        if (params.equalsIgnoreCase("bleed_intensity")) {
            return readStatusInt(variables, combatUiVisible, "bleed_intensity");
        }
        if (params.equalsIgnoreCase("poisoning_intensity")) {
            return readStatusInt(variables, combatUiVisible, "poisoning_intensity");
        }
        if (params.equalsIgnoreCase("charge_intensity")) {
            return readStatusInt(variables, combatUiVisible, "charge_intensity");
        }
        if (params.equalsIgnoreCase("corrosion_intensity")) {
            return readStatusInt(variables, combatUiVisible, "corrosion_intensity");
        }
        if (params.equalsIgnoreCase("rupture_intensity")) {
            return readStatusInt(variables, combatUiVisible, "rupture_intensity");
        }
        if (params.equalsIgnoreCase("tremor_intensity")) {
            return readStatusInt(variables, combatUiVisible, "tremor_intensity");
        }
        if (params.equalsIgnoreCase("breath_intensity")) {
            return readStatusInt(variables, combatUiVisible, "breath_intensity");
        }
        if (params.equalsIgnoreCase("breath_count")) {
            return readStatusInt(variables, combatUiVisible, "breath_count");
        }
        if (params.equalsIgnoreCase("yellow_bar_current")) {
            return combatUiVisible ? wholeNumber(variables.getDouble("yellow_bar_current")) : "0";
        }
        if (params.equalsIgnoreCase("yellow_bar_max")) {
            return combatUiVisible ? wholeNumber(variables.getDouble("yellow_bar_max")) : "0";
        }
        if (params.equalsIgnoreCase("stagger_stage")) {
            return combatUiVisible ? String.valueOf(variables.getInt("stagger_stage")) : "0";
        }
        if (params.equalsIgnoreCase("class_name")) {
            String className = variables.has("class_name") ? variables.getString("class_name") : "";
            return className == null || className.isBlank() ? "未选择" : className;
        }
        if (params.equalsIgnoreCase("res_gloom")) {
            return String.valueOf(variables.getInt("res_gloom"));
        }
        if (params.equalsIgnoreCase("res_pierce")) {
            return String.valueOf(variables.getInt("res_pierce"));
        }
        return null;
    }
}
