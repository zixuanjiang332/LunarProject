package jdd.lunarProject.Command;

import jdd.lunarProject.Game.Game;
import jdd.lunarProject.Game.GameManager;
import jdd.lunarProject.LunarProject;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GameCommand implements CommandExecutor, TabCompleter {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                LunarProject.getInstance().getGuiManager().openMainMenu(player);
            } else {
                sendHelp(sender);
            }
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);
        switch (subCommand) {
            case "menu" -> handleMenu(sender);
            case "create" -> handleCreate(sender);
            case "join" -> handleJoin(sender, args);
            case "leave" -> handleLeave(sender);
            case "start" -> handleStart(sender);
            case "stop" -> handleStop(sender, args);
            case "status" -> handleStatus(sender, args);
            case "build" -> handleBuild(sender);
            case "vote" -> handleVote(sender, args);
            case "proceed" -> handleProceed(sender);
            case "shop" -> handleShop(sender, args);
            case "event" -> handleEvent(sender, args);
            case "reward" -> handleReward(sender, args);
            default -> sendHelp(sender);
        }

        return true;
    }

    private void handleMenu(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can open the GUI menu.");
            return;
        }
        LunarProject.getInstance().getGuiManager().openMainMenu(player);
    }

    private void handleCreate(CommandSender sender) {
        Game newGame = GameManager.createGame();
        sender.sendMessage("§aCreated room §f" + newGame.getGameId());
        sender.sendMessage("§7Join it with §f/game join " + newGame.getGameId());
    }

    private void handleJoin(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can join rooms.");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /game join <id>");
            return;
        }

        if (GameManager.getPlayerGame(player.getUniqueId()) != null) {
            sender.sendMessage("§cYou are already in a room.");
            return;
        }

        Game targetGame = GameManager.getGame(args[1]);
        if (targetGame == null) {
            sender.sendMessage("§cRoom not found: " + args[1]);
            return;
        }

        targetGame.playerJoin(player);
    }

    private void handleLeave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can leave rooms.");
            return;
        }

        Game game = GameManager.getPlayerGame(player.getUniqueId());
        if (game == null) {
            sender.sendMessage("§cYou are not in a room.");
            return;
        }

        game.playerLeave(player);
    }

    private void handleStart(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can start a room.");
            return;
        }

        Game game = GameManager.getPlayerGame(player.getUniqueId());
        if (game == null) {
            sender.sendMessage("§cYou are not in a room.");
            return;
        }

        game.start();
        sender.sendMessage("§aRequested room start.");
    }

    private void handleStop(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            if (!canForceStop(sender)) {
                sender.sendMessage("§cOnly admins can stop a specific room.");
                return;
            }

            Game game = GameManager.getGame(args[1]);
            if (game == null) {
                sender.sendMessage("§cRoom not found: " + args[1]);
                return;
            }

            game.stop();
            sender.sendMessage("§aStopped room §f" + args[1]);
            return;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cConsole must use /game stop <id>.");
            return;
        }

        Game game = GameManager.getPlayerGame(player.getUniqueId());
        if (game == null) {
            sender.sendMessage("§cYou are not in a room.");
            return;
        }

        game.stop();
        sender.sendMessage("§aStopped your current room.");
    }

    private void handleStatus(CommandSender sender, String[] args) {
        Game game = resolveGameForStatus(sender, args);
        if (game == null) {
            return;
        }

        sender.sendMessage("§6===== LunarProject Status =====");
        sender.sendMessage("§7Room ID: §f" + game.getGameId());
        sender.sendMessage("§7State: §f" + game.getGameState());
        sender.sendMessage("§7Players: §fAlive " + game.getActivePlayerCount() + " / Dead " + game.getDeadPlayerCount() + " / Total " + game.getTotalPlayerCount());
        sender.sendMessage("§7Round: §f" + game.getRoundManager().getCurrentRound() + "/5");
        sender.sendMessage("§7Phase: §f" + game.getRoundManager().getCurrentRoomType());
        sender.sendMessage("§7Objective: §f" + game.getRoundManager().getStatusHint());

        if (game.getRoundManager().isVotingPhase()) {
            for (String line : game.getRoundManager().getVoteOptionDisplay()) {
                sender.sendMessage(line);
            }
        }

        if (sender instanceof Player player && GameManager.getPlayerGame(player.getUniqueId()) == game) {
            if (game.getRoundManager().isRewardPhase()) {
                sender.sendMessage("§6===== Pending Rewards =====");
                for (String line : game.getRoundManager().getRewardOptionDisplay(player)) {
                    sender.sendMessage(line);
                }
            }
            sender.sendMessage("§6===== Your Build =====");
            for (String line : game.getBuildSummary(player.getUniqueId())) {
                sender.sendMessage("§7" + line);
            }
        }
    }

    private void handleBuild(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can inspect a run build.");
            return;
        }

        Game game = GameManager.getPlayerGame(player.getUniqueId());
        if (game == null) {
            sender.sendMessage("§cYou are not in an active room.");
            return;
        }

        sender.sendMessage("§6===== Current Run Build =====");
        for (String line : game.getBuildSummary(player.getUniqueId())) {
            sender.sendMessage("§7" + line);
        }
    }

    private void handleVote(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can vote.");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /game vote <number>");
            return;
        }

        Game game = GameManager.getPlayerGame(player.getUniqueId());
        if (game == null || !game.isRunning()) {
            sender.sendMessage("§cYou are not in an active room.");
            return;
        }

        try {
            int choice = Integer.parseInt(args[1]);
            game.getRoundManager().castVote(player, choice);
        } catch (NumberFormatException exception) {
            sender.sendMessage("§cPlease enter a valid node number.");
        }
    }

    private void handleProceed(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can proceed.");
            return;
        }

        Game game = GameManager.getPlayerGame(player.getUniqueId());
        if (game == null || !game.isRunning()) {
            sender.sendMessage("§cYou are not in an active room.");
            return;
        }

        game.getRoundManager().setPlayerProceed(player);
    }

    private void handleShop(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use the shop.");
            return;
        }

        if (args.length < 3 || !"buy".equalsIgnoreCase(args[1])) {
            sender.sendMessage("§cUsage: /game shop buy <offerId>");
            return;
        }

        Game game = GameManager.getPlayerGame(player.getUniqueId());
        if (game == null || !game.isRunning()) {
            sender.sendMessage("§cYou are not in an active room.");
            return;
        }

        game.getRoundManager().handleShopPurchase(player, args[2]);
    }

    private void handleEvent(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can resolve events.");
            return;
        }

        if (args.length < 3 || !"choose".equalsIgnoreCase(args[1])) {
            sender.sendMessage("§cUsage: /game event choose <1|2>");
            return;
        }

        Game game = GameManager.getPlayerGame(player.getUniqueId());
        if (game == null || !game.isRunning()) {
            sender.sendMessage("§cYou are not in an active room.");
            return;
        }

        game.getRoundManager().handleEventChoice(player, args[2]);
    }

    private void handleReward(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can claim rewards.");
            return;
        }

        if (args.length < 3 || !"choose".equalsIgnoreCase(args[1])) {
            sender.sendMessage("§cUsage: /game reward choose <1|2|3>");
            return;
        }

        Game game = GameManager.getPlayerGame(player.getUniqueId());
        if (game == null || !game.isRunning()) {
            sender.sendMessage("§cYou are not in an active room.");
            return;
        }

        try {
            int choice = Integer.parseInt(args[2]);
            game.getRoundManager().handleRewardChoice(player, choice);
        } catch (NumberFormatException exception) {
            sender.sendMessage("§cPlease choose a valid reward number.");
        }
    }

    private Game resolveGameForStatus(CommandSender sender, String[] args) {
        Game game = null;
        if (args.length >= 2) {
            game = GameManager.getGame(args[1]);
        } else if (sender instanceof Player player) {
            game = GameManager.getPlayerGame(player.getUniqueId());
        }

        if (game == null) {
            if (!(sender instanceof Player) && !GameManager.getActiveGameIds().isEmpty()) {
                sender.sendMessage("§eActive rooms: " + String.join(", ", GameManager.getActiveGameIds()));
            } else {
                sender.sendMessage("§cNo room status is available.");
            }
        }
        return game;
    }

    private boolean canForceStop(CommandSender sender) {
        return !(sender instanceof Player) || sender.hasPermission("lunarproject.admin");
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6===== LunarProject Commands =====");
        sender.sendMessage("§7/game create §8- Create a room");
        sender.sendMessage("§7/game menu §8- Open the GUI menu");
        sender.sendMessage("§7/game join <id> §8- Join a room");
        sender.sendMessage("§7/game leave §8- Leave your room");
        sender.sendMessage("§7/game start §8- Start your room");
        sender.sendMessage("§7/game vote <n> §8- Vote for the next node");
        sender.sendMessage("§7/game reward choose <n> §8- Claim a room reward");
        sender.sendMessage("§7/game shop buy <id> §8- Claim a shop service");
        sender.sendMessage("§7/game event choose <id> §8- Resolve an event");
        sender.sendMessage("§7/game proceed §8- Confirm a peaceful room");
        sender.sendMessage("§7/game build §8- Show your current run build");
        sender.sendMessage("§7/game status [id] §8- Show room status");
        sender.sendMessage("§7/game stop [id] §8- Stop a room");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.add("create");
            completions.add("menu");
            completions.add("join");
            completions.add("leave");
            completions.add("start");
            completions.add("stop");
            completions.add("status");
            completions.add("build");
            completions.add("vote");
            completions.add("reward");
            completions.add("proceed");
            completions.add("shop");
            completions.add("event");
            return completions;
        }

        if (args.length == 2 && ("join".equalsIgnoreCase(args[0]) || "stop".equalsIgnoreCase(args[0]) || "status".equalsIgnoreCase(args[0]))) {
            completions.addAll(GameManager.getActiveGameIds());
            return completions;
        }

        if (args.length == 2 && "shop".equalsIgnoreCase(args[0])) {
            completions.add("buy");
            return completions;
        }

        if (args.length == 3 && "shop".equalsIgnoreCase(args[0]) && "buy".equalsIgnoreCase(args[1])) {
            completions.add("field_medicine");
            completions.add("clear_mind");
            completions.add("assault_stim");
            completions.add("safety_harness");
            return completions;
        }

        if (args.length == 2 && "event".equalsIgnoreCase(args[0])) {
            completions.add("choose");
            return completions;
        }

        if (args.length == 3 && "event".equalsIgnoreCase(args[0]) && "choose".equalsIgnoreCase(args[1])) {
            completions.add("1");
            completions.add("2");
            return completions;
        }

        if (args.length == 2 && "reward".equalsIgnoreCase(args[0])) {
            completions.add("choose");
            return completions;
        }

        if (args.length == 3 && "reward".equalsIgnoreCase(args[0]) && "choose".equalsIgnoreCase(args[1])) {
            completions.add("1");
            completions.add("2");
            completions.add("3");
        }

        return completions;
    }
}
