package jdd.lunarProject.SkillManager;
import io.lumine.mythic.bukkit.MythicBukkit;
import jdd.lunarProject.Weapon.WeaponSkill;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public class SkillCastManager {
    private static final Map<String, WeaponSkill> SKILL_REGISTRY = new HashMap<>();
    public static void initSkills() {
        // 参数：ID, MM里的技能名, 中文名, 技能介绍
        register(new WeaponSkill("MM_LeftSlash", "TEST_LEFT", "基础斩击", "向前挥出一道剑气，造成小幅度斩击伤害。"));
        register(new WeaponSkill("MM_HeavyCleave", "TEST_RIGHT", "重型劈砍", "蓄力砸击地面，对范围内敌人造成物理伤害。"));
        register(new WeaponSkill("MM_EquipBuff", "TEST_EQUIP", "深渊庇护", "穿戴时获得生命值上限提升。"));
        register(new WeaponSkill("MM_UnequipDebuff", "TEST_UNEQUIP", "深渊反噬", "脱下时受到少量的真实伤害。"));
        register(new WeaponSkill("MM_SaySanity", "SAY_SANITY", "检测理智", "左键点击检测当前理智值。"));
        register(new WeaponSkill("MM_ResetSanity", "RESET_SANITY", "重置理智", "右键点击将理智归零。"));
    }

    private static void register(WeaponSkill skill) {
        SKILL_REGISTRY.put(skill.getId(), skill);
    }

    public static void tryCastSkill(Player player, String skillId) {
        WeaponSkill skill = SKILL_REGISTRY.get(skillId);
        if (skill == null) return;
        boolean success = MythicBukkit.inst().getAPIHelper().castSkill(player, skill.getMmSkillName());
        if (success) {
            player.sendActionBar(Component.text("§a▶ 触发技能: " + skill.getDisplayName()));
        } else {
            player.sendActionBar(Component.text("§c▶ 技能施放失败"));
        }
    }

    public static WeaponSkill getSkill(String id) {
        return SKILL_REGISTRY.get(id);
    }
}