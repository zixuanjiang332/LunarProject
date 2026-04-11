package jdd.lunarProject.Game;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public class StaggerManager {
    // 缓存内存表：<职业名称, 混乱配置>
    private final Map<String, StaggerConfig> classStaggerMap = new HashMap<>();
    private final JavaPlugin plugin;

    public StaggerManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfigs(); // 实例化时自动加载
    }

    // ==========================================
    // 1. 配置加载器 (支持 /lunar reload 时调用此方法热重载)
    // ==========================================
    public void loadConfigs() {
        classStaggerMap.clear();
        plugin.reloadConfig();

        ConfigurationSection section = plugin.getConfig().getConfigurationSection("stagger-thresholds");
        if (section == null) {
            plugin.getLogger().warning("未在 config.yml 中找到 stagger-thresholds 节点！");
            return;
        }

        // 遍历所有职业的键值
        for (String className : section.getKeys(false)) {
            double t1 = section.getDouble(className + ".t1", 0.0);
            double t2 = section.getDouble(className + ".t2", 0.0);
            classStaggerMap.put(className, new StaggerConfig(t1, t2));
        }

        plugin.getLogger().info("成功加载了 " + classStaggerMap.size() + " 个职业的混乱阈值配置！");
    }

    // ==========================================
    // 2. 极速查询接口 (供伤害监听器调用)
    // ==========================================
    public StaggerConfig getConfigForClass(String className) {
        // 如果找不到该职业，返回一个默认安全的 0.0 阈值（即该职业没有混乱机制）
        return classStaggerMap.getOrDefault(className, new StaggerConfig(0.0, 0.0));
    }

    // ==========================================
    // 3. 内部数据类：混乱配置
    // ==========================================
    public static class StaggerConfig {
        public final double t1;
        public final double t2;

        public StaggerConfig(double t1, double t2) {
            this.t1 = t1;
            this.t2 = t2;
        }
    }
}