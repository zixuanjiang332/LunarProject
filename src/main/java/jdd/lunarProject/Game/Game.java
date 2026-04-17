package jdd.lunarProject.Game;

import io.lumine.mythic.bukkit.MythicBukkit;
import jdd.lunarProject.Build.BuildModifierType;
import jdd.lunarProject.Build.PlayerRunData;
import jdd.lunarProject.Build.RelicDefinition;
import jdd.lunarProject.Build.RelicManager;
import jdd.lunarProject.Config.MessageManager;
import jdd.lunarProject.LunarProject;
import jdd.lunarProject.Tool.YellowBarUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class Game {
    private final String gameId;
    private final Set<UUID> activePlayers;
    private final Set<UUID> deadPlayers;
    private final Map<UUID, PlayerRunData> playerRunData;
    private final LobbyMap lobbyMap;
    private final RoundManager roundManager;

    private GameState gameState;
    private BukkitTask countdownTask;
    private int countdownTime;

    public Game() {
        this.gameId = UUID.randomUUID().toString().substring(0, 8);
        this.activePlayers = new LinkedHashSet<>();
        this.deadPlayers = new LinkedHashSet<>();
        this.playerRunData = new HashMap<>();
        this.lobbyMap = new LobbyMap(this.gameId);
        this.lobbyMap.load();
        this.roundManager = new RoundManager(this);
        setGameState(GameState.WAITING);
    }

    public void broadcast(String message) {
        for (UUID uuid : getAllPlayerIds()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.sendMessage(message);
            }
        }
    }

    public void sendTitle(String title, String subtitle) {
        for (UUID uuid : getAllPlayerIds()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.sendTitle(title, subtitle, 10, 40, 10);
            }
        }
    }

    public RoundManager getRoundManager() {
        return roundManager;
    }

    public void setGameState(GameState newState) {
        gameState = newState;
        cancelCountdown();
        switch (newState) {
            case WAITING -> broadcast(MessageManager.format(
                    "game.waiting",
                    "§e等待玩家中... (§f%current%/%required%§e)",
                    "current", activePlayers.size(),
                    "required", getMinPlayers()
            ));
            case STARTING -> beginStartingCountdown();
            case STARTED -> {
                sendTitle(
                        MessageManager.text("game.start-title", "§4旅途开始"),
                        MessageManager.text("game.start-subtitle", "§c前路已经展开")
                );
                roundManager.beginRunSelection();
            }
            case ENDED -> beginEndCountdown();
        }
    }

    public boolean playerJoin(Player player) {
        if (gameState == GameState.STARTED || gameState == GameState.ENDED) {
            player.sendMessage(MessageManager.text("game.room-unavailable", "§c这个房间已经开始或已经结束。"));
            return false;
        }
        if (activePlayers.size() >= getMaxPlayers()) {
            player.sendMessage(MessageManager.text("game.room-full", "§c这个房间已经满员。"));
            return false;
        }
        UUID uuid = player.getUniqueId();
        if (activePlayers.contains(uuid) || deadPlayers.contains(uuid)) {
            player.sendMessage(MessageManager.text("game.room-already-joined", "§e你已经在这个房间中了。"));
            return false;
        }
        activePlayers.add(uuid);
        getOrCreateRunData(uuid);
        GameManager.setPlayerGame(uuid, this);
        player.setGameMode(GameMode.SURVIVAL);
        PlayerClassManager.clearClassState(player);
        LunarProject.getInstance().getGuiManager().ensureMenuItem(player);
        Location spawn = lobbyMap.getSpawnLocation();
        if (spawn != null) {
            player.teleport(spawn);
        }
        player.sendMessage(MessageManager.text("game.join-select-class", "§e已加入房间，请先选择职业后再开始对局。"));
        broadcast(MessageManager.format(
                "game.join-broadcast",
                "§a%player% 加入了房间。(§f%current%/%max%§a)",
                "player", player.getName(),
                "current", activePlayers.size(),
                "max", getMaxPlayers()
        ));
        LunarProject.getInstance().getGuiManager().refreshGame(this);
        LunarProject.getInstance().getGuiManager().openClassSelect(player);
        if (gameState == GameState.WAITING && activePlayers.size() >= getMinPlayers()) {
            setGameState(GameState.STARTING);
        }
        return true;
    }

    public void playerLeave(Player player) {
        UUID uuid = player.getUniqueId();
        boolean wasActive = activePlayers.remove(uuid);
        boolean wasDead = deadPlayers.remove(uuid);
        if (!wasActive && !wasDead) {
            player.sendMessage(MessageManager.text("game.room-not-in-game", "§c你当前不在游戏房间中。"));
            return;
        }
        GameManager.setPlayerGame(uuid, null);
        playerRunData.remove(uuid);
        resetPlayerState(player);
        player.sendMessage(MessageManager.text("game.leave-self", "§e你已离开当前房间。"));
        if (gameState == GameState.WAITING || gameState == GameState.STARTING) {
            broadcast(MessageManager.format(
                    "game.leave-prestart",
                    "?c%player% ??????",
                    "player", player.getName()
            ));
            LunarProject.getInstance().getGuiManager().refreshGame(this);
            checkPlayerCount();
            return;
        }
        if (gameState == GameState.STARTED) {
            broadcast(MessageManager.format(
                    "game.leave-running",
                    "?c%player% ?????????",
                    "player", player.getName()
            ));
            LunarProject.getInstance().getGuiManager().refreshGame(this);
            checkPlayerCount();
            if (!activePlayers.isEmpty()) {
                roundManager.checkVote();
                roundManager.checkProceed();
                roundManager.checkRewardResolution();
            }
        }
    }

    public void onPlayerDeath(Player player) {
        UUID uuid = player.getUniqueId();
        if (!activePlayers.remove(uuid)) {
            return;
        }
        deadPlayers.add(uuid);
        player.setGameMode(GameMode.SPECTATOR);
        broadcast(MessageManager.format(
                "game.death",
                "?c?? %player% ????",
                "player", player.getName()
        ));
        LunarProject.getInstance().getGuiManager().refreshGame(this);
        if (activePlayers.isEmpty()) {
            broadcast(MessageManager.text("game.all-dead", "?4????????????????"));
            setGameState(GameState.ENDED);
        }
    }

    public void handleDisconnect(Player player) {
        UUID uuid = player.getUniqueId();
        if (gameState == GameState.WAITING || gameState == GameState.STARTING) {
            if (activePlayers.remove(uuid) || deadPlayers.remove(uuid)) {
                GameManager.setPlayerGame(uuid, null);
                playerRunData.remove(uuid);
                broadcast(MessageManager.format(
                        "game.disconnect-prestart",
                        "?c%player% ????????",
                        "player", player.getName()
                ));
                LunarProject.getInstance().getGuiManager().refreshGame(this);
                checkPlayerCount();
            }
            return;
        }
        if (gameState == GameState.STARTED && activePlayers.remove(uuid)) {
            deadPlayers.add(uuid);
            broadcast(MessageManager.format(
                    "game.disconnect-running",
                    "?c%player% ?????????????",
                    "player", player.getName()
            ));
            LunarProject.getInstance().getGuiManager().refreshGame(this);
            if (activePlayers.isEmpty()) {
                setGameState(GameState.ENDED);
                return;
            }
            roundManager.checkVote();
            roundManager.checkProceed();
            roundManager.checkRewardResolution();
        }
    }

    public void handleReconnect(Player player) {
        UUID uuid = player.getUniqueId();
        if (deadPlayers.contains(uuid)) {
            player.setGameMode(GameMode.SPECTATOR);
            player.teleport(getCurrentAnchorLocation());
            LunarProject.getInstance().getGuiManager().ensureMenuItem(player);
            player.sendMessage(MessageManager.text("game.reconnect-spectator", "?e???????????????"));
            return;
        }
        if (activePlayers.contains(uuid)) {
            player.setGameMode(GameMode.SURVIVAL);
            player.teleport(getCurrentAnchorLocation());
            LunarProject.getInstance().getGuiManager().ensureMenuItem(player);
            if (!isRunning() && !PlayerClassManager.hasSelectedClass(player)) {
                LunarProject.getInstance().getGuiManager().openClassSelect(player);
            }
            player.sendMessage(MessageManager.text("game.reconnect-player", "?a????????????"));
        }
    }

    public void start() {
        if (gameState == GameState.WAITING || gameState == GameState.STARTING) {
            if (!promptMissingClassSelection()) {
                return;
            }
            setGameState(GameState.STARTED);
        }
    }

    public void stop() {
        cancelCountdown();
        for (Player player : new ArrayList<>(getActiveOnlinePlayers())) {
            resetPlayerState(player);
        }
        for (Player player : new ArrayList<>(getDeadOnlinePlayers())) {
            resetPlayerState(player);
        }

        for (UUID uuid : getAllPlayerIds()) {
            GameManager.setPlayerGame(uuid, null);
        }

        activePlayers.clear();
        deadPlayers.clear();
        playerRunData.clear();

        new BukkitRunnable() {
            @Override
            public void run() {
                roundManager.cleanUp();
                lobbyMap.unload();
                GameManager.removeGame(gameId);
            }
        }.runTaskLater(LunarProject.getInstance(), 10L);
    }

    public void shutdownImmediately() {
        cancelCountdown();
        for (Player player : new ArrayList<>(getActiveOnlinePlayers())) {
            resetPlayerState(player);
        }
        for (Player player : new ArrayList<>(getDeadOnlinePlayers())) {
            resetPlayerState(player);
        }

        for (UUID uuid : getAllPlayerIds()) {
            GameManager.setPlayerGame(uuid, null);
        }

        activePlayers.clear();
        deadPlayers.clear();
        playerRunData.clear();
        roundManager.cleanUp();
        lobbyMap.unload();
        GameManager.removeGame(gameId);
    }

    public List<Player> getActiveOnlinePlayers() {
        return getOnlinePlayers(activePlayers);
    }

    public List<Player> getDeadOnlinePlayers() {
        return getOnlinePlayers(deadPlayers);
    }

    public boolean isRunning() {
        return gameState == GameState.STARTED;
    }

    public GameState getGameState() {
        return gameState;
    }

    public String getGameId() {
        return gameId;
    }

    public LobbyMap getLobbyMap() {
        return lobbyMap;
    }

    public int getActivePlayerCount() {
        return activePlayers.size();
    }

    public int getDeadPlayerCount() {
        return deadPlayers.size();
    }

    public int getTotalPlayerCount() {
        return activePlayers.size() + deadPlayers.size();
    }

    public boolean isActivePlayer(Player player) {
        return activePlayers.contains(player.getUniqueId());
    }

    public PlayerRunData getOrCreateRunData(UUID uuid) {
        return playerRunData.computeIfAbsent(uuid, ignored -> new PlayerRunData());
    }

    public boolean hasRelic(UUID uuid, String relicId) {
        PlayerRunData runData = playerRunData.get(uuid);
        return runData != null && runData.getRelicIds().contains(relicId);
    }

    public boolean addRelic(UUID uuid, String relicId) {
        if (!RelicManager.hasRelic(relicId)) {
            return false;
        }

        PlayerRunData runData = getOrCreateRunData(uuid);
        if (runData.getRelicIds().contains(relicId)) {
            return false;
        }

        runData.getRelicIds().add(relicId);
        runData.incrementRewardCount();
        return true;
    }

    public void addTemporaryModifier(UUID uuid, BuildModifierType modifierType, double amount, String sourceName) {
        PlayerRunData runData = getOrCreateRunData(uuid);
        runData.getTemporaryModifiers().merge(modifierType, amount, Double::sum);
        runData.incrementRewardCount();
        runData.getRewardHistory().add(sourceName + "（" + formatModifierLabel(modifierType) + " " + formatSignedValue(amount) + "）");
    }

    public void recordReward(UUID uuid, String rewardEntry) {
        getOrCreateRunData(uuid).getRewardHistory().add(rewardEntry);
    }

    public int getCoins(UUID uuid) {
        return getOrCreateRunData(uuid).getLunarCoins();
    }

    public void addCoins(UUID uuid, int amount) {
        if (amount <= 0) {
            return;
        }
        PlayerRunData runData = getOrCreateRunData(uuid);
        runData.addLunarCoins(amount);
    }

    public boolean spendCoins(UUID uuid, int amount) {
        return getOrCreateRunData(uuid).spendLunarCoins(amount);
    }

    public double getModifier(UUID uuid, BuildModifierType modifierType) {
        PlayerRunData runData = playerRunData.get(uuid);
        if (runData == null) {
            return 0.0;
        }

        double total = runData.getTemporaryModifiers().getOrDefault(modifierType, 0.0);
        for (String relicId : runData.getRelicIds()) {
            RelicDefinition relicDefinition = RelicManager.getRelic(relicId);
            if (relicDefinition != null && relicDefinition.effectType() == modifierType) {
                total += relicDefinition.effectValue();
            }
        }
        return total;
    }

    public int changePlayerSanity(Player player, int baseDelta) {
        var profile = MythicBukkit.inst().getPlayerManager().getProfile(player);
        if (profile == null) {
            return 0;
        }

        var variables = profile.getVariables();
        int delta = baseDelta;
        if (delta > 0) {
            delta += (int) Math.round(getModifier(player.getUniqueId(), BuildModifierType.SANITY_GAIN_BONUS));
        }

        int currentSanity = variables.has("sanity") ? variables.getInt("sanity") : 50;
        int updatedSanity = Math.max(-45, Math.min(50, currentSanity + delta));
        variables.putInt("sanity", updatedSanity);
        return delta;
    }

    public void healPlayerToFull(Player player) {
        if (player.getAttribute(Attribute.MAX_HEALTH) == null) {
            return;
        }
        player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
        YellowBarUtil.restorePostStagger(player);
    }

    public void applyPostCombatRecovery(Player player, double baseHealRatio, int baseSanityGain) {
        double totalHealRatio = Math.max(0.0, baseHealRatio + getModifier(player.getUniqueId(), BuildModifierType.POST_COMBAT_HEAL_BONUS));
        if (player.getAttribute(Attribute.MAX_HEALTH) != null) {
            double maxHealth = player.getAttribute(Attribute.MAX_HEALTH).getValue();
            double nextHealth = Math.min(maxHealth, player.getHealth() + (maxHealth * totalHealRatio));
            player.setHealth(Math.max(1.0, nextHealth));
        }
        changePlayerSanity(player, baseSanityGain);
    }

    public void clearTemporaryCombatState(Player player) {
        resetTransientCombatState(player);
    }

    public void restoreClassBaseline(Player player) {
        if (player == null) {
            return;
        }
        TemporaryCombatStateCleaner.clearTemporaryCombatState(player);
        PlayerClassManager.restoreClassBaseline(player, true, true, true);
    }

    public List<String> getBuildSummary(UUID uuid) {
        PlayerRunData runData = playerRunData.get(uuid);
        List<String> lines = new ArrayList<>();
        if (runData == null) {
            lines.add("暂无本局数据。");
            return lines;
        }

        lines.add("已领取奖励：" + runData.getRewardCount());
        lines.add("月蚀碎金：" + runData.getLunarCoins());
        if (runData.getRelicIds().isEmpty()) {
            lines.add("饰品：无");
        } else {
            List<String> relicNames = new ArrayList<>();
            for (String relicId : runData.getRelicIds()) {
                RelicDefinition relicDefinition = RelicManager.getRelic(relicId);
                relicNames.add(relicDefinition != null ? relicDefinition.name() : relicId);
            }
            lines.add("饰品：" + String.join("、", relicNames));
        }

        if (runData.getTemporaryModifiers().isEmpty()) {
            lines.add("临时强化：无");
        } else {
            List<String> tempLines = new ArrayList<>();
            for (Map.Entry<BuildModifierType, Double> entry : runData.getTemporaryModifiers().entrySet()) {
                tempLines.add(formatModifierLabel(entry.getKey()) + " " + formatSignedValue(entry.getValue()));
            }
            lines.add("临时强化：" + String.join("、", tempLines));
        }

        if (runData.getRewardHistory().isEmpty()) {
            lines.add("奖励记录：无");
        } else {
            int fromIndex = Math.max(0, runData.getRewardHistory().size() - 5);
            lines.add("最近奖励：" + String.join(" | ", runData.getRewardHistory().subList(fromIndex, runData.getRewardHistory().size())));
        }
        return lines;
    }

    private List<Player> getOnlinePlayers(Set<UUID> uuids) {
        List<Player> players = new ArrayList<>();
        for (UUID uuid : uuids) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                players.add(player);
            }
        }
        return players;
    }

    private Set<UUID> getAllPlayerIds() {
        Set<UUID> allPlayers = new LinkedHashSet<>(activePlayers);
        allPlayers.addAll(deadPlayers);
        return allPlayers;
    }

    private int getMinPlayers() {
        return LunarProject.getInstance().getGameMinPlayers();
    }

    private int getMaxPlayers() {
        return LunarProject.getInstance().getGameMaxPlayers();
    }

    private void beginStartingCountdown() {
        countdownTime = LunarProject.getInstance().getGameStartCountdown();
        broadcast(MessageManager.text("game.enough-players", "§a人数已满足要求，准备开始对局。"));
        countdownTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (activePlayers.size() < getMinPlayers()) {
                    broadcast(MessageManager.text("game.not-enough-cancel", "§c人数不足，开始倒计时已取消。"));
                    setGameState(GameState.WAITING);
                    cancel();
                    return;
                }

                if (countdownTime <= 0) {
                    if (promptMissingClassSelection()) {
                        setGameState(GameState.STARTED);
                        cancel();
                    } else {
                        countdownTime = 5;
                    }
                    return;
                }

                if (countdownTime % 5 == 0 || countdownTime <= 5) {
                    broadcast(MessageManager.format("game.start-countdown", "§e对局将在 §c%seconds% §e秒后开始。", "seconds", countdownTime));
                    if (countdownTime <= 5) {
                        sendTitle(
                                MessageManager.format("game.start-countdown-title", "§c%seconds%", "seconds", countdownTime),
                                MessageManager.text("game.start-countdown-subtitle", "§e准备战斗")
                        );
                    }
                }
                countdownTime--;
            }
        }.runTaskTimer(LunarProject.getInstance(), 0L, 20L);
    }

    private void beginEndCountdown() {
        countdownTime = 5;
        sendTitle(
                MessageManager.text("game.end-title", "§8本局结束"),
                MessageManager.text("game.end-subtitle", "§7正在清理房间...")
        );
        countdownTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (countdownTime <= 0) {
                    stop();
                    cancel();
                    return;
                }
                broadcast(MessageManager.format("game.end-countdown", "§c房间将在 %seconds% 秒后关闭。", "seconds", countdownTime));
                countdownTime--;
            }
        }.runTaskTimer(LunarProject.getInstance(), 0L, 20L);
    }

    private void cancelCountdown() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
    }

    private void checkPlayerCount() {
        if (gameState == GameState.STARTING && activePlayers.size() < getMinPlayers()) {
            setGameState(GameState.WAITING);
            return;
        }

        if (gameState == GameState.STARTED && activePlayers.isEmpty()) {
            setGameState(GameState.ENDED);
        }
    }

    private void resetPlayerState(Player player) {
        player.setGameMode(GameMode.SURVIVAL);
        PlayerClassManager.clearClassState(player);
        Location fallback = getFallbackLocation();
        if (fallback != null) {
            player.teleport(fallback);
        }
    }

    private void resetTransientCombatState(Player player) {
        TemporaryCombatStateCleaner.clearTemporaryCombatState(player);
    }

    private Location getCurrentAnchorLocation() {
        if (roundManager.getCurrentStage() != null) {
            Location stageAnchor = roundManager.getCurrentStage().getBossLocation();
            if (stageAnchor != null) {
                return stageAnchor;
            }

            if (roundManager.getCurrentStage().getWorld() != null) {
                return roundManager.getCurrentStage().getWorld().getSpawnLocation();
            }
        }

        if (lobbyMap.getSpawnLocation() != null) {
            return lobbyMap.getSpawnLocation();
        }

        return getFallbackLocation();
    }

    private Location getFallbackLocation() {
        List<World> worlds = Bukkit.getWorlds();
        if (worlds.isEmpty()) {
            return null;
        }
        return worlds.get(0).getSpawnLocation();
    }

    private String formatSignedValue(double value) {
        return (value > 0 ? "+" : "") + String.format("%.2f", value);
    }

    private String formatModifierLabel(BuildModifierType modifierType) {
        return switch (modifierType) {
            case DAMAGE_DEALT_MULT -> "伤害提高";
            case DAMAGE_TAKEN_MULT -> "承伤修正";
            case POST_COMBAT_HEAL_BONUS -> "战后恢复";
            case SANITY_GAIN_BONUS -> "理智回复";
            case FRAGILITY_BONUS -> "易损叠加";
        };
    }

    private boolean promptMissingClassSelection() {
        List<Player> missingPlayers = new ArrayList<>();
        for (Player player : getActiveOnlinePlayers()) {
            if (!PlayerClassManager.hasSelectedClass(player)) {
                missingPlayers.add(player);
            }
        }

        if (missingPlayers.isEmpty()) {
            return true;
        }

        broadcast(MessageManager.text("game.class-required-broadcast", "§c仍有玩家未选择职业，无法开始对局。"));
        for (Player player : missingPlayers) {
            player.sendMessage(MessageManager.text("game.class-required-player", "§e请先选择职业，目前可用职业：§f测试职业"));
            LunarProject.getInstance().getGuiManager().openClassSelect(player);
        }
        return false;
    }
}
