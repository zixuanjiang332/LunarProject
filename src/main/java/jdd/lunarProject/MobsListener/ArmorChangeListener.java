package jdd.lunarProject.MobsListener;
import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import jdd.lunarProject.SkillManager.ItemSkillManager;
import jdd.lunarProject.SkillManager.SkillCastManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

public class ArmorChangeListener implements Listener {
    @EventHandler
    public void onArmorChange(PlayerArmorChangeEvent event) {
        Player player = event.getPlayer();
        ItemStack oldItem = event.getOldItem();
        ItemStack newItem = event.getNewItem();

        // 1. 读取卸下防具的技能
        if (oldItem != null && !oldItem.getType().isAir()) {
            String unequipSkillId = ItemSkillManager.getSkillFromItem(oldItem, "UNEQUIP");
            if (unequipSkillId != null) {
                SkillCastManager.tryCastSkill(player, unequipSkillId);
            }
        }

        // 2. 读取穿上防具的技能
        if (newItem != null && !newItem.getType().isAir()) {
            String equipSkillId = ItemSkillManager.getSkillFromItem(newItem, "EQUIP");
            if (equipSkillId != null) {
                SkillCastManager.tryCastSkill(player, equipSkillId);
            }
        }
    }
}