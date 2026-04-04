package jdd.lunarProject.SkillManager;

import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.items.MythicItem;
import jdd.lunarProject.LunarProject;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
public class ItemSkillManager {
    // 不再需要构造器里写死固定的 SKILL_KEY 了
    public ItemSkillManager() {}
    /**
     * @param triggerType 触发方式 (例如: "LEFT_CLICK", "RIGHT_CLICK")
     */
    public static ItemStack applySkillToItem(ItemStack item, String triggerType, String mmSkillName) {
        if (item == null || !item.hasItemMeta()) return item;
        ItemMeta meta = item.getItemMeta();
        NamespacedKey key = new NamespacedKey(LunarProject.getInstance(), "mm_skill_" + triggerType.toLowerCase());
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, mmSkillName);
        item.setItemMeta(meta);
        return item;
    }

    public static String getSkillFromItem(ItemStack item, String triggerType) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        NamespacedKey key = new NamespacedKey(LunarProject.getInstance(), "mm_skill_" + triggerType.toLowerCase());
        return meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    public static ItemStack getMythicItem(String internalName) {
        java.util.Optional<MythicItem> maybeItem = MythicBukkit.inst().getItemManager().getItem(internalName);
        if (maybeItem.isPresent()) {
            return MythicBukkit.inst().getItemManager().getItemStack(internalName);
        }
        return null;
    }
}