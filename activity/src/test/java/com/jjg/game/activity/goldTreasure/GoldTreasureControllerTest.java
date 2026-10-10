package com.jjg.game.activity.goldTreasure;

import com.jjg.game.activity.activitylog.ActivityLogger;
import com.jjg.game.activity.common.dao.PlayerActivityDao;
import com.jjg.game.activity.common.data.ActivityData;
import com.jjg.game.activity.common.data.ActivityType;
import com.jjg.game.activity.common.data.PlayerActivityData;
import com.jjg.game.activity.constant.ActivityConstant;
import com.jjg.game.activity.goldTreasure.message.GoldTreasureDetailInfo;
import com.jjg.game.activity.goldTreasure.message.ResGoldTreasure;
import com.jjg.game.activity.manager.ActivityManager;
import com.jjg.game.core.constant.AddType;
import com.jjg.game.core.constant.Code;
import com.jjg.game.core.data.ChannelType;
import com.jjg.game.core.data.CommonResult;
import com.jjg.game.core.data.ItemOperationResult;
import com.jjg.game.core.data.Mail;
import com.jjg.game.core.data.Order;
import com.jjg.game.core.data.Player;
import com.jjg.game.core.data.PlayerPack;
import com.jjg.game.core.manager.ConditionManager;
import com.jjg.game.core.pb.RechargeType;
import com.jjg.game.core.pb.ReqGenerateOrder;
import com.jjg.game.core.service.CorePlayerService;
import com.jjg.game.core.service.GameFunctionService;
import com.jjg.game.core.service.MailService;
import com.jjg.game.core.service.PlayerPackService;
import com.jjg.game.sampledata.GameDataManager;
import com.jjg.game.sampledata.bean.GameFunctionCfg;
import com.jjg.game.sampledata.bean.GoldTreasureCfg;
import com.jjg.game.sampledata.bean.ShopRechargeListCfg;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GoldTreasureControllerTest {
    private static final long PLAYER_ID = 101L;
    private static final long ACTIVITY_ID = 57017L;
    private static final long ROUND = 3L;
    private static final int FREE = 1001;
    private static final int PAID = 1002;
    private static final int NEXT = 1003;
    private static final int CUMULATIVE = 1004;
    private static final int OTHER_CUMULATIVE = 1005;
    private static final int OUTSIDE = 9001;
    private static final int PAY_ID = 77;
    private static final int STAR_ITEM = 1022511;
    private static final Map<Integer, Long> REWARDS = Map.of(120001, 10L);
    private static final Map<Integer, Long> CONDITION = Map.of(STAR_ITEM, 3L);
    private static final BigDecimal PRICE = new BigDecimal("1.25");

    private final PlayerActivityDao dao = mock(PlayerActivityDao.class);
    private final ActivityManager activities = mock(ActivityManager.class);
    private final PlayerPackService pack = mock(PlayerPackService.class);
    private final GameFunctionService functions = mock(GameFunctionService.class);
    private final CorePlayerService players = mock(CorePlayerService.class);
    private final ConditionManager conditions = mock(ConditionManager.class);
    private final MailService mails = mock(MailService.class);
    private final PlayerPack playerPack = mock(PlayerPack.class);
    private final GameFunctionCfg functionCfg = mock(GameFunctionCfg.class);
    private final Player player = new Player();
    private final ActivityData activity = new ActivityData();
    private final Map<Integer, PlayerActivityData> saved = new HashMap<>();
    private final Map<Integer, GoldTreasureCfg> cfgs = new LinkedHashMap<>();
    private MockedStatic<GameDataManager> gameData;
    private TestController controller;

    @BeforeEach
    void setUp() {
        player.setId(PLAYER_ID);
        player.setChannel(ChannelType.APPLE);
        activity.setId(ACTIVITY_ID);
        activity.setType(ActivityType.GOLD_TREASURE);
        activity.setRound(ROUND);
        activity.setOpen(true);
        activity.setStatus(ActivityConstant.ActivityStatus.RUNNING);
        activity.setTimeStart(System.currentTimeMillis() - 60_000);
        activity.setTimeEnd(System.currentTimeMillis() + 600_000);
        activity.setCondition("");
        activity.setValue(List.of(FREE, PAID, NEXT, CUMULATIVE, OTHER_CUMULATIVE));
        addConfig(FREE, 1, 0, 0, Map.of());
        addConfig(PAID, 1, FREE, PAY_ID, Map.of());
        addConfig(NEXT, 1, PAID, 0, Map.of());
        addConfig(CUMULATIVE, 2, 0, 0, CONDITION);
        addConfig(OTHER_CUMULATIVE, 2, 0, 0, Map.of(STAR_ITEM, 6L));
        addConfig(OUTSIDE, 1, 0, PAY_ID, Map.of());

        gameData = mockStatic(GameDataManager.class);
        gameData.when(GameDataManager::getGoldTreasureCfgMap).thenReturn(cfgs);
        gameData.when(GameDataManager::getGoldTreasureCfgList).thenReturn(List.copyOf(cfgs.values()));
        gameData.when(() -> GameDataManager.getGoldTreasureCfg(anyInt()))
                .thenAnswer(invocation -> cfgs.get(invocation.getArgument(0)));
        when(functionCfg.getId()).thenReturn(5018);
        when(functionCfg.getIsOpen()).thenReturn(true);
        when(functionCfg.getFunctionType()).thenReturn(17);
        when(functionCfg.getCondition()).thenReturn("");
        gameData.when(() -> GameDataManager.getGameFunctionCfg(5018)).thenReturn(functionCfg);
        ShopRechargeListCfg shopCfg = mock(ShopRechargeListCfg.class);
        when(shopCfg.getId()).thenReturn(PAY_ID);
        when(shopCfg.getPrice()).thenReturn(PRICE);
        when(shopCfg.getIosShopId()).thenReturn("gold.apple");
        when(shopCfg.getGoogleShopId()).thenReturn("gold.google");
        gameData.when(() -> GameDataManager.getShopRechargeListCfg(PAY_ID)).thenReturn(shopCfg);

        when(activities.getOpenActivityData(player, ActivityType.GOLD_TREASURE)).thenReturn(activity);
        when(activities.getActivityData()).thenReturn(Map.of(ACTIVITY_ID, activity));
        when(activities.getActivityTypeData()).thenReturn(Map.of(ActivityType.GOLD_TREASURE, Map.of(ACTIVITY_ID, activity)));
        when(activities.playerCanJoinActivity(activity, player)).thenAnswer(invocation -> activity.canRun());
        when(players.get(PLAYER_ID)).thenReturn(player);
        when(conditions.isAchievement(eq(player), anyString(), any(), anyString())).thenReturn(true);
        when(conditions.isAchievement(eq(player), anyString(), anyString())).thenReturn(true);
        when(pack.addItems(eq(PLAYER_ID), anyMap(), eq(AddType.ACTIVITY_GOLD_TREASURE_REWARDS)))
                .thenReturn(new CommonResult<>(Code.SUCCESS, new ItemOperationResult()));
        when(pack.checkHasItems(player, CONDITION)).thenReturn(true);
        when(pack.findSatisfiedItemRequirements(eq(player), anyList())).thenAnswer(invocation ->
                ((List<?>) invocation.getArgument(1)).isEmpty() ? Set.of() : Set.of(0));
        when(pack.getFromAllDB(PLAYER_ID)).thenReturn(playerPack);
        when(pack.removeItems(eq(player), anyMap(), eq(AddType.ACTIVITY_GOLD_TREASURE_REWARDS)))
                .thenReturn(new CommonResult<>(Code.SUCCESS, new ItemOperationResult()));
        when(mails.addCfgMail(eq(PLAYER_ID), eq(50), anyList(),
                eq(AddType.ACTIVITY_GOLD_TREASURE_REWARDS), isNull())).thenReturn(mock(Mail.class));
        when(dao.<PlayerActivityData>getPlayerActivityDataOrThrow(PLAYER_ID, ActivityType.GOLD_TREASURE, ACTIVITY_ID))
                .thenAnswer(invocation -> snapshot(saved));
        doAnswer(invocation -> {
            BiPredicate<Long, Map<Integer, PlayerActivityData>> beforeClear = invocation.getArgument(2);
            if (beforeClear.test(PLAYER_ID, snapshot(saved))) {
                saved.clear();
            }
            return null;
        }).when(dao).clearActivityData(eq(ActivityType.GOLD_TREASURE), eq(ACTIVITY_ID), any());
        when(dao.savePlayerActivityData(eq(PLAYER_ID), eq(ActivityType.GOLD_TREASURE), eq(ACTIVITY_ID), anyMap()))
                .thenAnswer(invocation -> {
                    Map<Integer, PlayerActivityData> data = invocation.getArgument(3);
                    saved.clear();
                    saved.putAll(snapshot(data));
                    return true;
                });
        controller = new TestController(dao, activities, pack, functions, players, conditions, mails);
    }

    @AfterEach
    void closeStaticMock() {
        gameData.close();
    }

    @Test
    void detailsUseOnlyActivityValueAndExposeConfiguredPaymentAndConditions() {
        ResGoldTreasure response = info();
        assertEquals(Code.SUCCESS, response.code);
        assertEquals(List.of(FREE, PAID, NEXT, CUMULATIVE, OTHER_CUMULATIVE),
                response.activityData.stream().map(detail -> detail.detailId).toList());
        GoldTreasureDetailInfo paid = detail(response, PAID);
        assertEquals(PAY_ID, paid.payId);
        assertEquals(PRICE.toPlainString(), paid.price);
        assertEquals("gold.apple", paid.channelProductId);
        assertEquals(1, paid.rewardType);
        assertEquals(10L, paid.rewardItems.getFirst().count);
        assertEquals(2, detail(response, CUMULATIVE).rewardType);
        assertEquals(3L, detail(response, CUMULATIVE).condition.getFirst().count);
        assertEquals(activity.getTimeEnd(), response.endTime);
    }

    @Test
    void informationChecksAllCumulativeConditionsInOneBackpackRead() {
        ResGoldTreasure response = info();
        assertEquals(ActivityConstant.ClaimStatus.CAN_CLAIM, detail(response, CUMULATIVE).claimStatus);
        assertEquals(ActivityConstant.ClaimStatus.NOT_CLAIM, detail(response, OTHER_CUMULATIVE).claimStatus);
        verify(pack).findSatisfiedItemRequirements(eq(player), argThat(requirements ->
                requirements.equals(List.of(CONDITION, Map.of(STAR_ITEM, 6L)))));
        verify(pack, never()).checkHasItems(any(Player.class), anyMap());
        verify(dao, never()).savePlayerActivityData(anyLong(), any(), anyLong(), anyMap());
    }

    @Test
    void firstFreeRewardClaimsOnceAndUnlocksPaidNode() {
        assertEquals(Code.SUCCESS, controller.claimActivityRewards(player, activity, FREE).code);
        assertEquals(ActivityConstant.ClaimStatus.CLAIMED, saved.get(FREE).getClaimStatus());
        assertEquals(Code.REPEAT_OP, controller.claimActivityRewards(player, activity, FREE).code);
        verify(pack, times(1)).addItems(PLAYER_ID, REWARDS, AddType.ACTIVITY_GOLD_TREASURE_REWARDS);
        assertTrue(controller.generateOrderDetailInfo(player, request(PAID)).success());
    }

    @Test
    void paidNodeCannotBeOrderedOrClaimedBeforePreviousRewardIsClaimed() {
        assertFalse(controller.generateOrderDetailInfo(player, request(PAID)).success());
        assertNotEquals(Code.SUCCESS, controller.claimActivityRewards(player, activity, PAID).code);
        saved.put(PAID, data(ROUND, ActivityConstant.ClaimStatus.CAN_CLAIM));
        assertNotEquals(Code.SUCCESS, controller.claimActivityRewards(player, activity, PAID).code);
        verify(pack, never()).addItems(anyLong(), anyMap(), any(AddType.class));
    }

    @Test
    void nextNodeRemainsLockedUntilPaidRewardIsClaimed() {
        saved.put(FREE, data(ROUND, ActivityConstant.ClaimStatus.CLAIMED));
        saved.put(PAID, data(ROUND, ActivityConstant.ClaimStatus.CAN_CLAIM));
        assertNotEquals(Code.SUCCESS, controller.claimActivityRewards(player, activity, NEXT).code);
        assertEquals(Code.SUCCESS, controller.claimActivityRewards(player, activity, PAID).code);
        assertEquals(Code.SUCCESS, controller.claimActivityRewards(player, activity, NEXT).code);
    }

    @Test
    void paidRewardRequiresPaymentAndCannotBeOrderedAgainAfterPaymentOrClaim() {
        saved.put(FREE, data(ROUND, ActivityConstant.ClaimStatus.CLAIMED));
        assertNotEquals(Code.SUCCESS, controller.claimActivityRewards(player, activity, PAID).code);
        saved.put(PAID, data(ROUND, ActivityConstant.ClaimStatus.CAN_CLAIM));
        assertFalse(controller.generateOrderDetailInfo(player, request(PAID)).success());
        assertEquals(Code.SUCCESS, controller.claimActivityRewards(player, activity, PAID).code);
        assertFalse(controller.generateOrderDetailInfo(player, request(PAID)).success());
        assertEquals(Code.REPEAT_OP, controller.claimActivityRewards(player, activity, PAID).code);
        verify(pack, times(1)).addItems(PLAYER_ID, REWARDS, AddType.ACTIVITY_GOLD_TREASURE_REWARDS);
    }

    @Test
    void cumulativeRewardUsesCurrentInventoryWithoutConsumingConditionItems() {
        assertEquals(Code.SUCCESS, controller.claimActivityRewards(player, activity, CUMULATIVE).code);
        verify(pack).checkHasItems(player, CONDITION);
        verify(pack).addItems(PLAYER_ID, REWARDS, AddType.ACTIVITY_GOLD_TREASURE_REWARDS);
        verifyNoMoreInteractionsAfterRewardChecks();
    }

    @Test
    void cumulativeRewardRejectsInsufficientInventory() {
        when(pack.checkHasItems(player, CONDITION)).thenReturn(false);
        assertNotEquals(Code.SUCCESS, controller.claimActivityRewards(player, activity, CUMULATIVE).code);
        assertTrue(saved.isEmpty());
        verify(pack, never()).addItems(anyLong(), anyMap(), any(AddType.class));
    }

    @Test
    void oldRoundPaymentAndClaimsDoNotUnlockCurrentRound() {
        saved.put(FREE, data(ROUND - 1, ActivityConstant.ClaimStatus.CLAIMED));
        saved.put(PAID, data(ROUND - 1, ActivityConstant.ClaimStatus.CAN_CLAIM));
        assertFalse(controller.generateOrderDetailInfo(player, request(PAID)).success());
        assertNotEquals(Code.SUCCESS, controller.claimActivityRewards(player, activity, PAID).code);
        assertEquals(Code.SUCCESS, controller.claimActivityRewards(player, activity, FREE).code);
        assertEquals(ROUND, saved.get(FREE).getRound());
        assertNotEquals(Code.SUCCESS, controller.claimActivityRewards(player, activity, PAID).code);
    }

    @Test
    void configurationOutsideThisActivityCannotBePurchasedOrClaimed() {
        assertFalse(controller.generateOrderDetailInfo(player, request(OUTSIDE)).success());
        assertNotEquals(Code.SUCCESS, controller.claimActivityRewards(player, activity, OUTSIDE).code);
        assertFalse(controller.getDetailCfgBean(activity).containsKey(OUTSIDE));
        verify(pack, never()).addItems(anyLong(), anyMap(), any(AddType.class));
    }

    @Test
    void orderUsesShopPriceAndKeepsDetailIdAsProductId() {
        saved.put(FREE, data(ROUND, ActivityConstant.ClaimStatus.CLAIMED));
        ReqGenerateOrder request = request(PAID);
        CommonResult<BigDecimal> result = controller.generateOrderDetailInfo(player, request);
        assertTrue(result.success());
        assertEquals(PRICE, result.data);
        assertEquals(String.valueOf(PAID), request.productId);
        verify(pack, never()).addItems(anyLong(), anyMap(), any(AddType.class));
        verify(dao, never()).savePlayerActivityData(anyLong(), any(), anyLong(), anyMap());
    }

    @Test
    void invalidAndUnboundClientProductIdsCannotGenerateOrders() {
        ReqGenerateOrder request = request(PAID);
        request.productId = ACTIVITY_ID + "_" + ROUND + "_" + PAID;
        assertFalse(controller.generateOrderDetailInfo(player, request).success());
        request.productId = "invalid";
        assertFalse(controller.generateOrderDetailInfo(player, request).success());
        assertFalse(controller.generateOrderDetailInfo(player, request(FREE)).success());
    }

    @Test
    void paymentCallbackPersistsPaymentWithoutGrantingAndIsIdempotent() {
        saved.put(FREE, data(ROUND, ActivityConstant.ClaimStatus.CLAIMED));
        Order order = order(PAID);
        assertTrue(controller.onReceivedRecharge(player, order));
        assertEquals(ActivityConstant.ClaimStatus.CAN_CLAIM, saved.get(PAID).getClaimStatus());
        assertTrue(controller.onReceivedRecharge(player, order));
        saved.put(PAID, data(ROUND, ActivityConstant.ClaimStatus.CLAIMED));
        assertTrue(controller.onReceivedRecharge(player, order));
        verify(dao, times(1)).savePlayerActivityData(anyLong(), any(), anyLong(), anyMap());
        verify(pack, never()).addItems(anyLong(), anyMap(), any(AddType.class));
    }

    @Test
    void callbacksRejectUnavailableActivityInvalidDetailAndWrongPlayer() {
        saved.put(FREE, data(ROUND, ActivityConstant.ClaimStatus.CLAIMED));
        assertFalse(controller.onReceivedRecharge(player, order(OUTSIDE)));
        Order wrongPlayer = order(PAID);
        wrongPlayer.setPlayerId(PLAYER_ID + 1);
        assertFalse(controller.onReceivedRecharge(player, wrongPlayer));
        when(activities.getOpenActivityData(player, ActivityType.GOLD_TREASURE)).thenReturn(null);
        assertFalse(controller.onReceivedRecharge(player, order(PAID)));
        verify(dao, never()).savePlayerActivityData(anyLong(), any(), anyLong(), anyMap());
    }

    @Test
    void disabledFunctionBlocksQueriesOrdersAndClaims() {
        when(functionCfg.getIsOpen()).thenReturn(false);
        assertTrue(info().activityData.isEmpty());
        assertFalse(controller.generateOrderDetailInfo(player, request(PAID)).success());
        assertNotEquals(Code.SUCCESS, controller.claimActivityRewards(player, activity, FREE).code);
        verify(pack, never()).addItems(anyLong(), anyMap(), any(AddType.class));
    }

    @Test
    void expiredActivityCannotCreateOrdersOrGrantRewards() {
        activity.setStatus(ActivityConstant.ActivityStatus.ENDED);
        activity.setTimeEnd(System.currentTimeMillis() - 1);
        when(activities.getOpenActivityData(player, ActivityType.GOLD_TREASURE)).thenReturn(null);
        assertTrue(info().activityData.isEmpty());
        assertFalse(controller.generateOrderDetailInfo(player, request(PAID)).success());
        assertNotEquals(Code.SUCCESS, controller.claimActivityRewards(player, activity, FREE).code);
        verify(pack, never()).addItems(anyLong(), anyMap(), any(AddType.class));
    }

    @Test
    void storageReadFailureCannotBeMistakenForAnUnclaimedReward() {
        when(dao.getPlayerActivityDataOrThrow(PLAYER_ID, ActivityType.GOLD_TREASURE, ACTIVITY_ID))
                .thenThrow(new IllegalStateException("redis unavailable"));
        assertNotEquals(Code.SUCCESS, controller.claimActivityRewards(player, activity, FREE).code);
        assertFalse(controller.generateOrderDetailInfo(player, request(PAID)).success());
        assertFalse(controller.onReceivedRecharge(player, order(PAID)));
        verify(pack, never()).addItems(anyLong(), anyMap(), any(AddType.class));
    }

    @Test
    void claimStateWriteFailurePreventsRewardGrant() {
        when(dao.savePlayerActivityData(eq(PLAYER_ID), eq(ActivityType.GOLD_TREASURE), eq(ACTIVITY_ID), anyMap()))
                .thenReturn(false);
        assertNotEquals(Code.SUCCESS, controller.claimActivityRewards(player, activity, FREE).code);
        assertTrue(saved.isEmpty());
        verify(pack, never()).addItems(anyLong(), anyMap(), any(AddType.class));
    }

    @Test
    void paymentStateWriteFailureReportsCallbackFailure() {
        saved.put(FREE, data(ROUND, ActivityConstant.ClaimStatus.CLAIMED));
        when(dao.savePlayerActivityData(eq(PLAYER_ID), eq(ActivityType.GOLD_TREASURE), eq(ACTIVITY_ID), anyMap()))
                .thenReturn(false);
        assertFalse(controller.onReceivedRecharge(player, order(PAID)));
        assertFalse(saved.containsKey(PAID));
    }

    @Test
    void rewardGrantFailureRestoresPaymentStateSoClaimCanRetry() {
        saved.put(FREE, data(ROUND, ActivityConstant.ClaimStatus.CLAIMED));
        saved.put(PAID, data(ROUND, ActivityConstant.ClaimStatus.CAN_CLAIM));
        when(pack.addItems(PLAYER_ID, REWARDS, AddType.ACTIVITY_GOLD_TREASURE_REWARDS))
                .thenReturn(new CommonResult<>(Code.FAIL), new CommonResult<>(Code.SUCCESS, new ItemOperationResult()));
        assertEquals(Code.FAIL, controller.claimActivityRewards(player, activity, PAID).code);
        assertEquals(ActivityConstant.ClaimStatus.CAN_CLAIM, saved.get(PAID).getClaimStatus());
        assertEquals(Code.SUCCESS, controller.claimActivityRewards(player, activity, PAID).code);
        assertEquals(ActivityConstant.ClaimStatus.CLAIMED, saved.get(PAID).getClaimStatus());
    }

    @Test
    void rewardStateIsDurableBeforeTheRewardGrant() {
        when(pack.addItems(PLAYER_ID, REWARDS, AddType.ACTIVITY_GOLD_TREASURE_REWARDS))
                .thenAnswer(invocation -> {
                    assertEquals(ActivityConstant.ClaimStatus.CLAIMED, saved.get(FREE).getClaimStatus());
                    return new CommonResult<>(Code.SUCCESS, new ItemOperationResult());
                });
        assertEquals(Code.SUCCESS, controller.claimActivityRewards(player, activity, FREE).code);
    }

    @Test
    void activityEndMailsOnlyUnlockedPaidRewardsAndFiltersStarsBeforeClearing() {
        saved.put(FREE, data(ROUND, ActivityConstant.ClaimStatus.CLAIMED));
        saved.put(PAID, data(ROUND, ActivityConstant.ClaimStatus.CAN_CLAIM));
        when(cfgs.get(PAID).getGetItem()).thenReturn(Map.of(STAR_ITEM, 3L, 120001, 10L, 120003, 2L));
        when(playerPack.getItemCount(STAR_ITEM)).thenReturn(7L);
        addConfig(OUTSIDE, 2, 0, 0, Map.of(1022599, 1L));
        when(playerPack.getItemCount(1022599)).thenReturn(99L);

        controller.onActivityEnd(activity);

        verify(mails).addCfgMail(eq(PLAYER_ID), eq(50), argThat(items ->
                        items.size() == 2 && items.stream().noneMatch(item -> item.getId() == STAR_ITEM)
                                && items.stream().anyMatch(item -> item.getId() == 120001 && item.getItemCount() == 10L)
                                && items.stream().anyMatch(item -> item.getId() == 120003 && item.getItemCount() == 2L)),
                eq(AddType.ACTIVITY_GOLD_TREASURE_REWARDS), isNull());
        verifyNoMoreInteractions(mails);
        verify(pack).removeItems(player, Map.of(STAR_ITEM, 7L), AddType.ACTIVITY_GOLD_TREASURE_REWARDS);
        verify(pack).getFromAllDB(PLAYER_ID);
        verify(pack, never()).findSatisfiedItemRequirements(any(), anyList());
        assertTrue(saved.isEmpty());
    }

    @Test
    void activityEndDoesNotMailAlreadyClaimedRewards() {
        for (int id : activity.getValue()) {
            saved.put(id, data(ROUND, ActivityConstant.ClaimStatus.CLAIMED));
        }
        when(playerPack.getItemCount(STAR_ITEM)).thenReturn(7L);
        controller.onActivityEnd(activity);
        verifyNoInteractions(mails);
        verify(pack).removeItems(player, Map.of(STAR_ITEM, 7L), AddType.ACTIVITY_GOLD_TREASURE_REWARDS);
        assertTrue(saved.isEmpty());
    }

    @Test
    void activityEndChecksCumulativeConditionsAgainstLoadedBackpack() {
        saved.put(FREE, data(ROUND, ActivityConstant.ClaimStatus.CLAIMED));
        when(playerPack.checkHasItems(anyList())).thenAnswer(invocation -> {
            List<com.jjg.game.core.data.Item> requirements = invocation.getArgument(0);
            return requirements.getFirst().getItemCount() <= 3;
        });
        when(playerPack.getItemCount(STAR_ITEM)).thenReturn(3L);
        controller.onActivityEnd(activity);
        verify(mails).addCfgMail(eq(PLAYER_ID), eq(50), anyList(),
                eq(AddType.ACTIVITY_GOLD_TREASURE_REWARDS), isNull());
        verifyNoMoreInteractions(mails);
        verify(pack).getFromAllDB(PLAYER_ID);
        verify(pack, never()).findSatisfiedItemRequirements(any(), anyList());
        verify(pack, never()).checkHasItems(any(Player.class), anyMap());
    }

    @Test
    void activityEndCleanupFailureRetainsClaimedRewards() {
        saved.put(FREE, data(ROUND, ActivityConstant.ClaimStatus.CLAIMED));
        saved.put(PAID, data(ROUND, ActivityConstant.ClaimStatus.CAN_CLAIM));
        when(playerPack.getItemCount(STAR_ITEM)).thenReturn(7L);
        when(pack.removeItems(player, Map.of(STAR_ITEM, 7L), AddType.ACTIVITY_GOLD_TREASURE_REWARDS))
                .thenReturn(new CommonResult<>(Code.FAIL));
        controller.onActivityEnd(activity);
        assertEquals(ActivityConstant.ClaimStatus.CLAIMED, saved.get(PAID).getClaimStatus());
        verify(mails).addCfgMail(eq(PLAYER_ID), eq(50), anyList(),
                eq(AddType.ACTIVITY_GOLD_TREASURE_REWARDS), isNull());
        verify(pack).removeItems(player, Map.of(STAR_ITEM, 7L), AddType.ACTIVITY_GOLD_TREASURE_REWARDS);
    }

    @Test
    void activityEndMailFailureRetainsDataAndDoesNotClearStars() {
        saved.put(FREE, data(ROUND, ActivityConstant.ClaimStatus.CLAIMED));
        saved.put(PAID, data(ROUND, ActivityConstant.ClaimStatus.CAN_CLAIM));
        when(playerPack.getItemCount(STAR_ITEM)).thenReturn(7L);
        when(mails.addCfgMail(eq(PLAYER_ID), eq(50), anyList(),
                eq(AddType.ACTIVITY_GOLD_TREASURE_REWARDS), isNull())).thenReturn(null);
        controller.onActivityEnd(activity);
        assertEquals(ActivityConstant.ClaimStatus.CAN_CLAIM, saved.get(PAID).getClaimStatus());
        verify(pack, never()).removeItems(any(Player.class), anyMap(), any(AddType.class));
    }

    @Test
    void activityEndBackpackReadFailureRetainsRewardsAndStars() {
        saved.put(FREE, data(ROUND, ActivityConstant.ClaimStatus.CLAIMED));
        saved.put(PAID, data(ROUND, ActivityConstant.ClaimStatus.CAN_CLAIM));
        when(pack.getFromAllDB(PLAYER_ID)).thenThrow(new IllegalStateException("redis unavailable"));
        assertThrows(IllegalStateException.class, () -> controller.onActivityEnd(activity));
        assertEquals(ActivityConstant.ClaimStatus.CAN_CLAIM, saved.get(PAID).getClaimStatus());
        verifyNoInteractions(mails);
        verify(pack, never()).removeItems(any(Player.class), anyMap(), any(AddType.class));
    }

    @Test
    void activityEndDoesNotAutoClaimTheEntireUnlockedChain() {
        saved.put(CUMULATIVE, data(ROUND, ActivityConstant.ClaimStatus.CLAIMED));
        saved.put(OTHER_CUMULATIVE, data(ROUND, ActivityConstant.ClaimStatus.CLAIMED));
        controller.onActivityEnd(activity);
        verify(mails).addCfgMail(eq(PLAYER_ID), eq(50), anyList(),
                eq(AddType.ACTIVITY_GOLD_TREASURE_REWARDS), isNull());
        verifyNoMoreInteractions(mails);
    }

    @Test
    void queryingDetailsAndRedDotsDoesNotSettleOldRoundsOrWriteData() {
        saved.put(FREE, data(ROUND - 1, ActivityConstant.ClaimStatus.CLAIMED));
        saved.put(PAID, data(ROUND - 1, ActivityConstant.ClaimStatus.CAN_CLAIM));
        assertEquals(Code.SUCCESS, info().code);
        assertTrue(controller.hasRedDot(PLAYER_ID, activity));
        assertEquals(ROUND - 1, saved.get(PAID).getRound());
        verifyNoInteractions(mails);
        verify(dao, never()).savePlayerActivityData(anyLong(), any(), anyLong(), anyMap());
        verify(pack, never()).removeItems(any(Player.class), anyMap(), any(AddType.class));
    }

    private ResGoldTreasure info() {
        return (ResGoldTreasure) controller.getPlayerActivityInfoByType(player, ActivityType.GOLD_TREASURE);
    }

    private GoldTreasureDetailInfo detail(ResGoldTreasure response, int detailId) {
        return response.activityData.stream().filter(info -> info.detailId == detailId).findFirst().orElseThrow();
    }

    private void verifyNoMoreInteractionsAfterRewardChecks() {
        verify(pack, atMostOnce()).findSatisfiedItemRequirements(eq(player), anyList());
        verifyNoMoreInteractions(pack);
    }

    private ReqGenerateOrder request(int detailId) {
        ReqGenerateOrder request = new ReqGenerateOrder();
        request.productId = String.valueOf(detailId);
        request.rechargeType = RechargeType.GOLD_TREASURE.getType();
        request.payType = ChannelType.APPLE.getValue();
        return request;
    }

    private Order order(int detailId) {
        Order order = new Order();
        order.setId("order-101");
        order.setPlayerId(PLAYER_ID);
        order.setProductId(String.valueOf(detailId));
        order.setRechargeType(RechargeType.GOLD_TREASURE);
        order.setPrice(PRICE);
        order.setChannelProductId("gold.apple");
        return order;
    }

    private PlayerActivityData data(long round, int claimStatus) {
        PlayerActivityData data = new PlayerActivityData(ACTIVITY_ID, round);
        data.setClaimStatus(claimStatus);
        return data;
    }

    private Map<Integer, PlayerActivityData> snapshot(Map<Integer, PlayerActivityData> source) {
        Map<Integer, PlayerActivityData> snapshot = new HashMap<>();
        source.forEach((id, data) -> snapshot.put(id, data(data.getRound(), data.getClaimStatus())));
        return snapshot;
    }

    private void addConfig(int id, int rewardType, int previousId, int payId, Map<Integer, Long> condition) {
        GoldTreasureCfg cfg = mock(GoldTreasureCfg.class);
        when(cfg.getId()).thenReturn(id);
        when(cfg.getRewardType()).thenReturn(rewardType);
        when(cfg.getPreviousId()).thenReturn(previousId);
        when(cfg.getPayId()).thenReturn(payId);
        when(cfg.getGetItem()).thenReturn(REWARDS);
        when(cfg.getCondition()).thenReturn(condition);
        cfgs.put(id, cfg);
    }

    private static class TestController extends GoldTreasureController {
        TestController(PlayerActivityDao dao, ActivityManager activities, PlayerPackService pack,
                       GameFunctionService functions, CorePlayerService players, ConditionManager conditions,
                       MailService mails) {
            super(mails);
            this.playerActivityDao = dao;
            this.activityManager = activities;
            this.playerPackService = pack;
            this.gameFunctionService = functions;
            this.corePlayerService = players;
            this.conditionManager = conditions;
            this.activityLogger = mock(ActivityLogger.class);
        }
    }
}
