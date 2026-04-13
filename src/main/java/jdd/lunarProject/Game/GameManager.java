package jdd.lunarProject.Game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    public static void registerMob(UUID mobUuid, Game game) {
        mobGameMap.put(mobUuid, game);
    }

    public static void unregisterMob(UUID mobUuid) {
        mobGameMap.remove(mobUuid);
    }

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
        for (Game game : new ArrayList<>(activeGames.values())) {
            game.shutdownImmediately();
        }
        mobGameMap.clear();
        activeGames.clear();
        playerGameMap.clear();
    }

    public static void removeGame(String id) {
        Game game = activeGames.get(id);
        activeGames.remove(id);
        if (game == null) {
            return;
        }
        mobGameMap.entrySet().removeIf(entry -> entry.getValue() == game);
        playerGameMap.entrySet().removeIf(entry -> entry.getValue() == game);
    }

    public static List<String> getActiveGameIds() {
        return new ArrayList<>(activeGames.keySet());
    }
}
