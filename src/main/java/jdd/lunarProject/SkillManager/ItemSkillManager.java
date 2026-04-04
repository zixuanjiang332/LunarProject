package jdd.lunarProject.SkillManager;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.items.MythicItem;
import jdd.lunarProject.LunarProject;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class ItemSkillManager {

    /**
     * 基础方法：向已有物品追加技能标签
     */
    public static ItemStack applySkillToItem(ItemStack item, String triggerType, String skillId) {
        if (item == null || !item.hasItemMeta() || skillId == null) return item;
        ItemMeta meta = item.getItemMeta();
        NamespacedKey key = new NamespacedKey(LunarProject.getInstance(), "mm_skill_" + triggerType.toLowerCase());
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, skillId);
        item.setItemMeta(meta);
        return item;
    }
    /**
     * 基础方法：读取物品上的技能
     */
    public static String getSkillFromItem(ItemStack item, String triggerType) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        NamespacedKey key = new NamespacedKey(LunarProject.getInstance(), "mm_skill_" + triggerType.toLowerCase());
        return meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }
    /**
     * 【全新进阶方法】：一键生成并装配完整的神兵利器
     * * @param mmItemInternalName 你的朋友在 MM Items 文件夹里写的物品 ID
     * @param leftSkillId        左键触发的技能 ID (可填 null)
     * @param rightSkillId       右键触发的技能 ID (可填 null)
     * @param shiftRightSkillId  潜行右键触发的技能 ID (可填 null)
     * @return 已经带有各种 PDC 技能标签的成品武器
     */
    public static ItemStack createSkillWeapon(String mmItemInternalName, String leftSkillId, String rightSkillId, String shiftRightSkillId) {
        // 1. 获取基础 MM 物品
        java.util.Optional<MythicItem> maybeItem = MythicBukkit.inst().getItemManager().getItem(mmItemInternalName);
        if (maybeItem.isEmpty()) return null;

        ItemStack weapon = MythicBukkit.inst().getItemManager().getItemStack(mmItemInternalName);
        // 2. 一键批量附上技能
        if (leftSkillId != null) weapon = applySkillToItem(weapon, "LEFT_CLICK", leftSkillId);
        if (rightSkillId != null) weapon = applySkillToItem(weapon, "RIGHT_CLICK", rightSkillId);
        if (shiftRightSkillId != null) weapon = applySkillToItem(weapon, "SHIFT_RIGHT_CLICK", shiftRightSkillId);

        return weapon;
    }
    public static ItemStack createSkillArmor(String mmItemInternalName, String equipSkillId, String unequipSkillId) {
        java.util.Optional<MythicItem> maybeItem = MythicBukkit.inst().getItemManager().getItem(mmItemInternalName);
        if (maybeItem.isEmpty()) return null;
        ItemStack armor = MythicBukkit.inst().getItemManager().getItemStack(mmItemInternalName);

        if (equipSkillId != null) applySkillToItem(armor, "EQUIP", equipSkillId);
        if (unequipSkillId != null) applySkillToItem(armor, "UNEQUIP", unequipSkillId);

        return armor;
    }
}