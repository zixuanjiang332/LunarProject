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
            game.broadcast("§a" + eventDefinition.intro());
            game.broadcast("§b[1] " + eventDefinition.optionOne());
            game.broadcast("§b[2] " + eventDefinition.optionTwo());
        } else {
            game.broadcast("§aA strange event blocks the road ahead.");
            game.broadcast("§b[1] Leave carefully");
            game.broadcast("§b[2] Investigate");
        }
        game.broadcast("§eUse /game event choose <1|2> to resolve this room.");
    }

    public static EventOutcome resolveTimeout(Game game, String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return EventOutcome.INVALID;
        }
        game.broadcast("§eNo one acted in time. The team falls back to the safe option.");
        return handleEventChoice(game, pickFallbackPlayer(game), eventId, 1);
    }

    private static EventOutcome handleWeirdMachine(Game game, Player player, int choice) {
        if (choice == 1) {
            adjustSanityForAlivePlayers(game, 5);
            game.broadcast("§aThe crew leaves the machine untouched and regains some composure.");
            return EventOutcome.SAFE_RESOLVED;
        }

        if (choice != 2) {
            return EventOutcome.INVALID;
        }

        game.broadcast("§c" + player.getName() + " forces the machine online. Warning sirens erupt.");
        if (ThreadLocalRandom.current().nextDouble() < 0.55) {
            StageTemplate ambushTemplate = findAmbushTemplate(game);
            if (ambushTemplate != null) {
                game.getRoundManager().triggerAmbush(ambushTemplate);
                return EventOutcome.AMBUSH_TRIGGERED;
            }
        }

        applyTeamEffect(game, alive -> alive.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * 90, 0, false, true, true)));
        adjustSanityForAlivePlayers(game, 8);
        game.broadcast("§aThe machine stabilizes and shields the crew.");
        return EventOutcome.SAFE_RESOLVED;
    }

    private static EventOutcome handleAbandonedSupplies(Game game, Player player, int choice) {
        if (choice == 1) {
            healAlivePlayersPercent(game, 0.20);
            adjustSanityForAlivePlayers(game, 8);
            game.broadcast("§aThe supplies are divided carefully. The team patches itself up.");
            return EventOutcome.SAFE_RESOLVED;
        }

        if (choice != 2) {
            return EventOutcome.INVALID;
        }

        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 20 * 90, 0, false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 90, 0, false, true, true));
        game.changePlayerSanity(player, -10);
        game.broadcast("§c" + player.getName() + " uses the stimulant pack.");
        return EventOutcome.SAFE_RESOLVED;
    }

    private static EventOutcome handleWhisperingConsole(Game game, Player player, int choice) {
        if (choice == 1) {
            adjustSanityForAlivePlayers(game, 3);
            game.broadcast("§aThe console is shut down before it can say more.");
            return EventOutcome.SAFE_RESOLVED;
        }

        if (choice != 2) {
            return EventOutcome.INVALID;
        }

        applyTeamEffect(game, alive -> alive.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 20 * 120, 1, false, true, true)));
        adjustSanityForAlivePlayers(game, -6);
        game.broadcast("§6" + player.getName() + " reads the logs aloud. The crew learns something useful, at a cost.");
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
