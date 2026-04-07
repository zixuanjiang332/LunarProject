package jdd.lunarProject.Weapon;
public class WeaponSkill {
    private final String id;
    private final String mmSkillName;
    private final String displayName;
    private final String description; // 【新增】技能介绍
    public WeaponSkill(String id, String mmSkillName, String displayName, String description) {
        this.id = id;
        this.mmSkillName = mmSkillName;
        this.displayName = displayName;
        this.description = description;
    }
    public String getId() { return id; }
    public String getMmSkillName() { return mmSkillName; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
}