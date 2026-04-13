package jdd.lunarProject.Build;

public record RewardOption(
        RewardType rewardType,
        String targetId,
        String name,
        String rarity,
        String description
) {
    public enum RewardType {
        RELIC,
        SERVICE
    }
}
