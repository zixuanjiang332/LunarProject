package jdd.lunarProject.Game;
import jdd.lunarProject.LunarProject;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Game {
    private final String gameId;
    private final List<UUID> activePlayers; // 存活玩家 (使用 UUID 防断网报错)
    private final List<UUID> deadPlayers;   // 阵亡玩家
    private final LobbyMap lobbyMap;
    private final RoundManager roundManager;

    public static final int MIN_PLAYERS = 3;
    public static final int MAX_PLAYERS = 6;
    private GameState gameState;

    private BukkitTask countdownTask;
    private int countdownTime;

    public Game() {
        this.gameId = UUID.randomUUID().toString().substring(0, 8);
        this.activePlayers = new ArrayList<>();
        this.deadPlayers = new ArrayList<>();
        this.lobbyMap = new LobbyMap(this.gameId);
        this.lobbyMap.load();
        this.roundManager = new RoundManager(this);
        setGameState(GameState.WAITING);
    }

    // ================== 房间内专属防串频广播 ==================
    public void broadcast(String message) {
        for (UUID uuid : getAllPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) p.sendMessage(message);
        }
    }

    public void sendTitle(String title, String subtitle) {
        for (UUID uuid : getAllPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) p.sendTitle(title, subtitle, 10, 40, 10);
        }
    }

    private List<UUID> getAllPlayers() {
        List<UUID> all = new ArrayList<>(activePlayers);
        all.addAll(deadPlayers);
        return all;
    }

    // ================== 核心状态机 ==================
    public void setGameState(GameState newState) {
        this.gameState = newState;

        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }

        switch (newState) {
            case WAITING:
                broadcast("§e等待玩家加入... (" + activePlayers.size() + "/" + MIN_PLAYERS + ")");
                break;

            case STARTING:
                this.countdownTime = 60;
                broadcast("§a人数已达标，游戏准备开始！");
                this.countdownTask = new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (countdownTime <= 0) {
                            setGameState(GameState.STARTED);
                            this.cancel();
                            return;
                        }
                        if (countdownTime % 10 == 0 || countdownTime <= 5) {
                            broadcast("§e游戏将在 §c" + countdownTime + " §e秒后开始...");
                            if (countdownTime <= 5) sendTitle("§c" + countdownTime, "§e准备战斗");
                        }
                        countdownTime--;
                    }
                }.runTaskTimer(LunarProject.getInstance(), 0L, 20L);
                break;

            case STARTED:
                sendTitle("§4游戏开始", "§c深渊正在凝视你...");
                roundManager.loadNextStage("test-map");
                break;

            case ENDED:
                this.countdownTime = 5;
                sendTitle("§8游戏结束", "§7正在清理战场...");
                this.countdownTask = new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (countdownTime <= 0) {
                            stop();
                            this.cancel();
                            return;
                        }
                        broadcast("§c房间将在 " + countdownTime + " 秒后解散...");
                        countdownTime--;
                    }
                }.runTaskTimer(LunarProject.getInstance(), 0L, 20L);
                break;
        }
    }

    // ================== 玩家行为逻辑 ==================
    public boolean playerJoin(Player player) {
        if (gameState == GameState.STARTED || gameState == GameState.ENDED) {
            player.sendMessage("§c游戏已开始或结束，无法加入！");
            return false;
        }
        if (activePlayers.size() >= MAX_PLAYERS) {
            player.sendMessage("§c房间已满！");
            return false;
        }

        if (!activePlayers.contains(player.getUniqueId())) {
            activePlayers.add(player.getUniqueId());
            GameManager.setPlayerGame(player.getUniqueId(), this);

            if (lobbyMap.getSpawnLocation() != null) {
                player.teleport(lobbyMap.getSpawnLocation());
            }
            broadcast("§a" + player.getName() + " 加入了游戏！ (" + activePlayers.size() + "/" + MAX_PLAYERS + ")");

            if (gameState == GameState.WAITING && activePlayers.size() >= MIN_PLAYERS) {
                setGameState(GameState.STARTING);
            }
            return true;
        }
        return false;
    }

    // 玩家主动退出游戏
    public void playerLeave(Player player) {
        UUID uuid = player.getUniqueId();
        activePlayers.remove(uuid);
        deadPlayers.remove(uuid);
        GameManager.setPlayerGame(uuid, null);

        player.setGameMode(GameMode.SURVIVAL);
        player.sendMessage("§e你已退出游戏。");
        broadcast("§c" + player.getName() + " 退出了游戏！");

        checkPlayerCount();
    }

    // 局内死亡逻辑
    public void onPlayerDeath(Player player) {
        UUID uuid = player.getUniqueId();
        if (activePlayers.contains(uuid)) {
            activePlayers.remove(uuid);
            deadPlayers.add(uuid); // 记录不清空，转入死亡名单

            player.setGameMode(GameMode.SPECTATOR);
            broadcast("§c☠ 玩家 " + player.getName() + " 已阵亡！");

            if (activePlayers.isEmpty()) {
                broadcast("§4队伍已全军覆没...");
                setGameState(GameState.ENDED);
            }
        }
    }

    // ================== 断网与重连处理 ==================
    public void handleDisconnect(Player player) {
        UUID uuid = player.getUniqueId();
        // 如果游戏还没开始，断网直接算作退出
        if (gameState == GameState.WAITING || gameState == GameState.STARTING) {
            activePlayers.remove(uuid);
            GameManager.setPlayerGame(uuid, null);
            broadcast("§c" + player.getName() + " 在准备阶段断开了连接。");
            checkPlayerCount();
        }
        // 游戏进行中断网，直接判定死亡，保留数据！
        else if (gameState == GameState.STARTED && activePlayers.contains(uuid)) {
            activePlayers.remove(uuid);
            deadPlayers.add(uuid);
            broadcast("§c☠ 玩家 " + player.getName() + " 在战斗中断开了连接！(算作阵亡)");
            if (activePlayers.isEmpty()) setGameState(GameState.ENDED);
        }
    }

    public void handleReconnect(Player player) {
        UUID uuid = player.getUniqueId();
        if (deadPlayers.contains(uuid)) {
            player.sendMessage("§c你之前在游戏中阵亡（或断线）。已进入旁观模式。");
            player.setGameMode(GameMode.SPECTATOR);
            player.teleport(roundManager.getCurrentStage().getBossLocation());
        } else if (activePlayers.contains(uuid)) {
            player.teleport(roundManager.getCurrentStage().getBossLocation());
            player.sendMessage("§a欢迎重连！");
        }
    }

    private void checkPlayerCount() {
        if (gameState == GameState.STARTING && activePlayers.size() < MIN_PLAYERS) {
            setGameState(GameState.WAITING);
        } else if (gameState == GameState.STARTED && activePlayers.isEmpty()) {
            setGameState(GameState.ENDED);
        }
    }

    public void start() {
        if (gameState == GameState.WAITING || gameState == GameState.STARTING) {
            setGameState(GameState.STARTED);
        }
    }

    public void stop() {
        for (UUID uuid : getAllPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.setGameMode(GameMode.SURVIVAL);
                p.sendMessage("§e游戏已结束，您已离开房间。");
            }
            GameManager.setPlayerGame(uuid, null);
        }

        roundManager.cleanUp();
        lobbyMap.unload();
        GameManager.removeGame(this.gameId);
    }

    // 辅助方法：获取当前在线的存活玩家
    public List<Player> getActiveOnlinePlayers() {
        List<Player> online = new ArrayList<>();
        for (UUID id : activePlayers) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) online.add(p);
        }
        return online;
    }
    public List<Player> getDeadOnlinePlayers() {
        List<Player> online = new ArrayList<>();
        for (UUID id : deadPlayers) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) online.add(p);
        }
        return online;
    }
    public GameState getGameState() { return gameState; }
    public String getGameId() { return gameId; }

    public LobbyMap getLobbyMap() { return lobbyMap; }
    public boolean isRunning() {
        if (gameState.equals(GameState.STARTED)){
            return true;
        }
        else {
            return false;
        }
    }
}