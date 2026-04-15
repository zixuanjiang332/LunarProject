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
    private static final int TEST_TIER_ID = 1;
    private static final int NORMAL_CLEAR_COINS = 80;
    private static final int ELITE_CLEAR_COINS = 120;
    private static final String SHOP_OFFER_HEAL = "field_patch";
    private static final String SHOP_OFFER_SANITY = "clear_mind";
    private static final String SHOP_OFFER_RELIC = "demo_terminal_chip";
    private static final String SHOP_OFFER_SYNTH = "synthesis_placeholder";

    private final Game game;
    private final Map<UUID, Entity> activeMobs = new HashMap<>();
    private final Map<Integer, StageTemplate> currentNodeChoices = new LinkedHashMap<>();
    private final Map<Integer, String> currentMajorChoices = new LinkedHashMap<>();
    private final Map<UUID, Integer> playerVotes = new HashMap<>();
    private final Set<UUID> proceedPlayers = new LinkedHashSet<>();
    private final Map<UUID, List<RewardOption>> pendingRewards = new HashMap<>();
    private final Set<UUID> resolvedRewardPlayers = new LinkedHashSet<>();
    private final Set<UUID> shopPurchasedPlayers = new HashSet<>();
    private final Map<UUID, Set<String>> purchasedShopOffers = new HashMap<>();
    private final List<WaveData> queuedWaves = new ArrayList<>();

    private GameLevel currentStage;
    private StageTemplate currentTemplate;
    private String currentEventId;
    private String currentRewardPoolId;

    private BukkitTask voteCountdownTask;
    private BukkitTask readyCountdownTask;
    private BukkitTask combatWatchdogTask;
    private BukkitTask nextWaveTask;

    private int currentRound;
    private int currentTier = TEST_TIER_ID;
    private int currentWave;
    private int readyCountdown;
    private int voteCountdown;

    private boolean majorSelectionPhase;
    private boolean bossPrepShopPending;
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

    public record ShopOffer(String id, String name, String description, int cost, boolean purchased) {
    }

    public void beginRunSelection() {
        cancelVoteCountdown();
        cancelReadyCountdown();
        cancelCombatWatchdog();
        clearTransientRoomState();
        currentRound = 0;
        currentTier = TEST_TIER_ID;
        majorSelectionPhase = true;
        votingPhase = true;
        bossPrepShopPending = false;
        currentMajorChoices.clear();
        currentMajorChoices.put(1, "第一大关卡：测试线路");
        voteCountdown = LunarProject.getInstance().getVoteTimeoutSeconds();
        playerVotes.clear();
        game.broadcast("§6===== 选择大关卡 =====");
        game.broadcast("§e请选择本次要进入的大关卡。测试阶段当前仅开放 1 条线路。");
        for (Map.Entry<Integer, String> entry : currentMajorChoices.entrySet()) {
            game.broadcast("§b[" + entry.getKey() + "] §f" + entry.getValue());
        }
        startVoteCountdown();
        LunarProject.getInstance().getGuiManager().refreshGame(game);
    }

    public void generateNextNodeChoices() {
        cancelVoteCountdown();
        cancelReadyCountdown();
        cancelCombatWatchdog();
        clearTransientRoomState();
        if (currentRound <= 0) {
            currentRound = 1;
        }
        if (currentRound >= MAX_ROUNDS && bossPrepShopPending) {
            startBossPrepShop();
            return;
        }
        if (currentRound > MAX_ROUNDS) {
            game.broadcast("§a当前路线已经完成。");
            game.setGameState(GameState.ENDED);
            return;
        }
        currentNodeChoices.clear();
        Set<String> seenStageIds = new LinkedHashSet<>();
        List<String> preferredTypes = getPreferredTypesForRound(currentRound);
        int targetChoices = currentRound >= MAX_ROUNDS ? 1 : 2;
        for (String stageType : preferredTypes) {
            StageTemplate template = StageManager.getRandomStage(currentTier, currentRound, stageType);
            if (template == null || !seenStageIds.add(template.stageId())) {
                continue;
            }
            currentNodeChoices.put(currentNodeChoices.size() + 1, template);
            if (currentNodeChoices.size() >= targetChoices) {
                break;
            }
        }
        if (currentNodeChoices.isEmpty()) {
            StageTemplate fallback = StageManager.getAnyStageFallback(currentTier, currentRound);
            if (fallback != null) {
                currentNodeChoices.put(1, fallback);
            }
        }
        if (currentNodeChoices.isEmpty()) {
            game.broadcast("§c第 " + currentRound + " 回合未找到可用节点，流程已终止。");
            game.setGameState(GameState.ENDED);
            return;
        }
        votingPhase = true;
        voteCountdown = LunarProject.getInstance().getVoteTimeoutSeconds();
        playerVotes.clear();
        game.broadcast("§6===== 第 " + currentRound + " 回合 =====");
        game.broadcast("§e请使用 /game vote <编号> 选择下一小关卡。");
        for (String line : getVoteOptionDisplay()) {
            game.broadcast(line);
        }
        startVoteCountdown();
        LunarProject.getInstance().getGuiManager().refreshGame(game);
    }

    public void castVote(Player player, int choiceIndex) {
        if (!votingPhase) {
            player.sendMessage("§c当前不在投票阶段。");
            return;
        }
        if (!game.isActivePlayer(player)) {
            player.sendMessage("§c只有存活玩家可以投票。");
            return;
        }
        if (majorSelectionPhase) {
            if (!currentMajorChoices.containsKey(choiceIndex)) {
                player.sendMessage("§c该大关卡选项不存在。");
                return;
            }
            playerVotes.put(player.getUniqueId(), choiceIndex);
            game.broadcast("§b" + player.getName() + " 选择了大关卡 " + choiceIndex + "。");
            LunarProject.getInstance().getGuiManager().refreshGame(game);
            checkVote();
            return;
        }
        if (!currentNodeChoices.containsKey(choiceIndex)) {
            player.sendMessage("§c该节点不存在。");
            return;
        }
        playerVotes.put(player.getUniqueId(), choiceIndex);
        game.broadcast("§b" + player.getName() + " 投票选择了节点 " + choiceIndex + "。");
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
            player.sendMessage("§c当前没有可推进的阶段。");
            return;
        }
        if (!game.isActivePlayer(player)) {
            player.sendMessage("§c只有存活玩家可以推进。");
            return;
        }
        if (eventRoom && !eventResolved) {
            player.sendMessage("§c请先使用 /game event choose <1|2> 处理事件。");
            return;
        }
        if (rewardPhase) {
            player.sendMessage("§c请先使用 /game reward choose <1|2|3> 选择奖励。");
            return;
        }
        proceedPlayers.add(player.getUniqueId());
        game.broadcast("§a" + player.getName() + " 已准备推进。");
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
            player.sendMessage("§c当前不在商店房间中。");
            return;
        }
        if (!game.isActivePlayer(player)) {
            player.sendMessage("§c只有存活玩家可以使用商店。");
            return;
        }
        if (shopPurchasedPlayers.contains(player.getUniqueId())) {
            player.sendMessage("§c你已经领取过当前房间的商店服务。");
            return;
        }

        String normalizedId = normalizeShopOfferId(offerId);
        RewardPoolDefinition shopPool = RewardManager.getPool("shop_room");
        if (shopPool == null || !shopPool.services().contains(normalizedId)) {
            player.sendMessage("§c未知商店商品: " + offerId);
            return;
        }

        ServiceDefinition service = RewardManager.getService(normalizedId);
        if (service == null) {
            player.sendMessage("§c该商店服务未正确配置。");
            return;
        }

        boolean applied = RewardManager.applyReward(
                game,
                player,
                new RewardOption(RewardOption.RewardType.SERVICE, service.id(), service.name(), service.rarity(), service.description(), "")
        );
        if (!applied) {
            player.sendMessage("§c商店服务发放失败。");
            return;
        }

        shopPurchasedPlayers.add(player.getUniqueId());
        player.sendMessage("§a已购买商店服务: §f" + service.name());
        LunarProject.getInstance().getGuiManager().refreshGame(game);
    }

    public void purchaseShopOffer(Player player, String offerId) {
        if (!shopRoom || !peacefulRoom) {
            player.sendMessage("§c当前不在商店房间中。");
            return;
        }
        if (!game.isActivePlayer(player)) {
            player.sendMessage("§c只有存活玩家可以使用商店。");
            return;
        }
        ShopOffer selectedOffer = getShopOffers(player.getUniqueId()).stream()
                .filter(offer -> offer.id().equalsIgnoreCase(normalizeShopOfferId(offerId)))
                .findFirst()
                .orElse(null);
        if (selectedOffer == null) {
            player.sendMessage("§c未知商店商品: " + offerId);
            return;
        }
        if (selectedOffer.purchased()) {
            player.sendMessage("§e这个商品在当前商店已经购买过了。");
            return;
        }
        if (selectedOffer.cost() > 0 && !game.spendCoins(player.getUniqueId(), selectedOffer.cost())) {
            player.sendMessage("§c你的碎金不足，需要 §f" + selectedOffer.cost() + " §c枚。当前持有 §f" + game.getCoins(player.getUniqueId()));
            return;
        }
        if (!applyShopOffer(player, selectedOffer)) {
            if (selectedOffer.cost() > 0) {
                game.addCoins(player.getUniqueId(), selectedOffer.cost());
            }
            player.sendMessage("§c购买失败，请检查商品配置。");
            return;
        }
        purchasedShopOffers.computeIfAbsent(player.getUniqueId(), ignored -> new HashSet<>()).add(selectedOffer.id());
        player.sendMessage("§a已购买 §f" + selectedOffer.name() + "§7，剩余碎金 §f" + game.getCoins(player.getUniqueId()));
        LunarProject.getInstance().getGuiManager().refreshGame(game);
    }

    public List<ShopOffer> getShopOffers(UUID playerId) {
        Set<String> purchased = purchasedShopOffers.getOrDefault(playerId, Set.of());
        List<ShopOffer> offers = new ArrayList<>();
        offers.add(new ShopOffer(SHOP_OFFER_HEAL, "战地疗养", "消耗 60 碎金，回复 35% 生命与混乱抗性。", 60, purchased.contains(SHOP_OFFER_HEAL)));
        offers.add(new ShopOffer(SHOP_OFFER_SANITY, "理智校准", "消耗 40 碎金，恢复 15 点理智。", 40, purchased.contains(SHOP_OFFER_SANITY)));
        offers.add(new ShopOffer(SHOP_OFFER_RELIC, "测试饰品样品", "消耗 120 碎金，购买一个演示饰品。", 120, purchased.contains(SHOP_OFFER_RELIC)));
        offers.add(new ShopOffer(SHOP_OFFER_SYNTH, "饰品合成接口", "测试接口，暂未实装正式合成表。", 0, purchased.contains(SHOP_OFFER_SYNTH)));
        return offers;
    }

    public void handleEventChoice(Player player, String choiceId) {
        if (!eventRoom || !peacefulRoom) {
            player.sendMessage("§c当前不在事件房间中。");
            return;
        }
        if (eventResolved) {
            player.sendMessage("§e这个事件已经处理完成。");
            return;
        }
        if (!game.isActivePlayer(player)) {
            player.sendMessage("§c只有存活玩家可以处理事件。");
            return;
        }
        int choice;
        try {
            choice = Integer.parseInt(choiceId);
        } catch (NumberFormatException exception) {
            player.sendMessage("§c请输入 1 或 2。");
            return;
        }
        EventOutcome outcome = RandomEventManager.handleEventChoice(game, player, currentEventId, choice);
        if (outcome == EventOutcome.INVALID) {
            player.sendMessage("§c请输入 1 或 2。");
            return;
        }
        if (outcome == EventOutcome.SAFE_RESOLVED) {
            eventResolved = true;
            startRewardPhase("event_room", false);
            game.broadcast("§e事件已处理完成，请选择奖励后再推进。");
        }
        LunarProject.getInstance().getGuiManager().refreshGame(game);
    }

    public void handleRewardChoice(Player player, int choiceIndex) {
        if (!rewardPhase) {
            player.sendMessage("§c当前没有待选择的奖励。");
            return;
        }
        if (!game.isActivePlayer(player)) {
            player.sendMessage("§c只有存活玩家可以领取奖励。");
            return;
        }
        if (resolvedRewardPlayers.contains(player.getUniqueId())) {
            player.sendMessage("§e你已经锁定了本次奖励。");
            return;
        }
        List<RewardOption> rewardOptions = pendingRewards.get(player.getUniqueId());
        if (rewardOptions == null || rewardOptions.isEmpty()) {
            resolvedRewardPlayers.add(player.getUniqueId());
            checkRewardResolution();
            return;
        }
        if (choiceIndex < 1 || choiceIndex > rewardOptions.size()) {
            player.sendMessage("§c请输入有效的奖励编号。");
            return;
        }
        RewardOption rewardOption = rewardOptions.get(choiceIndex - 1);
        if (!RewardManager.applyReward(game, player, rewardOption)) {
            player.sendMessage("§c奖励发放失败。");
            return;
        }
        resolvedRewardPlayers.add(player.getUniqueId());
        player.sendMessage("§a已选择奖励: §f" + rewardOption.name());
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

        game.broadcast("§a所有奖励均已锁定，准备好后可使用 /game proceed 推进。");
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
            game.broadcast("§c未能触发伏击战斗。");
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
        game.broadcast("§c事件局势失控，伏击战开始！");
        spawnNextWave();
        LunarProject.getInstance().getGuiManager().refreshGame(game);
    }

    public void cleanUp() {
        cancelVoteCountdown();
        cancelReadyCountdown();
        cancelCombatWatchdog();
        cancelNextWaveTask();
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

    public boolean isMajorSelectionPhase() {
        return majorSelectionPhase;
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

    public Map<Integer, String> getMajorChoices() {
        return new LinkedHashMap<>(currentMajorChoices);
    }

    public boolean hasPurchasedShopService(UUID playerId) {
        return !purchasedShopOffers.getOrDefault(playerId, Set.of()).isEmpty();
    }

    public boolean hasPurchasedShopOffer(UUID playerId, String offerId) {
        return purchasedShopOffers.getOrDefault(playerId, Set.of()).contains(offerId);
    }

    public String getVotePhaseKey() {
        if (!votingPhase) {
            return "";
        }
        return majorSelectionPhase ? "major_select" : "vote:" + currentRound;
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
        if (majorSelectionPhase) {
            return "MAJOR_SELECT";
        }
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
        if (majorSelectionPhase) {
            return "请选择本次要进入的大关卡。";
        }
        if (votingPhase) {
            return "请为下一小关卡投票。";
        }
        if (rewardPhase) {
            return "请选择当前房间奖励。";
        }
        if (eventRoom && !eventResolved) {
            return "请处理当前事件选项。";
        }
        if (peacefulRoom) {
            if (shopRoom) {
                return "可在商店中花费碎金购买恢复、饰品与合成接口。";
            }
            return "队伍准备完成后即可前进。";
        }
        if (currentTemplate != null) {
            return "击败当前房间内的所有敌人。";
        }
        return "等待下一阶段开始。";
    }

    public List<String> getVoteOptionDisplay() {
        List<String> lines = new ArrayList<>();
        if (majorSelectionPhase) {
            for (Map.Entry<Integer, String> entry : currentMajorChoices.entrySet()) {
                lines.add("§b[" + entry.getKey() + "] §f" + entry.getValue());
            }
            return lines;
        }
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
            lines.add("§7当前没有待选奖励。");
            return lines;
        }
        for (int index = 0; index < rewardOptions.size(); index++) {
            RewardOption option = rewardOptions.get(index);
            String typeLabel = option.rewardType() == RewardOption.RewardType.RELIC ? "饰品" : "服务";
            lines.add("§b[" + (index + 1) + "] §f" + option.name() + " §7(" + typeLabel + " / " + option.rarity() + ")");
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
        if (majorSelectionPhase) {
            int chosenTier = currentMajorChoices.keySet().stream()
                    .min(Comparator.<Integer>comparingInt(choice -> -totals.getOrDefault(choice, 0)).thenComparingInt(Integer::intValue))
                    .orElse(1);
            currentTier = TEST_TIER_ID;
            votingPhase = false;
            majorSelectionPhase = false;
            playerVotes.clear();
            currentMajorChoices.clear();
            currentRound = 1;
            bossPrepShopPending = false;
            game.broadcast("§a已选定第 " + chosenTier + " 大关卡，开始生成第一层路线。");
            new BukkitRunnable() {
                @Override
                public void run() {
                    generateNextNodeChoices();
                }
            }.runTaskLater(LunarProject.getInstance(), 20L);
            return;
        }
        int chosenNode = currentNodeChoices.keySet().stream()
                .min(Comparator.<Integer>comparingInt(choice -> -totals.getOrDefault(choice, 0)).thenComparingInt(Integer::intValue))
                .orElse(1);
        StageTemplate chosenTemplate = currentNodeChoices.get(chosenNode);
        votingPhase = false;
        playerVotes.clear();
        currentNodeChoices.clear();
        if (chosenTemplate == null) {
            chosenTemplate = StageManager.getAnyStageFallback(currentTier, currentRound);
        }
        if (chosenTemplate == null) {
            game.broadcast("§c未能确定下一小关卡。");
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
        purchasedShopOffers.clear();
        if (currentStage != null) {
            currentStage.unload();
        }
        currentStage = new GameLevel(template.mapName(), game.getGameId() + "_r" + currentRound);
        if (!currentStage.load()) {
            game.broadcast("§c地图加载失败：" + template.mapName() + "。");
            game.setGameState(GameState.ENDED);
            return;
        }
        teleportPlayersToCurrentStage();
        game.broadcast("§6进入 " + formatStageType(template.stageType()) + " §7(" + template.stageId() + ")");
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
        game.broadcast("§a队伍获得了短暂喘息。请先选择房间奖励，再决定是否推进。");
        startRewardPhase("rest_room", false);
        startReadyCountdown();
        LunarProject.getInstance().getGuiManager().refreshGame(game);
    }

    private void enterShopRoom() {
        peacefulRoom = true;
        shopRoom = true;
        game.broadcast("§6补给站已开放。你可以花费碎金恢复状态、购买测试饰品，或查看合成接口。");
        broadcastShopServices();
        startReadyCountdown();
        LunarProject.getInstance().getGuiManager().refreshGame(game);
        for (Player player : game.getActiveOnlinePlayers()) {
            LunarProject.getInstance().getGuiManager().openShopMenu(player);
        }
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
        nextWaveTask = null;
        pruneInactiveMobs();
        if (currentWave >= queuedWaves.size()) {
            onCombatStageCleared();
            return;
        }
        if (currentStage == null || currentStage.getWorld() == null) {
            game.broadcast("§c当前战斗地图已不可用。");
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
        game.broadcast("§c第 " + waveData.waveIndex() + " 波敌人出现。");
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
            game.broadcast("§e这一波没有成功刷出怪物，系统将自动跳过。");
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
            scheduleNextWave();
            return;
        }

        onCombatStageCleared();
    }

    private void onCombatStageCleared() {
        cancelCombatWatchdog();
        awardCombatCoins();
        for (Player player : game.getActiveOnlinePlayers()) {
            game.clearTemporaryCombatState(player);
        }
        for (Player player : game.getDeadOnlinePlayers()) {
            game.clearTemporaryCombatState(player);
        }
        if (currentTemplate != null && "NORMAL".equalsIgnoreCase(currentTemplate.stageType())) {
            game.broadcast("§a普通战已结束，开始选择下一小关卡。");
            returnToLobbyAndAdvance();
            return;
        }
        game.broadcast("§a房间已清空，请选择奖励。");
        startRewardPhase(RewardManager.getPoolForStageType(currentTemplate.stageType()), true);
        LunarProject.getInstance().getGuiManager().refreshGame(game);
    }

    private void startRewardPhase(String poolId, boolean autoAdvance) {
        finishRewardPhase();
        if (poolId == null || poolId.isBlank()) {
            if (autoAdvance) {
                returnToLobbyAndAdvance();
            }
            return;
        }
        rewardPhase = true;
        rewardAutoAdvance = autoAdvance;
        currentRewardPoolId = poolId;
        for (Player player : game.getActiveOnlinePlayers()) {
            List<RewardOption> rewardOptions = RewardManager.generateRewardOptions(game, player, poolId);
            if (rewardOptions.isEmpty()) {
                resolvedRewardPlayers.add(player.getUniqueId());
                player.sendMessage("§7你在这个房间没有可选的独特奖励。");
                continue;
            }
            pendingRewards.put(player.getUniqueId(), rewardOptions);
            player.sendMessage("§6===== 奖励选择 =====");
            for (String line : getRewardOptionDisplay(player)) {
                player.sendMessage(line);
            }
            player.sendMessage("§e使用 /game reward choose <1|2|3> 选择奖励。");
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
        cancelNextWaveTask();
        despawnTrackedMobs();
        boolean completedBossRoom = currentTemplate != null && "BOSS".equalsIgnoreCase(currentTemplate.stageType());
        for (Player player : game.getActiveOnlinePlayers()) {
            if (completedBossRoom) {
                game.restoreClassBaseline(player);
            } else {
                game.clearTemporaryCombatState(player);
            }
        }
        for (Player player : game.getDeadOnlinePlayers()) {
            game.clearTemporaryCombatState(player);
        }
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
        String clearedStageType = currentTemplate == null ? "" : currentTemplate.stageType();
        if (currentStage != null) {
            currentStage.unload();
            currentStage = null;
        }
        currentTemplate = null;
        clearTransientRoomState();
        if (bossVictory) {
            game.broadcast("§6最终 Boss 倒下了，本轮挑战完成。");
            game.setGameState(GameState.ENDED);
            return;
        }
        if (bossPrepShopPending && "SHOP".equalsIgnoreCase(clearedStageType)) {
            bossPrepShopPending = false;
            LunarProject.getInstance().getGuiManager().refreshGame(game);
            new BukkitRunnable() {
                @Override
                public void run() {
                    startBossStage();
                }
            }.runTaskLater(LunarProject.getInstance(), 20L);
            return;
        }
        if (!bossPrepShopPending && currentRound == MAX_ROUNDS - 1) {
            currentRound = MAX_ROUNDS;
            bossPrepShopPending = true;
            LunarProject.getInstance().getGuiManager().refreshGame(game);
            new BukkitRunnable() {
                @Override
                public void run() {
                    startBossPrepShop();
                }
            }.runTaskLater(LunarProject.getInstance(), 20L);
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

    private void startBossPrepShop() {
        StageTemplate shopTemplate = StageManager.getRandomStage(currentTier, MAX_ROUNDS, "SHOP");
        if (shopTemplate == null) {
            shopTemplate = StageManager.getAnyStageByType(currentTier, "SHOP");
        }
        if (shopTemplate == null) {
            game.broadcast("§c未找到决战前商店关卡，无法继续推进。");
            game.setGameState(GameState.ENDED);
            return;
        }
        game.broadcast("§6已抵达决战前补给站。请完成补给后前往 Boss。");
        startStage(shopTemplate);
    }

    private void startBossStage() {
        StageTemplate bossTemplate = StageManager.getRandomStage(currentTier, MAX_ROUNDS, "BOSS");
        if (bossTemplate == null) {
            bossTemplate = StageManager.getAnyStageByType(currentTier, "BOSS");
        }
        if (bossTemplate == null) {
            game.broadcast("§c未找到 Boss 关卡，无法继续推进。");
            game.setGameState(GameState.ENDED);
            return;
        }
        game.broadcast("§4补给完成，前方即为 Boss 关卡。");
        startStage(bossTemplate);
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
                    game.broadcast("§e投票超时，未选择的玩家将默认投给节点 " + fallbackChoice + "。");
                    resolveVotes();
                    return;
                }
                if (voteCountdown == LunarProject.getInstance().getVoteTimeoutSeconds() || voteCountdown <= 5 || voteCountdown % 10 == 0) {
                    game.broadcast("§7投票剩余 §f" + voteCountdown + "§7 秒。");
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
                    game.broadcast("§7房间将在 §f" + readyCountdown + "§7 秒后自动推进。");
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

    private void scheduleNextWave() {
        if (nextWaveTask != null) {
            return;
        }
        nextWaveTask = new BukkitRunnable() {
            @Override
            public void run() {
                nextWaveTask = null;
                spawnNextWave();
            }
        }.runTaskLater(LunarProject.getInstance(), 40L);
    }

    private void cancelNextWaveTask() {
        if (nextWaveTask != null) {
            nextWaveTask.cancel();
            nextWaveTask = null;
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
                player.sendMessage("§e奖励选择超时，已自动选择：§f" + fallbackReward.name());
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

    private boolean applyShopOffer(Player player, ShopOffer offer) {
        return switch (offer.id()) {
            case SHOP_OFFER_HEAL -> {
                if (player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) != null) {
                    double maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
                    double nextHealth = Math.min(maxHealth, player.getHealth() + (maxHealth * 0.35));
                    player.setHealth(Math.max(1.0, nextHealth));
                }
                double yellowMax = jdd.lunarProject.Tool.YellowBarUtil.getMax(player);
                double yellowCurrent = jdd.lunarProject.Tool.YellowBarUtil.getCurrent(player);
                double yellowNext = Math.min(yellowMax, yellowCurrent + (yellowMax * 0.35));
                jdd.lunarProject.Tool.YellowBarUtil.setValues(player, yellowNext, yellowMax);
                yield true;
            }
            case SHOP_OFFER_SANITY -> {
                game.changePlayerSanity(player, 15);
                yield true;
            }
            case SHOP_OFFER_RELIC -> RewardManager.applyReward(
                    game,
                    player,
                    new RewardOption(RewardOption.RewardType.RELIC, SHOP_OFFER_RELIC, "测试饰品样品", "COMMON", "用于测试商店购买饰品流程。", "Demo_Relic_Test")
            );
            case SHOP_OFFER_SYNTH -> {
                player.sendMessage("§e饰品合成系统暂未实装，当前只保留了接口按钮。");
                yield true;
            }
            default -> false;
        };
    }

    private void awardCombatCoins() {
        if (currentTemplate == null) {
            return;
        }
        int coinAmount = switch (currentTemplate.stageType().toUpperCase(Locale.ROOT)) {
            case "NORMAL" -> NORMAL_CLEAR_COINS;
            case "ELITE" -> ELITE_CLEAR_COINS;
            default -> 0;
        };
        if (coinAmount <= 0) {
            return;
        }
        for (Player player : game.getActiveOnlinePlayers()) {
            game.addCoins(player.getUniqueId(), coinAmount);
            player.sendMessage("§6获得碎金 §f+" + coinAmount + "§6，当前持有 §f" + game.getCoins(player.getUniqueId()));
        }
    }

    private void broadcastShopServices() {
        game.broadcast("§e当前商店商品：");
        for (ShopOffer offer : getShopOffers(UUID.randomUUID())) {
            game.broadcast("§b- " + offer.id() + " §7-> §f" + offer.name() + "§7 | 价格: " + offer.cost());
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
            case "heal" -> SHOP_OFFER_HEAL;
            case "sanity" -> "clear_mind";
            case "relic" -> SHOP_OFFER_RELIC;
            case "synth", "combine", "craft" -> SHOP_OFFER_SYNTH;
            default -> offerId;
        };
    }

    private void clearTransientRoomState() {
        cancelNextWaveTask();
        majorSelectionPhase = false;
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
        currentMajorChoices.clear();
        playerVotes.clear();
        proceedPlayers.clear();
        pendingRewards.clear();
        resolvedRewardPlayers.clear();
        shopPurchasedPlayers.clear();
        purchasedShopOffers.clear();
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
        return switch (stageType.toUpperCase(Locale.ROOT)) {
            case "NORMAL" -> "普通战";
            case "ELITE" -> "精英战";
            case "SHOP" -> "商店";
            case "REST" -> "休息";
            case "EVENT" -> "事件";
            case "BOSS" -> "Boss";
            default -> "未知阶段";
        };
    }
}


