package jdd.lunarProject.Game;
import io.lumine.mythic.bukkit.MythicBukkit;
import jdd.lunarProject.Game.StageManager;
import jdd.lunarProject.Game.StageModels.*;
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
    private int currentWave = 0;

    private StageTemplate currentTemplate;
    private final Queue<String> currentWaveQueue = new LinkedList<>();
    private final Set<UUID> activeMobs = new HashSet<>();

    // ======== 路线选择与大厅投票系统 ========
    private boolean isVotingPhase = false;
    private final List<StageTemplate> nextNodeChoices = new ArrayList<>();
    private final Map<UUID, Integer> playerVotes = new HashMap<>();

    // ======== 和平房间(非战斗)系统 ========
    private boolean isPeacefulRoom = false;
    private final Set<UUID> proceedPlayers = new HashSet<>(); // 记录谁按了继续前进

    public RoundManager(Game game) {
        this.game = game;
    }
    private void startNextWave() {
        currentWave++;
        currentWaveQueue.clear();

        // 获取当前模板里对应波次的数据
        StageModels.WaveData waveData = currentTemplate.waves().get(currentWave);
        if (waveData == null) {
            checkWaveClear(); // 防御性判断：如果没有波次数据，直接通关
            return;
        }

        game.sendTitle("§c第 " + currentWave + " 波", "§e敌方信号已锁定...");

        // ==========================================
        // 根据数据库配置，精准装填本波次怪物并打乱出场顺序
        // ==========================================
        List<String> mobsToPrepare = new ArrayList<>();
        for (StageModels.MobSpawn spawn : waveData.mobs()) {
            for (int i = 0; i < spawn.amount(); i++) {
                mobsToPrepare.add(spawn.mythicMobsId()); // 固定数量！
            }
        }
        Collections.shuffle(mobsToPrepare);
        currentWaveQueue.addAll(mobsToPrepare);

        // ==========================================
        // 定时缓慢出怪 (每2秒从队列里弹出一只)
        // ==========================================
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!game.isRunning() || currentWaveQueue.isEmpty()) {
                    this.cancel();
                    // 队列吐空后，如果场上也没有活怪了，立刻进入下一波/结算
                    if (currentWaveQueue.isEmpty() && activeMobs.isEmpty()) {
                        checkWaveClear();
                    }
                    return;
                }

                List<Location> spawns = currentStage.getMonsterLocations();
                if (!spawns.isEmpty()) {
                    Location loc = spawns.get(ThreadLocalRandom.current().nextInt(spawns.size()));
                    String mobToSpawn = currentWaveQueue.poll();

                    try {
                        Entity mob = MythicBukkit.inst().getAPIHelper().spawnMythicMob(mobToSpawn, loc);
                        if (mob != null) {
                            activeMobs.add(mob.getUniqueId());
                            GameManager.registerMob(mob.getUniqueId(), game);
                        }
                    } catch (Exception e) {
                        Bukkit.getLogger().severe("[LunarProject] 怪物生成异常: " + mobToSpawn);
                    }
                }
            }
        }.runTaskTimer(LunarProject.getInstance(), 0L, 40L);
    }

    public void handleMobDeath(UUID mobId) {
        if (activeMobs.remove(mobId)) {
            GameManager.unregisterMob(mobId);
            checkWaveClear();
        }
    }

    private void checkWaveClear() {
        // 双重保险：只有预备队列空了，且场上的活怪死光了，才算波次肃清
        if (activeMobs.isEmpty() && currentWaveQueue.isEmpty()) {
            // 动态判断关卡模板里还有没有下一波
            if (currentTemplate.waves().containsKey(currentWave + 1)) {
                game.broadcast("§a当前波次已被肃清，准备迎接下一波...");
                new BukkitRunnable() {
                    @Override
                    public void run() { if (game.isRunning()) startNextWave(); }
                }.runTaskLater(LunarProject.getInstance(), 60L);
            } else {
                // 如果没有下一波了，结算回合
                if (currentRound >= 5) {
                    onGameVictory(); // 第5关结算胜利
                } else {
                    onRoundCleared(); // 返回大厅投票
                }
            }
        }
    }
    private void onGameVictory() {
        game.sendTitle("§6§l通关成功！", "§e你已成功击败该区域所有首领！");
        game.broadcast("§a======================");
        game.broadcast("§e🎉 恭喜本局所有玩家成功通关第一阶！");
        game.broadcast("§e房间将在 15 秒后解散。");
        game.broadcast("§a======================");

        new BukkitRunnable() {
            @Override
            public void run() {
                if (game.isRunning()) {
                    game.setGameState(GameState.ENDED);
                }
            }
        }.runTaskLater(LunarProject.getInstance(), 300L);
    }

    public GameLevel getCurrentStage() { return currentStage; }
    public void cleanUp() {
        if (currentStage != null) {
            currentStage.unload();
            currentStage = null;
        }
        for (UUID mobId : activeMobs) GameManager.unregisterMob(mobId);
        activeMobs.clear();
        currentWaveQueue.clear();
    }
    // 游戏开始，或者回大厅后生成下一关的选项
    public void generateNextNodeChoices() {
        nextNodeChoices.clear();
        playerVotes.clear();
        isVotingPhase = true;

        int nextRound = currentRound + 1;
        int tier = 1;

        // 如果下一关是第5关，固定只有一条路：BOSS
        if (nextRound % 5 == 0) {
            StageTemplate bossStage = StageManager.getRandomStage(tier, nextRound, "BOSS");
            if (bossStage != null) nextNodeChoices.add(bossStage);
        } else {
            // 否则生成 2 条路线选项：
            // 路线1：必定是普通战斗
            StageTemplate normalStage = StageManager.getRandomStage(tier, nextRound, "NORMAL");
            if (normalStage != null) nextNodeChoices.add(normalStage);

            // 路线2：随机抽取 (危险战 / 事件 / 商店 / 休息站)
            String[] specialTypes = {"ELITE", "EVENT", "SHOP", "REST"};
            String randomType = specialTypes[ThreadLocalRandom.current().nextInt(specialTypes.length)];
            StageTemplate specialStage = StageManager.getRandomStage(tier, nextRound, randomType);

            // 如果数据库里暂时没配置这个特殊房，就再塞个普通房补位
            if (specialStage == null) specialStage = StageManager.getRandomStage(tier, nextRound, "NORMAL");
            if (specialStage != null) nextNodeChoices.add(specialStage);
        }

        broadcastChoices();
    }

    private void broadcastChoices() {
        game.broadcast("§a=================================");
        game.broadcast("§e前方出现了多条岔路，请选择前往的节点：");
        for (int i = 0; i < nextNodeChoices.size(); i++) {
            StageTemplate st = nextNodeChoices.get(i);
            String icon = getIconForType(st.stageType());
            game.broadcast("§b[" + (i + 1) + "] §f" + icon + " §7" + st.stageType() + " 节点");
        }
        game.broadcast("§7(输入 §c/game vote <编号> §7进行全车人投票)");
        game.broadcast("§a=================================");
    }

    private String getIconForType(String type) {
        return switch (type) {
            case "NORMAL" -> "⚔ 普通战斗";
            case "ELITE" -> "☠ 危险战斗";
            case "EVENT" -> "❓ 异想体事件";
            case "SHOP" -> "💰 诡异的自动售货机";
            case "REST" -> "☕ 休息站";
            case "BOSS" -> "👹 区域首领";
            default -> "🚪 未知区域";
        };
    }

    // 玩家进行投票
    public void castVote(Player player, int choiceIndex) {
        if (!isVotingPhase) {
            player.sendMessage("§c当前不是路线选择阶段！");
            return;
        }
        if (choiceIndex < 1 || choiceIndex > nextNodeChoices.size()) {
            player.sendMessage("§c无效的选项编号！");
            return;
        }

        playerVotes.put(player.getUniqueId(), choiceIndex - 1);
        int totalPlayers = game.getActiveOnlinePlayers().size();
        game.broadcast("§a" + player.getName() + " 选择了路线 [" + choiceIndex + "]。 (" + playerVotes.size() + "/" + totalPlayers + ")");

        // 如果所有人都投票了
        if (playerVotes.size() >= totalPlayers) {
            determineVoteWinner();
        }
    }

    private void determineVoteWinner() {
        isVotingPhase = false;
        // 简单统计票数，选出得票最多的路线
        int[] voteCounts = new int[nextNodeChoices.size()];
        for (int vote : playerVotes.values()) {
            voteCounts[vote]++;
        }

        int winnerIndex = 0;
        int maxVotes = 0;
        for (int i = 0; i < voteCounts.length; i++) {
            if (voteCounts[i] > maxVotes) {
                maxVotes = voteCounts[i];
                winnerIndex = i;
            }
        }

        StageTemplate winnerTemplate = nextNodeChoices.get(winnerIndex);
        game.broadcast("§e🎯 路线已确定，即将前往: " + getIconForType(winnerTemplate.stageType()));
        loadStage(winnerTemplate);
    }

    // ===============================================
    // 载入选中的节点
    // ===============================================
    private void loadStage(StageTemplate template) {
        currentRound++;
        currentWave = 0;
        currentTemplate = template;
        proceedPlayers.clear();
        // 判断是否是和平房间（没有怪物）
        String type = template.stageType();
        isPeacefulRoom = type.equals("EVENT") || type.equals("SHOP") || type.equals("REST");

        if (currentStage != null) currentStage.unload();
        currentStage = new GameLevel(template.mapName(), "ROUND_" + currentRound);

        if (currentStage.load()) {
            Location spawnLoc = currentStage.getBossLocation();
            if (spawnLoc == null) spawnLoc = currentStage.getWorld().getSpawnLocation();

            for (Player p : game.getActiveOnlinePlayers()) p.teleport(spawnLoc);
            for (Player p : game.getDeadOnlinePlayers()) p.teleport(spawnLoc);

            game.sendTitle("§c第 " + currentRound + " 节点", "§7" + getIconForType(type));

            if (isPeacefulRoom) {
                handlePeacefulRoom();
            } else {
                startNextWave(); // 战斗房开始刷怪
            }
        }
    }

    // 处理非战斗房间逻辑
    private void handlePeacefulRoom() {
        String type = currentTemplate.stageType();
        game.broadcast("§a=================================");
        if (type.equals("REST")) {
            game.broadcast("§a☕ 你们找到了一个安全的休息站。");
            // 【预留系统】：全员回血逻辑
            for(Player p : game.getActiveOnlinePlayers()) p.setHealth(p.getMaxHealth());
        } else if (type.equals("SHOP")) {
            game.broadcast("§a💰 这里有一个看起来很诡异的自动售货机...");
            // 【预留系统】：弹出商店 GUI
        } else if (type.equals("EVENT")) {
            game.broadcast("§a❓ 异想体事件触发中...");
            // 【预留系统】：发送事件文本和选项
        }
        game.broadcast("§e休整完毕后，全员输入 §c/game proceed §e即可前往下一个节点。");
        game.broadcast("§a=================================");
    }

    // 玩家在和平房间确认继续前进
    public void setPlayerProceed(Player player) {
        if (!isPeacefulRoom) {
            player.sendMessage("§c当前是战斗节点，必须消灭所有敌人才可前进！");
            return;
        }
        proceedPlayers.add(player.getUniqueId());
        int total = game.getActiveOnlinePlayers().size();
        game.broadcast("§a" + player.getName() + " 准备好继续前进了！ (" + proceedPlayers.size() + "/" + total + ")");

        if (proceedPlayers.size() >= total) {
            onRoundCleared(); // 全员准备好，视为通关，回大厅
        }
    }

    private void onRoundCleared() {
        game.sendTitle("§a节点完成", "§e正在返回抉择大厅...");
        new BukkitRunnable() {
            @Override
            public void run() {
                if (game.isRunning()) {
                    isPeacefulRoom = false;
                    Location lobbyLoc = game.getLobbyMap().getSpawnLocation();

                    for (Player p : game.getActiveOnlinePlayers()) p.teleport(lobbyLoc);
                    for (Player p : game.getDeadOnlinePlayers()) p.teleport(lobbyLoc);

                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            cleanUp();
                            generateNextNodeChoices(); // 回到大厅后立刻生成下一关的选项！
                        }
                    }.runTaskLater(LunarProject.getInstance(), 20L);
                }
            }
        }.runTaskLater(LunarProject.getInstance(), 100L);
    }
}