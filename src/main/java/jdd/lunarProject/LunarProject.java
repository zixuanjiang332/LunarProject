package jdd.lunarProject;

import jdd.lunarProject.MobsListener.*;
import jdd.lunarProject.SkillManager.ItemSkillManager;
import jdd.lunarProject.SkillManager.SkillCastManager;
import jdd.lunarProject.Task.SinkingSpeedManager;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
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
        // ... 下面是你原本的代码 ...
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new ProjectMoonExpansion().register();
        }
        ItemSkillManager itemSkillManager = new ItemSkillManager();
        new SinkingSpeedManager().runTaskTimer(this, 0, 10);
        getServer().getPluginManager().registerEvents(new ItemUseListener(itemSkillManager), this);
        getServer().getPluginManager().registerEvents(new TestMobListener(), this);
        getServer().getPluginManager().registerEvents(new MythicDamageListener(), this);
        getServer().getPluginManager().registerEvents(new DamageCalculator(),this);
        getServer().getPluginManager().registerEvents(new ArmorChangeListener(), this);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
