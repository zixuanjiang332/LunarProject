package jdd.lunarProject.SkillManager;
public class WeaponSkill {
    private final String id;            // 插件内部唯一识别码 (写在物品NBT里的)
    private final String mmSkillName;   // 对应的 MythicMobs 技能名称


    public WeaponSkill(String id, String mmSkillName) {
        this.id = id;
        this.mmSkillName = mmSkillName;
    }
    // --- Getters ---
    public String getId() { return id; }
    public String getMmSkillName() { return mmSkillName; }
}