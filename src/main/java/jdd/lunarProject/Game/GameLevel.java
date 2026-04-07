package jdd.lunarProject.Game;
import jdd.lunarProject.Config.MapConfig;
import jdd.lunarProject.LunarProject;
import net.kyori.adventure.util.TriState;
import org.bukkit.*;
import org.bukkit.persistence.PersistentDataType;
import org.codehaus.plexus.util.FileUtils;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GameLevel {
    private final String mapName;
    private final File sourceFolder;
    private File activeFolder;
    private World world;
    private final String uniqueId;
    private Location centerBossLocation;
    private final List<Location> monsterSpawnLocations = new ArrayList<>();

    public GameLevel(String mapName, String uniqueId) {
        this.mapName = mapName;
        this.uniqueId = uniqueId;
        this.sourceFolder = new File(new File(LunarProject.getInstance().getDataFolder(), "maps"), mapName);
    }
    public boolean load() {
        this.activeFolder = new File(
                Bukkit.getWorldContainer(),
                mapName + "_level_" + uniqueId+"_"+System.currentTimeMillis()
        );
        try {
            FileUtils.copyDirectoryStructure(sourceFolder, activeFolder);
            this.world = Bukkit.createWorld(new WorldCreator(activeFolder.getName()).keepSpawnLoaded(TriState.FALSE));

            if (world != null) {
                world.setAutoSave(false);
                world.getPersistentDataContainer().set(new NamespacedKey(LunarProject.getInstance(), "lunar_level"), PersistentDataType.BOOLEAN, true);
                // 禁用自然属性以符合肉鸽玩法
                world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
                world.setGameRule(GameRule.NATURAL_REGENERATION, false);

                loadPoints(); // 加载配置中的坐标点
            }
        } catch (IOException e) {
            return false;
        }
        return world != null;
    }

    private void loadPoints() {
        // 读取中心 Boss 点
        List<Double> center = MapConfig.getCenterLocation(mapName);
        if (center != null && center.size() >= 3) {
            this.centerBossLocation = new Location(world, center.get(0), center.get(1), center.get(2));
        }

        // 读取 4 个怪物出生点
        List<List<Double>> spawners = MapConfig.getMonsterSpawners(mapName);
        if (spawners != null) {
            for (List<Double> coords : spawners) {
                monsterSpawnLocations.add(new Location(world, coords.get(0), coords.get(1), coords.get(2)));
            }
        }
    }

    public void unload() {
        if (world != null) Bukkit.unloadWorld(world, false);
        if (activeFolder != null) {
            try { FileUtils.deleteDirectory(activeFolder); } catch (IOException ignored) {}
        }
    }

    public World getWorld() { return world; }
    public Location getBossLocation() { return centerBossLocation; }
    public List<Location> getMonsterLocations() { return monsterSpawnLocations; }
}