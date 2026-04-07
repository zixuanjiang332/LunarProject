package jdd.lunarProject.MobsListener;
import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.skills.variables.VariableRegistry;
import io.lumine.mythic.core.skills.variables.VariableScope;
import jdd.lunarProject.SkillManager.ItemSkillManager;
import jdd.lunarProject.SkillManager.SkillCastManager;
import jdd.lunarProject.Tool.DamageIndicatorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

import static jdd.lunarProject.MobsListener.MythicDamageListener.*;

public class DamageCalculator implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;
        Player player = (Player) event.getDamager();
        float attackStrength = player.getCooledAttackStrength(0.0F);
        // 如果蓄力低于 0.9 (即 90% 蓄力，留一点容错率防延迟)
        if (attackStrength < 0.9F) {
            event.setCancelled(true);
            return;
        }
        Entity attacker = event.getDamager();
        Entity victim = event.getEntity();
        String attackSin = getAttackSin(attacker);
        String attackType = getAttackType(attacker);
        if (attackSin == null || attackType == null) return;
        // 1. 获取基础抗性乘区
        double sinRes = getEntityResistance(victim, attackSin);
        double typeRes = getEntityResistance(victim, attackType);
        sinRes = Math.max(0.5, Math.min(2.0, sinRes));
        typeRes = Math.max(0.5, Math.min(2.0, typeRes));

        // ==========================================
        // 【新增】2. 读取受击者的所有易损层数
        // ==========================================
        int pureFragility = getEntityFragility(victim, "pure_fragility");
        int slashFragility = getEntityFragility(victim, "slash_fragility");
        int pierceFragility = getEntityFragility(victim, "pierce_fragility");
        int bluntFragility = getEntityFragility(victim, "blunt_fragility");

        // 基础易损倍率：1.0
        double fragilityMultiplier = 1.0;

        // 通用乘区：纯易损 (每层 +10%)
        if (pureFragility > 0) {
            fragilityMultiplier += (pureFragility * 0.1);
        }

        // 专属乘区：根据本次攻击属性加算易损
        switch (attackType) {
            case "slash":
                if (slashFragility > 0) fragilityMultiplier += (slashFragility * 0.1);
                break;
            case "pierce":
                if (pierceFragility > 0) fragilityMultiplier += (pierceFragility * 0.1);
                break;
            case "blunt":
                if (bluntFragility > 0) fragilityMultiplier += (bluntFragility * 0.1);
                break;
        }

        // 3. 计算综合最终伤害
        // 公式：基础伤害 * 罪孽抗性 * 物理抗性 * 易损倍率
        double baseDamage = event.getDamage();
        double finalDamage = baseDamage * sinRes * typeRes * fragilityMultiplier;

        // 4. 暴击计算 (暴击独立于各种抗性和易损进行最终翻倍)
        boolean isCrit = ThreadLocalRandom.current().nextDouble() < CRIT_CHANCE;
        if (isCrit) {
            finalDamage *= CRIT_DAMAGE_MULTIPLIER;
            MythicDamageListener.playCriticalFeedback(victim);
        }
        // 5. 飘字与应用伤害
        Location targetLocation = victim.getLocation();
        DamageIndicatorUtil.spawnIndicator(targetLocation, finalDamage, isCrit);
        event.setDamage(finalDamage);

        // 打印详细日志方便测试数值计算是否正确
        Bukkit.getLogger().severe(
                "原伤害:" + baseDamage +
                        " 罪孽乘区:" + sinRes +
                        " 物理乘区:" + typeRes +
                        " 易损倍率:" + fragilityMultiplier +
                        " 暴击:" + isCrit +
                        " 最终伤害:" + finalDamage
        );
    }

    private int getEntityFragility(Entity entity, String varName) {
        if (entity instanceof Player player) {
            var variables = MythicBukkit.inst().getPlayerManager().getProfile(player).getVariables();
            return variables.has(varName) ? variables.getInt(varName) : 0;
        } else {
            var activeMob = MythicBukkit.inst().getMobManager().getActiveMob(entity.getUniqueId());
            if (activeMob.isPresent()) {
                var variables = activeMob.get().getVariables();
                return variables.has(varName) ? variables.getInt(varName) : 0;
            }
        }
        return 0; // 没找到变量统一当做 0 层处理
    }

    private double getEntityResistance(Entity entity, String resName) {
        if (entity instanceof Player) {
            var variables = MythicBukkit.inst().getPlayerManager().getProfile((Player) entity).getVariables();
            return variables.has(resName) ? variables.getDouble(resName) : 1.0;
        } else {
            var activeMob = MythicBukkit.inst().getMobManager().getActiveMob(entity.getUniqueId());
            if (activeMob.isPresent()) {
                var variables = activeMob.get().getVariables();
                return variables.has(resName) ? variables.getDouble(resName) : 1.0;
            }
        }
        return 1.0;
    }

    private String getAttackSin(Entity attacker) {
        if (attacker instanceof Player player) {
            var variables = MythicBukkit.inst().getPlayerManager().getProfile(player).getVariables();
            if (variables.has("attack_sin")) {
                return variables.getString("attack_sin");
            }
        } else {
            var activeMob = MythicBukkit.inst().getMobManager().getActiveMob(attacker.getUniqueId());
            if (activeMob.isPresent()) {
                var variables = activeMob.get().getVariables();
                if (variables.has("attack_sin")) {
                    return variables.getString("attack_sin");
                }
            }
        }
        return "envy";
    }

    private String getAttackType(Entity attacker) {
        if (attacker instanceof Player player) {
            var variables = MythicBukkit.inst().getPlayerManager().getProfile(player).getVariables();
            if (variables.has("attack_type")) {
                return variables.getString("attack_type");
            }
        } else {
            var activeMob = MythicBukkit.inst().getMobManager().getActiveMob(attacker.getUniqueId());
            if (activeMob.isPresent()) {
                var variables = activeMob.get().getVariables();
                if (variables.has("attack_type")) {
                    return variables.getString("attack_type");
                }
            }
        }
        return "blunt";
    }
    public void initIfMissing(Player player, String varName, String defaultValue) {
        AbstractEntity mmPlayer = BukkitAdapter.adapt(player);
        VariableRegistry registry = MythicBukkit.inst().getVariableManager()
                .getRegistry(VariableScope.TARGET, mmPlayer);
        if (!registry.has(varName)) {
            registry.putString(varName, defaultValue);
        }
    }
}