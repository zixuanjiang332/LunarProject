package jdd.lunarProject.Build;

import java.util.List;

public record RewardPoolDefinition(
        String id,
        int count,
        List<String> relics,
        List<String> services
) {
}
