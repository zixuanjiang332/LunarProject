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
    public void onPlayerJoin (PlayerJoinEvent event){
        Player player = event.getPlayer();
        player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20000);
        player.setHealthScaled(true);
        player.setHealthScale(20);

        ItemStack testItem = ItemSkillManager.getMythicItem("Example_Player_Weapon");

        if (testItem != null) {
            testItem = ItemSkillManager.applySkillToItem(testItem, "RIGHT_CLICK", "Player_Weapon_RightClick");
            testItem = ItemSkillManager.applySkillToItem(testItem, "LEFT_CLICK", "Player_Weapon_LeftClick");
            // testItem = ItemSkillManager.applySkillToItem(testItem, "SHIFT_RIGHT_CLICK", "Player_Ultimate_Skill");

            player.getInventory().addItem(testItem);
        }
    }
}