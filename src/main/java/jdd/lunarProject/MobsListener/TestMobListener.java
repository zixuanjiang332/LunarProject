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
        ItemStack ultimateWeapon = ItemSkillManager.createSkillWeapon(
                "Testitem",  // MM 中配置的外观和基础属性
                "TEST_LEFT",            // 左键：基础斩击 (1秒CD)
                "TEST_RIGHT",           // 右键：重型劈砍 (5秒CD)
                null
        );
        ItemStack ultimateArmor = ItemSkillManager.createSkillArmor(
                "Abyss_Boots",  // MM 中配置的外观和基础属性
                "TEST_EQUIP",
                "TEST_UNEQUIP"
        );
        if (ultimateWeapon != null) {
            player.getInventory().addItem(ultimateWeapon);
        }
    }
}