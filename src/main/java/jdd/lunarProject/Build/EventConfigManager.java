package jdd.lunarProject.Build;

import jdd.lunarProject.LunarProject;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class EventConfigManager {
    private static final Map<String, EventDefinition> eventDefinitions = new HashMap<>();
    private static final List<String> loadMessages = new ArrayList<>();

    private EventConfigManager() {
    }

    public static List<String> init() {
        eventDefinitions.clear();
        loadMessages.clear();

        File file = new File(LunarProject.getInstance().getDataFolder(), "events.yml");
        if (!file.exists()) {
            LunarProject.getInstance().saveResource("events.yml", false);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("events");
        if (section == null) {
            loadMessages.add("events.yml 中未找到事件定义。");
            return List.copyOf(loadMessages);
        }

        for (String eventId : section.getKeys(false)) {
            ConfigurationSection eventSection = section.getConfigurationSection(eventId);
            if (eventSection == null) {
                continue;
            }

            eventDefinitions.put(eventId, new EventDefinition(
                    eventId,
                    eventSection.getString("intro", "前方出现了一段尚未定义的异常记录。"),
                    eventSection.getString("option_1", "谨慎离开"),
                    eventSection.getString("option_2", "继续调查")
            ));
        }

        loadMessages.add("已加载 " + eventDefinitions.size() + " 个事件定义。");
        return List.copyOf(loadMessages);
    }

    public static List<String> getEventIds() {
        return new ArrayList<>(eventDefinitions.keySet());
    }

    public static EventDefinition getEvent(String eventId) {
        return eventDefinitions.get(eventId);
    }
}
