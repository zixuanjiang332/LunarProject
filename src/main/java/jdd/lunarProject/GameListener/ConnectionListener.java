package jdd.lunarProject.GameListener;

import jdd.lunarProject.Game.Game;
import jdd.lunarProject.Game.GameManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class ConnectionListener implements Listener {
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Game game = GameManager.getPlayerGame(player.getUniqueId());
        if (game != null) {
            game.handleDisconnect(player);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Game game = GameManager.getPlayerGame(player.getUniqueId());
        if (game != null) {
            game.handleReconnect(player);
        }
    }
}
