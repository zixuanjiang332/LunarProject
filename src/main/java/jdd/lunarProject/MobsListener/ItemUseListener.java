package jdd.lunarProject.MobsListener;
import jdd.lunarProject.SkillManager.ItemSkillManager;
import jdd.lunarProject.SkillManager.SkillCastManager; // 引入刚刚写的管理类
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class ItemUseListener implements Listener {
    private final ItemSkillManager itemSkillManager;

    public ItemUseListener(ItemSkillManager skillManager) {
        this.itemSkillManager = skillManager;
    }
    @EventHandler
    public void onPlayerRightClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        // 获取武器上的技能ID (比如 "HEAVY_CLEAVE")
        String skillId = ItemSkillManager.getSkillFromItem(item, "RIGHT_CLICK");
        if (skillId != null) {
            event.setCancelled(true);
            SkillCastManager.tryCastSkill(player, skillId);
        }
    }

    @EventHandler
    public void onPlayerLeftClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        String skillId = ItemSkillManager.getSkillFromItem(item, "LEFT_CLICK");

        if (skillId != null) {
            event.setCancelled(true);
            SkillCastManager.tryCastSkill(player, skillId);
        }
    }
}