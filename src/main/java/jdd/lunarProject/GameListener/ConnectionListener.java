package jdd.lunarProject.GameListener;
import jdd.lunarProject.Game.Game;
import jdd.lunarProject.Game.GameManager;
import net.kyori.adventure.text.Component;
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
            game.handleDisconnect(player); // 触发我们刚刚写的断网逻辑
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Game game = GameManager.getPlayerGame(player.getUniqueId());
        // 如果玩家上线时发现他的 UUID 还在某个游戏的记录里，说明他断线重连了
        if (game != null) {
            game.handleReconnect(player); // 触发重连逻辑，切为旁观者
        }
        else{
            player.sendMessage(Component.text("&6&l原游戏已结束！"));
        }
    }
}