package jdd.lunarProject;

import jdd.lunarProject.Command.GameCommand;
import jdd.lunarProject.Config.MapConfig;
import jdd.lunarProject.Game.GameManager;
import jdd.lunarProject.Game.StageManager;
import jdd.lunarProject.MobsListener.*;
import jdd.lunarProject.SkillManager.ItemSkillManager;
import jdd.lunarProject.SkillManager.SkillCastManager;
import jdd.lunarProject.Task.SinkingSpeedManager;
import jdd.lunarProject.Weapon.ItemUseListener;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class LunarProject extends JavaPlugin {
    private static LunarProject instance;
    @Override
    public void onLoad() {
        instance = this;
    }
    public static LunarProject getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        SkillCastManager.initSkills();
        jdd.lunarProject.Weapon.WeaponRegistry.init();
        MapConfig.init();
        StageManager.initDatabase();
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new ProjectMoonExpansion().register();
        }
        ItemSkillManager itemSkillManager = new ItemSkillManager();
        new SinkingSpeedManager().runTaskTimer(this, 0, 10);
        getCommand("game").setExecutor(new GameCommand());
        getServer().getPluginManager().registerEvents(new ItemUseListener(itemSkillManager), this);
        getServer().getPluginManager().registerEvents(new TestMobListener(), this);
        getServer().getPluginManager().registerEvents(new MythicDamageListener(), this);
        getServer().getPluginManager().registerEvents(new GameMobDeathListener(), this);
        getServer().getPluginManager().registerEvents(new DamageCalculator(),this);
    }

    @Override
    public void onDisable() {
        // 【核心防 Bug 操作】插件关闭时强制结束所有进行中的游戏，回收地图内存
        Bukkit.getLogger().info("正在清理所有的游戏实例和临时世界...");
        GameManager.stopAllGames();
    }
}
