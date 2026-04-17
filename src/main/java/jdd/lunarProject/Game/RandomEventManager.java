package jdd.lunarProject.Game;

import jdd.lunarProject.Build.EventConfigManager;
import jdd.lunarProject.Build.EventDefinition;
import jdd.lunarProject.Config.MessageManager;
import jdd.lunarProject.Game.StageModels.StageTemplate;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

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
            game.broadcast(MessageManager.text("event.intro-fallback", "§6你在房间中发现了一段未定义的异常记录。"));
            game.broadcast(MessageManager.text("event.option-one-fallback", "§b[1] §f谨慎离开"));
            game.broadcast(MessageManager.text("event.option-two-fallback", "§b[2] §f继续调查"));
        }
        game.broadcast(MessageManager.text("event.choose-prompt", "§e使用 /game event choose <1|2> 进行选择。"));
    }

    public static EventOutcome resolveTimeout(Game game, String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return EventOutcome.INVALID;
        }
        game.broadcast(MessageManager.text("event.choose-timeout", "§e事件选择超时，系统将默认执行选项 1。"));
        return handleEventChoice(game, pickFallbackPlayer(game), eventId, 1);
    }

    private static EventOutcome handleWeirdMachine(Game game, Player player, int choice) {
        if (choice == 1) {
            game.broadcast(MessageManager.text("event.weird-machine-safe", "§a队伍谨慎地避开了古怪装置，本次事件不直接发放任何增益。"));
            return EventOutcome.SAFE_RESOLVED;
        }

        if (choice != 2) {
            return EventOutcome.INVALID;
        }

        game.broadcast(MessageManager.format("event.weird-machine-trigger", "§c%player% 触碰了古怪装置，周围的空气开始扭曲。", "player", player.getName()));
        if (ThreadLocalRandom.current().nextDouble() < 0.55) {
            StageTemplate ambushTemplate = findAmbushTemplate(game);
            if (ambushTemplate != null) {
                game.getRoundManager().triggerAmbush(ambushTemplate);
                return EventOutcome.AMBUSH_TRIGGERED;
            }
        }

        game.broadcast(MessageManager.text("event.weird-machine-stable", "§a装置最终稳定下来，但当前测试阶段不会额外发放任何增益。"));
        return EventOutcome.SAFE_RESOLVED;
    }

    private static EventOutcome handleAbandonedSupplies(Game game, Player player, int choice) {
        if (choice == 1) {
            game.broadcast(MessageManager.text("event.supplies-safe", "§a队伍整理了废弃补给，但当前测试阶段不再直接提供恢复与增益。"));
            return EventOutcome.SAFE_RESOLVED;
        }

        if (choice != 2) {
            return EventOutcome.INVALID;
        }

        game.broadcast(MessageManager.format("event.supplies-risk", "§e%player% 强行拆解了补给箱，但当前测试阶段不再直接发放临时强化。", "player", player.getName()));
        return EventOutcome.SAFE_RESOLVED;
    }

    private static EventOutcome handleWhisperingConsole(Game game, Player player, int choice) {
        if (choice == 1) {
            game.broadcast(MessageManager.text("event.console-safe", "§a控制台的低语逐渐平息，本次事件以无增益的安全结果收尾。"));
            return EventOutcome.SAFE_RESOLVED;
        }

        if (choice != 2) {
            return EventOutcome.INVALID;
        }

        game.broadcast(MessageManager.format("event.console-risk", "§e%player% 解开了控制台封锁，但当前测试阶段不再直接发放护盾与其他增益。", "player", player.getName()));
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
