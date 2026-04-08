package jdd.lunarProject.MobsListener;
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import jdd.lunarProject.Game.Game;
import jdd.lunarProject.Game.GameManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import java.util.UUID;
public class GameMobDeathListener implements Listener {

    @EventHandler
    public void onMythicMobDeath(MythicMobDeathEvent event) {
        // 在 5.11.0 中，event.getEntity() 返回的是 Bukkit Entity
        UUID deadMobUuid = event.getEntity().getUniqueId();

        // 查询这只怪物属于哪个游戏房间
        Game game = GameManager.getGameByMob(deadMobUuid);

        if (game != null && game.isRunning()) {
            // 通知回合管理器：扣减存活怪物数量
            game.getRoundManager().handleMobDeath(deadMobUuid);
        }
    }
}