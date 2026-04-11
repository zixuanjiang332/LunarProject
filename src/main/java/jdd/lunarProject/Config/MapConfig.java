package jdd.lunarProject.Config;

import jdd.lunarProject.LunarProject;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MapConfig {
    private final static File file = new File(LunarProject.getInstance().getDataFolder(), "map_data.yml");
    private static YamlConfiguration config;

    public static void init() {
        if (!file.exists()) LunarProject.getInstance().saveResource("map_data.yml", false);
        config = YamlConfiguration.loadConfiguration(file);
    }
    public static List<Double> getCenterLocation(String map) {
        return config.getDoubleList(map + ".center-location");
    }

    public static List<List<Double>> getMonsterSpawners(String map) {
        List<List<Double>> spawners = new ArrayList<>();
        List<?> rawList = config.getList(map + ".spawners");
        if (rawList != null) {
            for (Object obj : rawList) {
                if (obj instanceof List) spawners.add((List<Double>) obj);
            }
        }
        return spawners;
    }
}