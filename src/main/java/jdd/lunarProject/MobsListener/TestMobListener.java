package jdd.lunarProject.MobsListener;

import io.lumine.mythic.bukkit.events.MythicMobSpawnEvent;
import jdd.lunarProject.SkillManager.ItemSkillManager;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

public class TestMobListener implements Listener {
    @EventHandler
    public void onMobSpawn(MythicMobSpawnEvent event){
        event.getMob().getEntity().setHealth(event.getMob().getEntity().getHealth()*1000);
    }
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        player.setHealthScaled( true);
        player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(2000);
        player.setHealthScale(20);
    }
}