package jdd.lunarProject.Command;

import jdd.lunarProject.Config.MessageManager;
import jdd.lunarProject.Game.Game;
import jdd.lunarProject.Game.GameManager;
import jdd.lunarProject.Game.PlayerClassManager;
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
            case "class" -> handleClass(sender, args);
            default -> sendHelp(sender);
        }

        return true;
    }

    private void handleMenu(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            send(sender, "command.only-player-menu", "§c只有玩家可以打开主菜单。");
            return;
        }
        LunarProject.getInstance().getGuiManager().openMainMenu(player);
    }

    private void handleCreate(CommandSender sender) {
        Game newGame = GameManager.createGame();
        send(sender, "command.create-success", "§a已创建房间：§f%room%", "room", newGame.getGameId());
        send(sender, "command.create-hint", "§7使用 §f/game join %room% §7加入该房间。", "room", newGame.getGameId());
    }

    private void handleJoin(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "command.only-player-join", "§c只有玩家可以加入房间。");
            return;
        }

        if (args.length < 2) {
            send(sender, "command.join-usage", "§c用法：/game join <房间ID>");
            return;
        }

        if (GameManager.getPlayerGame(player.getUniqueId()) != null) {
            send(sender, "command.already-in-room", "§c你已经在一个房间中，不能重复加入。");
            return;
        }

        Game targetGame = GameManager.getGame(args[1]);
        if (targetGame == null) {
            send(sender, "command.room-not-found", "§c未找到房间：§f%room%", "room", args[1]);
            return;
        }

        targetGame.playerJoin(player);
    }

    private void handleLeave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            send(sender, "command.only-player-leave", "§c只有玩家可以离开房间。");
            return;
        }

        Game game = GameManager.getPlayerGame(player.getUniqueId());
        if (game == null) {
            send(sender, "command.not-in-room", "§c你当前不在任何房间中。");
            return;
        }

        game.playerLeave(player);
    }

    private void handleStart(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            send(sender, "command.only-player-start", "§c只有玩家可以启动房间。");
            return;
        }

        Game game = GameManager.getPlayerGame(player.getUniqueId());
        if (game == null) {
            send(sender, "command.not-in-room", "§c你当前不在任何房间中。");
            return;
        }

        game.start();
        send(sender, "command.start-attempt", "§a已尝试启动当前房间。");
    }

    private void handleStop(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            if (!canForceStop(sender)) {
                send(sender, "command.stop-no-permission", "§c你没有权限强制关闭指定房间。");
                return;
            }

            Game game = GameManager.getGame(args[1]);
            if (game == null) {
                send(sender, "command.room-not-found", "§c未找到房间：§f%room%", "room", args[1]);
                return;
            }

            game.stop();
            send(sender, "command.stop-success-force", "§a已强制关闭房间：§f%room%", "room", args[1]);
            return;
        }

        if (!(sender instanceof Player player)) {
            send(sender, "command.stop-console-usage", "§c控制台请使用 /game stop <房间ID>。");
            return;
        }

        Game game = GameManager.getPlayerGame(player.getUniqueId());
        if (game == null) {
            send(sender, "command.not-in-room", "§c你当前不在任何房间中。");
            return;
        }

        game.stop();
        send(sender, "command.stop-success-self", "§a已关闭你所在的当前房间。");
    }

    private void handleStatus(CommandSender sender, String[] args) {
        Game game = resolveGameForStatus(sender, args);
        if (game == null) {
            return;
        }

        send(sender, "command.status-title", "§6===== 房间状态 =====");
        send(sender, "command.status-room-id", "§7房间编号：§f%room%", "room", game.getGameId());
        send(sender, "command.status-room-state", "§7房间状态：§f%state%", "state", formatGameState(game.getGameState()));
        send(sender, "command.status-players", "§7玩家情况：§f存活 %alive% / 阵亡 %dead% / 总计 %total%",
                "alive", game.getActivePlayerCount(),
                "dead", game.getDeadPlayerCount(),
                "total", game.getTotalPlayerCount());
        send(sender, "command.status-round", "§7当前回合：§f%round%/5", "round", game.getRoundManager().getCurrentRound());
        send(sender, "command.status-phase", "§7当前阶段：§f%phase%", "phase", formatRoomType(game.getRoundManager().getCurrentRoomType()));
        send(sender, "command.status-hint", "§7阶段提示：§f%hint%", "hint", game.getRoundManager().getStatusHint());

        if (game.getRoundManager().isVotingPhase()) {
            for (String line : game.getRoundManager().getVoteOptionDisplay()) {
                sender.sendMessage(line);
            }
        }

        if (sender instanceof Player player && GameManager.getPlayerGame(player.getUniqueId()) == game) {
            String className = PlayerClassManager.getSelectedClass(player);
            send(sender, "command.status-class", "§7当前职业：§f%class%", "class", className.isBlank() ? "未选择" : className);
            if (!PlayerClassManager.hasSelectedClass(player)) {
                send(sender, "command.status-class-missing", "§e你还没有选择职业。");
            }
            if (game.getRoundManager().isRewardPhase()) {
                send(sender, "command.status-reward-title", "§6===== 当前奖励 =====");
                for (String line : game.getRoundManager().getRewardOptionDisplay(player)) {
                    sender.sendMessage(line);
                }
            }
            send(sender, "command.status-build-title", "§6===== 当前构筑 =====");
            for (String line : game.getBuildSummary(player.getUniqueId())) {
                sender.sendMessage("§7" + line);
            }
        }
    }

    private void handleBuild(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            send(sender, "command.only-player-build", "§c只有玩家可以查看当前构筑。");
            return;
        }

        Game game = GameManager.getPlayerGame(player.getUniqueId());
        if (game == null) {
            send(sender, "command.not-in-running-room", "§c你当前不在运行中的房间中。");
            return;
        }

        if (!PlayerClassManager.hasSelectedClass(player)) {
            send(sender, "command.status-class-missing", "§e你还没有选择职业。");
        }

        send(sender, "command.status-build-title", "§6===== 当前构筑 =====");
        for (String line : game.getBuildSummary(player.getUniqueId())) {
            sender.sendMessage("§7" + line);
        }
    }

    private void handleVote(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "command.only-player-vote", "§c只有玩家可以投票。");
            return;
        }

        if (args.length < 2) {
            send(sender, "command.vote-usage", "§c用法：/game vote <编号>");
            return;
        }

        Game game = GameManager.getPlayerGame(player.getUniqueId());
        if (game == null || !game.isRunning()) {
            send(sender, "command.not-in-running-room", "§c你当前不在运行中的房间中。");
            return;
        }

        try {
            int choice = Integer.parseInt(args[1]);
            game.getRoundManager().castVote(player, choice);
        } catch (NumberFormatException exception) {
            send(sender, "command.invalid-number", "§c请输入有效的数字编号。");
        }
    }

    private void handleProceed(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            send(sender, "command.only-player-proceed", "§c只有玩家可以推进流程。");
            return;
        }

        Game game = GameManager.getPlayerGame(player.getUniqueId());
        if (game == null || !game.isRunning()) {
            send(sender, "command.not-in-running-room", "§c你当前不在运行中的房间中。");
            return;
        }

        game.getRoundManager().setPlayerProceed(player);
    }

    private void handleShop(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "command.only-player-shop", "§c只有玩家可以使用商店。");
            return;
        }

        if (args.length < 3 || !"buy".equalsIgnoreCase(args[1])) {
            send(sender, "command.shop-usage", "§c用法：/game shop buy <商品代号>");
            return;
        }

        Game game = GameManager.getPlayerGame(player.getUniqueId());
        if (game == null || !game.isRunning()) {
            send(sender, "command.not-in-running-room", "§c你当前不在运行中的房间中。");
            return;
        }

        game.getRoundManager().purchaseShopOffer(player, args[2]);
    }

    private void handleEvent(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "command.only-player-event", "§c只有玩家可以处理事件。");
            return;
        }

        if (args.length < 3 || !"choose".equalsIgnoreCase(args[1])) {
            send(sender, "command.event-usage", "§c用法：/game event choose <1|2>");
            return;
        }

        Game game = GameManager.getPlayerGame(player.getUniqueId());
        if (game == null || !game.isRunning()) {
            send(sender, "command.not-in-running-room", "§c你当前不在运行中的房间中。");
            return;
        }

        game.getRoundManager().handleEventChoice(player, args[2]);
    }

    private void handleReward(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "command.only-player-reward", "§c只有玩家可以选择奖励。");
            return;
        }

        if (args.length < 3 || !"choose".equalsIgnoreCase(args[1])) {
            send(sender, "command.reward-usage", "§c用法：/game reward choose <1|2|3>");
            return;
        }

        Game game = GameManager.getPlayerGame(player.getUniqueId());
        if (game == null || !game.isRunning()) {
            send(sender, "command.not-in-running-room", "§c你当前不在运行中的房间中。");
            return;
        }

        try {
            int choice = Integer.parseInt(args[2]);
            game.getRoundManager().handleRewardChoice(player, choice);
        } catch (NumberFormatException exception) {
            send(sender, "command.invalid-reward-number", "§c请输入有效的奖励编号。");
        }
    }

    private void handleClass(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "command.only-player-class", "§c只有玩家可以选择职业。");
            return;
        }

        Game game = GameManager.getPlayerGame(player.getUniqueId());
        if (game == null) {
            send(sender, "command.not-in-room", "§c你当前不在任何房间中。");
            return;
        }

        if (args.length < 2) {
            LunarProject.getInstance().getGuiManager().openClassSelect(player);
            return;
        }

        if ("test".equalsIgnoreCase(args[1]) || PlayerClassManager.TEST_CLASS_ID.equalsIgnoreCase(args[1])) {
            if (!PlayerClassManager.applyClass(player, PlayerClassManager.TEST_CLASS_ID)) {
                send(sender, "command.class-init-failed", "§c测试职业初始化失败，请检查 config.yml 中的职业配置。");
                return;
            }
            send(sender, "command.class-selected", "§a已选择职业：§f%class%", "class", PlayerClassManager.TEST_CLASS_ID);
            LunarProject.getInstance().getGuiManager().refreshGame(game);
            return;
        }

        send(sender, "command.class-only-test", "§c当前仅开放测试职业。");
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
                send(sender, "command.active-rooms", "§7当前活跃房间：§f%rooms%", "rooms", String.join(", ", GameManager.getActiveGameIds()));
            } else {
                send(sender, "command.target-room-missing", "§c未找到目标房间。");
            }
        }
        return game;
    }

    private boolean canForceStop(CommandSender sender) {
        return sender.isOp() || sender.hasPermission("lunarproject.admin");
    }

    private String formatGameState(jdd.lunarProject.Game.GameState gameState) {
        if (gameState == null) {
            return "未知";
        }
        return switch (gameState) {
            case WAITING -> "等待中";
            case STARTING -> "准备开始";
            case STARTED -> "进行中";
            case ENDED -> "已结束";
        };
    }

    private String formatRoomType(String roomType) {
        if (roomType == null || roomType.isBlank()) {
            return "未知";
        }
        String upper = roomType.toUpperCase(Locale.ROOT);
        if ("MAJOR_SELECT".equals(upper)) {
            return "大关卡选择";
        }
        if ("VOTING".equals(upper)) {
            return "节点投票";
        }
        if (upper.startsWith("REWARD/")) {
            return "奖励选择";
        }
        if ("LOBBY".equals(upper)) {
            return "大厅准备";
        }
        return switch (upper) {
            case "NORMAL" -> "普通战";
            case "ELITE" -> "精英战";
            case "SHOP" -> "商店";
            case "REST" -> "休息";
            case "EVENT" -> "事件";
            case "BOSS" -> "首领战";
            default -> roomType;
        };
    }

    private void sendHelp(CommandSender sender) {
        for (String line : MessageManager.list("command.help", List.of(
                "§6===== /game 指令帮助 =====",
                "§f/game §7- 打开主菜单"
        ))) {
            sender.sendMessage(line);
        }
    }

    private void send(CommandSender sender, String key, String fallback, Object... placeholders) {
        sender.sendMessage(MessageManager.format(key, fallback, placeholders));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.add("menu");
            completions.add("create");
            completions.add("join");
            completions.add("leave");
            completions.add("start");
            completions.add("status");
            completions.add("build");
            completions.add("class");
            completions.add("vote");
            completions.add("reward");
            completions.add("shop");
            completions.add("event");
            completions.add("proceed");
            completions.add("stop");
            return filterCompletions(completions, args[0]);
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "join", "status", "stop" -> completions.addAll(GameManager.getActiveGameIds());
                case "class" -> completions.add("test");
                case "shop" -> completions.add("buy");
                case "event", "reward" -> completions.add("choose");
            }
            return filterCompletions(completions, args[1]);
        }

        if (args.length == 3) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "shop" -> {
                    completions.add("demo_terminal_chip");
                }
                case "event" -> {
                    completions.add("1");
                    completions.add("2");
                }
                case "reward" -> {
                    completions.add("1");
                    completions.add("2");
                    completions.add("3");
                }
            }
            return filterCompletions(completions, args[2]);
        }

        return completions;
    }

    private List<String> filterCompletions(List<String> completions, String input) {
        String lowered = input.toLowerCase(Locale.ROOT);
        return completions.stream()
                .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lowered))
                .toList();
    }
}
