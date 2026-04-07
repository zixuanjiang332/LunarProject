package jdd.lunarProject.Command;
import jdd.lunarProject.Game.Game;
import jdd.lunarProject.Game.GameManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class GameCommand implements CommandExecutor, TabCompleter {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("lunarproject.admin")) {
            sender.sendMessage("§c[LunarProject] 你没有权限执行此命令！");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§8======= §c月亮计划(LunarProject) 游戏管理 §8=======");
            sender.sendMessage("§7/game create §8- §f创建一局新游戏");
            sender.sendMessage("§7/game join <id> §8- §f加入指定ID的游戏");
            sender.sendMessage("§7/game start §8- §f开始你所在的游戏");
            sender.sendMessage("§7/game stop [id] §8- §f结束你所在的游戏(或指定ID的游戏)");
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "create":
                Game newGame = GameManager.createGame();
                sender.sendMessage("§a[LunarProject] 成功创建游戏！游戏ID: §e" + newGame.getGameId());
                sender.sendMessage("§7提示: 输入 /game join " + newGame.getGameId() + " 加入该游戏");
                break;

            case "join":
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§c只有玩家可以加入游戏！");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("§c用法: /game join <id>");
                    return true;
                }
                Game targetGame = GameManager.getGame(args[1]);
                if (targetGame == null) {
                    sender.sendMessage("§c[LunarProject] 找不到ID为 " + args[1] + " 的游戏！");
                    return true;
                }
                Game current = GameManager.getPlayerGame(player.getUniqueId());
                if (current != null) {
                    sender.sendMessage("§c[LunarProject] 你已经在游戏 [" + current.getGameId() + "] 中了！请先退出或结束该游戏。");
                    return true;
                }
                targetGame.playerJoin(player);
                break;

            case "start":
                if (!(sender instanceof Player p1)) {
                    sender.sendMessage("§c只有玩家可以执行此操作！");
                    return true;
                }
                Game myGame = GameManager.getPlayerGame(p1.getUniqueId());
                if (myGame == null) {
                    sender.sendMessage("§c[LunarProject] 你当前不在任何游戏中！");
                    return true;
                }
                if (myGame.isRunning()) {
                    sender.sendMessage("§c[LunarProject] 这局游戏已经开始了！");
                    return true;
                }
                myGame.start();
                break;

            case "stop":
                if (args.length == 2) {
                    // 强制结束指定ID的游戏
                    String gameId = args[1];
                    Game gameToStop = GameManager.getGame(gameId);
                    if (gameToStop == null) {
                        sender.sendMessage("§c[LunarProject] 找不到ID为 " + gameId + " 的游戏！");
                        return true;
                    }
                    gameToStop.stop();
                    sender.sendMessage("§a[LunarProject] 已强制结束游戏: " + gameId);
                } else if (sender instanceof Player p2) {
                    // 结束自己所在的游戏
                    Game gameToStop = GameManager.getPlayerGame(p2.getUniqueId());
                    if (gameToStop == null) {
                        sender.sendMessage("§c[LunarProject] 你当前不在任何游戏中，或者请指定游戏ID: /game stop <id>");
                        return true;
                    }
                    gameToStop.stop();
                    sender.sendMessage("§a[LunarProject] 你所在的游戏已被强制结束。");
                } else {
                    sender.sendMessage("§c控制台请使用: /game stop <id>");
                }
                break;

            default:
                sender.sendMessage("§c未知的子命令，请输入 /game 查看帮助。");
                break;
        }

        return true;
    }

    // 提供 Tab 补全，按 Tab 键自动提示指令和当前进行中的游戏ID
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.add("create");
            completions.add("join");
            completions.add("start");
            completions.add("stop");
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("join") || args[0].equalsIgnoreCase("stop"))) {
            // 目前没有写暴露 activeGames 的 public 方法，这里暂且不补全ID
            // 如果你想让 Tab 能补全在线的游戏ID，可以在 GameManager 里加一个 getActiveGameIds() 方法
        }
        return completions;
    }
}