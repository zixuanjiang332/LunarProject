package jdd.lunarProject;
import io.lumine.mythic.bukkit.MythicBukkit;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

public class ProjectMoonExpansion extends PlaceholderExpansion {

    @Override
    public String getIdentifier() {
        // 这是你的专属前缀！以后用 %pm_...% 来调用
        return "pm";
    }

    @Override
    public String getAuthor() {
        return "YourName";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null || !player.isOnline()) {
            return "0";
        }
        var variables = MythicBukkit.inst().getPlayerManager().getProfile(player.getPlayer()).getVariables();
        if (params.equalsIgnoreCase("sanity")) {
            return String.valueOf(variables.getInt("sanity"));
        }
        if (params.equalsIgnoreCase("sinking_intensity")) {
            return String.valueOf(variables.getInt("sinking_intensity"));
        }
        if (params.equalsIgnoreCase("res_gloom")){
            return String.valueOf(variables.getInt("res_gloom"));
        }
        if (params.equalsIgnoreCase("res_pierce")){
            return String.valueOf(variables.getInt("res_pierce"));
        }
        return null;
    }
}