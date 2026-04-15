package jdd.lunarProject.Game;

import io.lumine.mythic.bukkit.MythicBukkit;
import jdd.lunarProject.Tool.CombatVariableUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public class StaggerManager {
    private final Map<String, StaggerConfig> classStaggerMap = new HashMap<>();
    private final JavaPlugin plugin;

    public StaggerManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfigs();
    }

    public void loadConfigs() {
        classStaggerMap.clear();
        plugin.reloadConfig();

        ConfigurationSection section = plugin.getConfig().getConfigurationSection("stagger-thresholds");
        if (section == null) {
            plugin.getLogger().warning("config.yml 中缺少 stagger-thresholds 配置段。");
            return;
        }

        for (String className : section.getKeys(false)) {
            double t1 = section.getDouble(className + ".t1", 0.0);
            double t2 = section.getDouble(className + ".t2", 0.0);
            classStaggerMap.put(className, new StaggerConfig(t1, t2));
        }

        plugin.getLogger().info("已加载 " + classStaggerMap.size() + " 个职业的混乱阈值配置。");
    }

    public StaggerConfig getConfigForClass(String className) {
        return classStaggerMap.getOrDefault(className, new StaggerConfig(0.0, 0.0));
    }

    public boolean triggerYellowBarBreak(Entity entity) {
        if (entity == null || !entity.isValid()) {
            return false;
        }

        int staggerStage = CombatVariableUtil.getInt(entity, "stagger_stage", 0);
        if (staggerStage > 0) {
            return false;
        }

        return MythicBukkit.inst().getAPIHelper().castSkill(entity, "System_Apply_Stagger_T1");
    }

    public static class StaggerConfig {
        public final double t1;
        public final double t2;

        public StaggerConfig(double t1, double t2) {
            this.t1 = t1;
            this.t2 = t2;
        }
    }
}
