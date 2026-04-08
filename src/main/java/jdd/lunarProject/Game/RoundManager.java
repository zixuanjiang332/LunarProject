package jdd.lunarProject.Game;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import jdd.lunarProject.LunarProject;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class RoundManager {
    private final Game game;
    private GameLevel currentStage;
    private int currentRound = 0;

    // 【新增】当前回合存活的怪物集合
    private final Set<UUID> activeMobs = new HashSet<>();

    // 【新增】怪物池设定（你可以随时往这里添加你在 MM 里写好的怪物 ID）
    private final List<String> normalMobPool = Arrays.asList("FuneralOfDeadButterflies", "TestNormalMob1", "TestNormalMob2");
    private final List<String> bossMobPool = Arrays.asList("TestMob_Weak_WrathBlunt", "TestBoss2");

    public RoundManager(Game game) {
        this.game = game;
    }

    public void loadNextStage(String nextMapName) {
        currentRound++;
        for (Player p : game.getActiveOnlinePlayers()) {
            p.sendMessage("§e正在为您生成第 " + currentRound + " 回合...");
        }

        if (currentStage != null) {
            currentStage.unload();
        }

        currentStage = new GameLevel(nextMapName, "ROUND_" + currentRound);
        if (currentStage.load()) {
            Location spawnLoc = currentStage.getBossLocation();
            if (spawnLoc == null) spawnLoc = currentStage.getWorld().getSpawnLocation();

            for (Player p : game.getActiveOnlinePlayers()) {
                p.teleport(spawnLoc);
                p.sendMessage("§c⚔ 第 " + currentRound + " 回合 战斗开始！");
            }
            for (Player p : game.getDeadOnlinePlayers()) {
                p.teleport(spawnLoc);
            }

            startSpawningMonsters();
        } else {
            Bukkit.getLogger().severe("游戏 [" + game.getGameId() + "] 关卡加载失败！");
        }
    }

    private void startSpawningMonsters() {
        activeMobs.clear(); // 清空上一回合的记录

        try {
            List<Location> spawns = currentStage.getMonsterLocations();
            for (Location loc : spawns) {
                // 从普通怪物池中随机抽取一只
                String randomMob = normalMobPool.get(ThreadLocalRandom.current().nextInt(normalMobPool.size()));
                Entity mob = MythicBukkit.inst().getAPIHelper().spawnMythicMob(randomMob, loc);

                if (mob != null) {
                    UUID mobId = mob.getUniqueId();
                    activeMobs.add(mobId);
                    GameManager.registerMob(mobId, game); // 绑定怪物到本局游戏
                }
            }

            // 根据回合数判断是否生成 Boss (每 5 关一个 Boss)
            if (currentRound % 5 == 0) {
                Location bossLoc = currentStage.getBossLocation();
                if (bossLoc != null) {
                    String randomBoss = bossMobPool.get(ThreadLocalRandom.current().nextInt(bossMobPool.size()));
                    Entity boss = MythicBukkit.inst().getAPIHelper().spawnMythicMob(randomBoss, bossLoc);
                    if (boss != null) {
                        UUID bossId = boss.getUniqueId();
                        activeMobs.add(bossId);
                        GameManager.registerMob(bossId, game);
                    }
                    game.sendTitle("§4警告", "§c异想体已突破收容！");
                }
            }
        } catch (Exception e) {
            Bukkit.getLogger().severe("MythicMobs 刷怪发生异常，请检查怪物ID是否正确配置！");
        }
    }

    // 【核心新增】处理怪物死亡逻辑
    public void handleMobDeath(UUID mobId) {
        if (activeMobs.contains(mobId)) {
            activeMobs.remove(mobId);
            GameManager.unregisterMob(mobId);

            // 当存活怪物数量为 0 时，触发回合胜利！
            if (activeMobs.isEmpty()) {
                onRoundCleared();
            }
        }
    }

    private void onRoundCleared() {
        game.sendTitle("§a区域已净化", "§e5秒后进入下一层...");

        // 延迟 5 秒后自动开启下一回合
        new BukkitRunnable() {
            @Override
            public void run() {
                if (game.isRunning()) {
                    // 这里可以根据情况随机加载下一张地图，为了测试暂时还是用 test-map
                    loadNextStage("test-map");
                }
            }
        }.runTaskLater(LunarProject.getInstance(), 100L); // 20 ticks * 5s = 100L
    }

    public GameLevel getCurrentStage() { return currentStage; }

    public void cleanUp() {
        if (currentStage != null) {
            currentStage.unload();
            currentStage = null;
        }
        // 清理绑定的怪物内存
        for (UUID mobId : activeMobs) {
            GameManager.unregisterMob(mobId);
        }
        activeMobs.clear();
    }
}