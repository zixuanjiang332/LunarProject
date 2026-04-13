package jdd.lunarProject.Config;

import jdd.lunarProject.LunarProject;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

public class MapConfig {
    private static final File file = new File(LunarProject.getInstance().getDataFolder(), "map_data.yml");
    private static YamlConfiguration config;

    public static void init() {
        if (!file.exists()) {
            LunarProject.getInstance().saveResource("map_data.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    public static boolean hasMap(String map) {
        return config != null && config.contains(map);
    }

    public static boolean hasMapDirectory(String map) {
        return new File(getMapsFolder(), map).isDirectory();
    }

    public static List<Double> getCenterLocation(String map) {
        return config.getDoubleList(map + ".center-location");
    }

    public static boolean hasCenterLocation(String map) {
        return hasMap(map) && getCenterLocation(map).size() >= 3;
    }

    @SuppressWarnings("unchecked")
    public static List<List<Double>> getMonsterSpawners(String map) {
        List<List<Double>> spawners = new ArrayList<>();
        List<?> rawList = config.getList(map + ".spawners");
        if (rawList != null) {
            for (Object obj : rawList) {
                if (obj instanceof List<?>) {
                    spawners.add((List<Double>) obj);
                }
            }
        }
        return spawners;
    }

    public static boolean hasMonsterSpawners(String map) {
        return hasMap(map) && !getMonsterSpawners(map).isEmpty();
    }

    public static List<String> ensureConfiguredMapFolders() {
        List<String> issues = new ArrayList<>();
        if (config == null) {
            issues.add("map_data.yml is not loaded.");
            return issues;
        }

        File mapsFolder = getMapsFolder();
        if (!mapsFolder.exists() && !mapsFolder.mkdirs()) {
            issues.add("Failed to create maps folder: " + mapsFolder.getAbsolutePath());
            return issues;
        }

        String fallbackTemplate = LunarProject.getInstance().getConfig().getString("game.map-bootstrap-template", "test-map");
        File fallbackFolder = fallbackTemplate == null || fallbackTemplate.isBlank()
                ? null
                : new File(mapsFolder, fallbackTemplate);

        for (String mapName : config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(mapName);
            if (section == null) {
                continue;
            }

            if (hasMapDirectory(mapName)) {
                continue;
            }

            if (fallbackFolder == null || !fallbackFolder.isDirectory()) {
                issues.add("Map folder missing for " + mapName + " and fallback template " + fallbackTemplate + " is unavailable.");
                continue;
            }

            if (mapName.equalsIgnoreCase(fallbackTemplate)) {
                issues.add("Required fallback map folder is missing: " + fallbackTemplate);
                continue;
            }

            File targetFolder = new File(mapsFolder, mapName);
            try {
                copyDirectory(fallbackFolder.toPath(), targetFolder.toPath());
                LunarProject.getInstance().getLogger().warning(
                        "Map folder " + mapName + " was missing. Bootstrapped it from template " + fallbackTemplate + "."
                );
            } catch (IOException exception) {
                issues.add("Failed to bootstrap map folder " + mapName + ": " + exception.getMessage());
            }
        }

        return issues;
    }

    private static File getMapsFolder() {
        return new File(LunarProject.getInstance().getDataFolder(), "maps");
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path relative = source.relativize(dir);
                Files.createDirectories(target.resolve(relative));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path relative = source.relativize(file);
                Files.copy(file, target.resolve(relative), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
