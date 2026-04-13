package jdd.lunarProject.Build;

import io.lumine.mythic.bukkit.MythicBukkit;
import jdd.lunarProject.Game.Game;
import jdd.lunarProject.LunarProject;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class RewardManager {
    private static final Map<String, RewardPoolDefinition> rewardPools = new HashMap<>();
    private static final Map<String, ServiceDefinition> serviceDefinitions = new HashMap<>();
    private static final List<String> loadMessages = new ArrayList<>();

    private RewardManager() {
    }

    public static List<String> init() {
        rewardPools.clear();
        serviceDefinitions.clear();
        loadMessages.clear();

        File file = new File(LunarProject.getInstance().getDataFolder(), "rewards.yml");
        if (!file.exists()) {
            LunarProject.getInstance().saveResource("rewards.yml", false);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        loadServices(config.getConfigurationSection("services"));
        loadPools(config.getConfigurationSection("pools"));
        loadMessages.add("Loaded " + rewardPools.size() + " reward pools and " + serviceDefinitions.size() + " service rewards.");
        return List.copyOf(loadMessages);
    }

    public static List<RewardOption> generateRewardOptions(Game game, Player player, String poolId) {
        RewardPoolDefinition pool = rewardPools.get(poolId);
        if (pool == null) {
            return List.of();
        }

        List<RewardOption> candidates = new ArrayList<>();
        for (String relicId : pool.relics()) {
            if (!RelicManager.hasRelic(relicId)) {
                continue;
            }
            if (game.hasRelic(player.getUniqueId(), relicId)) {
                continue;
            }
            RelicDefinition relicDefinition = RelicManager.getRelic(relicId);
            candidates.add(new RewardOption(
                    RewardOption.RewardType.RELIC,
                    relicDefinition.id(),
                    relicDefinition.name(),
                    relicDefinition.rarity(),
                    relicDefinition.description()
            ));
        }

        for (String serviceId : pool.services()) {
            ServiceDefinition serviceDefinition = serviceDefinitions.get(serviceId);
            if (serviceDefinition == null) {
                continue;
            }
            candidates.add(new RewardOption(
                    RewardOption.RewardType.SERVICE,
                    serviceDefinition.id(),
                    serviceDefinition.name(),
                    serviceDefinition.rarity(),
                    serviceDefinition.description()
            ));
        }

        if (candidates.isEmpty()) {
            return List.of();
        }

        Collections.shuffle(candidates);
        List<RewardOption> selected = new ArrayList<>();
        Set<String> seenOptionKeys = new LinkedHashSet<>();
        for (RewardOption candidate : candidates) {
            if (selected.size() >= pool.count()) {
                break;
            }
            String uniqueKey = candidate.rewardType() + ":" + candidate.targetId();
            if (seenOptionKeys.add(uniqueKey)) {
                selected.add(candidate);
            }
        }

        return selected;
    }

    public static RewardPoolDefinition getPool(String poolId) {
        return rewardPools.get(poolId);
    }

    public static ServiceDefinition getService(String serviceId) {
        return serviceDefinitions.get(serviceId);
    }

    public static boolean applyReward(Game game, Player player, RewardOption rewardOption) {
        if (rewardOption.rewardType() == RewardOption.RewardType.RELIC) {
            RelicDefinition relicDefinition = RelicManager.getRelic(rewardOption.targetId());
            if (relicDefinition == null || !game.addRelic(player.getUniqueId(), relicDefinition.id())) {
                return false;
            }
            game.recordReward(player.getUniqueId(), "Relic - " + relicDefinition.name());
            LunarProject.getInstance().getLogger().info("[Reward] " + player.getName() + " picked relic " + relicDefinition.id());
            return true;
        }

        ServiceDefinition serviceDefinition = serviceDefinitions.get(rewardOption.targetId());
        if (serviceDefinition == null) {
            return false;
        }

        applyServiceEffect(game, player, serviceDefinition);
        game.recordReward(player.getUniqueId(), "Service - " + serviceDefinition.name());
        LunarProject.getInstance().getLogger().info("[Reward] " + player.getName() + " picked service " + serviceDefinition.id());
        return true;
    }

    public static String getPoolForStageType(String stageType) {
        if (stageType == null) {
            return "normal_clear";
        }
        return switch (stageType.toUpperCase(Locale.ROOT)) {
            case "ELITE" -> "elite_clear";
            case "SHOP" -> "shop_room";
            case "REST" -> "rest_room";
            case "EVENT" -> "event_room";
            case "BOSS" -> "boss_clear";
            default -> "normal_clear";
        };
    }

    private static void applyServiceEffect(Game game, Player player, ServiceDefinition serviceDefinition) {
        String effectType = serviceDefinition.effectType().toUpperCase(Locale.ROOT);
        double effectValue = serviceDefinition.effectValue();

        switch (effectType) {
            case "FULL_HEAL" -> game.healPlayerToFull(player);
            case "GAIN_SANITY" -> game.changePlayerSanity(player, (int) Math.round(effectValue));
            case "CLEANSE_NEGATIVE" -> {
                for (PotionEffectType effectTypeEntry : new PotionEffectType[]{
                        PotionEffectType.POISON,
                        PotionEffectType.WITHER,
                        PotionEffectType.WEAKNESS,
                        PotionEffectType.SLOWNESS,
                        PotionEffectType.MINING_FATIGUE,
                        PotionEffectType.BLINDNESS,
                        PotionEffectType.NAUSEA,
                        PotionEffectType.UNLUCK
                }) {
                    player.removePotionEffect(effectTypeEntry);
                }
            }
            case "TEMP_DAMAGE_DEALT_MULT" ->
                    game.addTemporaryModifier(player.getUniqueId(), BuildModifierType.DAMAGE_DEALT_MULT, effectValue, serviceDefinition.name());
            case "TEMP_DAMAGE_TAKEN_MULT" ->
                    game.addTemporaryModifier(player.getUniqueId(), BuildModifierType.DAMAGE_TAKEN_MULT, effectValue, serviceDefinition.name());
            case "TEMP_POST_COMBAT_HEAL_BONUS" ->
                    game.addTemporaryModifier(player.getUniqueId(), BuildModifierType.POST_COMBAT_HEAL_BONUS, effectValue, serviceDefinition.name());
            case "TEMP_SANITY_GAIN_BONUS" ->
                    game.addTemporaryModifier(player.getUniqueId(), BuildModifierType.SANITY_GAIN_BONUS, effectValue, serviceDefinition.name());
            case "TEMP_FRAGILITY_BONUS" ->
                    game.addTemporaryModifier(player.getUniqueId(), BuildModifierType.FRAGILITY_BONUS, effectValue, serviceDefinition.name());
            case "RESTORE_HEALTH_PERCENT" -> {
                if (player.getAttribute(Attribute.MAX_HEALTH) != null) {
                    double maxHealth = player.getAttribute(Attribute.MAX_HEALTH).getValue();
                    double nextHealth = Math.min(maxHealth, player.getHealth() + (maxHealth * effectValue));
                    player.setHealth(Math.max(1.0, nextHealth));
                }
            }
            case "RESTORE_SANITY_AND_HEAL" -> {
                game.changePlayerSanity(player, (int) Math.round(effectValue));
                game.healPlayerToFull(player);
            }
            default -> LunarProject.getInstance().getLogger().warning("Unknown service effect type: " + serviceDefinition.effectType());
        }
    }

    private static void loadServices(ConfigurationSection section) {
        if (section == null) {
            return;
        }

        for (String serviceId : section.getKeys(false)) {
            ConfigurationSection serviceSection = section.getConfigurationSection(serviceId);
            if (serviceSection == null) {
                continue;
            }

            serviceDefinitions.put(serviceId, new ServiceDefinition(
                    serviceId,
                    serviceSection.getString("name", serviceId),
                    serviceSection.getString("rarity", "COMMON"),
                    serviceSection.getStringList("tags"),
                    serviceSection.getString("description", "No description."),
                    serviceSection.getString("effect_type", "GAIN_SANITY"),
                    serviceSection.getDouble("effect_value", 0.0)
            ));
        }
    }

    private static void loadPools(ConfigurationSection section) {
        if (section == null) {
            return;
        }

        for (String poolId : section.getKeys(false)) {
            ConfigurationSection poolSection = section.getConfigurationSection(poolId);
            if (poolSection == null) {
                continue;
            }

            rewardPools.put(poolId, new RewardPoolDefinition(
                    poolId,
                    Math.max(1, poolSection.getInt("count", 3)),
                    poolSection.getStringList("relics"),
                    poolSection.getStringList("services")
            ));
        }
    }
}
