package jdd.lunarProject.Game;

import jdd.lunarProject.LunarProject;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MythicMobRegistry {
    private static final Set<String> allMobIds = new LinkedHashSet<>();
    private static final List<String> combatMobIds = new ArrayList<>();
    private static final List<String> bossMobIds = new ArrayList<>();
    private static final List<String> loadMessages = new ArrayList<>();

    private MythicMobRegistry() {
    }

    public static List<String> load() {
        allMobIds.clear();
        combatMobIds.clear();
        bossMobIds.clear();
        loadMessages.clear();

        File mobsFolder = new File(new File(LunarProject.getInstance().getDataFolder().getParentFile(), "MythicMobs"), "mobs");
        if (!mobsFolder.isDirectory()) {
            loadMessages.add("MythicMobs mob folder is missing: " + mobsFolder.getAbsolutePath());
            return List.copyOf(loadMessages);
        }

        File[] files = mobsFolder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null || files.length == 0) {
            loadMessages.add("No MythicMobs mob files were found in " + mobsFolder.getAbsolutePath());
            return List.copyOf(loadMessages);
        }

        for (File file : files) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            for (String mobId : yaml.getKeys(false)) {
                if (mobId == null || mobId.isBlank()) {
                    continue;
                }

                allMobIds.add(mobId);
                if (isVanillaOverride(mobId) || isTestMob(mobId)) {
                    continue;
                }

                if (looksLikeBoss(mobId)) {
                    addUnique(bossMobIds, mobId);
                } else {
                    addUnique(combatMobIds, mobId);
                }
            }
        }

        if (combatMobIds.isEmpty()) {
            for (String mobId : allMobIds) {
                if (!isVanillaOverride(mobId) && !isTestMob(mobId)) {
                    addUnique(combatMobIds, mobId);
                }
            }
        }

        if (bossMobIds.isEmpty()) {
            for (String mobId : combatMobIds) {
                if (looksLikeBoss(mobId)) {
                    addUnique(bossMobIds, mobId);
                }
            }
        }

        if (bossMobIds.isEmpty() && !combatMobIds.isEmpty()) {
            addUnique(bossMobIds, combatMobIds.get(combatMobIds.size() - 1));
        }

        if (allMobIds.isEmpty()) {
            loadMessages.add("No MythicMob ids could be parsed from the mob files.");
        }

        loadMessages.add("Loaded " + allMobIds.size() + " MythicMob ids for stage validation.");
        loadMessages.add("Combat pool: " + combatMobIds);
        loadMessages.add("Boss pool: " + bossMobIds);
        return List.copyOf(loadMessages);
    }

    public static boolean hasAnyMob() {
        return !allMobIds.isEmpty();
    }

    public static boolean isKnownMob(String mobId) {
        return mobId != null && allMobIds.contains(mobId);
    }

    public static boolean isUsableCombatMob(String mobId) {
        return mobId != null && combatMobIds.contains(mobId);
    }

    public static boolean isBossMob(String mobId) {
        return mobId != null && bossMobIds.contains(mobId);
    }

    public static String getCombatMobByIndex(int index) {
        if (combatMobIds.isEmpty()) {
            return null;
        }
        int normalized = Math.floorMod(index, combatMobIds.size());
        return combatMobIds.get(normalized);
    }

    public static String getBossMobByIndex(int index) {
        if (bossMobIds.isEmpty()) {
            return getCombatMobByIndex(index);
        }
        int normalized = Math.floorMod(index, bossMobIds.size());
        return bossMobIds.get(normalized);
    }

    public static List<String> getCombatMobIds() {
        return Collections.unmodifiableList(combatMobIds);
    }

    public static Collection<String> getAllMobIds() {
        return Collections.unmodifiableSet(allMobIds);
    }

    private static boolean isVanillaOverride(String mobId) {
        return mobId.equals(mobId.toUpperCase(Locale.ROOT)) && mobId.contains("_");
    }

    private static boolean isTestMob(String mobId) {
        String normalized = mobId.toLowerCase(Locale.ROOT);
        return normalized.startsWith("test") || normalized.contains("sandbag");
    }

    private static boolean looksLikeBoss(String mobId) {
        String normalized = mobId.toLowerCase(Locale.ROOT);
        return normalized.contains("boss")
                || normalized.contains("king")
                || normalized.contains("funeral")
                || normalized.contains("butterflies");
    }

    private static void addUnique(List<String> list, String mobId) {
        if (!list.contains(mobId)) {
            list.add(mobId);
        }
    }
}
