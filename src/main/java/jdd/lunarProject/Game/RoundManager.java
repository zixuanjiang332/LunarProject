package jdd.lunarProject.Game;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;

public class RoundManager {
    private GameLevel currentStage;
    private int currentRound = 1;

    // 当一局游戏开始，或者进入下一关时调用
    public void loadNextStage(String nextMapName, List<Player> players) {
        // 1. 如果之前有关卡，先卸载掉
        if (currentStage != null) {
            currentStage.unload();
        }

        // 2. 加载新一轮的关卡地图
        currentStage = new GameLevel(nextMapName);

        // 3. 将玩家传送进新地图
        for (Player p : players) {
            // 可以传送到大厅，或者传送到指定的起始点位

        }

        // 4. 开始在这个地图生成怪物
        startSpawningMonsters();
    }

    private void startSpawningMonsters() {
        // 获取刚才读取好的 4 个怪物点
        List<Location> spawns = currentStage.getMonsterLocations();
        for (Location loc : spawns) {
            // 生成小怪
            // loc.getWorld().spawnEntity(loc, EntityType.ZOMBIE);
        }
        // 根据回合数判断是否生成 Boss
        if (currentRound % 5 == 0) {
            Location bossLoc = currentStage.getBossLocation();
            // bossLoc.getWorld().spawnEntity(bossLoc, EntityType.WARDEN);
        }
    }
}