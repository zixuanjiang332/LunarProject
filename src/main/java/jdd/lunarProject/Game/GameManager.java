package jdd.lunarProject.Game;
import org.bukkit.entity.Player;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GameManager {
    private static final Map<String, Game> activeGames = new HashMap<>();
    private static final Map<UUID, Game> playerGameMap = new HashMap<>();

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
        activeGames.clear();
        playerGameMap.clear();
    }

    public static void removeGame(String id) {
        activeGames.remove(id);
    }
}