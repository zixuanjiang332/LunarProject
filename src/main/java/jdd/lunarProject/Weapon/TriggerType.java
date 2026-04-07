package jdd.lunarProject.Weapon;
public enum TriggerType {
    LEFT_CLICK("【左键技能】"),
    RIGHT_CLICK("【右键技能】"),
    SHIFT_RIGHT_CLICK("【潜行右键】"),
    HIT_ENTITY("【命中触发】"),
    EQUIP("【穿戴激活】"),
    UNEQUIP("【卸下触发】");
    private final String displayName;

    TriggerType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}