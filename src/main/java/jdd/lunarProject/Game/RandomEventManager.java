package jdd.lunarProject.Game;

import jdd.lunarProject.Build.EventConfigManager;
import jdd.lunarProject.Build.EventDefinition;
import jdd.lunarProject.Game.StageModels.StageTemplate;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

public final class RandomEventManager {
    private static final List<String> DEFAULT_EVENT_POOL = List.of(
            "WEIRD_MACHINE",
            "ABANDONED_SUPPLIES",
            "WHISPERING_CONSOLE"
    );

    private RandomEventManager() {
    }

    public enum EventOutcome {
        INVALID,
        SAFE_RESOLVED,
        AMBUSH_TRIGGERED
    }

    public static String pickRandomEventId() {
        List<String> configuredEvents = EventConfigManager.getEventIds();
        List<String> pool = configuredEvents.isEmpty() ? DEFAULT_EVENT_POOL : configuredEvents;
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    public static EventOutcome handleEventChoice(Game game, Player player, String eventId, int choice) {
        if (eventId == null || eventId.isBlank()) {
            return EventOutcome.INVALID;
        }

        return switch (eventId.toUpperCase()) {
            case "WEIRD_MACHINE" -> handleWeirdMachine(game, player, choice);
            case "ABANDONED_SUPPLIES" -> handleAbandonedSupplies(game, player, choice);
            case "WHISPERING_CONSOLE" -> handleWhisperingConsole(game, player, choice);
            default -> EventOutcome.INVALID;
        };
    }

    public static void broadcastEventIntro(Game game, String eventId) {
        EventDefinition eventDefinition = EventConfigManager.getEvent(eventId);
        if (eventDefinition != null) {
            game.broadcast("§6" + eventDefinition.intro());
            game.broadcast("§b[1] §f" + eventDefinition.optionOne());
            game.broadcast("§b[2] §f" + eventDefinition.optionTwo());
        } else {
            game.broadcast("§6你在房间中发现了一个异常事件，但当前没有读取到对应的事件配置。");
            game.broadcast("§b[1] §f谨慎离开");
            game.broadcast("§b[2] §f继续调查");
        }
        game.broadcast("§e使用 /game event choose <1|2> 进行选择。");
    }

    public static EventOutcome resolveTimeout(Game game, String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return EventOutcome.INVALID;
        }
        game.broadcast("§e事件选择超时，系统将默认执行选项 1。");
        return handleEventChoice(game, pickFallbackPlayer(game), eventId, 1);
    }

    private static EventOutcome handleWeirdMachine(Game game, Player player, int choice) {
        if (choice == 1) {
            adjustSanityForAlivePlayers(game, 5);
            game.broadcast("§a队伍谨慎地避开了古怪装置，理智略有恢复。");
            return EventOutcome.SAFE_RESOLVED;
        }

        if (choice != 2) {
            return EventOutcome.INVALID;
        }

        game.broadcast("§c" + player.getName() + " 触碰了古怪装置，周围的空气开始扭曲。");
        if (ThreadLocalRandom.current().nextDouble() < 0.55) {
            StageTemplate ambushTemplate = findAmbushTemplate(game);
            if (ambushTemplate != null) {
                game.getRoundManager().triggerAmbush(ambushTemplate);
                return EventOutcome.AMBUSH_TRIGGERED;
            }
        }

        applyTeamEffect(game, alive -> alive.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * 90, 0, false, true, true)));
        adjustSanityForAlivePlayers(game, 8);
        game.broadcast("§a装置最终稳定下来，队伍获得了短暂庇护与理智恢复。");
        return EventOutcome.SAFE_RESOLVED;
    }

    private static EventOutcome handleAbandonedSupplies(Game game, Player player, int choice) {
        if (choice == 1) {
            healAlivePlayersPercent(game, 0.20);
            adjustSanityForAlivePlayers(game, 8);
            game.broadcast("§a队伍整理了废弃补给，恢复了部分生命与理智。");
            return EventOutcome.SAFE_RESOLVED;
        }

        if (choice != 2) {
            return EventOutcome.INVALID;
        }

        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 20 * 90, 0, false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 90, 0, false, true, true));
        game.changePlayerSanity(player, -10);
        game.broadcast("§e" + player.getName() + " 强行拆解了补给箱，获得临时强化，但也付出了理智代价。");
        return EventOutcome.SAFE_RESOLVED;
    }

    private static EventOutcome handleWhisperingConsole(Game game, Player player, int choice) {
        if (choice == 1) {
            adjustSanityForAlivePlayers(game, 3);
            game.broadcast("§a控制台的低语逐渐平息，队伍稳住了心神。");
            return EventOutcome.SAFE_RESOLVED;
        }

        if (choice != 2) {
            return EventOutcome.INVALID;
        }

        applyTeamEffect(game, alive -> alive.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 20 * 120, 1, false, true, true)));
        adjustSanityForAlivePlayers(game, -6);
        game.broadcast("§e" + player.getName() + " 解开了控制台封锁，队伍获得护盾，但也承受了精神压力。");
        return EventOutcome.SAFE_RESOLVED;
    }

    private static StageTemplate findAmbushTemplate(Game game) {
        int currentRound = game.getRoundManager().getCurrentRound();
        StageTemplate ambushTemplate = StageManager.getRandomStage(1, currentRound, "ELITE");
        if (ambushTemplate == null) {
            ambushTemplate = StageManager.getRandomStage(1, currentRound, "NORMAL");
        }
        if (ambushTemplate == null) {
            ambushTemplate = StageManager.getAnyStageFallback(1, currentRound);
        }
        return ambushTemplate;
    }

    private static void healAlivePlayersPercent(Game game, double percent) {
        applyTeamEffect(game, player -> {
            if (player.getAttribute(Attribute.MAX_HEALTH) == null) {
                return;
            }
            double maxHealth = player.getAttribute(Attribute.MAX_HEALTH).getValue();
            double healedHealth = Math.min(maxHealth, player.getHealth() + (maxHealth * percent));
            player.setHealth(Math.max(1.0, healedHealth));
        });
    }

    private static void adjustSanityForAlivePlayers(Game game, int delta) {
        applyTeamEffect(game, player -> game.changePlayerSanity(player, delta));
    }

    private static void applyTeamEffect(Game game, Consumer<Player> effect) {
        for (Player alive : game.getActiveOnlinePlayers()) {
            effect.accept(alive);
        }
    }

    private static Player pickFallbackPlayer(Game game) {
        List<Player> alivePlayers = game.getActiveOnlinePlayers();
        if (!alivePlayers.isEmpty()) {
            return alivePlayers.get(0);
        }
        List<Player> deadPlayers = game.getDeadOnlinePlayers();
        if (!deadPlayers.isEmpty()) {
            return deadPlayers.get(0);
        }
        throw new IllegalStateException("Cannot resolve event without any tracked players.");
    }
}
