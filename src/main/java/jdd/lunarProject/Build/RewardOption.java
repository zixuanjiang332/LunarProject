package jdd.lunarProject.Build;

public record RewardOption(
        RewardType rewardType,
        String targetId,
        String name,
        String rarity,
        String description,
        String mythicItemId
) {
    public enum RewardType {
        RELIC,
        SERVICE
    }
}
