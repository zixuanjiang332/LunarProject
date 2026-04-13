package jdd.lunarProject.Build;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class PlayerRunData {
    private final List<String> relicIds = new ArrayList<>();
    private final Map<BuildModifierType, Double> temporaryModifiers = new EnumMap<>(BuildModifierType.class);
    private final List<String> rewardHistory = new ArrayList<>();
    private int rewardCount = 0;

    public List<String> getRelicIds() {
        return relicIds;
    }

    public Map<BuildModifierType, Double> getTemporaryModifiers() {
        return temporaryModifiers;
    }

    public List<String> getRewardHistory() {
        return rewardHistory;
    }

    public int getRewardCount() {
        return rewardCount;
    }

    public void incrementRewardCount() {
        rewardCount++;
    }
}
