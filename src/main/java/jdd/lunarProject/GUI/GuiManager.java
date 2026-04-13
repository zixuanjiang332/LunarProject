package jdd.lunarProject.GUI;

import jdd.lunarProject.Build.EventConfigManager;
import jdd.lunarProject.Build.EventDefinition;
import jdd.lunarProject.Build.RewardManager;
import jdd.lunarProject.Build.RewardOption;
import jdd.lunarProject.Build.RewardPoolDefinition;
import jdd.lunarProject.Build.ServiceDefinition;
import jdd.lunarProject.Game.Game;
import jdd.lunarProject.Game.GameManager;
import jdd.lunarProject.Game.RoundManager;
import jdd.lunarProject.Game.StageModels.StageTemplate;
import jdd.lunarProject.LunarProject;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class GuiManager {
    private static final int MAIN_MENU_SIZE = 45;
    private static final int PAGE_SIZE = 54;

    private final LunarProject plugin;
    private final NamespacedKey menuItemKey;
    private final Map<UUID, EnumMap<GuiType, String>> autoOpenKeys = new HashMap<>();

    public GuiManager(LunarProject plugin) {
        this.plugin = plugin;
        this.menuItemKey = new NamespacedKey(plugin, "menu_item");
    }

    public LunarProject getPlugin() {
        return plugin;
    }

    public void ensureMenuItem(Player player) {
        if (!isEnabled() || player == null || !player.isOnline()) {
            return;
        }

        ItemStack existingItem = findMenuItem(player);
        if (existingItem != null) {
            moveMenuItemToPreferredSlot(player, existingItem);
            return;
        }

        ItemStack menuItem = createMenuItem();
        int preferredSlot = getMenuItemSlot();
        ItemStack currentItem = player.getInventory().getItem(preferredSlot);
        if (currentItem == null || currentItem.getType().isAir()) {
            player.getInventory().setItem(preferredSlot, menuItem);
        } else {
            player.getInventory().addItem(menuItem);
        }
    }

    public boolean isMenuItem(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir() || !itemStack.hasItemMeta()) {
            return false;
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        return itemMeta.getPersistentDataContainer().has(menuItemKey, PersistentDataType.BYTE);
    }

    public void openMainMenu(Player player) {
        GuiSession session = createFramedSession(GuiType.MAIN_MENU, MAIN_MENU_SIZE, "Lunar Terminal", Material.BLACK_STAINED_GLASS_PANE);
        Game game = GameManager.getPlayerGame(player.getUniqueId());

        session.setItem(4, createFeatureCard(Material.NETHER_STAR, "Run Snapshot", buildStatusLore(game), true), event -> openRoomStatus(player));
        session.setItem(10, createActionCard(Material.EMERALD, "Create Room", "Spin up a fresh room and join it."), event -> {
            if (GameManager.getPlayerGame(player.getUniqueId()) != null) {
                player.sendMessage("§cYou are already in a room.");
                failFeedback(player);
                return;
            }
            Game newGame = GameManager.createGame();
            newGame.playerJoin(player);
            player.sendMessage("§aCreated and joined room §f" + newGame.getGameId());
            successFeedback(player);
            openRoomStatus(player);
        });
        session.setItem(12, createActionCard(Material.CHEST_MINECART, "Join Room", "Browse active rooms and hop in."), event -> openRoomList(player));
        session.setItem(14, createActionCard(Material.LIME_CONCRETE, "Start Room", game == null ? "Join a room before starting." : "Launch the current route immediately."), event -> {
            if (game == null) {
                player.sendMessage("§cYou are not in a room.");
                failFeedback(player);
                return;
            }
            game.start();
            successFeedback(player);
            refreshGame(game);
        });
        session.setItem(16, createActionCard(Material.BARRIER, "Leave Room", game == null ? "You are currently outside any room." : "Drop out of the current room."), event -> {
            Game currentGame = GameManager.getPlayerGame(player.getUniqueId());
            if (currentGame == null) {
                player.sendMessage("§cYou are not in a room.");
                failFeedback(player);
                return;
            }
            currentGame.playerLeave(player);
            successFeedback(player);
            openMainMenu(player);
        });

        session.setItem(20, createFeatureCard(Material.COMPASS, "Stage Console", buildStageActionLore(game), false), event -> openCurrentStageGui(player));
        session.setItem(22, createFeatureCard(Material.BOOK, "Build Ledger", buildBuildPreviewLore(game, player), false), event -> openBuildView(player));
        session.setItem(24, createActionCard(Material.CLOCK, "Refresh", "Refresh all current room information."), event -> openMainMenu(player));
        session.setItem(40, createBackCard("Close Terminal", "Close this menu."), event -> player.closeInventory());

        player.openInventory(session.getInventory());
    }

    public void openRoomList(Player player) {
        GuiSession session = createFramedSession(GuiType.ROOM_LIST, PAGE_SIZE, "Room Registry", Material.GRAY_STAINED_GLASS_PANE);
        List<String> activeRoomIds = new ArrayList<>(GameManager.getActiveGameIds());
        activeRoomIds.sort(String::compareTo);

        session.setItem(4, createFeatureCard(Material.MAP, "Available Rooms", List.of(
                "Rooms online: " + activeRoomIds.size(),
                "Pick a room card below to join it."
        ), true), null);

        int[] roomSlots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34
        };

        int roomIndex = 0;
        for (String roomId : activeRoomIds) {
            if (roomIndex >= roomSlots.length) {
                break;
            }
            Game listedGame = GameManager.getGame(roomId);
            if (listedGame == null) {
                continue;
            }

            RoundManager roundManager = listedGame.getRoundManager();
            session.setItem(roomSlots[roomIndex], createFeatureCard(
                    materialForPhase(roundManager.getCurrentRoomType()),
                    "Room " + roomId,
                    List.of(
                            "State: " + listedGame.getGameState(),
                            "Players: " + listedGame.getTotalPlayerCount() + " total",
                            "Round: " + roundManager.getCurrentRound() + "/5",
                            "Phase: " + roundManager.getCurrentRoomType(),
                            "Objective: " + roundManager.getStatusHint()
                    ),
                    false
            ), event -> {
                if (GameManager.getPlayerGame(player.getUniqueId()) != null) {
                    player.sendMessage("§cLeave your current room before joining another one.");
                    failFeedback(player);
                    return;
                }
                Game targetGame = GameManager.getGame(roomId);
                if (targetGame == null) {
                    player.sendMessage("§cThat room no longer exists.");
                    failFeedback(player);
                    openRoomList(player);
                    return;
                }
                targetGame.playerJoin(player);
                successFeedback(player);
                openRoomStatus(player);
            });
            roomIndex++;
        }

        if (activeRoomIds.isEmpty()) {
            session.setItem(22, createFeatureCard(Material.BARRIER, "No Active Rooms", List.of(
                    "The registry is empty right now.",
                    "Create a room from the terminal."
            ), false), null);
        }

        session.setItem(49, createBackCard("Return", "Go back to the terminal menu."), event -> openMainMenu(player));
        player.openInventory(session.getInventory());
    }

    public void openRoomStatus(Player player) {
        GuiSession session = createFramedSession(GuiType.ROOM_STATUS, PAGE_SIZE, "Room Status", Material.BLUE_STAINED_GLASS_PANE);
        Game game = GameManager.getPlayerGame(player.getUniqueId());

        if (game == null) {
            session.setItem(22, createFeatureCard(Material.BARRIER, "No Active Room", List.of(
                    "You are currently outside any room.",
                    "Create or join one from the terminal."
            ), false), null);
            session.setItem(49, createBackCard("Return", "Go back to the terminal menu."), event -> openMainMenu(player));
            player.openInventory(session.getInventory());
            return;
        }

        RoundManager roundManager = game.getRoundManager();
        session.setItem(4, createFeatureCard(Material.MAP, "Room " + game.getGameId(), List.of(
                "State: " + game.getGameState(),
                "Alive: " + game.getActivePlayerCount(),
                "Dead: " + game.getDeadPlayerCount(),
                "Round: " + roundManager.getCurrentRound() + "/5",
                "Phase: " + roundManager.getCurrentRoomType()
        ), true), null);
        session.setItem(13, createFeatureCard(materialForPhase(roundManager.getCurrentRoomType()), "Current Objective", buildObjectiveLore(game), false), null);
        session.setItem(22, createFeatureCard(Material.HEART_OF_THE_SEA, "Route Flow", List.of(
                "Ready: " + progressText(roundManager.getProceedReadyCount(), game.getActivePlayerCount()),
                "Rewards pending: " + roundManager.getPendingRewardPlayerCount(),
                "Vote timer: " + roundManager.getVoteCountdownRemaining() + "s",
                "Room timer: " + roundManager.getReadyCountdownRemaining() + "s"
        ), false), null);

        session.setItem(29, createActionCard(Material.LIME_CONCRETE, "Start Room", "Launch the room if it is ready."), event -> {
            game.start();
            successFeedback(player);
            refreshGame(game);
        });
        session.setItem(31, createActionCard(Material.COMPASS, "Open Stage Console", "Jump to the active stage interaction."), event -> openCurrentStageGui(player));
        session.setItem(33, createActionCard(Material.BOOK, "Open Build Ledger", "Inspect relics and run bonuses."), event -> openBuildView(player));
        session.setItem(40, createActionCard(Material.BARRIER, "Leave Room", "Exit the current room immediately."), event -> {
            game.playerLeave(player);
            successFeedback(player);
            openMainMenu(player);
        });
        session.setItem(49, createBackCard("Return", "Go back to the terminal menu."), event -> openMainMenu(player));

        if (roundManager.isPeacefulRoom() && !roundManager.isRewardPhase()) {
            session.setItem(24, createActionCard(
                    roundManager.hasPlayerProceeded(player.getUniqueId()) ? Material.LIME_DYE : Material.YELLOW_DYE,
                    roundManager.hasPlayerProceeded(player.getUniqueId()) ? "Ready Locked" : "Mark Ready",
                    roundManager.hasPlayerProceeded(player.getUniqueId())
                            ? "You are already queued for departure."
                            : "Confirm that you are ready to leave this room."
            ), event -> {
                roundManager.setPlayerProceed(player);
                successFeedback(player);
                refreshGame(game);
            });
        }

        if (roundManager.isRewardPhase()) {
            session.setItem(24, createActionCard(Material.AMETHYST_SHARD, "Open Reward Draft", "Choose your current room reward."), event -> openRewardMenu(player));
        } else if (roundManager.isShopRoom()) {
            session.setItem(24, createActionCard(Material.GOLD_INGOT, "Open Supply Counter", "Inspect current shop services."), event -> openShopMenu(player));
        } else if (roundManager.isEventRoom() && !roundManager.isEventResolved()) {
            session.setItem(24, createActionCard(Material.WRITABLE_BOOK, "Open Event Choice", "Resolve the current abnormality event."), event -> openEventMenu(player));
        }

        populatePartyPanel(session, game);
        player.openInventory(session.getInventory());
    }

    public void openBuildView(Player player) {
        GuiSession session = createFramedSession(GuiType.BUILD_VIEW, PAGE_SIZE, "Run Build Ledger", Material.PURPLE_STAINED_GLASS_PANE);
        Game game = GameManager.getPlayerGame(player.getUniqueId());

        if (game == null) {
            session.setItem(22, createFeatureCard(Material.BARRIER, "No Build Data", List.of(
                    "Join a room to start building a run."
            ), false), null);
            session.setItem(49, createBackCard("Return", "Go back to the terminal menu."), event -> openMainMenu(player));
            player.openInventory(session.getInventory());
            return;
        }

        List<String> buildSummary = game.getBuildSummary(player.getUniqueId());
        session.setItem(4, createFeatureCard(Material.BOOK, "Build Overview", List.of(
                "Room: " + game.getGameId(),
                "Round: " + game.getRoundManager().getCurrentRound() + "/5",
                "Phase: " + game.getRoundManager().getCurrentRoomType()
        ), true), null);

        int[] detailSlots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34
        };

        for (int index = 0; index < buildSummary.size() && index < detailSlots.length; index++) {
            String line = buildSummary.get(index);
            session.setItem(detailSlots[index], createFeatureCard(materialForBuildLine(line), summarizeBuildLine(line), splitBuildLine(line), false), null);
        }

        session.setItem(49, createBackCard("Return", "Go back to room status."), event -> openRoomStatus(player));
        player.openInventory(session.getInventory());
    }

    public void openCurrentStageGui(Player player) {
        Game game = GameManager.getPlayerGame(player.getUniqueId());
        if (game == null) {
            openMainMenu(player);
            return;
        }

        RoundManager roundManager = game.getRoundManager();
        if (roundManager.isVotingPhase()) {
            openVoteMenu(player);
            return;
        }
        if (roundManager.isRewardPhase()) {
            openRewardMenu(player);
            return;
        }
        if (roundManager.isEventRoom() && !roundManager.isEventResolved()) {
            openEventMenu(player);
            return;
        }
        if (roundManager.isShopRoom()) {
            openShopMenu(player);
            return;
        }

        openRoomStatus(player);
    }

    public void openVoteMenu(Player player) {
        Game game = GameManager.getPlayerGame(player.getUniqueId());
        if (game == null) {
            openMainMenu(player);
            return;
        }

        RoundManager roundManager = game.getRoundManager();
        GuiSession session = createFramedSession(GuiType.VOTE, MAIN_MENU_SIZE, "Route Vote", Material.ORANGE_STAINED_GLASS_PANE);
        session.setItem(4, createFeatureCard(Material.CLOCK, "Vote Window", List.of(
                "Round: " + roundManager.getCurrentRound() + "/5",
                "Time left: " + roundManager.getVoteCountdownRemaining() + "s",
                "Your vote: " + formatVote(roundManager.getPlayerVote(player.getUniqueId()))
        ), true), null);
        session.setItem(40, createBackCard("Return", "Go back to room status."), event -> openRoomStatus(player));

        List<Map.Entry<Integer, StageTemplate>> choices = new ArrayList<>(roundManager.getNodeChoices().entrySet());
        choices.sort(Map.Entry.comparingByKey());
        int[] choiceSlots = {20, 22, 24};
        for (int index = 0; index < choices.size() && index < choiceSlots.length; index++) {
            Map.Entry<Integer, StageTemplate> entry = choices.get(index);
            int choiceIndex = entry.getKey();
            StageTemplate template = entry.getValue();
            session.setItem(choiceSlots[index], createFeatureCard(
                    materialForStage(template.stageType()),
                    "Node " + choiceIndex + " " + stageLabel(template.stageType()),
                    List.of(
                            "Map: " + template.mapName(),
                            "Stage id: " + template.stageId(),
                            "Reward focus: " + rewardFocus(template.stageType()),
                            "Votes: " + roundManager.getVoteCount(choiceIndex),
                            "Click to vote for this route."
                    ),
                    roundManager.getPlayerVote(player.getUniqueId()) == choiceIndex
            ), event -> {
                roundManager.castVote(player, choiceIndex);
                successFeedback(player);
                refreshGame(game);
            });
        }

        player.openInventory(session.getInventory());
    }

    public void openRewardMenu(Player player) {
        Game game = GameManager.getPlayerGame(player.getUniqueId());
        if (game == null) {
            openMainMenu(player);
            return;
        }

        RoundManager roundManager = game.getRoundManager();
        GuiSession session = createFramedSession(GuiType.REWARD, MAIN_MENU_SIZE, "Reward Draft", Material.AMETHYST_BLOCK);
        session.setItem(4, createFeatureCard(Material.AMETHYST_CLUSTER, "Selection Status", List.of(
                "Pending players: " + roundManager.getPendingRewardPlayerCount(),
                "You locked in: " + yesNo(roundManager.isRewardResolved(player.getUniqueId())),
                "Room phase: " + roundManager.getCurrentRoomType()
        ), true), null);
        session.setItem(40, createBackCard("Return", "Go back to room status."), event -> openRoomStatus(player));

        List<RewardOption> rewardOptions = roundManager.getPendingRewardOptions(player.getUniqueId());
        int[] rewardSlots = {20, 22, 24};
        for (int index = 0; index < rewardOptions.size() && index < rewardSlots.length; index++) {
            RewardOption rewardOption = rewardOptions.get(index);
            int rewardIndex = index + 1;
            session.setItem(rewardSlots[index], createFeatureCard(
                    materialForReward(rewardOption),
                    rewardOption.name(),
                    List.of(
                            "Type: " + rewardOption.rewardType(),
                            "Rarity: " + rewardOption.rarity(),
                            rewardOption.description(),
                            "Click to lock this reward."
                    ),
                    roundManager.isRewardResolved(player.getUniqueId())
            ), event -> {
                roundManager.handleRewardChoice(player, rewardIndex);
                successFeedback(player);
                refreshGame(game);
            });
        }

        if (roundManager.isShopRoom()) {
            session.setItem(31, createActionCard(Material.GOLD_INGOT, "Open Supply Counter", "Inspect current shop services."), event -> openShopMenu(player));
        }

        player.openInventory(session.getInventory());
    }

    public void openShopMenu(Player player) {
        Game game = GameManager.getPlayerGame(player.getUniqueId());
        if (game == null) {
            openMainMenu(player);
            return;
        }

        RoundManager roundManager = game.getRoundManager();
        RewardPoolDefinition shopPool = RewardManager.getPool("shop_room");
        GuiSession session = createFramedSession(GuiType.SHOP, PAGE_SIZE, "Supply Counter", Material.YELLOW_STAINED_GLASS_PANE);

        session.setItem(4, createFeatureCard(Material.GOLD_BLOCK, "Supply Status", List.of(
                "Purchased: " + yesNo(roundManager.hasPurchasedShopService(player.getUniqueId())),
                "Rewards pending: " + roundManager.getPendingRewardPlayerCount(),
                "Ready progress: " + progressText(roundManager.getProceedReadyCount(), game.getActivePlayerCount())
        ), true), null);
        session.setItem(49, createBackCard("Return", "Go back to room status."), event -> openRoomStatus(player));

        if (shopPool != null) {
            int[] serviceSlots = {19, 21, 23, 25, 31};
            int serviceIndex = 0;
            for (String serviceId : shopPool.services()) {
                if (serviceIndex >= serviceSlots.length) {
                    break;
                }
                ServiceDefinition serviceDefinition = RewardManager.getService(serviceId);
                if (serviceDefinition == null) {
                    continue;
                }
                boolean purchased = roundManager.hasPurchasedShopService(player.getUniqueId());
                session.setItem(serviceSlots[serviceIndex], createFeatureCard(
                        purchased ? Material.GRAY_DYE : Material.GOLD_INGOT,
                        serviceDefinition.name(),
                        List.of(
                                serviceDefinition.description(),
                                "Offer id: " + serviceDefinition.id(),
                                purchased ? "Already claimed for this room." : "Click to claim this service."
                        ),
                        false
                ), event -> {
                    roundManager.handleShopPurchase(player, serviceDefinition.id());
                    if (roundManager.hasPurchasedShopService(player.getUniqueId())) {
                        successFeedback(player);
                    } else {
                        failFeedback(player);
                    }
                    refreshGame(game);
                });
                serviceIndex++;
            }
        }

        if (roundManager.isRewardPhase()) {
            session.setItem(40, createActionCard(Material.AMETHYST_SHARD, "Open Reward Draft", "Return to your reward selection."), event -> openRewardMenu(player));
        }

        player.openInventory(session.getInventory());
    }

    public void openEventMenu(Player player) {
        Game game = GameManager.getPlayerGame(player.getUniqueId());
        if (game == null) {
            openMainMenu(player);
            return;
        }

        RoundManager roundManager = game.getRoundManager();
        EventDefinition eventDefinition = EventConfigManager.getEvent(roundManager.getCurrentEventId());
        GuiSession session = createFramedSession(GuiType.EVENT, MAIN_MENU_SIZE, "Abnormality Event", Material.RED_STAINED_GLASS_PANE);

        List<String> introLore = new ArrayList<>();
        if (eventDefinition != null) {
            introLore.addAll(buildWrappedLore(eventDefinition.intro()));
        } else {
            introLore.add("No event data is available.");
        }
        introLore.add("Time left: " + roundManager.getReadyCountdownRemaining() + "s");
        session.setItem(4, createFeatureCard(Material.WRITABLE_BOOK, "Current Event", introLore, true), null);
        session.setItem(40, createBackCard("Return", "Go back to room status."), event -> openRoomStatus(player));

        String optionOne = eventDefinition != null ? eventDefinition.optionOne() : "Leave carefully";
        String optionTwo = eventDefinition != null ? eventDefinition.optionTwo() : "Investigate";
        session.setItem(20, createFeatureCard(Material.LIME_WOOL, "Option 1", List.of(
                optionOne,
                "Safer default route.",
                "Click to confirm this choice."
        ), false), event -> {
            roundManager.handleEventChoice(player, "1");
            successFeedback(player);
            refreshGame(game);
        });
        session.setItem(24, createFeatureCard(Material.ORANGE_WOOL, "Option 2", List.of(
                optionTwo,
                "Higher variance route.",
                "Click to confirm this choice."
        ), false), event -> {
            roundManager.handleEventChoice(player, "2");
            successFeedback(player);
            refreshGame(game);
        });

        player.openInventory(session.getInventory());
    }

    public void refreshGame(Game game) {
        if (!isEnabled() || game == null) {
            return;
        }

        List<Player> players = new ArrayList<>();
        players.addAll(game.getActiveOnlinePlayers());
        players.addAll(game.getDeadOnlinePlayers());
        for (Player player : players) {
            refreshPlayer(player);
        }
    }

    public void refreshPlayer(Player player) {
        if (!isEnabled() || player == null || !player.isOnline()) {
            return;
        }

        ensureMenuItem(player);
        if (tryAutoOpenPhaseGui(player)) {
            return;
        }

        GuiSession openSession = getOpenSession(player);
        if (openSession == null) {
            return;
        }
        openByType(player, openSession.getGuiType());
    }

    public boolean handleInventoryClick(InventoryClickEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (!(topInventory.getHolder() instanceof GuiSession guiSession)) {
            return false;
        }

        event.setCancelled(true);
        if (event.getRawSlot() >= 0 && event.getRawSlot() < topInventory.getSize()) {
            guiSession.handleClick(event);
        }
        return true;
    }

    public boolean isProtectedMenuInteraction(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return false;
        }

        if (isMenuItem(event.getCurrentItem()) || isMenuItem(event.getCursor())) {
            return true;
        }

        if (event.getHotbarButton() >= 0 && event.getHotbarButton() <= 8) {
            return isMenuItem(player.getInventory().getItem(event.getHotbarButton()));
        }

        return false;
    }

    public boolean handleInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof GuiSession) {
            return true;
        }
        return isMenuItem(event.getOldCursor());
    }

    private boolean tryAutoOpenPhaseGui(Player player) {
        Game game = GameManager.getPlayerGame(player.getUniqueId());
        if (game == null || !game.isRunning()) {
            return false;
        }

        RoundManager roundManager = game.getRoundManager();
        GuiType guiType = null;
        String phaseKey = null;

        if (roundManager.isVotingPhase() && plugin.isGuiAutoOpenVoteEnabled()) {
            guiType = GuiType.VOTE;
            phaseKey = roundManager.getVotePhaseKey();
        } else if (roundManager.isRewardPhase() && plugin.isGuiAutoOpenRewardEnabled()) {
            guiType = GuiType.REWARD;
            phaseKey = roundManager.getRewardPhaseKey();
        } else if (roundManager.isEventRoom() && !roundManager.isEventResolved() && plugin.isGuiAutoOpenEventEnabled()) {
            guiType = GuiType.EVENT;
            phaseKey = roundManager.getEventPhaseKey();
        }

        if (guiType == null || phaseKey == null || phaseKey.isBlank()) {
            return false;
        }

        EnumMap<GuiType, String> playerKeys = autoOpenKeys.computeIfAbsent(player.getUniqueId(), ignored -> new EnumMap<>(GuiType.class));
        if (phaseKey.equals(playerKeys.get(guiType))) {
            return false;
        }

        playerKeys.put(guiType, phaseKey);
        openByType(player, guiType);
        return true;
    }

    private GuiSession getOpenSession(Player player) {
        Inventory topInventory = player.getOpenInventory().getTopInventory();
        if (topInventory == null) {
            return null;
        }
        if (topInventory.getHolder() instanceof GuiSession guiSession) {
            return guiSession;
        }
        return null;
    }

    private void openByType(Player player, GuiType guiType) {
        switch (guiType) {
            case MAIN_MENU -> openMainMenu(player);
            case ROOM_LIST -> openRoomList(player);
            case ROOM_STATUS -> openRoomStatus(player);
            case BUILD_VIEW -> openBuildView(player);
            case VOTE -> openVoteMenu(player);
            case REWARD -> openRewardMenu(player);
            case SHOP -> openShopMenu(player);
            case EVENT -> openEventMenu(player);
        }
    }

    private GuiSession createFramedSession(GuiType guiType, int size, String title, Material frameMaterial) {
        GuiSession session = new GuiSession(guiType, size, title);
        fillBorders(session, frameMaterial);
        return session;
    }

    private void fillBorders(GuiSession session, Material frameMaterial) {
        int size = session.getInventory().getSize();
        int rows = size / 9;
        for (int slot = 0; slot < size; slot++) {
            int column = slot % 9;
            int row = slot / 9;
            if (row == 0 || row == rows - 1 || column == 0 || column == 8) {
                session.setItem(slot, createPane(frameMaterial, " "), null);
            }
        }
    }

    private void populatePartyPanel(GuiSession session, Game game) {
        List<String> activeNames = game.getActiveOnlinePlayers().stream().map(Player::getName).sorted().toList();
        List<String> deadNames = game.getDeadOnlinePlayers().stream().map(Player::getName).sorted().toList();

        session.setItem(37, createFeatureCard(Material.LIME_DYE, "Active Crew", buildPlayerListLore(activeNames, "No active players."), false), null);
        session.setItem(38, createFeatureCard(Material.GRAY_DYE, "Fallen Crew", buildPlayerListLore(deadNames, "No fallen players."), false), null);
    }

    private List<String> buildPlayerListLore(List<String> names, String emptyLine) {
        if (names.isEmpty()) {
            return List.of(emptyLine);
        }
        return names.stream().limit(6).toList();
    }

    private ItemStack createMenuItem() {
        ItemStack itemStack = new ItemStack(resolveMenuMaterial());
        ItemMeta itemMeta = itemStack.getItemMeta();
        itemMeta.setDisplayName("§6Lunar Terminal");
        itemMeta.setLore(List.of(
                "§7Right click to open the route terminal.",
                "§8Main menu, room status, stage actions."
        ));
        itemMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        itemMeta.getPersistentDataContainer().set(menuItemKey, PersistentDataType.BYTE, (byte) 1);
        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

    private ItemStack findMenuItem(Player player) {
        for (ItemStack itemStack : player.getInventory().getContents()) {
            if (isMenuItem(itemStack)) {
                return itemStack;
            }
        }
        return null;
    }

    private void moveMenuItemToPreferredSlot(Player player, ItemStack itemStack) {
        int preferredSlot = getMenuItemSlot();
        ItemStack existingPreferred = player.getInventory().getItem(preferredSlot);
        if (existingPreferred != null && isMenuItem(existingPreferred)) {
            return;
        }
        if (player.getInventory().getItem(preferredSlot) == null || player.getInventory().getItem(preferredSlot).getType().isAir()) {
            int currentSlot = player.getInventory().first(itemStack);
            if (currentSlot >= 0) {
                player.getInventory().setItem(currentSlot, null);
            }
            player.getInventory().setItem(preferredSlot, itemStack);
        }
    }

    private ItemStack createPane(Material material, String name) {
        ItemStack itemStack = new ItemStack(material);
        ItemMeta itemMeta = itemStack.getItemMeta();
        itemMeta.setDisplayName(name);
        itemMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

    private ItemStack createActionCard(Material material, String name, String description) {
        return createFeatureCard(material, name, List.of(description), false);
    }

    private ItemStack createBackCard(String name, String description) {
        return createFeatureCard(Material.ARROW, name, List.of(description), false);
    }

    private ItemStack createFeatureCard(Material material, String name, List<String> lore, boolean highlight) {
        ItemStack itemStack = new ItemStack(material);
        ItemMeta itemMeta = itemStack.getItemMeta();
        itemMeta.setDisplayName((highlight ? "§6" : "§f") + name);
        if (!lore.isEmpty()) {
            itemMeta.setLore(lore.stream().map(line -> "§7" + line).collect(Collectors.toList()));
        }
        itemMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        itemMeta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

    private List<String> buildStatusLore(Game game) {
        if (game == null) {
            return List.of(
                    "No room joined.",
                    "Create one or browse the registry."
            );
        }

        RoundManager roundManager = game.getRoundManager();
        return List.of(
                "Room: " + game.getGameId(),
                "State: " + game.getGameState(),
                "Crew: " + game.getActivePlayerCount() + " active / " + game.getDeadPlayerCount() + " fallen",
                "Round: " + roundManager.getCurrentRound() + "/5",
                "Phase: " + roundManager.getCurrentRoomType()
        );
    }

    private List<String> buildBuildPreviewLore(Game game, Player player) {
        if (game == null) {
            return List.of(
                    "No active build yet.",
                    "Join a room to begin a run."
            );
        }

        List<String> summary = game.getBuildSummary(player.getUniqueId());
        List<String> preview = new ArrayList<>();
        for (int index = 0; index < summary.size() && index < 3; index++) {
            preview.add(summary.get(index));
        }
        if (summary.size() > 3) {
            preview.add("Open the ledger for full details.");
        }
        return preview;
    }

    private List<String> buildStageActionLore(Game game) {
        if (game == null) {
            return List.of(
                    "No active room.",
                    "Join or create one first."
            );
        }

        RoundManager roundManager = game.getRoundManager();
        if (roundManager.isVotingPhase()) {
            return List.of(
                    "Vote is live.",
                    "Open the route chooser."
            );
        }
        if (roundManager.isRewardPhase()) {
            return List.of(
                    "Rewards are waiting.",
                    "Open the reward draft."
            );
        }
        if (roundManager.isEventRoom() && !roundManager.isEventResolved()) {
            return List.of(
                    "Event is unresolved.",
                    "Open the event console."
            );
        }
        if (roundManager.isShopRoom()) {
            return List.of(
                    "Shop services are available.",
                    "Open the supply counter."
            );
        }
        return List.of(
                "Open the current room status.",
                "Use this to inspect the live phase."
        );
    }

    private List<String> buildObjectiveLore(Game game) {
        RoundManager roundManager = game.getRoundManager();
        List<String> lore = new ArrayList<>();
        lore.add("Hint: " + roundManager.getStatusHint());
        lore.add("Round: " + roundManager.getCurrentRound() + "/5");
        lore.add("Phase key: " + roundManager.getCurrentRoomType());
        if (roundManager.isVotingPhase()) {
            lore.add("Timer: " + roundManager.getVoteCountdownRemaining() + "s");
        } else if (roundManager.isPeacefulRoom()) {
            lore.add("Ready: " + progressText(roundManager.getProceedReadyCount(), game.getActivePlayerCount()));
            lore.add("Timer: " + roundManager.getReadyCountdownRemaining() + "s");
        }
        return lore;
    }

    private List<String> buildWrappedLore(String input) {
        if (input == null || input.isBlank()) {
            return List.of("No details.");
        }
        return Arrays.stream(input.split("\\n"))
                .flatMap(line -> wrapLine(line, 34).stream())
                .toList();
    }

    private List<String> wrapLine(String input, int maxLength) {
        if (input == null || input.isBlank()) {
            return List.of(" ");
        }

        List<String> wrapped = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : input.split(" ")) {
            if (current.isEmpty()) {
                current.append(word);
                continue;
            }
            if (current.length() + 1 + word.length() > maxLength) {
                wrapped.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current.append(" ").append(word);
            }
        }
        if (!current.isEmpty()) {
            wrapped.add(current.toString());
        }
        return wrapped;
    }

    private Material materialForPhase(String phase) {
        String normalized = phase == null ? "" : phase.toUpperCase();
        if (normalized.contains("VOTING")) {
            return Material.RECOVERY_COMPASS;
        }
        if (normalized.contains("REWARD")) {
            return Material.AMETHYST_SHARD;
        }
        return switch (normalized) {
            case "NORMAL" -> Material.IRON_SWORD;
            case "ELITE" -> Material.DIAMOND_SWORD;
            case "SHOP" -> Material.GOLD_INGOT;
            case "REST" -> Material.CAKE;
            case "EVENT" -> Material.WRITABLE_BOOK;
            case "BOSS" -> Material.NETHERITE_AXE;
            default -> Material.COMPASS;
        };
    }

    private Material materialForStage(String stageType) {
        return materialForPhase(stageType);
    }

    private Material materialForReward(RewardOption rewardOption) {
        if (rewardOption.rewardType() == RewardOption.RewardType.SERVICE) {
            return Material.GLOWSTONE_DUST;
        }
        return switch (rewardOption.rarity().toUpperCase()) {
            case "RARE" -> Material.ENDER_EYE;
            case "UNCOMMON" -> Material.ECHO_SHARD;
            default -> Material.PRISMARINE_CRYSTALS;
        };
    }

    private Material materialForBuildLine(String line) {
        String normalized = line.toLowerCase();
        if (normalized.startsWith("rewards")) {
            return Material.SUNFLOWER;
        }
        if (normalized.startsWith("relics")) {
            return Material.ENDER_EYE;
        }
        if (normalized.startsWith("temporary")) {
            return Material.BLAZE_POWDER;
        }
        if (normalized.startsWith("recent")) {
            return Material.PAPER;
        }
        return Material.BOOK;
    }

    private String summarizeBuildLine(String line) {
        int separator = line.indexOf(':');
        if (separator <= 0) {
            return line;
        }
        return line.substring(0, separator);
    }

    private List<String> splitBuildLine(String line) {
        int separator = line.indexOf(':');
        if (separator <= 0 || separator >= line.length() - 1) {
            return List.of(line);
        }
        return wrapLine(line.substring(separator + 1).trim(), 34);
    }

    private String stageLabel(String stageType) {
        return "[" + stageType.toUpperCase() + "]";
    }

    private String rewardFocus(String stageType) {
        return switch (stageType.toUpperCase()) {
            case "ELITE" -> "Higher quality reward pool";
            case "SHOP" -> "Service claim plus reward draft";
            case "REST" -> "Recovery and utility reward";
            case "EVENT" -> "Risk reward branch";
            case "BOSS" -> "Major clear reward";
            default -> "Standard room reward";
        };
    }

    private String progressText(int current, int total) {
        int safeTotal = Math.max(1, total);
        int safeCurrent = Math.max(0, Math.min(current, safeTotal));
        StringBuilder bar = new StringBuilder("[");
        for (int index = 0; index < safeTotal; index++) {
            bar.append(index < safeCurrent ? "#" : ".");
        }
        bar.append("] ").append(safeCurrent).append("/").append(safeTotal);
        return bar.toString();
    }

    private String yesNo(boolean value) {
        return value ? "YES" : "NO";
    }

    private String formatVote(int vote) {
        return vote > 0 ? "Node " + vote : "NONE";
    }

    private void successFeedback(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
    }

    private void failFeedback(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.8f);
    }

    private Material resolveMenuMaterial() {
        Material configured = Material.matchMaterial(plugin.getConfig().getString("gui.menu-item-material", "COMPASS"));
        return configured != null ? configured : Material.COMPASS;
    }

    private int getMenuItemSlot() {
        return Math.max(0, Math.min(8, plugin.getConfig().getInt("gui.menu-item-slot", 8)));
    }

    private boolean isEnabled() {
        return plugin.getConfig().getBoolean("gui.enable", true);
    }
}
