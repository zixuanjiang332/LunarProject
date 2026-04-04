package jdd.lunarProject.MobsListener;
import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.skills.variables.VariableRegistry;
import io.lumine.mythic.core.skills.variables.VariableScope;
import jdd.lunarProject.Tool.DamageIndicatorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.concurrent.ThreadLocalRandom;

import static jdd.lunarProject.MobsListener.MythicDamageListener.*;

public class DamageCalculator implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Entity attacker = event.getDamager();
        Entity victim = event.getEntity();
        String attackSin = getAttackSin(attacker);
        String attackType = getAttackType(attacker);
        if (attackSin == null || attackType == null) return;
        double sinRes = getEntityResistance(victim,  attackSin);
        double typeRes = getEntityResistance(victim,  attackType);
        sinRes = Math.max(0.5, Math.min(2.0, sinRes));
        typeRes = Math.max(0.5, Math.min(2.0, typeRes));
        double baseDamage = event.getDamage();
        Location targetLocation = victim.getLocation();
        boolean isCrit = ThreadLocalRandom.current().nextDouble() < CRIT_CHANCE;
        double finalDamage = baseDamage * sinRes * typeRes;
        if (isCrit) {
            finalDamage *= CRIT_DAMAGE_MULTIPLIER;
            Entity target = victim;
           MythicDamageListener.playCriticalFeedback(victim);
        }
        DamageIndicatorUtil.spawnIndicator(targetLocation, finalDamage,isCrit);
        event.setDamage(finalDamage);
       Bukkit.getLogger().severe("原伤害:" + baseDamage + " 罪孽乘区:" + sinRes + " 物理乘区:" + typeRes + "是否暴击"+isCrit+"暴击倍率"+CRIT_DAMAGE_MULTIPLIER+" 最终伤害:" + finalDamage);
    }

    private double getEntityResistance(Entity entity, String resName) {
        if (entity instanceof Player) {
            // 获取玩家变量
            var variables = MythicBukkit.inst().getPlayerManager().getProfile((Player) entity).getVariables();
            return variables.has(resName) ? variables.getDouble(resName) : 1.0;
        } else {
            // 获取怪物变量
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
            // 获取玩家变量
            var variables = MythicBukkit.inst().getPlayerManager().getProfile(player).getVariables();
            // 检查是否存在 attack_sin 这个变量名
            if (variables.has("attack_sin")) {
                // 直接返回它的值（比如 "gloom"）
                return variables.getString("attack_sin");
            }
        } else {
            // 获取怪物变量
            var activeMob = MythicBukkit.inst().getMobManager().getActiveMob(attacker.getUniqueId());
            if (activeMob.isPresent()) {
                var variables = activeMob.get().getVariables();
                if (variables.has("attack_sin")) {
                    return variables.getString("attack_sin");
                }
            }
        }
        // 如果没有设置，返回默认罪孽属性
        return "envy";
    }
    private String getAttackType(Entity attacker) {
        if (attacker instanceof Player player) {
            var variables = MythicBukkit.inst().getPlayerManager().getProfile(player).getVariables();
            if (variables.has("attack_type")) {
                return variables.getString("attack_type"); // 比如 "pierce"
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