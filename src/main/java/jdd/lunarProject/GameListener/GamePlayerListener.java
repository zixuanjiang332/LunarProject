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
    // ========== 1. 防破坏系统 ==========
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Game game = GameManager.getPlayerGame(player.getUniqueId());
        // 如果玩家在游戏中，且不是管理员，禁止拆方块
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

    // ========== 2. 死亡与自动重生系统 ==========
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Game game = GameManager.getPlayerGame(player.getUniqueId());

        if (game != null && game.isRunning()) {
            event.setKeepInventory(true);  // 保留物品
            event.getDrops().clear();      // 不掉落物品
            event.setDroppedExp(0);        // 不掉落经验

            // 延迟 2 tick 强制玩家自动重生，跳过原版的“你死了”画面
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (player.isOnline()) {
                        player.spigot().respawn();
                    }
                }
            }.runTaskLater(LunarProject.getInstance(), 2L);
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Game game = GameManager.getPlayerGame(player.getUniqueId());

        if (game != null && game.isRunning()) {
            // 重生时传送到当前关卡中心
            Location loc = game.getRoundManager().getCurrentStage().getBossLocation();
            if (loc != null) {
                event.setRespawnLocation(loc);
            }

            // 延迟 1 tick 将玩家移入死亡名单并设置为旁观模式
            new BukkitRunnable() {
                @Override
                public void run() {
                    game.onPlayerDeath(player);
                }
            }.runTaskLater(LunarProject.getInstance(), 1L);
        }
    }
}