package jdd.lunarProject.Game;

import jdd.lunarProject.LunarProject;
import org.bukkit.Bukkit;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import static jdd.lunarProject.Game.StageModels.*;

public class StageManager {

    // 【核心重构】：根据 阶数、回合数、节点类型 来寻找关卡
    public record StageQueryKey(int tier, int round, String stageType) {}

    private static final Map<StageQueryKey, List<StageTemplate>> stagesMap = new HashMap<>();

    public static void initDatabase() {
        LunarProject.getInstance().saveDefaultConfig();
        String host = LunarProject.getInstance().getConfig().getString("database.host");
        int port = LunarProject.getInstance().getConfig().getInt("database.port");
        String db = LunarProject.getInstance().getConfig().getString("database.database");
        String user = LunarProject.getInstance().getConfig().getString("database.username");
        String pass = LunarProject.getInstance().getConfig().getString("database.password");
        String url = "jdbc:mysql://" + host + ":" + port + "/" + db + "?useSSL=false&characterEncoding=utf8";
        stagesMap.clear();
        Map<String, StageTemplate> tempMap = new HashMap<>();
        String sql = "SELECT t.stage_id, t.tier, t.round, t.stage_type, t.map_name, m.wave, m.mythicmobs_id, m.amount " +
                "FROM stage_templates t LEFT JOIN stage_mobs m ON t.stage_id = m.stage_id ORDER BY t.stage_id, m.wave";

        try (Connection conn = DriverManager.getConnection(url, user, pass);
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String stageId = rs.getString("stage_id");
                StageTemplate template = tempMap.computeIfAbsent(stageId, id -> {
                    try {
                        return new StageTemplate(id, rs.getInt("tier"), rs.getInt("round"), rs.getString("stage_type"), rs.getString("map_name"), new HashMap<>());
                    } catch (Exception e) { return null; }
                });

                if (template != null && !rs.wasNull() && rs.getString("mythicmobs_id") != null) {
                    int wave = rs.getInt("wave");
                    String mmId = rs.getString("mythicmobs_id");
                    int amount = rs.getInt("amount");
                    WaveData waveData = template.waves().computeIfAbsent(wave, w -> new WaveData(w, new ArrayList<>()));
                    waveData.mobs().add(new MobSpawn(mmId, amount));
                }
            }

            for (StageTemplate st : tempMap.values()) {
                StageQueryKey key = new StageQueryKey(st.tier(), st.round(), st.stageType());
                stagesMap.computeIfAbsent(key, k -> new ArrayList<>()).add(st);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    // 随机抽取符合条件的路线节点
    public static StageTemplate getRandomStage(int tier, int round, String stageType) {
        List<StageTemplate> pool = stagesMap.get(new StageQueryKey(tier, round, stageType));
        if (pool == null || pool.isEmpty()) return null;
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }
}