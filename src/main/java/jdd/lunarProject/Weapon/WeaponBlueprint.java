package jdd.lunarProject.Weapon;
import jdd.lunarProject.SkillManager.ItemSkillManager;
import org.bukkit.inventory.ItemStack;
import java.util.HashMap;
import java.util.Map;
public class WeaponBlueprint {
    public final String internalId;
    private final String mmItemName;
    private final TriggerType[] displaySlots; // 这个武器该展示哪些孔位？
    private final Map<TriggerType, String> skills = new HashMap<>();
    // 构造器：强制要求传入你要展示的槽位
    public WeaponBlueprint(String internalId, String mmItemName, TriggerType... displaySlots) {
        this.internalId = internalId;
        this.mmItemName = mmItemName;
        this.displaySlots = displaySlots;
    }

    public WeaponBlueprint addSkill(TriggerType trigger, String skillId) {
        this.skills.put(trigger, skillId);
        return this;
    }
    // 终极生成方法！
    public ItemStack build() {
        ItemStack item = ItemSkillManager.getMythicItem(this.mmItemName);
        if (item == null) return null;
        // 1. 给物品打上隐形的 PDC 标签
        for (Map.Entry<TriggerType, String> entry : skills.entrySet()) {
            item = ItemSkillManager.applySkillToItem(item, entry.getKey().name(), entry.getValue());
        }
        // 2. 调用刚刚写的更新器，渲染出无敌好看的 Lore 排版
        return ItemSkillManager.updateSkillLore(item, this.displaySlots);
    }
    public String getMmItemName() { return mmItemName; }
    public Map<TriggerType, String> getSkills() { return skills; }
}