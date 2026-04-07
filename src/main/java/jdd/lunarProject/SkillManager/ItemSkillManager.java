package jdd.lunarProject.SkillManager;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.items.MythicItem;
import jdd.lunarProject.LunarProject;
import jdd.lunarProject.Weapon.TriggerType;
import jdd.lunarProject.Weapon.WeaponSkill;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

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
    public static ItemStack updateSkillLore(ItemStack item, TriggerType... displaySlots) {
        if (item == null || !item.hasItemMeta()) return item;
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();

        List<String> currentLore = meta.hasLore() ? meta.getLore() : new java.util.ArrayList<>();
        List<String> cleanLore = new java.util.ArrayList<>();

        // 1. 保留原本的 Lore（比如 MM 配置的攻击力、背景故事），直到遇到我们的"技能分割线"
        String divider = "§8§m━━━§7 灵魂技能 §8§m━━━";
        for (String line : currentLore) {
            if (line.equals(divider)) break; // 遇到旧的分割线就停止读取，相当于清理掉旧技能描述
            cleanLore.add(line);
        }

        // 2. 插入新的技能分割线
        if (!cleanLore.isEmpty()) cleanLore.add(""); // 留个空行更美观
        cleanLore.add(divider);

        // 3. 动态生成全新的技能介绍！
        for (TriggerType type : displaySlots) {
            cleanLore.add("§b" + type.getDisplayName() + " "); // 打印：【左键技能】

            // 去 PDC 里找这个孔位有没有绑技能
            String skillId = getSkillFromItem(item, type.name());

            if (skillId != null) {
                WeaponSkill skill = SkillCastManager.getSkill(skillId);
                if (skill != null) {
                    cleanLore.add("  §e▶ §6" + skill.getDisplayName());
                    cleanLore.add("    §7" + skill.getDescription());
                } else {
                    cleanLore.add("  §c▶ 技能数据丢失");
                }
            } else {
                // 没有技能的话，默认显示 无
                cleanLore.add("  §8▶ 无");
            }
        }

        cleanLore.add("§8§m━━━━━━━━━━━━"); // 底部收尾

        meta.setLore(cleanLore);
        item.setItemMeta(meta);
        return item;
    }
    public static ItemStack getMythicItem(String internalName) {
        java.util.Optional<MythicItem> maybeItem = MythicBukkit.inst().getItemManager().getItem(internalName);
        if (maybeItem.isPresent()) {
            return MythicBukkit.inst().getItemManager().getItemStack(internalName);
        }
        return null; // 如果 MM 里没这个物品，或者你朋友名字拼错了，安全返回 null
    }
}