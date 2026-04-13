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
            loadMessages.add("No event definitions found in events.yml.");
            return List.copyOf(loadMessages);
        }

        for (String eventId : section.getKeys(false)) {
            ConfigurationSection eventSection = section.getConfigurationSection(eventId);
            if (eventSection == null) {
                continue;
            }

            eventDefinitions.put(eventId, new EventDefinition(
                    eventId,
                    eventSection.getString("intro", "A strange event blocks the road."),
                    eventSection.getString("option_1", "Leave carefully"),
                    eventSection.getString("option_2", "Investigate")
            ));
        }

        loadMessages.add("Loaded " + eventDefinitions.size() + " event definitions.");
        return List.copyOf(loadMessages);
    }

    public static List<String> getEventIds() {
        return new ArrayList<>(eventDefinitions.keySet());
    }

    public static EventDefinition getEvent(String eventId) {
        return eventDefinitions.get(eventId);
    }
}
