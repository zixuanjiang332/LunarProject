package jdd.lunarProject.Config;

import jdd.lunarProject.LunarProject;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class MessageManager {
    private static YamlConfiguration messages;

    private MessageManager() {
    }

    public static void init() {
        LunarProject plugin = LunarProject.getInstance();
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(file);
    }

    public static void reload() {
        init();
    }

    public static String text(String key, String fallback) {
        ensureLoaded();
        return colorize(messages.getString(key, fallback));
    }

    public static String format(String key, String fallback, Object... placeholders) {
        String message = text(key, fallback);
        for (int index = 0; index + 1 < placeholders.length; index += 2) {
            String placeholder = String.valueOf(placeholders[index]);
            String value = String.valueOf(placeholders[index + 1]);
            message = message.replace("%" + placeholder + "%", value);
        }
        return message;
    }

    public static List<String> list(String key, List<String> fallback) {
        ensureLoaded();
        List<String> configured = messages.contains(key) ? messages.getStringList(key) : fallback;
        List<String> colored = new ArrayList<>();
        for (String line : configured) {
            colored.add(colorize(line));
        }
        return colored;
    }

    public static List<String> formatList(String key, List<String> fallback, Object... placeholders) {
        List<String> lines = list(key, fallback);
        List<String> formatted = new ArrayList<>();
        for (String line : lines) {
            String resolved = line;
            for (int index = 0; index + 1 < placeholders.length; index += 2) {
                String placeholder = String.valueOf(placeholders[index]);
                String value = String.valueOf(placeholders[index + 1]);
                resolved = resolved.replace("%" + placeholder + "%", value);
            }
            formatted.add(resolved);
        }
        return formatted;
    }

    private static void ensureLoaded() {
        if (messages == null) {
            init();
        }
    }

    private static String colorize(String input) {
        if (input == null) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', input);
    }
}
