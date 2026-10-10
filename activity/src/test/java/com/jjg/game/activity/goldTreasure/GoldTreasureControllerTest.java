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
import com.jjg.game.core.data.Item;
import com.jjg.game.core.data.ItemOperationResult;
import com.jjg.game.core.data.Mail;
import com.jjg.game.core.data.Order;
import com.jjg.game.core.data.Player;
import com.jjg.game.core.data.PlayerPack;
import com.jjg.game.core.data.PreGenerateOrderData;
import com.jjg.game.core.manager.ConditionManager;
import com.jjg.game.core.pb.RechargeCfgType;
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
    private static final int EXCHANGE = 1004;
    private static final int AFTER_EXCHANGE = 1005;
    private static final int OUTSIDE = 9001;
    private static final int FREE_PAY_ID = 76;
    private static final int CASH_PAY_ID = 77;
    private static final int EXCHANGE_PAY_ID = 78;
    private static final int STAR_ITEM = 1022511;
    private static final Map<Integer, Long> REWARDS = Map.of(120001, 10L);
    private static final Map<Integer, Long> REDEEM = Map.of(STAR_ITEM, 3L);
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
    private final Map<Integer, ShopRechargeListCfg> shops = new HashMap<>();
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
        activity.setValue(List.of(EXCHANGE, AFTER_EXCHANGE, NEXT, PAID, FREE));
        addConfig(FREE, 1, FREE_PAY_ID);
        addConfig(OUTSIDE, 1, CASH_PAY_ID);
        addConfig(PAID, 1, CASH_PAY_ID);
        addConfig(NEXT, 1, FREE_PAY_ID);
        addConfig(EXCHANGE, 2, EXCHANGE_PAY_ID);
        addConfig(AFTER_EXCHANGE, 1, FREE_PAY_ID);
        addShop(FREE_PAY_ID, 4, null, Map.of());
        addShop(CASH_PAY_ID, 1, PRICE, Map.of());
        addShop(EXCHANGE_PAY_ID, 2, null, REDEEM);

        gameData = mockStatic(GameDataManager.class);
        gameData.when(GameDataManager::getGoldTreasureCfgMap).thenReturn(cfgs);
        gameData.when(GameDataManager::getGoldTreasureCfgList).thenReturn(List.copyOf(cfgs.values()));
        gameData.when(() -> GameDataManager.getGoldTreasureCfg(anyInt()))
                .thenAnswer(invocation -> cfgs.get(invocation.getArgument(0)));
        gameData.when(() -> GameDataManager.getShopRechargeListCfg(anyInt()))
                .thenAnswer(invocation -> shops.get(invocation.getArgument(0)));
        when(functionCfg.getId()).thenReturn(5018);
        when(functionCfg.getIsOpen()).thenReturn(true);
        when(functionCfg.getFunctionType()).thenReturn(17);
        when(functionCfg.getCondition()).thenReturn("");
        gameData.when(() -> GameDataManager.getGameFunctionCfg(5018)).thenReturn(functionCfg);

        when(activities.getOpenActivityData(player, ActivityType.GOLD_TREASURE)).thenReturn(activity);
        when(activities.getActivityData()).thenReturn(Map.of(ACTIVITY_ID, activity));
        when(activities.getActivityTypeData()).thenReturn(Map.of(ActivityType.GOLD_TREASURE, Map.of(ACTIVITY_ID, activity)));
        when(activities.playerCanJoinActivity(activity, player)).thenAnswer(invocation -> activity.canRun());
        when(players.get(PLAYER_ID)).thenReturn(player);
        when(conditions.isAchievement(eq(player), anyString(), any(), anyString())).thenReturn(true);
        when(conditions.isAchievement(eq(player), anyString(), anyString())).thenReturn(true);
        when(pack.addItems(eq(PLAYER_ID), anyMap(), eq(AddType.ACTIVITY_GOLD_TREASURE_REWARDS)))
                .thenReturn(new CommonResult<>(Code.SUCCESS, new ItemOperationResult()));
        when(pack.useItem(eq(player), eq(STAR_ITEM), eq(3L), eq(REWARDS), eq(AddType.ACTIVITY_GOLD_TREASURE_REWARDS)))
                .thenReturn(new CommonResult<>(Code.SUCCESS, new ItemOperationResult()));
        when(pack.checkHasItems(player, REDEEM)).thenReturn(true);
        when(pack.getFromAllDB(PLAYER_ID)).thenReturn(playerPack);
        when(playerPack.checkHasItems(anyList())).thenReturn(true);
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
    void informationPreservesConfigListOrderAndMapsEveryPaymentType() {
        ResGoldTreasure response = info();
        assertEquals(activity.getTimeEnd(), response.endTime);
        assertEquals(List.of(FREE, PAID, NEXT, EXCHANGE, AFTER_EXCHANGE),
                response.activityData.stream().map(detail -> detail.detailId).toList());
        assertEquals(ActivityConstant.ClaimStatus.CAN_CLAIM, detail(response, FREE).claimStatus);
        assertEquals(ActivityConstant.ClaimStatus.NOT_CLAIM, detail(response, PAID).claimStatus);
        assertEquals(ActivityConstant.ClaimStatus.NOT_CLAIM, detail(response, EXCHANGE).claimStatus);
        assertEquals(4, detail(response, FREE).rechargeInfo.type);
        assertEquals(1, detail(response, PAID).rechargeInfo.type);
        assertEquals(PRICE.toPlainString(), detail(response, PAID).rechargeInfo.price);
        assertEquals("gold.apple", detail(response, PAID).rechargeInfo.channelProductId);
        assertEquals(2, detail(response, EXCHANGE).rechargeInfo.type);
        assertEquals(STAR_ITEM, detail(response, EXCHANGE).rechargeInfo.redeem.getFirst().itemId);
        assertEquals(3L, detail(response, EXCHANGE).rechargeInfo.redeem.getFirst().count);
        verify(pack, never()).checkHasItems(any(Player.class), anyMap());
        verify(dao, never()).savePlayerActivityData(anyLong(), any(), anyLong(), anyMap());
    }

    @Test
    void unlockingUsesExistingListOrderWithoutSortingIds() {
        gameData.when(GameDataManager::getGoldTreasureCfgList).thenReturn(List.of(
                cfgs.get(NEXT), cfgs.get(PAID), cfgs.get(FREE), cfgs.get(EXCHANGE), cfgs.get(AFTER_EXCHANGE), cfgs.get(OUTSIDE)));
        assertEquals(List.of(NEXT, PAID, FREE, EXCHANGE, AFTER_EXCHANGE),
                info().activityData.stream().map(detail -> detail.detailId).toList());
        assertEquals(ActivityConstant.ClaimStatus.CAN_CLAIM, detail(info(), NEXT).claimStatus);
        assertNotEquals(Code.SUCCESS, controller.claimActivityRewards(player, activity, FREE).code);
        assertEquals(Code.SUCCESS, controller.claimActivityRewards(player, activity, NEXT).code);
        assertTrue(controller.generateOrderDetailInfo(player, request(PAID)).success());
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
    void paymentTypeComesFromShopInsteadOfRewardType() {
        when(cfgs.get(FREE).getRewardType()).thenReturn(2);
        when(cfgs.get(PAID).getRewardType()).thenReturn(2);
        when(cfgs.get(EXCHANGE).getRewardType()).thenReturn(1);
        assertEquals(Code.SUCCESS, controller.claimActivityRewards(player, activity, FREE).code);
        assertTrue(controller.generateOrderDetailInfo(player, request(PAID)).success());
        assertTrue(controller.onReceivedRecharge(player, order(PAID)));
        assertEquals(Code.SUCCESS, controller.claimActivityRewards(player, activity, PAID).code);
        saved.put(NEXT, data(ROUND, ActivityConstant.ClaimStatus.CLAIMED));
        assertEquals(Code.SUCCESS, controller.claimActivityRewards(player, activity, EXCHANGE).code);
        verify(pack).useItem(player, STAR_ITEM, 3L, REWARDS, AddType.ACTIVITY_GOLD_TREASURE_REWARDS);
    }

    @Test
    void laterNodesCannotSkipTheFirstUnclaimedReward() {
        saved.put(PAID, data(ROUND, ActivityConstant.ClaimStatus.CAN_CLAIM));
        assertFalse(controller.generateOrderDetailInfo(player, request(PAID)).success());
        assertNotEquals(Code.SUCCESS, controller.claimActivityRewards(player, activity, PAID).code);
        assertNotEquals(Code.SUCCESS, controller.claimActivityRewards(player, activity, NEXT).code);
        assertNotEquals(Code.SUCCESS, controller.claimActivityRewards(player, activity, EXCHANGE).code);
        verify(pack, never()).addItems(anyLong(), anyMap(), any(AddType.class));
        verify(pack, never()).useItem(any(Player.class), anyInt(), anyLong(), anyMap(), any(AddType.class));
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
    void cashRewardRequiresPaymentAndCannotBeOrderedAgainAfterPaymentOrClaim() {
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
    void exchangeRewardUsesShopRedeemAndUnlocksTheNextReward() {
        unlockExchange();
        assertEquals(ActivityConstant.ClaimStatus.CAN_CLAIM, detail(info(), EXCHANGE).claimStatus);
        assertEquals(Code.SUCCESS, controller.claimActivityRewards(player, activity, EXCHANGE).code);
        verify(pack).useItem(player, STAR_ITEM, 3L, REWARDS, AddType.ACTIVITY_GOLD_TREASURE_REWARDS);
        verify(pack, never()).addItems(anyLong(), anyMap(), any(AddType.class));
        assertEquals(ActivityConstant.ClaimStatus.CLAIMED, saved.get(EXCHANGE).getClaimStatus());
        assertEquals(ActivityConstant.ClaimStatus.CAN_CLAIM, detail(info(), AFTER_EXCHANGE).claimStatus);
        assertEquals(Code.REPEAT_OP, controller.claimActivityRewards(player, activity, EXCHANGE).code);
    }

    @Test
    void exchangeRewardRejectsInsufficientInventory() {
        unlockExchange();
        when(pack.checkHasItems(player, REDEEM)).thenReturn(false);
        when(pack.useItem(player, STAR_ITEM, 3L, REWARDS, AddType.ACTIVITY_GOLD_TREASURE_REWARDS))
                .thenReturn(new CommonResult<>(Code.NOT_ENOUGH_ITEM));
        assertEquals(ActivityConstant.ClaimStatus.NOT_CLAIM, detail(info(), EXCHANGE).claimStatus);
        assertNotEquals(Code.SUCCESS, controller.claimActivityRewards(player, activity, EXCHANGE).code);
        assertFalse(saved.containsKey(EXCHANGE));
        assertEquals(ActivityConstant.ClaimStatus.NOT_CLAIM, detail(info(), AFTER_EXCHANGE).claimStatus);
    }

    @Test
    void exchangeFailureRestoresStateAndUsesTheExistingServiceForRetry() {
        unlockExchange();
        when(pack.useItem(player, STAR_ITEM, 3L, REWARDS, AddType.ACTIVITY_GOLD_TREASURE_REWARDS))
                .thenReturn(new CommonResult<>(Code.FAIL), new CommonResult<>(Code.SUCCESS, new ItemOperationResult()));
        assertEquals(Code.FAIL, controller.claimActivityRewards(player, activity, EXCHANGE).code);
        assertFalse(saved.containsKey(EXCHANGE));
        assertEquals(Code.SUCCESS, controller.claimActivityRewards(player, activity, EXCHANGE).code);
        verify(pack, times(2)).useItem(player, STAR_ITEM, 3L, REWARDS, AddType.ACTIVITY_GOLD_TREASURE_REWARDS);
        verify(pack, never()).removeItems(any(Player.class), anyMap(), any(AddType.class));
    }

    @Test
    void exchangeStateIsDurableBeforeUsingItems() {
        unlockExchange();
        when(pack.useItem(player, STAR_ITEM, 3L, REWARDS, AddType.ACTIVITY_GOLD_TREASURE_REWARDS))
                .thenAnswer(invocation -> {
                    assertEquals(ActivityConstant.ClaimStatus.CLAIMED, saved.get(EXCHANGE).getClaimStatus());
                    return new CommonResult<>(Code.SUCCESS, new ItemOperationResult());
                });
        assertEquals(Code.SUCCESS, controller.claimActivityRewards(player, activity, EXCHANGE).code);
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
        CommonResult<PreGenerateOrderData> result = controller.generateOrderDetailInfo(player, request);
        assertTrue(result.success());
        assertEquals(RechargeCfgType.MONEY.getType(), result.data.getType());
        assertEquals(PRICE, result.data.getPrice());
        assertEquals(String.valueOf(PAID), request.productId);
        verify(pack, never()).addItems(anyLong(), anyMap(), any(AddType.class));
        verify(dao, never()).savePlayerActivityData(anyLong(), any(), anyLong(), anyMap());
    }

    @Test
    void onlyCashPaymentCanUseTheOrderFlow() {
        ReqGenerateOrder request = request(PAID);
        request.productId = ACTIVITY_ID + "_" + ROUND + "_" + PAID;
        assertFalse(controller.generateOrderDetailInfo(player, request).success());
        request.productId = "invalid";
        assertFalse(controller.generateOrderDetailInfo(player, request).success());
        assertFalse(controller.generateOrderDetailInfo(player, request(FREE)).success());
        unlockExchange();
        assertFalse(controller.generateOrderDetailInfo(player, request(EXCHANGE)).success());
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
    void callbacksRejectUnavailableActivityInvalidDetailAndNonCashPayment() {
        saved.put(FREE, data(ROUND, ActivityConstant.ClaimStatus.CLAIMED));
        assertFalse(controller.onReceivedRecharge(player, order(OUTSIDE)));
        assertFalse(controller.onReceivedRecharge(player, order(FREE)));
        assertFalse(controller.onReceivedRecharge(player, order(EXCHANGE)));
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
        unlockExchange();
        assertNotEquals(Code.SUCCESS, controller.claimActivityRewards(player, activity, EXCHANGE).code);
        verify(pack, never()).addItems(anyLong(), anyMap(), any(AddType.class));
        verify(pack, never()).useItem(any(Player.class), anyInt(), anyLong(), anyMap(), any(AddType.class));
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
    void activityEndMailsUnlockedPaidRewardAndFiltersOnlyActivityStars() {
        saved.put(FREE, data(ROUND, ActivityConstant.ClaimStatus.CLAIMED));
        saved.put(PAID, data(ROUND, ActivityConstant.ClaimStatus.CAN_CLAIM));
        when(cfgs.get(PAID).getGetItem()).thenReturn(Map.of(STAR_ITEM, 3L, 120001, 10L, 120003, 2L));
        when(playerPack.getItemCount(STAR_ITEM)).thenReturn(7L);
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
    void activityEndChecksExchangeCostAgainstLoadedBackpack() {
        unlockExchange();
        when(playerPack.getItemCount(STAR_ITEM)).thenReturn(3L);
        controller.onActivityEnd(activity);
        verify(mails).addCfgMail(eq(PLAYER_ID), eq(50), anyList(),
                eq(AddType.ACTIVITY_GOLD_TREASURE_REWARDS), isNull());
        verifyNoMoreInteractions(mails);
        verify(playerPack).checkHasItems(argThat((List<Item> items) -> items.size() == 1
                && items.getFirst().getId() == STAR_ITEM && items.getFirst().getItemCount() == 3L));
        verify(pack).getFromAllDB(PLAYER_ID);
        verify(pack, never()).checkHasItems(any(Player.class), anyMap());
        verify(pack, never()).useItem(any(Player.class), anyInt(), anyLong(), anyMap(), any(AddType.class));
    }

    @Test
    void activityEndCannotMailExchangeRewardWithoutEnoughItems() {
        unlockExchange();
        when(playerPack.checkHasItems(anyList())).thenReturn(false);
        when(playerPack.getItemCount(STAR_ITEM)).thenReturn(2L);
        controller.onActivityEnd(activity);
        verifyNoInteractions(mails);
        verify(pack).removeItems(player, Map.of(STAR_ITEM, 2L), AddType.ACTIVITY_GOLD_TREASURE_REWARDS);
        assertTrue(saved.isEmpty());
    }

    @Test
    void activityEndCleanupFailureRetainsClaimedRewardState() {
        saved.put(FREE, data(ROUND, ActivityConstant.ClaimStatus.CLAIMED));
        saved.put(PAID, data(ROUND, ActivityConstant.ClaimStatus.CAN_CLAIM));
        when(playerPack.getItemCount(STAR_ITEM)).thenReturn(7L);
        when(pack.removeItems(player, Map.of(STAR_ITEM, 7L), AddType.ACTIVITY_GOLD_TREASURE_REWARDS))
                .thenReturn(new CommonResult<>(Code.FAIL));
        controller.onActivityEnd(activity);
        assertEquals(ActivityConstant.ClaimStatus.CLAIMED, saved.get(PAID).getClaimStatus());
        verify(mails).addCfgMail(eq(PLAYER_ID), eq(50), anyList(),
                eq(AddType.ACTIVITY_GOLD_TREASURE_REWARDS), isNull());
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
    void activityEndDoesNotAutoClaimTheEntireChain() {
        saved.put(AFTER_EXCHANGE, data(ROUND, ActivityConstant.ClaimStatus.CLAIMED));
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

    private void unlockExchange() {
        for (int id : List.of(FREE, PAID, NEXT)) {
            saved.put(id, data(ROUND, ActivityConstant.ClaimStatus.CLAIMED));
        }
    }

    private ResGoldTreasure info() {
        return (ResGoldTreasure) controller.getPlayerActivityInfoByType(player, ActivityType.GOLD_TREASURE);
    }

    private GoldTreasureDetailInfo detail(ResGoldTreasure response, int detailId) {
        return response.activityData.stream().filter(info -> info.detailId == detailId).findFirst().orElseThrow();
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

    private void addConfig(int id, int rewardType, int payId) {
        GoldTreasureCfg cfg = mock(GoldTreasureCfg.class);
        when(cfg.getId()).thenReturn(id);
        when(cfg.getRewardType()).thenReturn(rewardType);
        when(cfg.getPayId()).thenReturn(payId);
        when(cfg.getGetItem()).thenReturn(REWARDS);
        cfgs.put(id, cfg);
    }

    private void addShop(int id, int type, BigDecimal price, Map<Integer, Long> redeem) {
        ShopRechargeListCfg shop = mock(ShopRechargeListCfg.class);
        when(shop.getId()).thenReturn(id);
        when(shop.getType()).thenReturn(type);
        when(shop.getPrice()).thenReturn(price);
        when(shop.getRedeem()).thenReturn(redeem);
        when(shop.getIosShopId()).thenReturn("gold.apple");
        when(shop.getGoogleShopId()).thenReturn("gold.google");
        shops.put(id, shop);
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
