package jdd.lunarProject.GameListener;

import jdd.lunarProject.Game.Game;
import jdd.lunarProject.Game.GameManager;
import jdd.lunarProject.LunarProject;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class GamePlayerListener implements Listener {
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Game game = GameManager.getPlayerGame(player.getUniqueId());
        if (game != null && !player.hasPermission("lunarproject.admin")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Game game = GameManager.getPlayerGame(player.getUniqueId());
        if (game != null && !player.hasPermission("lunarproject.admin")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Game game = GameManager.getPlayerGame(player.getUniqueId());
        if (game == null || !game.isRunning()) {
            return;
        }

        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setDroppedExp(0);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    player.spigot().respawn();
                }
            }
        }.runTaskLater(LunarProject.getInstance(), 2L);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Game game = GameManager.getPlayerGame(player.getUniqueId());
        if (game == null || !game.isRunning()) {
            return;
        }

        Location respawnLocation = null;
        if (game.getRoundManager().getCurrentStage() != null) {
            respawnLocation = game.getRoundManager().getCurrentStage().getBossLocation();
            if (respawnLocation == null && game.getRoundManager().getCurrentStage().getWorld() != null) {
                respawnLocation = game.getRoundManager().getCurrentStage().getWorld().getSpawnLocation();
            }
        }

        if (respawnLocation == null) {
            respawnLocation = game.getLobbyMap().getSpawnLocation();
        }

        if (respawnLocation != null) {
            event.setRespawnLocation(respawnLocation);
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                game.onPlayerDeath(player);
            }
        }.runTaskLater(LunarProject.getInstance(), 1L);
    }
}
