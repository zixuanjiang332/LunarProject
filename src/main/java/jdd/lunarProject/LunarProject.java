package jdd.lunarProject;

import jdd.lunarProject.Build.EventConfigManager;
import jdd.lunarProject.Build.RelicManager;
import jdd.lunarProject.Build.RewardManager;
import jdd.lunarProject.Command.GameCommand;
import jdd.lunarProject.Config.MapConfig;
import jdd.lunarProject.Game.GameManager;
import jdd.lunarProject.Game.MythicMobRegistry;
import jdd.lunarProject.Game.StageManager;
import jdd.lunarProject.Game.StaggerManager;
import jdd.lunarProject.GUI.GuiClickListener;
import jdd.lunarProject.GUI.GuiListener;
import jdd.lunarProject.GUI.GuiManager;
import jdd.lunarProject.GameListener.ConnectionListener;
import jdd.lunarProject.GameListener.GamePlayerListener;
import jdd.lunarProject.MobsListener.DamageCalculator;
import jdd.lunarProject.MobsListener.GameMobDeathListener;
import jdd.lunarProject.MobsListener.MythicDamageListener;
import jdd.lunarProject.MobsListener.PoiseCritManager;
import jdd.lunarProject.MobsListener.StaggerListener;
import jdd.lunarProject.MobsListener.TestMobListener;
import jdd.lunarProject.Task.SinkingSpeedManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class LunarProject extends JavaPlugin {
    private static LunarProject instance;
    private StaggerManager staggerManager;
    private GuiManager guiManager;

    @Override
    public void onLoad() {
        instance = this;
    }

    public static LunarProject getInstance() {
        return instance;
    }

    public StaggerManager getStaggerManager() {
        return staggerManager;
    }

    public GuiManager getGuiManager() {
        return guiManager;
    }

    public int getGameMinPlayers() {
        return Math.max(1, getConfig().getInt("game.min-players", 1));
    }

    public int getGameMaxPlayers() {
        return Math.max(getGameMinPlayers(), getConfig().getInt("game.max-players", 4));
    }

    public int getGameStartCountdown() {
        return Math.max(1, getConfig().getInt("game.start-countdown", 10));
    }

    public int getVoteTimeoutSeconds() {
        return Math.max(5, getConfig().getInt("game.vote-timeout-seconds", 20));
    }

    public int getReadyTimeoutSeconds() {
        return Math.max(10, getConfig().getInt("game.ready-timeout-seconds", 30));
    }

    public boolean isCombatLogEnabled() {
        return getConfig().getBoolean("debug.combat-log", false);
    }

    public boolean isDevMobScalingEnabled() {
        return getConfig().getBoolean("debug.dev-mob-scaling", false);
    }

    public boolean isGuiAutoOpenVoteEnabled() {
        return getConfig().getBoolean("gui.auto-open-vote", true);
    }

    public boolean isGuiAutoOpenRewardEnabled() {
        return getConfig().getBoolean("gui.auto-open-reward", true);
    }

    public boolean isGuiAutoOpenEventEnabled() {
        return getConfig().getBoolean("gui.auto-open-event", true);
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        MapConfig.init();

        List<String> mapIssues = MapConfig.ensureConfiguredMapFolders();
        if (!mapIssues.isEmpty()) {
            getLogger().severe("Failed to prepare stage map folders.");
            for (String issue : mapIssues) {
                getLogger().severe(" - " + issue);
            }
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        List<String> mobRegistryMessages = MythicMobRegistry.load();
        for (String message : mobRegistryMessages) {
            getLogger().info(message);
        }
        if (!MythicMobRegistry.hasAnyMob() || MythicMobRegistry.getCombatMobIds().isEmpty()) {
            getLogger().severe("No usable MythicMobs were found for stage generation.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        for (String message : RelicManager.init()) {
            getLogger().info(message);
        }
        for (String message : RewardManager.init()) {
            getLogger().info(message);
        }
        for (String message : EventConfigManager.init()) {
            getLogger().info(message);
        }

        staggerManager = new StaggerManager(this);
        guiManager = new GuiManager(this);
        if (!StageManager.initDatabase()) {
            getLogger().severe("Failed to initialize stage data.");
            for (String error : StageManager.getValidationErrors()) {
                getLogger().severe(" - " + error);
            }
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new ProjectMoonExpansion().register();
        }

        new SinkingSpeedManager().runTaskTimer(this, 0L, 10L);
        PluginCommand gameCommand = getCommand("game");
        if (gameCommand == null) {
            getLogger().severe("Command /game is missing from plugin.yml.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        GameCommand executor = new GameCommand();
        gameCommand.setExecutor(executor);
        gameCommand.setTabCompleter(executor);

        getServer().getPluginManager().registerEvents(new TestMobListener(), this);
        getServer().getPluginManager().registerEvents(new MythicDamageListener(), this);
        getServer().getPluginManager().registerEvents(new GameMobDeathListener(), this);
        getServer().getPluginManager().registerEvents(new DamageCalculator(), this);
        getServer().getPluginManager().registerEvents(new PoiseCritManager(), this);
        getServer().getPluginManager().registerEvents(new GamePlayerListener(), this);
        getServer().getPluginManager().registerEvents(new ConnectionListener(), this);
        getServer().getPluginManager().registerEvents(new StaggerListener(staggerManager), this);
        getServer().getPluginManager().registerEvents(new GuiListener(guiManager), this);
        getServer().getPluginManager().registerEvents(new GuiClickListener(guiManager), this);

        Bukkit.getOnlinePlayers().forEach(player -> {
            TestMobListener.applyPlayerHealthModel(player);
            guiManager.ensureMenuItem(player);
        });
    }

    @Override
    public void onDisable() {
        Bukkit.getLogger().info("Stopping all running LunarProject games...");
        GameManager.stopAllGames();
    }
}
