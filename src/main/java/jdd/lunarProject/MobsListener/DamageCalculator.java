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
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

import static jdd.lunarProject.MobsListener.MythicDamageListener.*;

public class DamageCalculator implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void onGlobalVanillaAttack(EntityDamageByEntityEvent event) {
        // 1. 确保伤害来源是玩家
        if (!(event.getDamager() instanceof Player)) return;
        // 2. 我们只接管物理近战（左键平A 和 横扫），不影响跌落、燃烧或魔法伤害
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK &&
                event.getCause() != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            return;
        }
        // =========================================================
        // 3. 【核心黑科技】 溯源鉴别：是鼠标左键，还是 MM 技能？
        // 我们通过读取当前线程的堆栈，查找是不是 MythicMobs 引擎发出的攻击指令！
        // =========================================================
        boolean isMythicSkill = false;
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            // 如果执行代码的类名包含了 MM 的包名，说明这是技能打出的伤害！
            if (element.getClassName().contains("io.lumine.mythic")) {
                isMythicSkill = true;
                break;
            }
        }
        // 如果是 MM 技能打出的（比如你在 YAML 里写的 damage{a=5}），直接放行，绝对不改它的伤害！
        if (isMythicSkill) {
            return;
        }
        Player player = (Player) event.getDamager();
        // 4. 【蓄力连点拦截系统】
        float attackStrength = player.getCooledAttackStrength(0.0F);
        if (attackStrength < 0.8F) {
            // 如果玩家在瞎按连点，直接把整个攻击事件扬了。
            // 效果：打不出伤害，MM 的 ~onAttack 也不会触发，必须等武器抬起！
            event.setCancelled(true);
            return;
        }

        event.setDamage(0.0);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Entity attacker = event.getDamager();
        Entity victim = event.getEntity();
        String attackSin = getAttackSin(attacker);
        String attackType = getAttackType(attacker);

        // 如果缺少攻击标签则直接放行 (在你的设定中默认是"none")
        if (attackSin == null || attackType == null) return;

        // ==========================================
        // 1. 获取原始抗性与混乱 (Stagger) 覆写
        // ==========================================
        double sinRes = getEntityResistance(victim, attackSin);
        double typeRes = getEntityResistance(victim, attackType);
        sinRes = Math.max(0.5, Math.min(2.0, sinRes));
        typeRes = Math.max(0.5, Math.min(2.0, typeRes));

        int staggerStage = getStaggerStage(victim);
        if (staggerStage == 1) {
            // T1混乱：抗性强制拔高至 1.5
            sinRes = Math.max(sinRes, 1.5);
            typeRes = Math.max(typeRes, 1.5);
        } else if (staggerStage == 2) {
            // T2混乱：抗性强制拔高至 2.0
            sinRes = Math.max(sinRes, 2.0);
            typeRes = Math.max(typeRes, 2.0);
        }

        // ==========================================
        // 2. 【核心重构：加算抗性乘区】
        // ==========================================
        // 公式：1 + (罪孽抗性 - 1) + (物理抗性 - 1)
        double resMultiplier = 1.0 + (sinRes - 1.0) + (typeRes - 1.0);
        // 【防穿透兜底】：如果怪物拥有双重极致抗性 (例如双 0.5)，1-0.5-0.5 会算出 0，导致伤害归零甚至回血。
        // 所以我们加一个下限保底，即便再肉，也能打出 10% (0.1) 的强制伤害。
        resMultiplier = Math.max(0.1, resMultiplier);

        // ==========================================
        // 3. 【核心重构：动态获取易损层数】
        // ==========================================
        int pureFragility = getEntityFragility(victim, "pure_fragility");

        // 动态拼接法：如果 attackType 是 "slash"，这里自动查询 "slash_fragility"
        // 哪怕 attackType 是 "none"，它也会查 "none_fragility"，查不到自然返回 0，极其安全！
        int typeFragility = getEntityFragility(victim, attackType + "_fragility");
        int sinFragility = getEntityFragility(victim, attackSin + "_fragility");

        // 公式：1 + (纯粹易损*10%) + (对应物理易损*10%) + (对应罪孽易损*10%)
        double fragilityMultiplier = 1.0 + (pureFragility * 0.1) + (typeFragility * 0.1) + (sinFragility * 0.1);

        // ==========================================
        // 4. 计算综合最终伤害
        // ==========================================
        double baseDamage = event.getDamage();
        double finalDamage = baseDamage * resMultiplier * fragilityMultiplier;

        // 5. 暴击计算 (暴击独立于抗性，属于最终翻倍乘区)
        boolean isCrit = ThreadLocalRandom.current().nextDouble() < CRIT_CHANCE;
        if (isCrit) {
            finalDamage *= CRIT_DAMAGE_MULTIPLIER;
            MythicDamageListener.playCriticalFeedback(victim);
        }
        // 6. 飘字与应用伤害
        Location targetLocation = victim.getLocation();
        if (finalDamage != 0.0) {
            DamageIndicatorUtil.spawnIndicator(targetLocation, finalDamage, isCrit);
        }

        // 怪物伤害千倍放大机制
        if (!(attacker instanceof Player)) {
            finalDamage *= 1000.0;
        }
        event.setDamage(finalDamage);

        // 7. 测试用日志输出
        Bukkit.getLogger().severe(
                "原伤:" + baseDamage +
                        " | 抗性修正乘数:" + String.format("%.2f", resMultiplier) +
                        " (罪:" + sinRes + " 物:" + typeRes + ")" +
                        " | 易损倍率:" + String.format("%.2f", fragilityMultiplier) +
                        " (纯:" + pureFragility + " 类:" + typeFragility + " 罪:" + sinFragility + ")" +
                        " | 终伤:" + String.format("%.2f", finalDamage)
        );
    }
    private int getStaggerStage(Entity entity) {
        if (entity instanceof Player player) {
            var variables = MythicBukkit.inst().getPlayerManager().getProfile(player).getVariables();
            return variables.has("stagger_stage") ? variables.getInt("stagger_stage") : 0;
        } else {
            var activeMob = MythicBukkit.inst().getMobManager().getActiveMob(entity.getUniqueId());
            if (activeMob.isPresent()) {
                var variables = activeMob.get().getVariables();
                return variables.has("stagger_stage") ? variables.getInt("stagger_stage") : 0;
            }
        }
        return 0;
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
        return "none";
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
        return "none";
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