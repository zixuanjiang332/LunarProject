package jdd.lunarProject.Game;

import io.lumine.mythic.api.exceptions.InvalidMobTypeException;
import io.lumine.mythic.bukkit.MythicBukkit;
import jdd.lunarProject.Build.RewardManager;
import jdd.lunarProject.Build.RewardOption;
import jdd.lunarProject.Build.RewardPoolDefinition;
import jdd.lunarProject.Build.ServiceDefinition;
import jdd.lunarProject.Game.RandomEventManager.EventOutcome;
import jdd.lunarProject.Game.StageModels.MobSpawn;
import jdd.lunarProject.Game.StageModels.StageTemplate;
import jdd.lunarProject.Game.StageModels.WaveData;
import jdd.lunarProject.LunarProject;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class RoundManager {
    private static final int MAX_ROUNDS = 5;

    private final Game game;
    private final Map<UUID, Entity> activeMobs = new HashMap<>();
    private final Map<Integer, StageTemplate> currentNodeChoices = new LinkedHashMap<>();
    private final Map<UUID, Integer> playerVotes = new HashMap<>();
    private final Set<UUID> proceedPlayers = new LinkedHashSet<>();
    private final Map<UUID, List<RewardOption>> pendingRewards = new HashMap<>();
    private final Set<UUID> resolvedRewardPlayers = new LinkedHashSet<>();
    private final Set<UUID> shopPurchasedPlayers = new HashSet<>();
    private final List<WaveData> queuedWaves = new ArrayList<>();

    private GameLevel currentStage;
    private StageTemplate currentTemplate;
    private String currentEventId;
    private String currentRewardPoolId;

    private BukkitTask voteCountdownTask;
    private BukkitTask readyCountdownTask;
    private BukkitTask combatWatchdogTask;

    private int currentRound;
    private int currentWave;
    private int readyCountdown;
    private int voteCountdown;

    private boolean votingPhase;
    private boolean rewardPhase;
    private boolean rewardAutoAdvance;
    private boolean eventResolved;
    private boolean shopRoom;
    private boolean eventRoom;
    private boolean peacefulRoom;

    public RoundManager(Game game) {
        this.game = game;
    }

    public void generateNextNodeChoices() {
        cancelVoteCountdown();
        cancelReadyCountdown();
        cancelCombatWatchdog();
        clearTransientRoomState();

        if (currentRound <= 0) {
            currentRound = 1;
        }

        if (currentRound > MAX_ROUNDS) {
            game.broadcast("§aThe route has been completed.");
            game.setGameState(GameState.ENDED);
            return;
        }

        currentNodeChoices.clear();
        Set<String> seenStageIds = new LinkedHashSet<>();
        List<String> preferredTypes = getPreferredTypesForRound(currentRound);
        int targetChoices = currentRound >= MAX_ROUNDS ? 1 : 2;

        for (String stageType : preferredTypes) {
            StageTemplate template = StageManager.getRandomStage(1, currentRound, stageType);
            if (template == null || !seenStageIds.add(template.stageId())) {
                continue;
            }
            currentNodeChoices.put(currentNodeChoices.size() + 1, template);
            if (currentNodeChoices.size() >= targetChoices) {
                break;
            }
        }

        if (currentNodeChoices.isEmpty()) {
            StageTemplate fallback = StageManager.getAnyStageFallback(1, currentRound);
            if (fallback != null) {
                currentNodeChoices.put(1, fallback);
            }
        }

        if (currentNodeChoices.isEmpty()) {
            game.broadcast("§cNo valid node choices were found for round " + currentRound + ".");
            game.setGameState(GameState.ENDED);
            return;
        }

        votingPhase = true;
        voteCountdown = LunarProject.getInstance().getVoteTimeoutSeconds();
        playerVotes.clear();

        game.broadcast("§6===== Round " + currentRound + " =====");
        game.broadcast("§eVote for the next node with /game vote <n>.");
        for (String line : getVoteOptionDisplay()) {
            game.broadcast(line);
        }
        startVoteCountdown();
        LunarProject.getInstance().getGuiManager().refreshGame(game);
    }

    public void castVote(Player player, int choiceIndex) {
        if (!votingPhase) {
            player.sendMessage("§cThe room is not waiting for votes right now.");
            return;
        }
        if (!game.isActivePlayer(player)) {
            player.sendMessage("§cOnly active players can vote.");
            return;
        }
        if (!currentNodeChoices.containsKey(choiceIndex)) {
            player.sendMessage("§cThat node does not exist.");
            return;
        }

        playerVotes.put(player.getUniqueId(), choiceIndex);
        game.broadcast("§b" + player.getName() + " voted for node " + choiceIndex + ".");
        LunarProject.getInstance().getGuiManager().refreshGame(game);
        checkVote();
    }

    public void checkVote() {
        if (!votingPhase) {
            return;
        }
        if (game.getActivePlayerCount() <= 0) {
            game.setGameState(GameState.ENDED);
            return;
        }
        if (playerVotes.size() < game.getActivePlayerCount()) {
            return;
        }

        resolveVotes();
    }

    public void setPlayerProceed(Player player) {
        if (!peacefulRoom) {
            player.sendMessage("§cThere is nothing to proceed right now.");
            return;
        }
        if (!game.isActivePlayer(player)) {
            player.sendMessage("§cOnly active players can proceed.");
            return;
        }
        if (eventRoom && !eventResolved) {
            player.sendMessage("§cResolve the event first with /game event choose <1|2>.");
            return;
        }
        if (rewardPhase) {
            player.sendMessage("§cChoose your reward first with /game reward choose <1|2|3>.");
            return;
        }

        proceedPlayers.add(player.getUniqueId());
        game.broadcast("§a" + player.getName() + " is ready to move on.");
        LunarProject.getInstance().getGuiManager().refreshGame(game);
        checkProceed();
    }

    public void checkProceed() {
        if (!peacefulRoom || rewardPhase || (eventRoom && !eventResolved)) {
            return;
        }
        if (game.getActivePlayerCount() <= 0) {
            game.setGameState(GameState.ENDED);
            return;
        }
        if (proceedPlayers.size() < game.getActivePlayerCount()) {
            return;
        }

        finishPeacefulRoom();
    }

    public void handleShopPurchase(Player player, String offerId) {
        if (!shopRoom || !peacefulRoom) {
            player.sendMessage("§cYou are not in a shop room.");
            return;
        }
        if (!game.isActivePlayer(player)) {
            player.sendMessage("§cOnly active players can use the shop.");
            return;
        }
        if (shopPurchasedPlayers.contains(player.getUniqueId())) {
            player.sendMessage("§cYou have already claimed the shop service for this room.");
            return;
        }

        String normalizedId = normalizeShopOfferId(offerId);
        RewardPoolDefinition shopPool = RewardManager.getPool("shop_room");
        if (shopPool == null || !shopPool.services().contains(normalizedId)) {
            player.sendMessage("§cUnknown shop offer: " + offerId);
            return;
        }

        ServiceDefinition service = RewardManager.getService(normalizedId);
        if (service == null) {
            player.sendMessage("§cThat shop service is not configured.");
            return;
        }

        boolean applied = RewardManager.applyReward(
                game,
                player,
                new RewardOption(RewardOption.RewardType.SERVICE, service.id(), service.name(), service.rarity(), service.description())
        );
        if (!applied) {
            player.sendMessage("§cFailed to apply the shop service.");
            return;
        }

        shopPurchasedPlayers.add(player.getUniqueId());
        player.sendMessage("§aPurchased shop service: §f" + service.name());
        LunarProject.getInstance().getGuiManager().refreshGame(game);
    }

    public void handleEventChoice(Player player, String choiceId) {
        if (!eventRoom || !peacefulRoom) {
            player.sendMessage("§cYou are not in an event room.");
            return;
        }
        if (eventResolved) {
            player.sendMessage("§eThis event has already been resolved.");
            return;
        }
        if (!game.isActivePlayer(player)) {
            player.sendMessage("§cOnly active players can resolve the event.");
            return;
        }

        int choice;
        try {
            choice = Integer.parseInt(choiceId);
        } catch (NumberFormatException exception) {
            player.sendMessage("§cPlease choose 1 or 2.");
            return;
        }

        EventOutcome outcome = RandomEventManager.handleEventChoice(game, player, currentEventId, choice);
        if (outcome == EventOutcome.INVALID) {
            player.sendMessage("§cPlease choose 1 or 2.");
            return;
        }

        if (outcome == EventOutcome.SAFE_RESOLVED) {
            eventResolved = true;
            startRewardPhase("event_room", false);
            game.broadcast("§eThe event is resolved. Pick rewards, then use /game proceed.");
        }
        LunarProject.getInstance().getGuiManager().refreshGame(game);
    }

    public void handleRewardChoice(Player player, int choiceIndex) {
        if (!rewardPhase) {
            player.sendMessage("§cNo reward is waiting to be chosen right now.");
            return;
        }
        if (!game.isActivePlayer(player)) {
            player.sendMessage("§cOnly active players can claim rewards.");
            return;
        }
        if (resolvedRewardPlayers.contains(player.getUniqueId())) {
            player.sendMessage("§eYou already locked in your reward.");
            return;
        }

        List<RewardOption> rewardOptions = pendingRewards.get(player.getUniqueId());
        if (rewardOptions == null || rewardOptions.isEmpty()) {
            resolvedRewardPlayers.add(player.getUniqueId());
            checkRewardResolution();
            return;
        }
        if (choiceIndex < 1 || choiceIndex > rewardOptions.size()) {
            player.sendMessage("§cChoose a valid reward number.");
            return;
        }

        RewardOption rewardOption = rewardOptions.get(choiceIndex - 1);
        if (!RewardManager.applyReward(game, player, rewardOption)) {
            player.sendMessage("§cFailed to apply that reward.");
            return;
        }

        resolvedRewardPlayers.add(player.getUniqueId());
        player.sendMessage("§aSelected reward: §f" + rewardOption.name());
        LunarProject.getInstance().getGuiManager().refreshGame(game);
        checkRewardResolution();
    }

    public void checkRewardResolution() {
        if (!rewardPhase) {
            return;
        }

        for (Player player : game.getActiveOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            List<RewardOption> rewardOptions = pendingRewards.get(playerId);
            if (rewardOptions != null && !rewardOptions.isEmpty() && !resolvedRewardPlayers.contains(playerId)) {
                return;
            }
        }

        boolean autoAdvance = rewardAutoAdvance;
        finishRewardPhase();
        if (autoAdvance) {
            returnToLobbyAndAdvance();
            return;
        }

        game.broadcast("§aAll rewards are locked in. Use /game proceed when ready.");
        LunarProject.getInstance().getGuiManager().refreshGame(game);
        checkProceed();
    }

    public void handleMobDeath(UUID mobId) {
        Entity removed = activeMobs.remove(mobId);
        GameManager.unregisterMob(mobId);
        if (removed != null) {
            checkWaveClear();
        }
    }

    public void triggerAmbush(StageTemplate ambushTemplate) {
        if (ambushTemplate == null || currentStage == null) {
            eventResolved = true;
            game.broadcast("§cFailed to trigger the ambush encounter.");
            startRewardPhase("event_room", false);
            return;
        }

        eventResolved = true;
        eventRoom = false;
        peacefulRoom = false;
        proceedPlayers.clear();
        finishRewardPhase();
        cancelReadyCountdown();

        currentTemplate = ambushTemplate;
        prepareCombatStage();
        game.broadcast("§cThe event turns violent. An ambush begins!");
        spawnNextWave();
        LunarProject.getInstance().getGuiManager().refreshGame(game);
    }

    public void cleanUp() {
        cancelVoteCountdown();
        cancelReadyCountdown();
        cancelCombatWatchdog();
        despawnTrackedMobs();

        if (currentStage != null) {
            currentStage.unload();
            currentStage = null;
        }

        currentNodeChoices.clear();
        clearTransientRoomState();
    }

    public int getCurrentRound() {
        return Math.max(1, currentRound);
    }

    public GameLevel getCurrentStage() {
        return currentStage;
    }

    public boolean isVotingPhase() {
        return votingPhase;
    }

    public boolean isRewardPhase() {
        return rewardPhase;
    }

    public boolean isShopRoom() {
        return shopRoom;
    }

    public boolean isEventRoom() {
        return eventRoom;
    }

    public boolean isPeacefulRoom() {
        return peacefulRoom;
    }

    public boolean isEventResolved() {
        return eventResolved;
    }

    public int getVoteCountdownRemaining() {
        return Math.max(0, voteCountdown);
    }

    public int getReadyCountdownRemaining() {
        return Math.max(0, readyCountdown);
    }

    public int getProceedReadyCount() {
        return proceedPlayers.size();
    }

    public boolean hasPlayerProceeded(UUID playerId) {
        return proceedPlayers.contains(playerId);
    }

    public int getPendingRewardPlayerCount() {
        int count = 0;
        for (UUID playerId : pendingRewards.keySet()) {
            if (!resolvedRewardPlayers.contains(playerId) && !pendingRewards.getOrDefault(playerId, List.of()).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    public List<RewardOption> getPendingRewardOptions(UUID playerId) {
        return List.copyOf(pendingRewards.getOrDefault(playerId, List.of()));
    }

    public boolean isRewardResolved(UUID playerId) {
        return resolvedRewardPlayers.contains(playerId);
    }

    public String getCurrentEventId() {
        return currentEventId;
    }

    public int getVoteCount(int choiceIndex) {
        int total = 0;
        for (int vote : playerVotes.values()) {
            if (vote == choiceIndex) {
                total++;
            }
        }
        return total;
    }

    public int getPlayerVote(UUID playerId) {
        return playerVotes.getOrDefault(playerId, -1);
    }

    public Map<Integer, StageTemplate> getNodeChoices() {
        return new LinkedHashMap<>(currentNodeChoices);
    }

    public boolean hasPurchasedShopService(UUID playerId) {
        return shopPurchasedPlayers.contains(playerId);
    }

    public String getVotePhaseKey() {
        return votingPhase ? "vote:" + currentRound : "";
    }

    public String getRewardPhaseKey() {
        if (!rewardPhase || currentTemplate == null) {
            return "";
        }
        return "reward:" + currentTemplate.stageId() + ":" + currentRewardPoolId;
    }

    public String getEventPhaseKey() {
        if (!eventRoom || currentTemplate == null) {
            return "";
        }
        return "event:" + currentTemplate.stageId() + ":" + currentEventId;
    }

    public String getCurrentRoomType() {
        if (votingPhase) {
            return "VOTING";
        }
        if (rewardPhase && currentTemplate != null) {
            return "REWARD/" + currentTemplate.stageType();
        }
        if (currentTemplate != null) {
            return currentTemplate.stageType();
        }
        return "LOBBY";
    }

    public String getStatusHint() {
        if (votingPhase) {
            return "Vote for the next node with /game vote <n>.";
        }
        if (rewardPhase) {
            return "Choose a reward with /game reward choose <1|2|3>.";
        }
        if (eventRoom && !eventResolved) {
            return "Resolve the event with /game event choose <1|2>.";
        }
        if (peacefulRoom) {
            if (shopRoom) {
                return "Claim one shop service with /game shop buy <id>, then /game proceed.";
            }
            return "Use /game proceed once your team is ready.";
        }
        if (currentTemplate != null) {
            return "Defeat all enemies in the room.";
        }
        return "Wait for the next room.";
    }

    public List<String> getVoteOptionDisplay() {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<Integer, StageTemplate> entry : currentNodeChoices.entrySet()) {
            StageTemplate template = entry.getValue();
            lines.add("§b[" + entry.getKey() + "] §f" + formatStageType(template.stageType()) + " §7- " + template.stageId());
        }
        return lines;
    }

    public List<String> getRewardOptionDisplay(Player player) {
        List<String> lines = new ArrayList<>();
        List<RewardOption> rewardOptions = pendingRewards.get(player.getUniqueId());
        if (rewardOptions == null || rewardOptions.isEmpty()) {
            lines.add("§7No pending rewards.");
            return lines;
        }

        for (int index = 0; index < rewardOptions.size(); index++) {
            RewardOption option = rewardOptions.get(index);
            lines.add("§b[" + (index + 1) + "] §f" + option.name() + " §7(" + option.rewardType() + ", " + option.rarity() + ")");
            lines.add("§7 - " + option.description());
        }
        return lines;
    }

    private void resolveVotes() {
        cancelVoteCountdown();
        Map<Integer, Integer> totals = new HashMap<>();
        for (int vote : playerVotes.values()) {
            totals.merge(vote, 1, Integer::sum);
        }

        int chosenNode = currentNodeChoices.keySet().stream()
                .min(Comparator.<Integer>comparingInt(choice -> -totals.getOrDefault(choice, 0)).thenComparingInt(Integer::intValue))
                .orElse(1);

        StageTemplate chosenTemplate = currentNodeChoices.get(chosenNode);
        votingPhase = false;
        playerVotes.clear();
        currentNodeChoices.clear();

        if (chosenTemplate == null) {
            chosenTemplate = StageManager.getAnyStageFallback(1, currentRound);
        }

        if (chosenTemplate == null) {
            game.broadcast("§cFailed to resolve the next node.");
            game.setGameState(GameState.ENDED);
            return;
        }

        startStage(chosenTemplate);
    }

    private void startStage(StageTemplate template) {
        currentTemplate = template;
        currentEventId = null;
        eventResolved = false;
        proceedPlayers.clear();
        shopPurchasedPlayers.clear();

        if (currentStage != null) {
            currentStage.unload();
        }

        currentStage = new GameLevel(template.mapName(), game.getGameId() + "_r" + currentRound);
        if (!currentStage.load()) {
            game.broadcast("§cFailed to load map " + template.mapName() + ".");
            game.setGameState(GameState.ENDED);
            return;
        }

        teleportPlayersToCurrentStage();
        game.broadcast("§6Entering " + formatStageType(template.stageType()) + " §7(" + template.stageId() + ")");
        LunarProject.getInstance().getGuiManager().refreshGame(game);

        switch (template.stageType().toUpperCase(Locale.ROOT)) {
            case "REST" -> enterRestRoom();
            case "SHOP" -> enterShopRoom();
            case "EVENT" -> enterEventRoom();
            default -> {
                prepareCombatStage();
                spawnNextWave();
            }
        }
    }

    private void enterRestRoom() {
        peacefulRoom = true;
        for (Player player : game.getActiveOnlinePlayers()) {
            game.applyPostCombatRecovery(player, 0.10, 4);
        }
        game.broadcast("§aThe team catches its breath. Pick a room reward, then use /game proceed.");
        startRewardPhase("rest_room", false);
        startReadyCountdown();
        LunarProject.getInstance().getGuiManager().refreshGame(game);
    }

    private void enterShopRoom() {
        peacefulRoom = true;
        shopRoom = true;
        game.broadcast("§aA temporary shop opens. Each player may claim one shop service.");
        broadcastShopServices();
        startRewardPhase("shop_room", false);
        startReadyCountdown();
        LunarProject.getInstance().getGuiManager().refreshGame(game);
    }

    private void enterEventRoom() {
        peacefulRoom = true;
        eventRoom = true;
        currentEventId = RandomEventManager.pickRandomEventId();
        RandomEventManager.broadcastEventIntro(game, currentEventId);
        startReadyCountdown();
        LunarProject.getInstance().getGuiManager().refreshGame(game);
    }

    private void prepareCombatStage() {
        peacefulRoom = false;
        shopRoom = false;
        eventRoom = false;
        eventResolved = true;
        proceedPlayers.clear();
        queuedWaves.clear();
        queuedWaves.addAll(currentTemplate.waves().values());
        queuedWaves.sort(Comparator.comparingInt(WaveData::waveIndex));
        currentWave = 0;
        startCombatWatchdog();
    }

    private void spawnNextWave() {
        pruneInactiveMobs();
        if (currentWave >= queuedWaves.size()) {
            onCombatStageCleared();
            return;
        }
        if (currentStage == null || currentStage.getWorld() == null) {
            game.broadcast("§cThe combat map is not available anymore.");
            game.setGameState(GameState.ENDED);
            return;
        }

        WaveData waveData = queuedWaves.get(currentWave);
        currentWave++;
        List<Location> spawners = new ArrayList<>(currentStage.getMonsterLocations());
        if (spawners.isEmpty()) {
            Location fallback = getFallbackSpawn(currentStage);
            if (fallback != null) {
                spawners.add(fallback);
            }
        }

        int spawnIndex = 0;
        game.broadcast("§cWave " + waveData.waveIndex() + " begins.");
        for (MobSpawn mobSpawn : waveData.mobs()) {
            for (int amount = 0; amount < mobSpawn.amount(); amount++) {
                if (spawners.isEmpty()) {
                    break;
                }
                Location spawnLocation = spawners.get(Math.floorMod(spawnIndex, spawners.size())).clone();
                spawnIndex++;
                try {
                    Entity entity = MythicBukkit.inst().getAPIHelper().spawnMythicMob(mobSpawn.mythicMobsId(), spawnLocation, 1);
                    if (entity == null) {
                        LunarProject.getInstance().getLogger().warning("MythicMobs returned null while spawning mob: " + mobSpawn.mythicMobsId());
                        continue;
                    }
                    activeMobs.put(entity.getUniqueId(), entity);
                    GameManager.registerMob(entity.getUniqueId(), game);
                } catch (InvalidMobTypeException exception) {
                    LunarProject.getInstance().getLogger().warning("Failed to spawn MythicMob " + mobSpawn.mythicMobsId() + ": " + exception.getMessage());
                } catch (Exception exception) {
                    LunarProject.getInstance().getLogger().warning("Unexpected error while spawning MythicMob " + mobSpawn.mythicMobsId() + ": " + exception.getMessage());
                }
            }
        }

        if (activeMobs.isEmpty()) {
            game.broadcast("§eNo mobs survived spawning for this wave. Skipping ahead.");
            new BukkitRunnable() {
                @Override
                public void run() {
                    checkWaveClear();
                }
            }.runTaskLater(LunarProject.getInstance(), 10L);
        }
    }

    private void checkWaveClear() {
        pruneInactiveMobs();
        if (!activeMobs.isEmpty()) {
            return;
        }

        if (currentWave < queuedWaves.size()) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    spawnNextWave();
                }
            }.runTaskLater(LunarProject.getInstance(), 40L);
            return;
        }

        onCombatStageCleared();
    }

    private void onCombatStageCleared() {
        cancelCombatWatchdog();
        for (Player player : game.getActiveOnlinePlayers()) {
            game.applyPostCombatRecovery(player, 0.10, 5);
        }
        game.broadcast("§aRoom cleared. Choose your rewards.");
        startRewardPhase(RewardManager.getPoolForStageType(currentTemplate.stageType()), true);
        LunarProject.getInstance().getGuiManager().refreshGame(game);
    }

    private void startRewardPhase(String poolId, boolean autoAdvance) {
        finishRewardPhase();

        rewardPhase = true;
        rewardAutoAdvance = autoAdvance;
        currentRewardPoolId = poolId;

        for (Player player : game.getActiveOnlinePlayers()) {
            List<RewardOption> rewardOptions = RewardManager.generateRewardOptions(game, player, poolId);
            if (rewardOptions.isEmpty()) {
                resolvedRewardPlayers.add(player.getUniqueId());
                player.sendMessage("§7No unique rewards were available for you in this room.");
                continue;
            }
            pendingRewards.put(player.getUniqueId(), rewardOptions);
            player.sendMessage("§6===== Reward Choice =====");
            for (String line : getRewardOptionDisplay(player)) {
                player.sendMessage(line);
            }
            player.sendMessage("§eUse /game reward choose <1|2|3>.");
        }

        LunarProject.getInstance().getLogger().info("[Reward] Generated pool " + poolId + " for room " + getCurrentRoomType());
        checkRewardResolution();
    }

    private void finishRewardPhase() {
        rewardPhase = false;
        rewardAutoAdvance = false;
        currentRewardPoolId = null;
        pendingRewards.clear();
        resolvedRewardPlayers.clear();
    }

    private void finishPeacefulRoom() {
        cancelReadyCountdown();
        returnToLobbyAndAdvance();
    }

    private void returnToLobbyAndAdvance() {
        cancelVoteCountdown();
        cancelReadyCountdown();
        cancelCombatWatchdog();
        despawnTrackedMobs();

        if (game.getLobbyMap().getSpawnLocation() != null) {
            for (Player player : game.getActiveOnlinePlayers()) {
                player.teleport(game.getLobbyMap().getSpawnLocation());
            }
            for (Player player : game.getDeadOnlinePlayers()) {
                player.teleport(game.getLobbyMap().getSpawnLocation());
            }
        }

        boolean bossVictory = currentTemplate != null
                && "BOSS".equalsIgnoreCase(currentTemplate.stageType())
                && currentRound >= MAX_ROUNDS;

        if (currentStage != null) {
            currentStage.unload();
            currentStage = null;
        }

        currentTemplate = null;
        clearTransientRoomState();

        if (bossVictory) {
            game.broadcast("§6The final boss falls. The run is complete.");
            game.setGameState(GameState.ENDED);
            return;
        }

        currentRound++;
        LunarProject.getInstance().getGuiManager().refreshGame(game);
        new BukkitRunnable() {
            @Override
            public void run() {
                generateNextNodeChoices();
            }
        }.runTaskLater(LunarProject.getInstance(), 20L);
    }

    private void startVoteCountdown() {
        cancelVoteCountdown();
        voteCountdownTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!votingPhase) {
                    cancelVoteCountdown();
                    return;
                }
                if (voteCountdown <= 0) {
                    int fallbackChoice = currentNodeChoices.keySet().stream().min(Integer::compareTo).orElse(1);
                    for (Player player : game.getActiveOnlinePlayers()) {
                        playerVotes.putIfAbsent(player.getUniqueId(), fallbackChoice);
                    }
                    game.broadcast("§eVoting timed out. Remaining players default to node " + fallbackChoice + ".");
                    resolveVotes();
                    return;
                }
                if (voteCountdown == LunarProject.getInstance().getVoteTimeoutSeconds() || voteCountdown <= 5 || voteCountdown % 10 == 0) {
                    game.broadcast("§7Vote window closes in §f" + voteCountdown + "§7 seconds.");
                }
                voteCountdown--;
            }
        }.runTaskTimer(LunarProject.getInstance(), 20L, 20L);
    }

    private void startReadyCountdown() {
        cancelReadyCountdown();
        readyCountdown = LunarProject.getInstance().getReadyTimeoutSeconds();
        readyCountdownTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!peacefulRoom) {
                    cancelReadyCountdown();
                    return;
                }

                if (readyCountdown <= 0) {
                    if (eventRoom && !eventResolved) {
                        EventOutcome outcome = RandomEventManager.resolveTimeout(game, currentEventId);
                        if (outcome == EventOutcome.SAFE_RESOLVED) {
                            eventResolved = true;
                            startRewardPhase("event_room", false);
                        }
                    }
                    if (rewardPhase) {
                        autoResolvePendingRewards();
                    }
                    finishPeacefulRoom();
                    return;
                }

                if (readyCountdown == LunarProject.getInstance().getReadyTimeoutSeconds() || readyCountdown <= 5 || readyCountdown % 10 == 0) {
                    game.broadcast("§7Room auto-advances in §f" + readyCountdown + "§7 seconds.");
                }
                readyCountdown--;
            }
        }.runTaskTimer(LunarProject.getInstance(), 20L, 20L);
    }

    private void startCombatWatchdog() {
        cancelCombatWatchdog();
        combatWatchdogTask = new BukkitRunnable() {
            @Override
            public void run() {
                pruneInactiveMobs();
                if (activeMobs.isEmpty() && !queuedWaves.isEmpty()) {
                    checkWaveClear();
                }
            }
        }.runTaskTimer(LunarProject.getInstance(), 40L, 40L);
    }

    private void cancelVoteCountdown() {
        if (voteCountdownTask != null) {
            voteCountdownTask.cancel();
            voteCountdownTask = null;
        }
    }

    private void cancelReadyCountdown() {
        if (readyCountdownTask != null) {
            readyCountdownTask.cancel();
            readyCountdownTask = null;
        }
    }

    private void cancelCombatWatchdog() {
        if (combatWatchdogTask != null) {
            combatWatchdogTask.cancel();
            combatWatchdogTask = null;
        }
    }

    private void pruneInactiveMobs() {
        List<UUID> expiredIds = new ArrayList<>();
        for (Map.Entry<UUID, Entity> entry : activeMobs.entrySet()) {
            Entity entity = entry.getValue();
            if (entity == null || !entity.isValid() || entity.isDead()) {
                expiredIds.add(entry.getKey());
            }
        }

        for (UUID expiredId : expiredIds) {
            activeMobs.remove(expiredId);
            GameManager.unregisterMob(expiredId);
        }
    }

    private void despawnTrackedMobs() {
        for (Map.Entry<UUID, Entity> entry : new ArrayList<>(activeMobs.entrySet())) {
            Entity entity = entry.getValue();
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
            GameManager.unregisterMob(entry.getKey());
        }
        activeMobs.clear();
    }

    private void autoResolvePendingRewards() {
        if (!rewardPhase) {
            return;
        }

        for (Player player : game.getActiveOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            if (resolvedRewardPlayers.contains(playerId)) {
                continue;
            }

            List<RewardOption> rewardOptions = pendingRewards.get(playerId);
            if (rewardOptions == null || rewardOptions.isEmpty()) {
                resolvedRewardPlayers.add(playerId);
                continue;
            }

            RewardOption fallbackReward = rewardOptions.get(0);
            if (RewardManager.applyReward(game, player, fallbackReward)) {
                resolvedRewardPlayers.add(playerId);
                player.sendMessage("§eReward timed out. Auto-selected: §f" + fallbackReward.name());
            }
        }

        checkRewardResolution();
    }

    private void teleportPlayersToCurrentStage() {
        Location target = getFallbackSpawn(currentStage);
        if (target == null) {
            return;
        }
        for (Player player : game.getActiveOnlinePlayers()) {
            player.teleport(target);
        }
        for (Player player : game.getDeadOnlinePlayers()) {
            player.teleport(target);
        }
    }

    private void broadcastShopServices() {
        RewardPoolDefinition shopPool = RewardManager.getPool("shop_room");
        if (shopPool == null || shopPool.services().isEmpty()) {
            game.broadcast("§7No shop services are configured.");
            return;
        }

        game.broadcast("§eShop services:");
        for (String serviceId : shopPool.services()) {
            ServiceDefinition service = RewardManager.getService(serviceId);
            if (service == null) {
                continue;
            }
            game.broadcast("§b- " + service.id() + " §7-> §f" + service.name() + "§7: " + service.description());
        }
    }

    private Location getFallbackSpawn(GameLevel stage) {
        if (stage == null) {
            return game.getLobbyMap().getSpawnLocation();
        }
        if (stage.getBossLocation() != null) {
            return stage.getBossLocation();
        }
        World world = stage.getWorld();
        if (world != null) {
            return world.getSpawnLocation();
        }
        return game.getLobbyMap().getSpawnLocation();
    }

    private String normalizeShopOfferId(String offerId) {
        return switch (offerId.toLowerCase(Locale.ROOT)) {
            case "heal" -> "field_medicine";
            case "sanity" -> "clear_mind";
            case "guard" -> "safety_harness";
            case "fury" -> "assault_stim";
            default -> offerId;
        };
    }

    private void clearTransientRoomState() {
        votingPhase = false;
        rewardPhase = false;
        rewardAutoAdvance = false;
        eventResolved = false;
        shopRoom = false;
        eventRoom = false;
        peacefulRoom = false;
        currentEventId = null;
        currentRewardPoolId = null;
        currentWave = 0;
        queuedWaves.clear();
        playerVotes.clear();
        proceedPlayers.clear();
        pendingRewards.clear();
        resolvedRewardPlayers.clear();
        shopPurchasedPlayers.clear();
    }

    private List<String> getPreferredTypesForRound(int round) {
        return switch (round) {
            case 1 -> List.of("NORMAL", "REST");
            case 2 -> List.of("NORMAL", "EVENT", "SHOP");
            case 3 -> List.of("ELITE", "NORMAL", "REST");
            case 4 -> List.of("ELITE", "EVENT", "SHOP", "NORMAL");
            case 5 -> List.of("BOSS");
            default -> List.of("NORMAL");
        };
    }

    private String formatStageType(String stageType) {
        return "[" + stageType.toUpperCase(Locale.ROOT) + "]";
    }
}
