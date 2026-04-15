package jdd.lunarProject.GUI;

import jdd.lunarProject.Build.EventConfigManager;
import jdd.lunarProject.Build.EventDefinition;
import jdd.lunarProject.Build.RewardManager;
import jdd.lunarProject.Build.RewardOption;
import jdd.lunarProject.Build.RewardPoolDefinition;
import jdd.lunarProject.Build.ServiceDefinition;
import jdd.lunarProject.Game.Game;
import jdd.lunarProject.Game.GameManager;
import jdd.lunarProject.Game.PlayerClassManager;
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
        GuiSession session = createFramedSession(GuiType.MAIN_MENU, MAIN_MENU_SIZE, "边狱终端", Material.BLACK_STAINED_GLASS_PANE);
        Game game = GameManager.getPlayerGame(player.getUniqueId());

        session.setItem(4, createFeatureCard(Material.NETHER_STAR, "局内概览", buildStatusLore(game), true), event -> openRoomStatus(player));
        session.setItem(10, createActionCard(Material.EMERALD, "创建房间", "创建一个新的测试房间并立即加入。"), event -> {
            if (GameManager.getPlayerGame(player.getUniqueId()) != null) {
                player.sendMessage("§c你已经在一个房间中。");
                failFeedback(player);
                return;
            }
            Game newGame = GameManager.createGame();
            newGame.playerJoin(player);
            player.sendMessage("§a已创建并加入房间：§f" + newGame.getGameId());
            successFeedback(player);
            openRoomStatus(player);
        });
        session.setItem(12, createActionCard(Material.CHEST_MINECART, "加入房间", "浏览当前正在运行的房间并加入。"), event -> openRoomList(player));
        session.setItem(14, createActionCard(Material.LIME_CONCRETE, "开始房间", game == null ? "请先加入房间再开始。" : "立即启动当前房间流程。"), event -> {
            if (game == null) {
                player.sendMessage("§c你当前不在任何房间中。");
                failFeedback(player);
                return;
            }
            game.start();
            successFeedback(player);
            refreshGame(game);
        });
        session.setItem(16, createActionCard(Material.BARRIER, "离开房间", game == null ? "你当前不在任何房间中。" : "退出当前所在房间。"), event -> {
            Game currentGame = GameManager.getPlayerGame(player.getUniqueId());
            if (currentGame == null) {
                player.sendMessage("§c你当前不在任何房间中。");
                failFeedback(player);
                return;
            }
            currentGame.playerLeave(player);
            successFeedback(player);
            openMainMenu(player);
        });

        session.setItem(20, createFeatureCard(Material.COMPASS, "阶段操作", buildStageActionLore(game), false), event -> openCurrentStageGui(player));
        session.setItem(22, createFeatureCard(Material.BOOK, "当前构筑", buildBuildPreviewLore(game, player), false), event -> openBuildView(player));
        session.setItem(24, createActionCard(Material.CLOCK, "刷新信息", "刷新当前房间与流程信息。"), event -> openMainMenu(player));
        session.setItem(31, createActionCard(Material.IRON_SWORD, "选择职业", "打开职业选择界面。"), event -> openClassSelect(player));
        session.setItem(40, createBackCard("关闭终端", "关闭当前菜单。"), event -> player.closeInventory());

        player.openInventory(session.getInventory());
    }

    public void openClassSelect(Player player) {
        Game game = GameManager.getPlayerGame(player.getUniqueId());
        GuiSession session = createFramedSession(GuiType.CLASS_SELECT, MAIN_MENU_SIZE, "职业选择", Material.LIGHT_BLUE_STAINED_GLASS_PANE);

        String currentClass = PlayerClassManager.getSelectedClass(player);
        session.setItem(4, createFeatureCard(Material.NETHER_STAR, "当前职业", List.of(
                "已选择：" + (currentClass.isBlank() ? "未选择" : currentClass),
                "测试阶段仅开放一个职业供联调使用。"
        ), true), null);

        session.setItem(20, createFeatureCard(Material.IRON_SWORD, "测试职业", List.of(
                "生命值：180",
                "混乱抗性：135",
                "混乱阈值：0.75",
                "理智：50",
                "点击后立即应用该职业。"
        ), PlayerClassManager.TEST_CLASS_ID.equals(currentClass)), event -> {
            if (!PlayerClassManager.applyClass(player, PlayerClassManager.TEST_CLASS_ID)) {
                player.sendMessage("§c测试职业初始化失败，请检查职业配置。");
                failFeedback(player);
                return;
            }
            player.sendMessage("§a已选择职业：§f" + PlayerClassManager.TEST_CLASS_ID);
            successFeedback(player);
            if (game != null) {
                refreshGame(game);
            } else {
                openMainMenu(player);
            }
        });

        session.setItem(40, createBackCard("返回上级", "返回上一层菜单。"), event -> {
            if (game != null) {
                openRoomStatus(player);
            } else {
                openMainMenu(player);
            }
        });

        player.openInventory(session.getInventory());
    }

    public void openRoomList(Player player) {
        GuiSession session = createFramedSession(GuiType.ROOM_LIST, PAGE_SIZE, "房间列表", Material.GRAY_STAINED_GLASS_PANE);
        List<String> activeRoomIds = new ArrayList<>(GameManager.getActiveGameIds());
        activeRoomIds.sort(String::compareTo);

        session.setItem(4, createFeatureCard(Material.MAP, "可加入房间", List.of(
                "当前房间数：" + activeRoomIds.size(),
                "点击下方房间卡片即可加入。"
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
                    "房间 " + roomId,
                    List.of(
                            "状态：" + listedGame.getGameState(),
                            "人数：" + listedGame.getTotalPlayerCount(),
                            "回合：" + roundManager.getCurrentRound() + "/5",
                            "阶段：" + roundManager.getCurrentRoomType(),
                            "目标：" + roundManager.getStatusHint()
                    ),
                    false
            ), event -> {
                if (GameManager.getPlayerGame(player.getUniqueId()) != null) {
                    player.sendMessage("§c请先离开当前房间，再加入其他房间。");
                    failFeedback(player);
                    return;
                }
                Game targetGame = GameManager.getGame(roomId);
                if (targetGame == null) {
                    player.sendMessage("§c该房间已不存在。");
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
            session.setItem(22, createFeatureCard(Material.BARRIER, "暂无活跃房间", List.of(
                    "当前没有可加入的房间。",
                    "请先在主菜单中创建房间。"
            ), false), null);
        }

        session.setItem(49, createBackCard("返回终端", "返回主菜单。"), event -> openMainMenu(player));
        player.openInventory(session.getInventory());
    }

    public void openRoomStatus(Player player) {
        GuiSession session = createFramedSession(GuiType.ROOM_STATUS, PAGE_SIZE, "房间状态", Material.BLUE_STAINED_GLASS_PANE);
        Game game = GameManager.getPlayerGame(player.getUniqueId());

        if (game == null) {
            session.setItem(22, createFeatureCard(Material.BARRIER, "当前无房间", List.of(
                    "你目前不在任何房间中。",
                    "请从主菜单创建或加入房间。"
            ), false), null);
            session.setItem(49, createBackCard("返回终端", "返回主菜单。"), event -> openMainMenu(player));
            player.openInventory(session.getInventory());
            return;
        }

        RoundManager roundManager = game.getRoundManager();
        session.setItem(4, createFeatureCard(Material.MAP, "房间 " + game.getGameId(), List.of(
                "状态：" + game.getGameState(),
                "存活：" + game.getActivePlayerCount(),
                "阵亡：" + game.getDeadPlayerCount(),
                "回合：" + roundManager.getCurrentRound() + "/5",
                "阶段：" + roundManager.getCurrentRoomType()
        ), true), null);
        session.setItem(13, createFeatureCard(materialForPhase(roundManager.getCurrentRoomType()), "当前目标", buildObjectiveLore(game), false), null);
        session.setItem(22, createFeatureCard(Material.HEART_OF_THE_SEA, "流程进度", List.of(
                "准备进度：" + progressText(roundManager.getProceedReadyCount(), game.getActivePlayerCount()),
                "未选奖励：" + roundManager.getPendingRewardPlayerCount(),
                "投票倒计时：" + roundManager.getVoteCountdownRemaining() + "秒",
                "房间倒计时：" + roundManager.getReadyCountdownRemaining() + "秒"
        ), false), null);

        session.setItem(29, createActionCard(Material.LIME_CONCRETE, "开始房间", "在房间满足条件时启动流程。"), event -> {
            game.start();
            successFeedback(player);
            refreshGame(game);
        });
        session.setItem(31, createActionCard(Material.COMPASS, "打开阶段界面", "进入当前阶段对应的交互界面。"), event -> openCurrentStageGui(player));
        session.setItem(33, createActionCard(Material.BOOK, "查看构筑", "查看当前局内奖励与饰品。"), event -> openBuildView(player));
        session.setItem(40, createActionCard(Material.BARRIER, "离开房间", "立即退出当前房间。"), event -> {
            game.playerLeave(player);
            successFeedback(player);
            openMainMenu(player);
        });
        session.setItem(49, createBackCard("返回终端", "返回主菜单。"), event -> openMainMenu(player));

        if (roundManager.isPeacefulRoom() && !roundManager.isRewardPhase()) {
            session.setItem(24, createActionCard(
                    roundManager.hasPlayerProceeded(player.getUniqueId()) ? Material.LIME_DYE : Material.YELLOW_DYE,
                    roundManager.hasPlayerProceeded(player.getUniqueId()) ? "已准备" : "标记准备",
                    roundManager.hasPlayerProceeded(player.getUniqueId())
                            ? "你已经标记为准备推进。"
                            : "确认你已完成当前房间内容并准备前进。"
            ), event -> {
                roundManager.setPlayerProceed(player);
                successFeedback(player);
                refreshGame(game);
            });
        }

        if (roundManager.isRewardPhase()) {
            session.setItem(24, createActionCard(Material.AMETHYST_SHARD, "打开奖励界面", "选择当前房间的奖励。"), event -> openRewardMenu(player));
        } else if (roundManager.isShopRoom()) {
            session.setItem(24, createActionCard(Material.GOLD_INGOT, "打开商店", "查看当前商店服务。"), event -> openShopMenu(player));
        } else if (roundManager.isEventRoom() && !roundManager.isEventResolved()) {
            session.setItem(24, createActionCard(Material.WRITABLE_BOOK, "打开事件界面", "处理当前异常事件。"), event -> openEventMenu(player));
        }

        populatePartyPanel(session, game);
        player.openInventory(session.getInventory());
    }

    public void openBuildView(Player player) {
        GuiSession session = createFramedSession(GuiType.BUILD_VIEW, PAGE_SIZE, "当前构筑", Material.PURPLE_STAINED_GLASS_PANE);
        Game game = GameManager.getPlayerGame(player.getUniqueId());

        if (game == null) {
            session.setItem(22, createFeatureCard(Material.BARRIER, "暂无构筑信息", List.of(
                    "加入房间后才会开始记录构筑。"
            ), false), null);
            session.setItem(49, createBackCard("返回终端", "返回主菜单。"), event -> openMainMenu(player));
            player.openInventory(session.getInventory());
            return;
        }

        List<String> buildSummary = game.getBuildSummary(player.getUniqueId());
        session.setItem(4, createFeatureCard(Material.BOOK, "构筑概览", List.of(
                "房间：" + game.getGameId(),
                "回合：" + game.getRoundManager().getCurrentRound() + "/5",
                "阶段：" + game.getRoundManager().getCurrentRoomType()
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

        session.setItem(49, createBackCard("返回房间", "返回房间状态页。"), event -> openRoomStatus(player));
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
        GuiSession session = createFramedSession(GuiType.VOTE, MAIN_MENU_SIZE, "节点投票", Material.ORANGE_STAINED_GLASS_PANE);
        session.setItem(4, createFeatureCard(Material.CLOCK, "投票窗口", List.of(
                "回合：" + roundManager.getCurrentRound() + "/5",
                "剩余时间：" + roundManager.getVoteCountdownRemaining() + "秒",
                "你的选择：" + formatVote(roundManager.getPlayerVote(player.getUniqueId()))
        ), true), null);
        session.setItem(40, createBackCard("返回房间", "返回房间状态页。"), event -> openRoomStatus(player));

        List<Map.Entry<Integer, StageTemplate>> choices = new ArrayList<>(roundManager.getNodeChoices().entrySet());
        choices.sort(Map.Entry.comparingByKey());
        int[] choiceSlots = {20, 22, 24};
        for (int index = 0; index < choices.size() && index < choiceSlots.length; index++) {
            Map.Entry<Integer, StageTemplate> entry = choices.get(index);
            int choiceIndex = entry.getKey();
            StageTemplate template = entry.getValue();
            session.setItem(choiceSlots[index], createFeatureCard(
                    materialForStage(template.stageType()),
                    "节点 " + choiceIndex + " " + stageLabel(template.stageType()),
                    List.of(
                            "地图：" + template.mapName(),
                            "关卡 ID：" + template.stageId(),
                            "奖励倾向：" + rewardFocus(template.stageType()),
                            "当前票数：" + roundManager.getVoteCount(choiceIndex),
                            "点击后为该路线投票。"
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
        GuiSession session = createFramedSession(GuiType.REWARD, MAIN_MENU_SIZE, "奖励选择", Material.AMETHYST_BLOCK);
        session.setItem(4, createFeatureCard(Material.AMETHYST_CLUSTER, "选择状态", List.of(
                "未完成玩家：" + roundManager.getPendingRewardPlayerCount(),
                "是否锁定：" + yesNo(roundManager.isRewardResolved(player.getUniqueId())),
                "当前阶段：" + roundManager.getCurrentRoomType()
        ), true), null);
        session.setItem(40, createBackCard("返回房间", "返回房间状态页。"), event -> openRoomStatus(player));

        List<RewardOption> rewardOptions = roundManager.getPendingRewardOptions(player.getUniqueId());
        int[] rewardSlots = {20, 22, 24};
        for (int index = 0; index < rewardOptions.size() && index < rewardSlots.length; index++) {
            RewardOption rewardOption = rewardOptions.get(index);
            int rewardIndex = index + 1;
            session.setItem(rewardSlots[index], createFeatureCard(
                    materialForReward(rewardOption),
                    rewardOption.name(),
                    List.of(
                            "类型：" + formatRewardType(rewardOption.rewardType()),
                            "稀有度：" + formatRarity(rewardOption.rarity()),
                            rewardOption.description(),
                            "点击后锁定这个奖励。"
                    ),
                    roundManager.isRewardResolved(player.getUniqueId())
            ), event -> {
                roundManager.handleRewardChoice(player, rewardIndex);
                successFeedback(player);
                refreshGame(game);
            });
        }

        if (roundManager.isShopRoom()) {
            session.setItem(31, createActionCard(Material.GOLD_INGOT, "打开商店", "查看当前商店服务。"), event -> openShopMenu(player));
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
        GuiSession session = createFramedSession(GuiType.SHOP, PAGE_SIZE, "商店补给", Material.YELLOW_STAINED_GLASS_PANE);

        session.setItem(4, createFeatureCard(Material.GOLD_BLOCK, "商店状态", List.of(
                "是否已购买：" + yesNo(roundManager.hasPurchasedShopService(player.getUniqueId())),
                "未选奖励：" + roundManager.getPendingRewardPlayerCount(),
                "准备进度：" + progressText(roundManager.getProceedReadyCount(), game.getActivePlayerCount())
        ), true), null);
        session.setItem(49, createBackCard("返回房间", "返回房间状态页。"), event -> openRoomStatus(player));

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
                                "商品 ID：" + serviceDefinition.id(),
                                purchased ? "当前房间已购买过该商品。" : "点击后购买该服务。"
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
            session.setItem(40, createActionCard(Material.AMETHYST_SHARD, "打开奖励界面", "返回奖励选择页。"), event -> openRewardMenu(player));
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
        GuiSession session = createFramedSession(GuiType.EVENT, MAIN_MENU_SIZE, "异常事件", Material.RED_STAINED_GLASS_PANE);

        List<String> introLore = new ArrayList<>();
        if (eventDefinition != null) {
            introLore.addAll(buildWrappedLore(eventDefinition.intro()));
        } else {
            introLore.add("当前没有读取到事件数据。");
        }
        introLore.add("剩余时间：" + roundManager.getReadyCountdownRemaining() + "秒");
        session.setItem(4, createFeatureCard(Material.WRITABLE_BOOK, "当前事件", introLore, true), null);
        session.setItem(40, createBackCard("返回房间", "返回房间状态页。"), event -> openRoomStatus(player));

        String optionOne = eventDefinition != null ? eventDefinition.optionOne() : "谨慎离开";
        String optionTwo = eventDefinition != null ? eventDefinition.optionTwo() : "继续调查";
        session.setItem(20, createFeatureCard(Material.LIME_WOOL, "选项一", List.of(
                optionOne,
                "更稳妥的默认路线。",
                "点击确认该选择。"
        ), false), event -> {
            roundManager.handleEventChoice(player, "1");
            successFeedback(player);
            refreshGame(game);
        });
        session.setItem(24, createFeatureCard(Material.ORANGE_WOOL, "选项二", List.of(
                optionTwo,
                "波动更大的路线。",
                "点击确认该选择。"
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
            case CLASS_SELECT -> openClassSelect(player);
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

        session.setItem(37, createFeatureCard(Material.LIME_DYE, "存活队员", buildPlayerListLore(activeNames, "当前没有存活玩家。"), false), null);
        session.setItem(38, createFeatureCard(Material.GRAY_DYE, "阵亡队员", buildPlayerListLore(deadNames, "当前没有阵亡玩家。"), false), null);
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
        itemMeta.setDisplayName("§6边狱终端");
        itemMeta.setLore(List.of(
                "§7右键打开整局流程主菜单。",
                "§8可查看房间、阶段、构筑与事件。"
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
                    "当前未加入任何房间。",
                    "请先创建房间或浏览房间列表。"
            );
        }

        RoundManager roundManager = game.getRoundManager();
        return List.of(
                "房间：" + game.getGameId(),
                "状态：" + game.getGameState(),
                "队伍：" + game.getActivePlayerCount() + " 存活 / " + game.getDeadPlayerCount() + " 阵亡",
                "回合：" + roundManager.getCurrentRound() + "/5",
                "阶段：" + roundManager.getCurrentRoomType()
        );
    }

    private List<String> buildBuildPreviewLore(Game game, Player player) {
        if (game == null) {
            return List.of(
                    "当前还没有构筑信息。",
                    "加入房间后开始记录本局成长。"
            );
        }

        List<String> summary = game.getBuildSummary(player.getUniqueId());
        List<String> preview = new ArrayList<>();
        for (int index = 0; index < summary.size() && index < 3; index++) {
            preview.add(summary.get(index));
        }
        if (summary.size() > 3) {
            preview.add("打开构筑页查看完整详情。");
        }
        return preview;
    }

    private List<String> buildStageActionLore(Game game) {
        if (game == null) {
            return List.of(
                    "当前没有活跃房间。",
                    "请先创建房间或加入房间。"
            );
        }

        RoundManager roundManager = game.getRoundManager();
        if (roundManager.isVotingPhase()) {
            return List.of(
                    "当前正在投票。",
                    "打开节点投票界面。"
            );
        }
        if (roundManager.isRewardPhase()) {
            return List.of(
                    "当前有待领取奖励。",
                    "打开奖励选择界面。"
            );
        }
        if (roundManager.isEventRoom() && !roundManager.isEventResolved()) {
            return List.of(
                    "当前事件尚未处理。",
                    "打开事件选择界面。"
            );
        }
        if (roundManager.isShopRoom()) {
            return List.of(
                    "当前可使用商店服务。",
                    "打开商店补给界面。"
            );
        }
        return List.of(
                "打开当前房间状态。",
                "用于查看当前进行中的阶段。"
        );
    }

    private List<String> buildObjectiveLore(Game game) {
        RoundManager roundManager = game.getRoundManager();
        List<String> lore = new ArrayList<>();
        lore.add("提示：" + roundManager.getStatusHint());
        lore.add("回合：" + roundManager.getCurrentRound() + "/5");
        lore.add("阶段键：" + roundManager.getCurrentRoomType());
        if (roundManager.isVotingPhase()) {
            lore.add("倒计时：" + roundManager.getVoteCountdownRemaining() + "秒");
        } else if (roundManager.isPeacefulRoom()) {
            lore.add("准备进度：" + progressText(roundManager.getProceedReadyCount(), game.getActivePlayerCount()));
            lore.add("倒计时：" + roundManager.getReadyCountdownRemaining() + "秒");
        }
        return lore;
    }

    private List<String> buildWrappedLore(String input) {
        if (input == null || input.isBlank()) {
            return List.of("暂无说明。");
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
        return "【" + formatStageType(stageType) + "】";
    }

    private String rewardFocus(String stageType) {
        return switch (stageType.toUpperCase()) {
            case "ELITE" -> "高品质奖励池";
            case "SHOP" -> "商店服务与奖励";
            case "REST" -> "恢复与辅助奖励";
            case "EVENT" -> "高风险高收益";
            case "BOSS" -> "大关卡结算奖励";
            default -> "标准战斗奖励";
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
        return value ? "是" : "否";
    }

    private String formatVote(int vote) {
        return vote > 0 ? "节点 " + vote : "未投票";
    }

    private String formatRewardType(RewardOption.RewardType rewardType) {
        return switch (rewardType) {
            case RELIC -> "饰品";
            case SERVICE -> "服务";
        };
    }

    private String formatRarity(String rarity) {
        return switch (rarity.toUpperCase()) {
            case "COMMON" -> "普通";
            case "UNCOMMON" -> "优秀";
            case "RARE" -> "稀有";
            default -> rarity;
        };
    }

    private String formatStageType(String stageType) {
        if (stageType == null) {
            return "未知";
        }
        return switch (stageType.toUpperCase()) {
            case "NORMAL" -> "普通战";
            case "ELITE" -> "精英战";
            case "SHOP" -> "商店";
            case "REST" -> "休息";
            case "EVENT" -> "事件";
            case "BOSS" -> "Boss";
            default -> stageType;
        };
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
