package jdd.lunarProject.Build;

import jdd.lunarProject.LunarProject;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RelicManager {
    private static final Map<String, RelicDefinition> relicDefinitions = new HashMap<>();
    private static final List<String> loadMessages = new ArrayList<>();

    private RelicManager() {
    }

    public static List<String> init() {
        relicDefinitions.clear();
        loadMessages.clear();

        File file = new File(LunarProject.getInstance().getDataFolder(), "relics.yml");
        if (!file.exists()) {
            LunarProject.getInstance().saveResource("relics.yml", false);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("relics");
        if (section == null) {
            loadMessages.add("relics.yml 中未找到饰品定义。");
            return List.copyOf(loadMessages);
        }

        for (String relicId : section.getKeys(false)) {
            ConfigurationSection relicSection = section.getConfigurationSection(relicId);
            if (relicSection == null) {
                continue;
            }

            BuildModifierType effectType = BuildModifierType.fromConfig(relicSection.getString("effect_type"));
            if (effectType == null) {
                loadMessages.add("跳过了 effect_type 无效的饰品: " + relicId);
                continue;
            }

            RelicDefinition relicDefinition = new RelicDefinition(
                    relicId,
                    relicSection.getString("name", relicId),
                    relicSection.getString("rarity", "COMMON"),
                    relicSection.getStringList("tags"),
                    relicSection.getString("description", "暂无描述"),
                    effectType,
                    relicSection.getDouble("effect_value", 0.0),
                    relicSection.getString("stack_rule", "UNIQUE"),
                    relicSection.getString("mythic_item_id", "")
            );
            relicDefinitions.put(relicId, relicDefinition);
        }

        loadMessages.add("已加载 " + relicDefinitions.size() + " 个饰品定义。");
        return List.copyOf(loadMessages);
    }

    public static boolean hasRelic(String relicId) {
        return relicDefinitions.containsKey(relicId);
    }

    public static RelicDefinition getRelic(String relicId) {
        return relicDefinitions.get(relicId);
    }
}
