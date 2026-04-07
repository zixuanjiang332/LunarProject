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
}