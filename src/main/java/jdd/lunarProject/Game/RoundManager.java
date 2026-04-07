package jdd.lunarProject.Game;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import io.lumine.mythic.bukkit.MythicBukkit;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;

public class RoundManager {
    private final Game game;
    private GameLevel currentStage;
    private int currentRound = 0;

    public RoundManager(Game game) {
        this.game = game;
    }

    // 推进下一关卡
    public void loadNextStage(String nextMapName) {
        currentRound++;

        for (Player p : game.getActiveOnlinePlayers()) {
            p.sendMessage("§e正在为您生成第 " + currentRound + " 回合...");
        }

        // 1. 如果之前有关卡，先卸载掉
        if (currentStage != null) {
            currentStage.unload();
        }

        // 2. 加载新一轮的关卡地图
        currentStage = new GameLevel(nextMapName,"ROUND_"+currentRound);
        if (currentStage.load()) {
            Location spawnLoc = currentStage.getBossLocation();
            if (spawnLoc == null) spawnLoc = currentStage.getWorld().getSpawnLocation();
            // 3. 将存活玩家传送进新地图
            for (Player p : game.getActiveOnlinePlayers()) {
                p.teleport(spawnLoc);
                p.sendMessage("§c⚔ 第 " + currentRound + " 回合 战斗开始！");
            }

            // 将阵亡玩家也传送过去观看
            for (Player p : game.getDeadOnlinePlayers()) {
                p.teleport(spawnLoc);
            }
            // 4. 触发刷怪
            startSpawningMonsters();

        } else {
            Bukkit.getLogger().severe("游戏 [" + game.getGameId() + "] 关卡加载失败！");
        }
    }

    private void startSpawningMonsters() {
        // 接入 MythicMobs API
        try {
            // 获取刚才读取好的 4 个怪物点
            List<Location> spawns = currentStage.getMonsterLocations();
            for (Location loc : spawns) {
                // 生成小怪（请确保你的 MythicMobs 里有名字叫 "TestMob" 的怪物）
                MythicBukkit.inst().getAPIHelper().spawnMythicMob("FuneralOfDeadButterflies", loc);
            }

            // 根据回合数判断是否生成 Boss
            if (currentRound % 5 == 0) {
                Location bossLoc = currentStage.getBossLocation();
                if (bossLoc != null) {
                    MythicBukkit.inst().getAPIHelper().spawnMythicMob("TestMob_Weak_WrathBlunt", bossLoc);
                    Bukkit.getLogger().info("游戏 [" + game.getGameId() + "] 已生成阶段 Boss！");
                }
            }
        } catch (Exception e) {
            Bukkit.getLogger().severe("MythicMobs 刷怪发生异常，请检查怪物ID是否正确配置！");
            e.printStackTrace();
        }
    }
    public GameLevel getCurrentStage() {
        return currentStage;
    }
    public void cleanUp() {
        if (currentStage != null) {
            currentStage.unload();
            currentStage = null;
        }
    }
}