package jdd.lunarProject.MobsListener;

import jdd.lunarProject.Tool.CombatVariableUtil;
import jdd.lunarProject.Tool.YellowBarUtil;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ResistanceDebugStickListener implements Listener {
    private static final String DEBUG_STICK_MARKER = "LP_DEBUG_STICK";
    private static final String[] SIN_RESISTANCES = {"wrath", "lust", "sloth", "gluttony", "gloom", "pride", "envy"};
    private static final String[] TYPE_RESISTANCES = {"slash", "pierce", "blunt"};
    private static final String[] FRAGILITY_VARIABLES = {
            "pure_fragility",
            "slash_fragility",
            "pierce_fragility",
            "blunt_fragility",
            "dot_fragility",
            "bleed_fragility",
            "bleed_dot_fragility",
            "rupture_fragility",
            "rupture_dot_fragility",
            "burn_fragility",
            "tremor_fragility",
            "poisoning_fragility",
            "corrosion_fragility",
            "wrath_fragility",
            "lust_fragility",
            "sloth_fragility",
            "gluttony_fragility",
            "gloom_fragility",
            "pride_fragility",
            "envy_fragility"
    };

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!isDebugStick(event.getItem())) {
            return;
        }

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        if (player.isSneaking()) {
            Entity target = player.getTargetEntity(8);
            if (target instanceof LivingEntity livingEntity) {
                sendDebugPanel(player, livingEntity, "目标");
                return;
            }
        }

        sendDebugPanel(player, player, "自己");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        if (!isDebugStick(player.getInventory().getItemInMainHand())) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity livingEntity)) {
            return;
        }

        sendDebugPanel(player, livingEntity, "目标");
    }

    private void sendDebugPanel(Player viewer, LivingEntity target, String scopeLabel) {
        viewer.sendMessage(ChatColor.DARK_GRAY + "==============================");
        viewer.sendMessage(ChatColor.GOLD + "[调试] " + ChatColor.YELLOW + scopeLabel + ChatColor.WHITE + " " + ChatColor.AQUA + readableName(target));
        viewer.sendMessage(ChatColor.YELLOW + "罪孽抗性");
        for (String resistance : SIN_RESISTANCES) {
            viewer.sendMessage(ChatColor.GRAY + " - " + toChineseLabel(resistance) + ChatColor.WHITE + ": " + formatDouble(CombatVariableUtil.getDouble(target, resistance, 1.0)));
        }

        viewer.sendMessage(ChatColor.YELLOW + "物理抗性");
        for (String resistance : TYPE_RESISTANCES) {
            viewer.sendMessage(ChatColor.GRAY + " - " + toChineseLabel(resistance) + ChatColor.WHITE + ": " + formatDouble(CombatVariableUtil.getDouble(target, resistance, 1.0)));
        }

        viewer.sendMessage(ChatColor.YELLOW + "黄条相关");
        viewer.sendMessage(ChatColor.GRAY + " - 黄条抗性" + ChatColor.WHITE + ": " + formatDouble(CombatVariableUtil.getDouble(target, YellowBarUtil.VAR_YELLOW_BAR_RESISTANCE, 1.0)));
        viewer.sendMessage(ChatColor.GRAY + " - 混乱抗性修正" + ChatColor.WHITE + ": " + formatDouble(CombatVariableUtil.getDouble(target, YellowBarUtil.VAR_STAGGER_RESISTANCE, 1.0)));

        viewer.sendMessage(ChatColor.YELLOW + "易损层数");
        for (Map.Entry<String, Integer> entry : collectFragilities(target).entrySet()) {
            viewer.sendMessage(ChatColor.GRAY + " - " + entry.getKey() + ChatColor.WHITE + ": " + entry.getValue());
        }
        viewer.sendMessage(ChatColor.DARK_GRAY + "==============================");
    }

    private Map<String, Integer> collectFragilities(LivingEntity target) {
        Map<String, Integer> values = new LinkedHashMap<>();
        for (String variable : FRAGILITY_VARIABLES) {
            values.put(toChineseFragilityLabel(variable), CombatVariableUtil.getInt(target, variable, 0));
        }
        return values;
    }

    private String readableName(LivingEntity entity) {
        if (entity instanceof Player player) {
            return player.getName();
        }
        return entity.getName();
    }

    private boolean isDebugStick(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() != Material.STICK) {
            return false;
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null || !itemMeta.hasLore()) {
            return false;
        }

        List<String> lore = itemMeta.getLore();
        if (lore == null) {
            return false;
        }

        for (String line : lore) {
            String stripped = ChatColor.stripColor(line);
            if (stripped != null && stripped.contains(DEBUG_STICK_MARKER)) {
                return true;
            }
        }
        return false;
    }

    private String formatDouble(double value) {
        return String.format("%.2f", value);
    }

    private String toChineseLabel(String key) {
        return switch (key) {
            case "wrath" -> "暴怒";
            case "lust" -> "色欲";
            case "sloth" -> "怠惰";
            case "gluttony" -> "暴食";
            case "gloom" -> "忧郁";
            case "pride" -> "傲慢";
            case "envy" -> "嫉妒";
            case "slash" -> "斩击";
            case "pierce" -> "穿刺";
            case "blunt" -> "打击";
            default -> key;
        };
    }

    private String toChineseFragilityLabel(String variable) {
        return switch (variable) {
            case "pure_fragility" -> "普通易损";
            case "slash_fragility" -> "斩击易损";
            case "pierce_fragility" -> "穿刺易损";
            case "blunt_fragility" -> "打击易损";
            case "dot_fragility" -> "DOT易损";
            case "bleed_fragility" -> "流血易损";
            case "bleed_dot_fragility" -> "流血DOT易损";
            case "rupture_fragility" -> "破裂易损";
            case "rupture_dot_fragility" -> "破裂DOT易损";
            case "burn_fragility" -> "烧伤易损";
            case "tremor_fragility" -> "震颤易损";
            case "poisoning_fragility" -> "中毒易损";
            case "corrosion_fragility" -> "腐蚀易损";
            case "wrath_fragility" -> "暴怒易损";
            case "lust_fragility" -> "色欲易损";
            case "sloth_fragility" -> "怠惰易损";
            case "gluttony_fragility" -> "暴食易损";
            case "gloom_fragility" -> "忧郁易损";
            case "pride_fragility" -> "傲慢易损";
            case "envy_fragility" -> "嫉妒易损";
            default -> variable;
        };
    }
}
