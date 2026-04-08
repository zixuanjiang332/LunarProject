package jdd.lunarProject.Game;
import org.bukkit.entity.Player;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GameManager {
    private static final Map<String, Game> activeGames = new HashMap<>();
    private static final Map<UUID, Game> playerGameMap = new HashMap<>();
    private static final Map<UUID, Game> mobGameMap = new HashMap<>();
    public static Game createGame() {
        Game game = new Game();
        activeGames.put(game.getGameId(), game);
        return game;
    }

    public static Game getGame(String id) {
        return activeGames.get(id);
    }

    public static Game getPlayerGame(UUID uuid) {
        return playerGameMap.get(uuid);
    }

    // 将刷出来的 MythicMob 绑定到指定的对局
    public static void registerMob(UUID mobUuid, Game game) {
        mobGameMap.put(mobUuid, game);
    }

    // 怪物死亡或清理时解绑
    public static void unregisterMob(UUID mobUuid) {
        mobGameMap.remove(mobUuid);
    }

    // 通过怪物实体获取它属于哪局游戏
    public static Game getGameByMob(UUID mobUuid) {
        return mobGameMap.get(mobUuid);
    }

    public static void setPlayerGame(UUID uuid, Game game) {
        if (game == null) {
            playerGameMap.remove(uuid);
        } else {
            playerGameMap.put(uuid, game);
        }
    }

    public static void stopAllGames() {
        for (Game game : activeGames.values()) {
            game.stop();
        }
        mobGameMap.clear();
        activeGames.clear();
        playerGameMap.clear();
    }

    public static void removeGame(String id) {
        activeGames.remove(id);
        Game game = getGame(id);
        mobGameMap.keySet().removeIf(mobUuid -> mobGameMap.get(mobUuid) == game);
    }
}