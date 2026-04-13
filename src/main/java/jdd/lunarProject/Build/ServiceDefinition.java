package jdd.lunarProject.Build;

import java.util.List;

public record ServiceDefinition(
        String id,
        String name,
        String rarity,
        List<String> tags,
        String description,
        String effectType,
        double effectValue
) {
}
