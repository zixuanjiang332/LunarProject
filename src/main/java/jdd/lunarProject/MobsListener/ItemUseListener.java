package jdd.lunarProject.MobsListener;

import io.lumine.mythic.bukkit.MythicBukkit;
import jdd.lunarProject.LunarProject;
import jdd.lunarProject.SkillManager.ItemSkillManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
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
        // 【关键修复】过滤掉副手的事件触发，防止右键时技能执行两次
        if (event.getHand() != EquipmentSlot.HAND) return;

        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        // 明确告诉 Manager：我要读取的是"右键"绑定的技能
        String skillName = ItemSkillManager.getSkillFromItem(item, "RIGHT_CLICK");

        if (skillName != null) {
            event.setCancelled(true);
            boolean success = MythicBukkit.inst().getAPIHelper().castSkill(player, skillName);
            if (success) {
                // Paper 1.21 推荐使用 MiniMessage 解析颜色代码 (或者保留你原来的写法)
                player.sendMessage(Component.text("§6§l" + skillName + " 技能释放成功(右键)"));
            } else {
                player.sendMessage(Component.text("§c§l" + skillName + " 技能不存在或正在冷却"));
            }
        }
    }

    @EventHandler
    public void onPlayerLeftClick(PlayerInteractEvent event) {
        // 左键通常不需要过滤副手，但为了严谨可以加上
        if (event.getHand() != EquipmentSlot.HAND) return;

        if (event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        // 明确告诉 Manager：我要读取的是"左键"绑定的技能
        String skillName = ItemSkillManager.getSkillFromItem(item, "LEFT_CLICK");

        if (skillName != null) {
            event.setCancelled(true);
            boolean success = MythicBukkit.inst().getAPIHelper().castSkill(player, skillName);
            if (success) {
                player.sendMessage(Component.text("§6§l" + skillName + " 技能释放成功(左键)"));
            } else {
                player.sendMessage(Component.text("§c§l" + skillName + " 技能不存在或正在冷却"));
            }
        }
    }
}