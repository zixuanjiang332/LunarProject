package jdd.lunarProject.Game;

import jdd.lunarProject.LunarProject;
import net.kyori.adventure.util.TriState;
import org.bukkit.*;
import org.codehaus.plexus.util.FileUtils;
import java.io.File;
import java.io.IOException;

public class LobbyMap {
    private final File sourceFolder;
    private File activeFolder;
    private World bukkitWorld;
    private final String gameId;
    public LobbyMap(String gameId) {
        this.gameId = gameId;
        this.sourceFolder = new File(new File(LunarProject.getInstance().getDataFolder(), "maps"), "LobbyTemplate");
    }

    public boolean load() {
        if (bukkitWorld != null) return true;

        this.activeFolder = new File(
                Bukkit.getWorldContainer(),
                "Lobby_Active_" + gameId + "_" + System.currentTimeMillis()
        );

        try {
            if (sourceFolder.exists()) {
                FileUtils.copyDirectoryStructure(sourceFolder, activeFolder);
                this.bukkitWorld = Bukkit.createWorld(new WorldCreator(activeFolder.getName()).keepSpawnLoaded(TriState.FALSE));

                if (bukkitWorld != null) {
                    bukkitWorld.setAutoSave(false);
                    bukkitWorld.setPVP(false);
                    bukkitWorld.setGameRule(GameRule.DO_MOB_SPAWNING, false);
                    bukkitWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
                    Bukkit.getLogger().info("游戏大厅（休整区）已准备就绪。");
                }
            } else {
                Bukkit.getLogger().warning("未找到大厅模板: LobbyTemplate");
                return false;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return bukkitWorld != null;
    }

    public void unload() {
        if (bukkitWorld != null) {
            Bukkit.unloadWorld(bukkitWorld, false);
        }
        if (activeFolder != null) {
            try {
                FileUtils.deleteDirectory(activeFolder);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        bukkitWorld = null;
    }

    public World getVoidWorld() {
        return bukkitWorld;
    }

    public Location getSpawnLocation() {
        return bukkitWorld != null ? bukkitWorld.getSpawnLocation() : null;
    }
}