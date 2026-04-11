package jdd.lunarProject.MobsListener;
import io.lumine.mythic.bukkit.MythicBukkit;
import jdd.lunarProject.Game.StaggerManager;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

public class StaggerListener implements Listener {
    private final StaggerManager staggerManager;
    public StaggerListener(StaggerManager staggerManager) {
        this.staggerManager = staggerManager;
    }
    private StaggerManager.StaggerConfig getPlayerStaggerConfig(Player player) {
        String playerClass = getPlayerClass(player);
        return staggerManager.getConfigForClass(playerClass);
    }
    private String getPlayerClass(Player player) {
        // 【方案A】：如果你把玩家的职业存在了 MythicMobs 的玩家变量里 (推荐，高度统合)
        // 例如：你在玩家选职业时执行了 - setvariable{var=player.class_name;type=STRING;val="Mage"}
        var variables = MythicBukkit.inst().getPlayerManager().getProfile(player).getVariables();
        if (variables.has("class_name")) {
            return variables.getString("class_name");
        }
        // 【方案B】：如果你使用了 MMOCore 这种职业插件
        // return net.Indyuce.mmocore.api.player.PlayerData.get(player.getUniqueId()).getProfess().getId();

        // 【方案C】：如果通过原版的 Scoreboard Tag 或者 Permission 判断
        // if (player.hasPermission("class.mage")) return "Mage";

        // 默认返回一个基础职业或空，确保不会抛出空指针
        return "None";
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        StaggerManager.StaggerConfig config = getPlayerStaggerConfig(player); // 伪代码调用
        if (config == null) return;

        // 2. 获取最大血量，计算百分比
        double maxHealth = player.getAttribute(Attribute.MAX_HEALTH).getValue();
        double currentHealth = player.getHealth();
        double expectedHealth = currentHealth - event.getFinalDamage();

        // 【算法核心】：被砍之前的血量百分比 vs 被砍之后的预期血量百分比
        double currentPct = currentHealth / maxHealth;
        double expectedPct = expectedHealth / maxHealth;

        // 3. 【优先判定 T2】跨越二阶段阈值
        // 判定逻辑：原本血量在 T2 之上，这一刀下去血量掉到了 T2 以下（包含等于）
        if (config.t2 > 0 && currentPct > config.t2 && expectedPct <= config.t2) {
            // 呼叫 MM 接口：执行深层混乱！
            MythicBukkit.inst().getAPIHelper().castSkill(player, "System_Apply_Stagger_T2", player, player.getLocation(), null, null, 1.0f);
            return; // 触发了 T2 就直接 return，不走 T1 判断
        }

        // 4. 【其次判定 T1】跨越一阶段阈值
        if (config.t1 > 0 && currentPct > config.t1 && expectedPct <= config.t1) {
            // 呼叫 MM 接口：执行一阶段混乱！
            MythicBukkit.inst().getAPIHelper().castSkill(player, "System_Apply_Stagger_T1", player, player.getLocation(), null, null, 1.0f);
        }
    }
    // 获取缓存配置的示例方法
}