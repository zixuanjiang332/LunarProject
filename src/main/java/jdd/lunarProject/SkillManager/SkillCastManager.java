package jdd.lunarProject.SkillManager;
import io.lumine.mythic.bukkit.MythicBukkit;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
public class SkillCastManager {
    private static final Map<String, WeaponSkill> SKILL_REGISTRY = new HashMap<>();
    public static void initSkills() {
        // 参数：ID, MM里的技能名, 中文名, 冷却时间(秒), 理智消耗
        register(new WeaponSkill("TEST_LEFT", "TEST_LEFT"));
        register(new WeaponSkill("TEST_RIGHT", "TEST_RIGHT"));
        register(new WeaponSkill("TEST_EQUIP", "TEST_EQUIP"));
        register(new WeaponSkill("TEST_UNEQUIP", "TEST_UNEQUIP"));
    }

    private static void register(WeaponSkill skill) {
        SKILL_REGISTRY.put(skill.getId(), skill);
    }

    /**
     * 尝试释放技能的核心方法
     */
    public static void tryCastSkill(Player player, String skillId) {
        // 1. 检查技能是否注册
        WeaponSkill skill = SKILL_REGISTRY.get(skillId);
        if (skill == null) return;
        boolean success = MythicBukkit.inst().getAPIHelper().castSkill(player, skill.getMmSkillName());
        if (success) {
            player.sendActionBar(Component.text("§a▶ 成功释放"));
        } else {
            player.sendActionBar(Component.text("§c▶ "+ " 释放失败(条件未满足)"));
        }
    }
}