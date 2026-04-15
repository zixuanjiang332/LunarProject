package jdd.lunarProject.Build;

import java.util.List;

public record RelicDefinition(
        String id,
        String name,
        String rarity,
        List<String> tags,
        String description,
        BuildModifierType effectType,
        double effectValue,
        String stackRule,
        String mythicItemId
) {
}
