package jdd.lunarProject.Game;
import org.bukkit.entity.Player;
import java.util.HashMap;
import java.util.Map;

public class GameManager {
    private static final Map<String, Game> activeGames = new HashMap<>();
    private static final Map<Player, Game> playerGameMap = new HashMap<>();
    // 创建一局新游戏
    public static Game createGame() {
        Game game = new Game();
        activeGames.put(game.getGameId(), game);
        return game;
    }
    public static Game getGame(String id) {
        return activeGames.get(id);
    }

    // 获取玩家当前所在的游戏
    public static Game getPlayerGame(Player player) {
        return playerGameMap.get(player);
    }

    // 绑定/解绑玩家与游戏的关系
    public static void setPlayerGame(Player player, Game game) {
        if (game == null) {
            playerGameMap.remove(player);
        } else {
            playerGameMap.put(player, game);
        }
    }

    // 强制停止所有游戏 (通常在插件 onDisable 时调用)
    public static void stopAllGames() {
        for (Game game : activeGames.values()) {
            game.stop();
        }
        activeGames.clear();
        playerGameMap.clear();
    }

    public static void removeGame(String id) {
        activeGames.remove(id);
    }
}