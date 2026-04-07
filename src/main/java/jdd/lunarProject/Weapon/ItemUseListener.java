package jdd.lunarProject.Weapon;
import jdd.lunarProject.SkillManager.ItemSkillManager;
import org.bukkit.event.Listener;

public class ItemUseListener implements Listener {
    private final ItemSkillManager itemSkillManager;
    public ItemUseListener(ItemSkillManager skillManager) {
        this.itemSkillManager = skillManager;
    }
}