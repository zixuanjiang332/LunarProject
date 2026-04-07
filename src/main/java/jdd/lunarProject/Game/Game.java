package jdd.lunarProject.Game;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Game {
    private final String gameId;
    private final List<Player> activePlayers; // 存活玩家
    private final List<Player> deadPlayers;   // 阵亡玩家
    private final LobbyMap lobbyMap;
    private final RoundManager roundManager;
    private boolean isRunning;

    public Game() {
        this.gameId = UUID.randomUUID().toString().substring(0, 8);
        this.activePlayers = new ArrayList<>();
        this.deadPlayers = new ArrayList<>();
        // 初始化本局专属大厅并加载
        this.lobbyMap = new LobbyMap(this.gameId);
        this.lobbyMap.load();

        this.roundManager = new RoundManager(this);
        this.isRunning = false;
    }

    public boolean playerJoin(Player player) {
        if (isRunning) {
            player.sendMessage("§c游戏已经开始，无法加入！");
            return false;
        }
        if (!activePlayers.contains(player)) {
            activePlayers.add(player);
            GameManager.setPlayerGame(player, this);

            // 传送到大厅等待
            if (lobbyMap.getSpawnLocation() != null) {
                player.teleport(lobbyMap.getSpawnLocation());
            }
            player.sendMessage("§a成功加入游戏！当前人数: " + activePlayers.size());
            return true;
        }
        return false;
    }

    public void playerLeave(Player player) {
        activePlayers.remove(player);
        deadPlayers.remove(player);
        GameManager.setPlayerGame(player, null);

        // 恢复正常模式并传送到主城 (需要你根据服务器实际情况替换坐标)
        player.setGameMode(GameMode.SURVIVAL);
        // player.teleport(Bukkit.getWorld("world").getSpawnLocation());
        player.sendMessage("§e你已离开游戏。");

        // 如果游戏进行中且存活玩家为空，直接结束游戏
        if (isRunning && activePlayers.isEmpty()) {
            Bukkit.broadcastMessage("§c游戏 [" + gameId + "] 玩家全部退出/阵亡，游戏结束！");
            stop();
        }
    }

    // 玩家死亡时调用此方法（可以在 PlayerDeathEvent 中监听并调用）
    public void onPlayerDeath(Player player) {
        if (activePlayers.contains(player)) {
            activePlayers.remove(player);
            deadPlayers.add(player);

            player.setGameMode(GameMode.SPECTATOR); // 设为旁观者
            player.sendMessage("§c你已阵亡！已进入旁观模式。");

            if (activePlayers.isEmpty()) {
                Bukkit.broadcastMessage("§c游戏 [" + gameId + "] 队伍团灭，游戏结束！");
                stop();
            }
        }
    }

    public void start() {
        if (activePlayers.isEmpty()) return;
        this.isRunning = true;
        // 加载第一关，默认读取 test-map
        roundManager.loadNextStage("test-map");
    }

    public void stop() {
        this.isRunning = false;

        // 踢出所有玩家
        for (Player p : new ArrayList<>(activePlayers)) playerLeave(p);
        for (Player p : new ArrayList<>(deadPlayers)) playerLeave(p);

        // 清理地图
        roundManager.cleanUp();
        lobbyMap.unload();

        GameManager.removeGame(this.gameId);
    }

    public String getGameId() { return gameId; }
    public List<Player> getActivePlayers() { return activePlayers; }
    public List<Player> getDeadPlayers() { return deadPlayers; }
    public LobbyMap getLobbyMap() { return lobbyMap; }
    public boolean isRunning() { return isRunning; }
}