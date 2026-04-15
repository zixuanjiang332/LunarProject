package jdd.lunarProject.Command;

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
            sender.sendMessage("§c只有玩家可以打开主菜单。");
            return;
        }
        LunarProject.getInstance().getGuiManager().openMainMenu(player);
    }

    private void handleCreate(CommandSender sender) {
        Game newGame = GameManager.createGame();
        sender.sendMessage("§a已创建房间：§f" + newGame.getGameId());
        sender.sendMessage("§7使用 §f/game join " + newGame.getGameId() + " §7加入该房间。");
    }

    private void handleJoin(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家可以加入房间。");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§c用法：/game join <房间ID>");
            return;
        }

        if (GameManager.getPlayerGame(player.getUniqueId()) != null) {
            sender.sendMessage("§c你已经在一个房间中，不能重复加入。");
            return;
        }

        Game targetGame = GameManager.getGame(args[1]);
        if (targetGame == null) {
            sender.sendMessage("§c未找到房间：§f" + args[1]);
            return;
        }

        targetGame.playerJoin(player);
    }

    private void handleLeave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家可以离开房间。");
            return;
        }

        Game game = GameManager.getPlayerGame(player.getUniqueId());
        if (game == null) {
            sender.sendMessage("§c你当前不在任何房间中。");
            return;
        }

        game.playerLeave(player);
    }

    private void handleStart(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家可以启动房间。");
            return;
        }

        Game game = GameManager.getPlayerGame(player.getUniqueId());
        if (game == null) {
            sender.sendMessage("§c你当前不在任何房间中。");
            return;
        }

        game.start();
        sender.sendMessage("§a已尝试启动当前房间。");
    }

    private void handleStop(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            if (!canForceStop(sender)) {
                sender.sendMessage("§c你没有权限强制关闭指定房间。");
                return;
            }

            Game game = GameManager.getGame(args[1]);
            if (game == null) {
                sender.sendMessage("§c未找到房间：§f" + args[1]);
                return;
            }

            game.stop();
            sender.sendMessage("§a已强制关闭房间：§f" + args[1]);
            return;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c控制台请使用 /game stop <房间ID>。");
            return;
        }

        Game game = GameManager.getPlayerGame(player.getUniqueId());
        if (game == null) {
            sender.sendMessage("§c你当前不在任何房间中。");
            return;
        }

        game.stop();
        sender.sendMessage("§a已关闭你所在的当前房间。");
    }

    private void handleStatus(CommandSender sender, String[] args) {
        Game game = resolveGameForStatus(sender, args);
        if (game == null) {
            return;
        }

        sender.sendMessage("§6===== 房间状态 =====");
        sender.sendMessage("§7房间 ID：§f" + game.getGameId());
        sender.sendMessage("§7房间状态：§f" + game.getGameState());
        sender.sendMessage("§7玩家情况：§f存活 " + game.getActivePlayerCount() + " / 阵亡 " + game.getDeadPlayerCount() + " / 总计 " + game.getTotalPlayerCount());
        sender.sendMessage("§7当前回合：§f" + game.getRoundManager().getCurrentRound() + "/5");
        sender.sendMessage("§7当前阶段：§f" + game.getRoundManager().getCurrentRoomType());
        sender.sendMessage("§7阶段提示：§f" + game.getRoundManager().getStatusHint());

        if (game.getRoundManager().isVotingPhase()) {
            for (String line : game.getRoundManager().getVoteOptionDisplay()) {
                sender.sendMessage(line);
            }
        }

        if (sender instanceof Player player && GameManager.getPlayerGame(player.getUniqueId()) == game) {
            String className = PlayerClassManager.getSelectedClass(player);
            sender.sendMessage("§7当前职业：§f" + (className.isBlank() ? "未选择" : className));
            if (!PlayerClassManager.hasSelectedClass(player)) {
                sender.sendMessage("§e你还没有选择职业。");
            }
            if (game.getRoundManager().isRewardPhase()) {
                sender.sendMessage("§6===== 当前奖励 =====");
                for (String line : game.getRoundManager().getRewardOptionDisplay(player)) {
                    sender.sendMessage(line);
                }
            }
            sender.sendMessage("§6===== 当前构筑 =====");
            for (String line : game.getBuildSummary(player.getUniqueId())) {
                sender.sendMessage("§7" + line);
            }
        }
    }

    private void handleBuild(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家可以查看当前构筑。");
            return;
        }

        Game game = GameManager.getPlayerGame(player.getUniqueId());
        if (game == null) {
            sender.sendMessage("§c你当前不在任何运行中的房间中。");
            return;
        }

        if (!PlayerClassManager.hasSelectedClass(player)) {
            sender.sendMessage("§e你当前还没有选择职业。");
        }

        sender.sendMessage("§6===== 当前构筑 =====");
        for (String line : game.getBuildSummary(player.getUniqueId())) {
            sender.sendMessage("§7" + line);
        }
    }

    private void handleVote(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家可以投票。");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§c用法：/game vote <编号>");
            return;
        }

        Game game = GameManager.getPlayerGame(player.getUniqueId());
        if (game == null || !game.isRunning()) {
            sender.sendMessage("§c你当前不在运行中的房间中。");
            return;
        }

        try {
            int choice = Integer.parseInt(args[1]);
            game.getRoundManager().castVote(player, choice);
        } catch (NumberFormatException exception) {
            sender.sendMessage("§c请输入有效的数字编号。");
        }
    }

    private void handleProceed(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家可以推进流程。");
            return;
        }

        Game game = GameManager.getPlayerGame(player.getUniqueId());
        if (game == null || !game.isRunning()) {
            sender.sendMessage("§c你当前不在运行中的房间中。");
            return;
        }

        game.getRoundManager().setPlayerProceed(player);
    }

    private void handleShop(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家可以使用商店。");
            return;
        }

        if (args.length < 3 || !"buy".equalsIgnoreCase(args[1])) {
            sender.sendMessage("§c用法：/game shop buy <商品ID>");
            return;
        }

        Game game = GameManager.getPlayerGame(player.getUniqueId());
        if (game == null || !game.isRunning()) {
            sender.sendMessage("§c你当前不在运行中的房间中。");
            return;
        }

        game.getRoundManager().purchaseShopOffer(player, args[2]);
    }

    private void handleEvent(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家可以处理事件。");
            return;
        }

        if (args.length < 3 || !"choose".equalsIgnoreCase(args[1])) {
            sender.sendMessage("§c用法：/game event choose <1|2>");
            return;
        }

        Game game = GameManager.getPlayerGame(player.getUniqueId());
        if (game == null || !game.isRunning()) {
            sender.sendMessage("§c你当前不在运行中的房间中。");
            return;
        }

        game.getRoundManager().handleEventChoice(player, args[2]);
    }

    private void handleReward(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家可以选择奖励。");
            return;
        }

        if (args.length < 3 || !"choose".equalsIgnoreCase(args[1])) {
            sender.sendMessage("§c用法：/game reward choose <1|2|3>");
            return;
        }

        Game game = GameManager.getPlayerGame(player.getUniqueId());
        if (game == null || !game.isRunning()) {
            sender.sendMessage("§c你当前不在运行中的房间中。");
            return;
        }

        try {
            int choice = Integer.parseInt(args[2]);
            game.getRoundManager().handleRewardChoice(player, choice);
        } catch (NumberFormatException exception) {
            sender.sendMessage("§c请输入有效的奖励编号。");
        }
    }

    private void handleClass(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家可以选择职业。");
            return;
        }

        Game game = GameManager.getPlayerGame(player.getUniqueId());
        if (game == null) {
            sender.sendMessage("§c你当前不在任何房间中。");
            return;
        }

        if (args.length < 2) {
            LunarProject.getInstance().getGuiManager().openClassSelect(player);
            return;
        }

        if ("test".equalsIgnoreCase(args[1]) || PlayerClassManager.TEST_CLASS_ID.equalsIgnoreCase(args[1])) {
            if (!PlayerClassManager.applyClass(player, PlayerClassManager.TEST_CLASS_ID)) {
                sender.sendMessage("§c测试职业初始化失败，请检查 config.yml 中的职业配置。");
                return;
            }
            sender.sendMessage("§a已选择职业：§f" + PlayerClassManager.TEST_CLASS_ID);
            LunarProject.getInstance().getGuiManager().refreshGame(game);
            return;
        }

        sender.sendMessage("§c当前仅开放测试职业。");
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
                sender.sendMessage("§7当前活跃房间：§f" + String.join(", ", GameManager.getActiveGameIds()));
            } else {
                sender.sendMessage("§c未找到目标房间。");
            }
        }
        return game;
    }

    private boolean canForceStop(CommandSender sender) {
        return sender.isOp() || sender.hasPermission("lunarproject.admin");
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6===== /game 指令帮助 =====");
        sender.sendMessage("§f/game §7- 打开主菜单");
        sender.sendMessage("§f/game create §7- 创建房间");
        sender.sendMessage("§f/game join <id> §7- 加入房间");
        sender.sendMessage("§f/game leave §7- 离开房间");
        sender.sendMessage("§f/game start §7- 启动当前房间");
        sender.sendMessage("§f/game status [id] §7- 查看房间状态");
        sender.sendMessage("§f/game build §7- 查看当前构筑");
        sender.sendMessage("§f/game class [test] §7- 选择职业");
        sender.sendMessage("§f/game vote <n> §7- 投票选择下一小关卡");
        sender.sendMessage("§f/game reward choose <n> §7- 选择奖励");
        sender.sendMessage("§f/game shop buy <id> §7- 购买商店商品");
        sender.sendMessage("§f/game event choose <1|2> §7- 选择事件选项");
        sender.sendMessage("§f/game proceed §7- 标记准备推进");
        sender.sendMessage("§f/game stop [id] §7- 关闭房间");
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
                    completions.add("field_patch");
                    completions.add("clear_mind");
                    completions.add("demo_terminal_chip");
                    completions.add("synthesis_placeholder");
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
