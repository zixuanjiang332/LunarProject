package jdd.lunarProject.Game;

import io.lumine.mythic.api.exceptions.InvalidMobTypeException;
import io.lumine.mythic.bukkit.MythicBukkit;
import jdd.lunarProject.Build.RewardManager;
import jdd.lunarProject.Build.RewardOption;
import jdd.lunarProject.Build.RewardPoolDefinition;
import jdd.lunarProject.Build.ServiceDefinition;
import jdd.lunarProject.Config.MessageManager;
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

    public List<ShopOffer> getActiveShopOffers(UUID playerId) {
        Set<String> purchased = purchasedShopOffers.getOrDefault(playerId, Set.of());
        List<ShopOffer> offers = new ArrayList<>();
        offers.add(new ShopOffer(
                SHOP_OFFER_RELIC,
                "测试饰品样品",
                "消耗 120 碎金，购买一个演示饰品。",
                120,
                purchased.contains(SHOP_OFFER_RELIC)
        ));
        return offers;
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
        game.broadcast(MessageManager.text("round.major-select-title", "§6===== 选择大关卡 ====="));
        game.broadcast(MessageManager.text("round.major-select-prompt", "§e请选择本次要进入的大关卡。测试阶段当前仅开放 1 条线路。"));
        for (Map.Entry<Integer, String> entry : currentMajorChoices.entrySet()) {
            game.broadcast(MessageManager.format(
                    "round.major-select-option",
                    "§b[%index%] §f%name%",
                    "index", entry.getKey(),
                    "name", entry.getValue()
            ));
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
            game.broadcast(MessageManager.text("round.route-complete", "?a?????????"));
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
            game.broadcast(MessageManager.format(
                    "round.round-missing-stage",
                    "?c? %round% ????????????????",
                    "round", currentRound
            ));
            game.setGameState(GameState.ENDED);
            return;
        }
        votingPhase = true;
        voteCountdown = LunarProject.getInstance().getVoteTimeoutSeconds();
        playerVotes.clear();
        game.broadcast(MessageManager.format(
                "round.node-round-title",
                "?6===== ? %round% ?? =====",
                "round", currentRound
        ));
        game.broadcast(MessageManager.text("round.node-vote-prompt", "?e??? /game vote <??> ????????"));
        for (String line : getVoteOptionDisplay()) {
            game.broadcast(line);
        }
        startVoteCountdown();
        LunarProject.getInstance().getGuiManager().refreshGame(game);
    }

    public void castVote(Player player, int choiceIndex) {
        if (!votingPhase) {
            player.sendMessage(MessageManager.text("round.vote-phase-invalid", "?c?????????"));
            return;
        }
        if (!game.isActivePlayer(player)) {
            player.sendMessage(MessageManager.text("round.vote-only-alive", "?c???????????"));
            return;
        }
        if (majorSelectionPhase) {
            if (!currentMajorChoices.containsKey(choiceIndex)) {
                player.sendMessage(MessageManager.text("round.major-choice-invalid", "?c??????????"));
                return;
            }
            playerVotes.put(player.getUniqueId(), choiceIndex);
            game.broadcast(MessageManager.format(
                    "round.major-picked-broadcast",
                    "?b%player% ?????? %index%?",
                    "player", player.getName(),
                    "index", choiceIndex
            ));
            LunarProject.getInstance().getGuiManager().refreshGame(game);
            checkVote();
            return;
        }
        if (!currentNodeChoices.containsKey(choiceIndex)) {
            player.sendMessage(MessageManager.text("round.node-choice-invalid", "?c???????"));
            return;
        }
        playerVotes.put(player.getUniqueId(), choiceIndex);
        game.broadcast(MessageManager.format(
                "round.node-picked-broadcast",
                "?b%player% ??????? %index%?",
                "player", player.getName(),
                "index", choiceIndex
        ));
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
            player.sendMessage(MessageManager.text("round.proceed-phase-invalid", "?c???????????"));
            return;
        }
        if (!game.isActivePlayer(player)) {
            player.sendMessage(MessageManager.text("round.proceed-only-alive", "?c???????????"));
            return;
        }
        if (eventRoom && !eventResolved) {
            player.sendMessage(MessageManager.text("round.proceed-event-first", "?c???? /game event choose <1|2> ?????"));
            return;
        }
        if (rewardPhase) {
            player.sendMessage(MessageManager.text("round.proceed-reward-first", "?c???? /game reward choose <1|2|3> ?????"));
            return;
        }
        proceedPlayers.add(player.getUniqueId());
        game.broadcast(MessageManager.format(
                "round.proceed-ready",
                "?a%player% ??????",
                "player", player.getName()
        ));
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
            player.sendMessage(MessageManager.text("round.shop-room-invalid", "§c当前不在商店房间中。"));
            return;
        }
        if (!game.isActivePlayer(player)) {
            player.sendMessage(MessageManager.text("round.shop-only-alive", "§c只有存活玩家可以使用商店。"));
            return;
        }
        if (shopPurchasedPlayers.contains(player.getUniqueId())) {
            player.sendMessage(MessageManager.text("round.shop-service-claimed", "§c你已经领取过当前房间的商店服务。"));
            return;
        }

        String normalizedId = normalizeShopOfferId(offerId);
        RewardPoolDefinition shopPool = RewardManager.getPool("shop_room");
        if (shopPool == null || !shopPool.services().contains(normalizedId)) {
            player.sendMessage(MessageManager.format("round.shop-item-missing", "§c未找到对应的商店商品：§f%offer%", "offer", offerId));
            return;
        }

        ServiceDefinition service = RewardManager.getService(normalizedId);
        if (service == null) {
            player.sendMessage(MessageManager.text("round.shop-service-invalid", "§c该商店服务未正确配置。"));
            return;
        }

        boolean applied = RewardManager.applyReward(
                game,
                player,
                new RewardOption(RewardOption.RewardType.SERVICE, service.id(), service.name(), service.rarity(), service.description(), "")
        );
        if (!applied) {
            player.sendMessage(MessageManager.text("round.shop-service-failed", "§c商店服务发放失败。"));
            return;
        }

        shopPurchasedPlayers.add(player.getUniqueId());
        player.sendMessage(MessageManager.format("round.shop-service-purchased", "§a已购买商店服务：§f%name%", "name", service.name()));
        LunarProject.getInstance().getGuiManager().refreshGame(game);
    }

    public void purchaseShopOffer(Player player, String offerId) {
        if (!shopRoom || !peacefulRoom) {
            player.sendMessage(MessageManager.text("round.shop-room-invalid", "§c当前不在商店房间中。"));
            return;
        }
        if (!game.isActivePlayer(player)) {
            player.sendMessage(MessageManager.text("round.shop-only-alive", "§c只有存活玩家可以使用商店。"));
            return;
        }
        ShopOffer selectedOffer = getActiveShopOffers(player.getUniqueId()).stream()
                .filter(offer -> offer.id().equalsIgnoreCase(normalizeShopOfferId(offerId)))
                .findFirst()
                .orElse(null);
        if (selectedOffer == null) {
            player.sendMessage(MessageManager.format("round.shop-item-missing", "§c未找到对应的商店商品：§f%offer%", "offer", offerId));
            return;
        }
        if (selectedOffer.purchased()) {
            player.sendMessage(MessageManager.text("round.shop-offer-owned", "§e这个商品在当前商店已经购买过了。"));
            return;
        }
        if (selectedOffer.cost() > 0 && !game.spendCoins(player.getUniqueId(), selectedOffer.cost())) {
            player.sendMessage(MessageManager.format(
                    "round.shop-no-coins",
                    "§c你的碎金不足，需要 §f%cost% §c枚。当前持有 §f%current%",
                    "cost", selectedOffer.cost(),
                    "current", game.getCoins(player.getUniqueId())
            ));
            return;
        }
        if (!applyShopOffer(player, selectedOffer)) {
            if (selectedOffer.cost() > 0) {
                game.addCoins(player.getUniqueId(), selectedOffer.cost());
            }
            player.sendMessage(MessageManager.text("round.shop-purchase-failed", "§c购买失败，请检查商品配置。"));
            return;
        }
        purchasedShopOffers.computeIfAbsent(player.getUniqueId(), ignored -> new HashSet<>()).add(selectedOffer.id());
        player.sendMessage(MessageManager.format(
                "round.shop-purchased",
                "§a已购买 §f%name%§7，剩余碎金 §f%current%",
                "name", selectedOffer.name(),
                "current", game.getCoins(player.getUniqueId())
        ));
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
            player.sendMessage(MessageManager.text("round.event-room-invalid", "§c当前不在事件房间中。"));
            return;
        }
        if (eventResolved) {
            player.sendMessage(MessageManager.text("round.event-already-resolved", "§e这个事件已经处理完成。"));
            return;
        }
        if (!game.isActivePlayer(player)) {
            player.sendMessage(MessageManager.text("round.event-only-alive", "§c只有存活玩家可以处理事件。"));
            return;
        }
        int choice;
        try {
            choice = Integer.parseInt(choiceId);
        } catch (NumberFormatException exception) {
            player.sendMessage(MessageManager.text("round.event-choice-invalid", "§c请输入 1 或 2。"));
            return;
        }
        EventOutcome outcome = RandomEventManager.handleEventChoice(game, player, currentEventId, choice);
        if (outcome == EventOutcome.INVALID) {
            player.sendMessage(MessageManager.text("round.event-choice-invalid", "§c请输入 1 或 2。"));
            return;
        }
        if (outcome == EventOutcome.SAFE_RESOLVED) {
            eventResolved = true;
            startRewardPhase("event_room", false);
            game.broadcast(MessageManager.text("round.event-resolved", "§e事件已处理完成，请选择奖励后再推进。"));
        }
        LunarProject.getInstance().getGuiManager().refreshGame(game);
    }

    public void handleRewardChoice(Player player, int choiceIndex) {
        if (!rewardPhase) {
            player.sendMessage(MessageManager.text("round.reward-none", "§c当前没有待选择的奖励。"));
            return;
        }
        if (!game.isActivePlayer(player)) {
            player.sendMessage(MessageManager.text("round.reward-only-alive", "§c只有存活玩家可以领取奖励。"));
            return;
        }
        if (resolvedRewardPlayers.contains(player.getUniqueId())) {
            player.sendMessage(MessageManager.text("round.reward-already-locked", "§e你已经锁定了本次奖励。"));
            return;
        }
        List<RewardOption> rewardOptions = pendingRewards.get(player.getUniqueId());
        if (rewardOptions == null || rewardOptions.isEmpty()) {
            resolvedRewardPlayers.add(player.getUniqueId());
            checkRewardResolution();
            return;
        }
        if (choiceIndex < 1 || choiceIndex > rewardOptions.size()) {
            player.sendMessage(MessageManager.text("round.reward-invalid-choice", "§c请输入有效的奖励编号。"));
            return;
        }
        RewardOption rewardOption = rewardOptions.get(choiceIndex - 1);
        if (!RewardManager.applyReward(game, player, rewardOption)) {
            player.sendMessage(MessageManager.text("round.reward-apply-failed", "§c奖励发放失败。"));
            return;
        }
        resolvedRewardPlayers.add(player.getUniqueId());
        player.sendMessage(MessageManager.format("round.reward-picked", "§a已选择奖励：§f%name%", "name", rewardOption.name()));
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

        game.broadcast(MessageManager.text("round.reward-locked", "§a所有奖励均已锁定，准备好后可使用 /game proceed 推进。"));
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
            game.broadcast(MessageManager.text("round.ambush-failed", "§c未能触发伏击战斗。"));
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
        game.broadcast(MessageManager.text("round.ambush-start", "§c事件局势失控，伏击战开始！"));
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
                lines.add(MessageManager.format(
                        "round.major-select-option",
                        "?b[%index%] ?f%name%",
                        "index", entry.getKey(),
                        "name", entry.getValue()
                ));
            }
            return lines;
        }
        for (Map.Entry<Integer, StageTemplate> entry : currentNodeChoices.entrySet()) {
            StageTemplate template = entry.getValue();
            lines.add(MessageManager.format(
                    "round.node-vote-option",
                    "?b[%index%] ?f?%stage_type%??7 - %stage_id%",
                    "index", entry.getKey(),
                    "stage_type", formatStageType(template.stageType()),
                    "stage_id", formatStageCode(template.stageId())
            ));
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
            lines.add("§b[" + (index + 1) + "] §f" + option.name() + " §7(" + typeLabel + " / " + formatRarity(option.rarity()) + ")");
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
            game.broadcast(MessageManager.format(
                    "round.major-select-picked",
                    "?a???? %index% ??????????????",
                    "index", chosenTier
            ));
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
            game.broadcast(MessageManager.text("round.next-stage-undetermined", "?c??????????"));
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
            game.broadcast(MessageManager.format("round.stage-load-failed", "§c地图加载失败：%map%。", "map", template.mapName()));
            game.setGameState(GameState.ENDED);
            return;
        }
        teleportPlayersToCurrentStage();
        game.broadcast(MessageManager.format(
                "round.stage-enter",
                "§6已进入 %stage_type%§7（节点编号：%stage_id%）",
                "stage_type", formatStageType(template.stageType()),
                "stage_id", formatStageCode(template.stageId())
        ));
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
        game.broadcast(MessageManager.text("round.rest-room-enter", "§a队伍获得了短暂喘息。请先选择房间奖励，再决定是否推进。"));
        startRewardPhase("rest_room", false);
        startReadyCountdown();
        LunarProject.getInstance().getGuiManager().refreshGame(game);
    }

    private void enterShopRoom() {
        peacefulRoom = true;
        shopRoom = true;
        game.broadcast(MessageManager.text("round.shop-intro", "?6??????????????????????????????????"));
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
            game.broadcast(MessageManager.text("round.combat-map-invalid", "§c当前战斗地图已不可用。"));
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
        game.broadcast(MessageManager.format("round.wave-begin", "§c第 %wave% 波敌人出现。", "wave", waveData.waveIndex()));
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
            game.broadcast(MessageManager.text("round.normal-clear", "?a?????????????????"));
            returnToLobbyAndAdvance();
            return;
        }
        game.broadcast(MessageManager.text("round.reward-open", "?a????????????"));
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
                player.sendMessage(MessageManager.text("round.reward-empty-player", "§7你在这个房间没有可选的独特奖励。"));
                continue;
            }
            pendingRewards.put(player.getUniqueId(), rewardOptions);
            player.sendMessage(MessageManager.text("round.reward-select-title", "§6===== 奖励选择 ====="));
            for (String line : getRewardOptionDisplay(player)) {
                player.sendMessage(line);
            }
            player.sendMessage(MessageManager.text("round.reward-select-prompt", "§e使用 /game reward choose <1|2|3> 选择奖励。"));
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
            game.broadcast(MessageManager.text("round.final-boss-clear", "§6最终首领已被击败，本轮挑战完成。"));
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
            game.broadcast(MessageManager.text("round.boss-prep-missing", "?c??????????????????"));
            game.setGameState(GameState.ENDED);
            return;
        }
        game.broadcast(MessageManager.text("round.boss-prep-enter", "?6???????????????????????"));
        startStage(shopTemplate);
    }

    private void startBossStage() {
        StageTemplate bossTemplate = StageManager.getRandomStage(currentTier, MAX_ROUNDS, "BOSS");
        if (bossTemplate == null) {
            bossTemplate = StageManager.getAnyStageByType(currentTier, "BOSS");
        }
        if (bossTemplate == null) {
            game.broadcast(MessageManager.text("round.boss-stage-missing", "?c???????????????"));
            game.setGameState(GameState.ENDED);
            return;
        }
        game.broadcast(MessageManager.text("round.boss-stage-enter", "?4??????????????"));
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
                    int fallbackChoice = majorSelectionPhase
                            ? currentMajorChoices.keySet().stream().min(Integer::compareTo).orElse(1)
                            : currentNodeChoices.keySet().stream().min(Integer::compareTo).orElse(1);
                    for (Player player : game.getActiveOnlinePlayers()) {
                        playerVotes.putIfAbsent(player.getUniqueId(), fallbackChoice);
                    }
                    game.broadcast(MessageManager.format(
                            majorSelectionPhase ? "round.vote-timeout-major" : "round.vote-timeout",
                            majorSelectionPhase
                                    ? "?e??????????????????? %target%?"
                                    : "?e???????????????? %target%?",
                            "target", majorSelectionPhase ? fallbackChoice : ("?? " + fallbackChoice)
                    ));
                    resolveVotes();
                    return;
                }
                if (voteCountdown == LunarProject.getInstance().getVoteTimeoutSeconds() || voteCountdown <= 5 || voteCountdown % 10 == 0) {
                    game.broadcast(MessageManager.format(
                            "round.vote-countdown",
                            "?7???? ?f%seconds%?7 ??",
                            "seconds", voteCountdown
                    ));
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
                    game.broadcast(MessageManager.format(
                            "round.ready-countdown",
                            "?7???? ?f%seconds%?7 ???????",
                            "seconds", readyCountdown
                    ));
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
                player.sendMessage(MessageManager.format("round.reward-timeout-picked", "§e奖励选择超时，已自动选择：§f%name%", "name", fallbackReward.name()));
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
        if (isMinimalShopCatalogEnabled()) {
            if (offer == null) {
                return false;
            }
            if (!SHOP_OFFER_RELIC.equals(offer.id())) {
                return false;
            }
            return RewardManager.applyReward(
                    game,
                    player,
                    new RewardOption(
                            RewardOption.RewardType.RELIC,
                            SHOP_OFFER_RELIC,
                            "测试饰品样品",
                            "COMMON",
                            "用于测试商店购买饰品流程。",
                            "Demo_Relic_Test"
                    )
            );
        }
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
                player.sendMessage(MessageManager.text("round.synth-placeholder", "§e饰品合成系统暂未实装，当前只保留了接口按钮。"));
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
            player.sendMessage(MessageManager.format(
                    "round.combat-coins",
                    "§6获得碎金 §f+%coins%§6，当前持有 §f%current%",
                    "coins", coinAmount,
                    "current", game.getCoins(player.getUniqueId())
            ));
        }
    }

    private void broadcastShopServices() {
        game.broadcast(MessageManager.text("round.shop-list-title", "?e???????"));
        for (ShopOffer offer : getActiveShopOffers(UUID.randomUUID())) {
            String priceText = offer.cost() > 0 ? offer.cost() + " ??" : "??";
            game.broadcast(MessageManager.format(
                    "round.shop-list-entry",
                    "?b- ?f%name%?7 | ???%price%?8 | ?????%alias%",
                    "name", offer.name(),
                    "price", priceText,
                    "alias", getShopOfferAlias(offer.id())
            ));
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

    public String getShopOfferAlias(String offerId) {
        return switch (offerId.toLowerCase(Locale.ROOT)) {
            case SHOP_OFFER_HEAL -> "疗养";
            case SHOP_OFFER_SANITY -> "理智";
            case SHOP_OFFER_RELIC -> "饰品";
            case SHOP_OFFER_SYNTH -> "合成";
            default -> offerId;
        };
    }

    private boolean isMinimalShopCatalogEnabled() {
        return true;
    }

    private String normalizeShopOfferId(String offerId) {
        return switch (offerId.toLowerCase(Locale.ROOT)) {
            case "heal", "疗养", "治疗" -> SHOP_OFFER_HEAL;
            case "sanity", "理智", "校准" -> SHOP_OFFER_SANITY;
            case "relic", "饰品" -> SHOP_OFFER_RELIC;
            case "synth", "combine", "craft", "合成" -> SHOP_OFFER_SYNTH;
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
            case "BOSS" -> "首领战";
            default -> "未知阶段";
        };
    }

    private String formatRarity(String rarity) {
        return switch (rarity.toUpperCase(Locale.ROOT)) {
            case "COMMON" -> "普通";
            case "UNCOMMON" -> "优秀";
            case "RARE" -> "稀有";
            default -> rarity;
        };
    }

    private String formatStageCode(String stageId) {
        if (stageId == null || stageId.isBlank()) {
            return "未命名节点";
        }
        return stageId.replace('_', '-');
    }
}


