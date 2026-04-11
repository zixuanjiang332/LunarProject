package jdd.lunarProject.Game;
import java.util.List;
import java.util.Map;

public class StageModels {
    // 记录单种怪物的固定生成数量
    public record MobSpawn(String mythicMobsId, int amount) {}
    // 记录一个波次的所有怪物
    public record WaveData(int waveIndex, List<MobSpawn> mobs) {}

    // 完整的关卡模板对象
    public record StageTemplate(
            String stageId,
            int tier,
            int round,      // 【新增】回合属性
            String stageType,
            String mapName,
            Map<Integer, WaveData> waves
    ) {}
}