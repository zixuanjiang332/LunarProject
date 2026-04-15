package jdd.lunarProject.Game;

import jdd.lunarProject.Config.MapConfig;
import jdd.lunarProject.LunarProject;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import static jdd.lunarProject.Game.StageModels.MobSpawn;
import static jdd.lunarProject.Game.StageModels.StageTemplate;
import static jdd.lunarProject.Game.StageModels.WaveData;

public class StageManager {
    public record StageQueryKey(int tier, int round, String stageType) {}

    private static final Map<StageQueryKey, List<StageTemplate>> stagesMap = new HashMap<>();
    private static final List<String> validationErrors = new ArrayList<>();

    public static boolean initDatabase() {
        stagesMap.clear();
        validationErrors.clear();

        String host = LunarProject.getInstance().getConfig().getString("database.host");
        int port = LunarProject.getInstance().getConfig().getInt("database.port");
        String db = LunarProject.getInstance().getConfig().getString("database.database");
        String user = LunarProject.getInstance().getConfig().getString("database.username");
        String pass = LunarProject.getInstance().getConfig().getString("database.password");
        String url = "jdbc:mysql://" + host + ":" + port + "/" + db + "?useSSL=false&characterEncoding=utf8";

        Map<String, StageTemplate> tempMap = new HashMap<>();
        String sql = "SELECT t.stage_id, t.tier, t.round, t.stage_type, t.map_name, m.wave, m.mythicmobs_id, m.amount " +
                "FROM stage_templates t LEFT JOIN stage_mobs m ON t.stage_id = m.stage_id ORDER BY t.stage_id, m.wave";

        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
            ensureSchema(conn);
            ensureStarterData(conn);
            repairStageMobReferences(conn);

            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String stageId = rs.getString("stage_id");
                    StageTemplate template = tempMap.computeIfAbsent(stageId, id -> {
                        try {
                            return new StageTemplate(
                                    id,
                                    rs.getInt("tier"),
                                    rs.getInt("round"),
                                    rs.getString("stage_type"),
                                    rs.getString("map_name"),
                                    new HashMap<>()
                            );
                        } catch (Exception exception) {
                            validationErrors.add("Failed to parse stage template: " + id);
                            return null;
                        }
                    });

                    if (template == null) {
                        continue;
                    }

                    String mythicMobsId = rs.getString("mythicmobs_id");
                    if (mythicMobsId == null) {
                        continue;
                    }

                    int waveIndex = rs.getInt("wave");
                    int amount = rs.getInt("amount");
                    WaveData waveData = template.waves().computeIfAbsent(waveIndex, index -> new WaveData(index, new ArrayList<>()));
                    waveData.mobs().add(new MobSpawn(mythicMobsId, amount));
                }
            }
        } catch (Exception exception) {
            validationErrors.add("Database connection or query failed: " + exception.getMessage());
            return false;
        }

        if (tempMap.isEmpty()) {
            validationErrors.add("No stage templates were loaded from MySQL.");
            return false;
        }

        for (StageTemplate template : tempMap.values()) {
            StageQueryKey key = new StageQueryKey(template.tier(), template.round(), template.stageType());
            stagesMap.computeIfAbsent(key, ignored -> new ArrayList<>()).add(template);
        }

        validateStageData(tempMap.values());
        return validationErrors.isEmpty();
    }

    public static List<String> getValidationErrors() {
        return List.copyOf(validationErrors);
    }

    public static StageTemplate getRandomStage(int tier, int round, String stageType) {
        List<StageTemplate> pool = stagesMap.get(new StageQueryKey(tier, round, stageType));
        if (pool == null || pool.isEmpty()) {
            return null;
        }
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    public static StageTemplate getAnyStageFallback(int tier, int round) {
        List<StageTemplate> allPossible = new ArrayList<>();
        for (Map.Entry<StageQueryKey, List<StageTemplate>> entry : stagesMap.entrySet()) {
            if (entry.getKey().tier() == tier && entry.getKey().round() == round) {
                allPossible.addAll(entry.getValue());
            }
        }
        if (allPossible.isEmpty()) {
            return null;
        }
        return allPossible.get(ThreadLocalRandom.current().nextInt(allPossible.size()));
    }

    public static StageTemplate getAnyStageByType(int tier, String stageType) {
        List<StageTemplate> allPossible = new ArrayList<>();
        for (Map.Entry<StageQueryKey, List<StageTemplate>> entry : stagesMap.entrySet()) {
            if (entry.getKey().tier() == tier && entry.getKey().stageType().equalsIgnoreCase(stageType)) {
                allPossible.addAll(entry.getValue());
            }
        }
        if (allPossible.isEmpty()) {
            return null;
        }
        return allPossible.get(ThreadLocalRandom.current().nextInt(allPossible.size()));
    }

    private static void validateStageData(Collection<StageTemplate> templates) {
        Set<String> presentTypes = new HashSet<>();

        for (StageTemplate template : templates) {
            if (template.stageType() == null || template.stageType().isBlank()) {
                validationErrors.add("Stage " + template.stageId() + " has no stage_type.");
                continue;
            }

            presentTypes.add(template.stageType());

            if (template.mapName() == null || template.mapName().isBlank()) {
                validationErrors.add("Stage " + template.stageId() + " has no map_name.");
                continue;
            }

            if (!MapConfig.hasMap(template.mapName())) {
                validationErrors.add("Stage " + template.stageId() + " references missing map config: " + template.mapName());
                continue;
            }

            if (!MapConfig.hasMapDirectory(template.mapName())) {
                validationErrors.add("Stage " + template.stageId() + " references missing map folder: " + template.mapName());
                continue;
            }

            if (!MapConfig.hasCenterLocation(template.mapName())) {
                validationErrors.add("Map " + template.mapName() + " is missing a valid center-location.");
            }

            if (requiresCombatSpawns(template.stageType()) && !MapConfig.hasMonsterSpawners(template.mapName())) {
                validationErrors.add("Combat map " + template.mapName() + " is missing monster spawners.");
            }

            if (requiresCombatSpawns(template.stageType()) && template.waves().isEmpty()) {
                validationErrors.add("Combat stage " + template.stageId() + " has no wave data.");
            }

            for (WaveData waveData : template.waves().values()) {
                for (MobSpawn spawn : waveData.mobs()) {
                    if (!MythicMobRegistry.isKnownMob(spawn.mythicMobsId())) {
                        validationErrors.add("Stage " + template.stageId() + " references unknown MythicMob: " + spawn.mythicMobsId());
                    }
                }
            }
        }

        for (int round = 1; round <= 4; round++) {
            if (getAnyStageFallback(1, round) == null) {
                validationErrors.add("Tier 1 round " + round + " has no available stage.");
            }
        }

        if (getRandomStage(1, 5, "BOSS") == null) {
            validationErrors.add("Tier 1 round 5 is missing a BOSS stage.");
        }

        String[] requiredTypes = {"NORMAL", "ELITE", "EVENT", "SHOP", "REST", "BOSS"};
        for (String type : requiredTypes) {
            if (!presentTypes.contains(type)) {
                validationErrors.add("Tier 1 loop is missing a stage of type " + type + ".");
            }
        }
    }

    private static boolean requiresCombatSpawns(String stageType) {
        return !"EVENT".equals(stageType) && !"SHOP".equals(stageType) && !"REST".equals(stageType);
    }

    private static void ensureSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS stage_templates (" +
                            "stage_id VARCHAR(64) PRIMARY KEY," +
                            "tier INT NOT NULL," +
                            "round INT NOT NULL," +
                            "stage_type VARCHAR(32) NOT NULL," +
                            "map_name VARCHAR(64) NOT NULL" +
                            ")"
            );

            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS stage_mobs (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "stage_id VARCHAR(64) NOT NULL," +
                            "wave INT NOT NULL," +
                            "mythicmobs_id VARCHAR(64) NOT NULL," +
                            "amount INT NOT NULL," +
                            "CONSTRAINT fk_stage_mobs_template FOREIGN KEY (stage_id) REFERENCES stage_templates(stage_id) ON DELETE CASCADE" +
                            ")"
            );
        }
    }

    private static void ensureStarterData(Connection connection) throws SQLException {
        String combatMobA = pickCombatMob(0);
        String combatMobB = pickCombatMob(1);
        String combatMobC = pickCombatMob(2);
        String bossMob = pickBossMob(0);

        if (combatMobA == null || combatMobB == null || combatMobC == null || bossMob == null) {
            throw new SQLException("Unable to build starter stage data because MythicMob pools are incomplete.");
        }

        connection.setAutoCommit(false);
        try {
            ensureTemplate(connection, "T1_R1_NORMAL_A", 1, 1, "NORMAL", "test-map");
            ensureTemplate(connection, "T1_R1_REST_A", 1, 1, "REST", "rest-map");
            ensureTemplate(connection, "T1_R2_NORMAL_A", 1, 2, "NORMAL", "test-map");
            ensureTemplate(connection, "T1_R2_EVENT_A", 1, 2, "EVENT", "event-map");
            ensureTemplate(connection, "T1_R2_SHOP_A", 1, 2, "SHOP", "shop-map");
            ensureTemplate(connection, "T1_R3_NORMAL_A", 1, 3, "NORMAL", "test-map");
            ensureTemplate(connection, "T1_R3_ELITE_A", 1, 3, "ELITE", "test-map");
            ensureTemplate(connection, "T1_R3_REST_A", 1, 3, "REST", "rest-map");
            ensureTemplate(connection, "T1_R4_NORMAL_A", 1, 4, "NORMAL", "test-map");
            ensureTemplate(connection, "T1_R4_ELITE_A", 1, 4, "ELITE", "test-map");
            ensureTemplate(connection, "T1_R4_EVENT_A", 1, 4, "EVENT", "event-map");
            ensureTemplate(connection, "T1_R5_SHOP_A", 1, 5, "SHOP", "shop-map");
            ensureTemplate(connection, "T1_R5_BOSS_A", 1, 5, "BOSS", "test-map");

            ensureStageMobs(connection, "T1_R1_NORMAL_A",
                    new MobSpawn(combatMobA, 2),
                    new MobSpawn(combatMobB, 1));
            ensureStageMobs(connection, "T1_R2_NORMAL_A",
                    new MobSpawn(combatMobA, 2),
                    new MobSpawn(combatMobB, 1),
                    new MobSpawn(combatMobC, 1));
            ensureStageMobs(connection, "T1_R3_NORMAL_A",
                    new MobSpawn(combatMobA, 3),
                    new MobSpawn(combatMobC, 1));
            ensureStageMobs(connection, "T1_R3_ELITE_A",
                    new MobSpawn(combatMobB, 1),
                    new MobSpawn(combatMobA, 2));
            ensureStageMobs(connection, "T1_R4_NORMAL_A",
                    new MobSpawn(combatMobA, 2),
                    new MobSpawn(combatMobB, 2));
            ensureStageMobs(connection, "T1_R4_ELITE_A",
                    new MobSpawn(combatMobB, 2),
                    new MobSpawn(combatMobC, 2));
            ensureStageMobs(connection, "T1_R5_BOSS_A",
                    new MobSpawn(bossMob, 1));

            connection.commit();
            LunarProject.getInstance().getLogger().info("Verified starter stage data for Tier 1 gameplay loop.");
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private static void repairStageMobReferences(Connection connection) throws SQLException {
        String selectSql = "SELECT sm.id, sm.stage_id, sm.wave, sm.mythicmobs_id, st.stage_type " +
                "FROM stage_mobs sm INNER JOIN stage_templates st ON st.stage_id = sm.stage_id";
        String updateSql = "UPDATE stage_mobs SET mythicmobs_id = ? WHERE id = ?";

        List<String> repairedRows = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement(selectSql);
             ResultSet resultSet = select.executeQuery();
             PreparedStatement update = connection.prepareStatement(updateSql)) {
            while (resultSet.next()) {
                int id = resultSet.getInt("id");
                String stageId = resultSet.getString("stage_id");
                int wave = resultSet.getInt("wave");
                String stageType = resultSet.getString("stage_type");
                String mythicMobId = resultSet.getString("mythicmobs_id");

                String replacement = chooseReplacementMob(stageType, wave, mythicMobId);
                if (replacement == null || replacement.equals(mythicMobId)) {
                    continue;
                }

                update.setString(1, replacement);
                update.setInt(2, id);
                update.addBatch();
                repairedRows.add(stageId + "[wave=" + wave + "]: " + mythicMobId + " -> " + replacement);
            }

            if (!repairedRows.isEmpty()) {
                update.executeBatch();
                LunarProject.getInstance().getLogger().warning("Repaired invalid stage mob references: " + repairedRows.size());
                for (String repairedRow : repairedRows) {
                    LunarProject.getInstance().getLogger().warning(" - " + repairedRow);
                }
            }
        }
    }

    private static String chooseReplacementMob(String stageType, int wave, String currentMobId) {
        if ("BOSS".equalsIgnoreCase(stageType)) {
            if (MythicMobRegistry.isBossMob(currentMobId)) {
                return currentMobId;
            }
            return pickBossMob(wave - 1);
        }

        if (MythicMobRegistry.isUsableCombatMob(currentMobId)) {
            return currentMobId;
        }
        return pickCombatMob(wave - 1);
    }

    private static String pickCombatMob(int index) {
        String mobId = MythicMobRegistry.getCombatMobByIndex(index);
        if (mobId != null) {
            return mobId;
        }
        return MythicMobRegistry.getBossMobByIndex(index);
    }

    private static String pickBossMob(int index) {
        String mobId = MythicMobRegistry.getBossMobByIndex(index);
        if (mobId != null) {
            return mobId;
        }
        return MythicMobRegistry.getCombatMobByIndex(index);
    }

    private static void ensureTemplate(Connection connection, String stageId, int tier, int round, String stageType, String mapName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT IGNORE INTO stage_templates (stage_id, tier, round, stage_type, map_name) VALUES (?, ?, ?, ?, ?)")) {
            statement.setString(1, stageId);
            statement.setInt(2, tier);
            statement.setInt(3, round);
            statement.setString(4, stageType);
            statement.setString(5, mapName);
            statement.executeUpdate();
        }
    }

    private static void ensureStageMobs(Connection connection, String stageId, MobSpawn... waves) throws SQLException {
        try (PreparedStatement countStatement = connection.prepareStatement("SELECT COUNT(*) FROM stage_mobs WHERE stage_id = ?")) {
            countStatement.setString(1, stageId);
            try (ResultSet resultSet = countStatement.executeQuery()) {
                if (resultSet.next() && resultSet.getInt(1) > 0) {
                    return;
                }
            }
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO stage_mobs (stage_id, wave, mythicmobs_id, amount) VALUES (?, ?, ?, ?)")) {
            for (int wave = 0; wave < waves.length; wave++) {
                statement.setString(1, stageId);
                statement.setInt(2, wave + 1);
                statement.setString(3, waves[wave].mythicMobsId());
                statement.setInt(4, waves[wave].amount());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }
}
