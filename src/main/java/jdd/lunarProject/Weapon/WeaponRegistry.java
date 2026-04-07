package jdd.lunarProject.Weapon;
import jdd.lunarProject.SkillManager.ItemSkillManager;
import org.bukkit.inventory.ItemStack;
import java.util.HashMap;
import java.util.Map;

public class WeaponRegistry {
    // 存储所有的武器蓝图
    private static final Map<String, WeaponBlueprint> REGISTRY = new HashMap<>();
    // 插件启动时调用，把你所有的武器都注册在这里！
    public static void init() {
        // 【案例 1】：普通的测试剑
        register(new WeaponBlueprint("TEST_SWORD", "Testitem")
                .addSkill(TriggerType.LEFT_CLICK, "TEST_LEFT")
                .addSkill(TriggerType.RIGHT_CLICK, "TEST_RIGHT")
        );
        register(new WeaponBlueprint("SANITY_TESTER", "Sanity_Tester",
                TriggerType.LEFT_CLICK, TriggerType.RIGHT_CLICK)
                .addSkill(TriggerType.LEFT_CLICK, "SAY_SANITY")
                .addSkill(TriggerType.RIGHT_CLICK, "RESET_SANITY")
        );

        // 【案例 3】：深渊战靴 (防具)
        register(new WeaponBlueprint("ABYSS_BOOTS", "Abyss_Boots")
                .addSkill(TriggerType.EQUIP, "TEST_EQUIP")
                .addSkill(TriggerType.UNEQUIP, "TEST_UNEQUIP")
        );
    }

    private static void register(WeaponBlueprint blueprint) {
        REGISTRY.put(blueprint.internalId, blueprint);
    }

    public static ItemStack createItem(String internalId) {
        WeaponBlueprint blueprint = REGISTRY.get(internalId);
        if (blueprint == null) return null;
        return blueprint.build();
    }
}