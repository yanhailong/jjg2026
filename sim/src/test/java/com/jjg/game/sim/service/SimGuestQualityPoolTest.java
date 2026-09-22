package com.jjg.game.sim.service;

import com.jjg.game.alliance.service.AllianceEventService;
import com.jjg.game.common.config.NodeConfig;
import com.jjg.game.common.utils.WeightRandom;
import com.jjg.game.core.constant.AddType;
import com.jjg.game.core.constant.Code;
import com.jjg.game.core.dao.CountDao;
import com.jjg.game.core.data.CommonResult;
import com.jjg.game.core.data.Item;
import com.jjg.game.core.data.Order;
import com.jjg.game.core.data.PayType;
import com.jjg.game.core.data.Player;
import com.jjg.game.core.manager.RedDotManager;
import com.jjg.game.core.service.OrderService;
import com.jjg.game.core.service.PlayerPackService;
import com.jjg.game.core.service.SpecialGuestDailyCountService;
import com.jjg.game.core.utils.ItemUtils;
import com.jjg.game.sampledata.GameDataManager;
import com.jjg.game.sampledata.bean.*;
import com.jjg.game.sim.constant.SimConstant;
import com.jjg.game.sim.data.*;
import com.jjg.game.sim.manager.SimPlayerContextRegistry;
import com.jjg.game.sim.pb.res.ResBuySpecialGuest;
import com.jjg.game.sim.pb.res.ResGenPurchasedGuest;
import com.jjg.game.sim.pb.res.ResSpecialGuestList;
import com.jjg.game.sim.pb.struct.SpecialGuestInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import static com.jjg.game.core.pb.RechargeType.BUY_GUEST;
import static com.jjg.game.sim.constant.SimConstant.SpecialGuest.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SimGuestQualityPoolTest {
    private final SimGuestService service = spy(new SimGuestService());
    private final SimConfigCacheService cache = mock(SimConfigCacheService.class);
    private final SpecialGuestDailyCountService counts = mock(SpecialGuestDailyCountService.class);
    private final PlayerPackService pack = mock(PlayerPackService.class);
    private final OrderService orders = mock(OrderService.class);
    private final SimRewardService rewards = mock(SimRewardService.class);
    private final SimPlayerContext ctx = mock(SimPlayerContext.class);
    private final SimCasinoData casino = new SimCasinoData();
    private final SimBaseData base = new SimBaseData();
    private MockedStatic<GameDataManager> data;
    private VisitorTargetListCfg paidPool;
    private VisitorTargetListCfg qualityPool;
    private VisitorQualityAcquisitionCfg product;
    private GlobalConfigCfg purchaseLimit;

    @BeforeEach
    void setUp() {
        data = mockStatic(GameDataManager.class);
        purchaseLimit = mock(GlobalConfigCfg.class);
        data.when(() -> GameDataManager.getGlobalConfigCfg(SimConstant.Global.SPECIAL_GUEST_MAX_PURCHASE_PER_REFRESH)).thenReturn(purchaseLimit);
        GlobalConfigCfg refreshLimit = mock(GlobalConfigCfg.class);
        data.when(() -> GameDataManager.getGlobalConfigCfg(SimConstant.Global.SPECIAL_GUEST_MAX_MANUAL_REFRESH_PER_PERIOD)).thenReturn(refreshLimit);
        casino.setCasinoId(1);
        casino.setCasinoLevel(1);
        casino.setSpecialGuestPaidCfgIds(new ArrayList<>());
        casino.setSpecialGuestQualityCfgIds(Map.of(30, List.of(101)));
        casino.setSpecialGuestOfferVersion(4);
        base.setSpecialGuestAdCfgIds(new ArrayList<>());
        when(ctx.getCurrentCasino()).thenReturn(casino);
        when(ctx.getSimBaseData()).thenReturn(base);
        when(ctx.playerId()).thenReturn(1L);
        when(ctx.getPlayer()).thenReturn(mock(Player.class));
        ReflectionTestUtils.setField(service, "configCache", cache);
        ReflectionTestUtils.setField(service, "specialGuestDailyCountService", counts);
        ReflectionTestUtils.setField(service, "playerPackService", pack);
        ReflectionTestUtils.setField(service, "orderService", orders);
        ReflectionTestUtils.setField(service, "rewardService", rewards);
        ReflectionTestUtils.setField(service, "nodeConfig", mock(NodeConfig.class));
        pool(10, POOL_AD, 100);
        paidPool = pool(20, POOL_PAID, 100);
        qualityPool = pool(30, POOL_QUALITY, 100);
        product = quality(101, COST_DIAMOND, Map.of(1001, 1L));
    }

    @AfterEach
    void close() {
        data.close();
    }

    @Test
    void adPurchaseRetainsOfferAndReportsOrderProgress() {
        VisitorTargetListCfg adPool = pool(10, POOL_AD, 100);
        VisitorGenWatchVideoCfg ad = mock(VisitorGenWatchVideoCfg.class);
        when(ad.getId()).thenReturn(201);
        when(ad.getVisitorID()).thenReturn(1001);
        when(ad.getVisitorCount()).thenReturn(1);
        when(ad.getRewardDisplay()).thenReturn(Map.of(2002, 8L));
        data.when(() -> GameDataManager.getVisitorGenWatchVideoCfg(201)).thenReturn(ad);
        base.setSpecialGuestAdCfgIds(List.of(201));
        ReflectionTestUtils.setField(service, "redDotManager", mock(RedDotManager.class));
        ReflectionTestUtils.setField(service, "allianceEventService", mock(AllianceEventService.class));
        doAnswer(invocation -> {
            PurchasedGuestData guest = new PurchasedGuestData();
            guest.setUid("ad-guest");
            guest.setOrderId(invocation.getArgument(1));
            guest.setPoolId(10);
            guest.setCfgId(201);
            guest.setDestinations(Map.of(0, new com.jjg.game.sim.pb.struct.DestinationInfo()));
            casino.addPurchasedGuest(guest);
            return Code.SUCCESS;
        }).when(service).invitePurchasedSpecialGuest(eq(ctx), anyString(), eq(10), eq(201), eq(1001), eq(1L));

        service.buySpecialGuest(ctx, 10, 201, COST_AD, 0);
        ArgumentCaptor<ResBuySpecialGuest> response = ArgumentCaptor.forClass(ResBuySpecialGuest.class);
        verify(ctx).send(response.capture());
        ResBuySpecialGuest result = response.getValue();
        assertEquals(Code.SUCCESS, result.code);
        assertEquals(List.of(201), base.getSpecialGuestAdCfgIds());
        assertEquals(201, result.specialGuest.id);
        assertEquals(result.orderId, result.specialGuest.orderId);
        assertEquals(2, result.specialGuest.btnStatus);
        assertEquals(0, result.specialGuest.guestCountKVInfo.key);
        assertEquals(1, result.specialGuest.guestCountKVInfo.value);
        assertEquals(2002, result.specialGuest.output.getFirst().itemId);

        service.buySpecialGuest(ctx, 10, 201, COST_AD, 0);
        verify(ctx, times(2)).send(response.capture());
        assertEquals(Code.REPEAT_OP, response.getValue().code);
        verify(counts, times(1)).addAdCount(1L);
        casino.removePurchasedGuest("ad-guest");
        SpecialGuestInfo ready = ReflectionTestUtils.invokeMethod(service, "toSpecialGuestInfo", ctx, base, ad, adPool);
        assertEquals(3, ready.btnStatus);
        assertEquals(1, ready.guestCountKVInfo.key);
        casino.setSpecialGuestMap(null);
        when(adPool.getDailyViewLimit()).thenReturn(1);
        when(counts.getAdCount(1L)).thenReturn(1);
        SpecialGuestInfo limited = ReflectionTestUtils.invokeMethod(service, "toSpecialGuestInfo", ctx, base, ad, adPool);
        assertEquals(1, limited.btnStatus);
        when(counts.getAdCount(1L)).thenReturn(0);
        SpecialGuestInfo available = ReflectionTestUtils.invokeMethod(service, "toSpecialGuestInfo", ctx, base, ad, adPool);
        assertEquals(0, available.btnStatus);
    }

    @Test
    void timedAdRefreshKeepsPendingOfferAndRefreshesRemainingSlots() {
        VisitorTargetListCfg adPool = pool(10, POOL_AD, 100);
        when(adPool.getDisplayCount()).thenReturn(2);
        VisitorGenWatchVideoCfg pending = mock(VisitorGenWatchVideoCfg.class);
        when(pending.getId()).thenReturn(201);
        VisitorGenWatchVideoCfg next = mock(VisitorGenWatchVideoCfg.class);
        when(next.getId()).thenReturn(202);
        data.when(GameDataManager::getVisitorGenWatchVideoCfgList).thenReturn(List.of(pending, next));
        BuySpecialGuestLogInfo order = new BuySpecialGuestLogInfo();
        order.setPoolId(10);
        order.setCfgId(201);
        casino.setSpecialGuestMap(Map.of("order", order));
        base.setSpecialGuestAdCfgIds(List.of(201, 203));
        base.setSpecialGuestAdNextRefreshTime(1L);
        assertEquals(true, ReflectionTestUtils.invokeMethod(service, "ensureSpecialGuestOffers", ctx, 2L));
        assertEquals(List.of(201, 202), base.getSpecialGuestAdCfgIds());
    }

    @Test
    void refreshedListKeepsEveryOrderWithItsOwnStatus() {
        quality(102, COST_DIAMOND, Map.of(1001, 1L));
        when(qualityPool.getVisitorWeight()).thenReturn(List.of(List.of(102, 1)));
        PurchasedGuestData waiting = new PurchasedGuestData();
        waiting.setUid("waiting");
        waiting.setOrderId("quality-waiting");
        waiting.setPoolId(30);
        waiting.setCfgId(101);
        waiting.setDestinations(Map.of(0, new com.jjg.game.sim.pb.struct.DestinationInfo()));
        casino.addPurchasedGuest(waiting);
        BuySpecialGuestLogInfo ready = new BuySpecialGuestLogInfo();
        ready.setPoolId(30);
        ready.setCfgId(101);
        ready.setCreateTime(System.currentTimeMillis());
        ready.setGuestIds(Set.of("removed"));
        casino.getSpecialGuestMap().put("quality-ready", ready);

        VisitorGenWatchVideoCfg ad = mock(VisitorGenWatchVideoCfg.class);
        when(ad.getId()).thenReturn(201);
        when(ad.getRewardDisplay()).thenReturn(Map.of(2002, 1L));
        data.when(() -> GameDataManager.getVisitorGenWatchVideoCfg(201)).thenReturn(ad);
        BuySpecialGuestLogInfo adOrder = new BuySpecialGuestLogInfo();
        adOrder.setPoolId(10);
        adOrder.setCfgId(201);
        adOrder.setGuestIds(Set.of("removed-ad"));
        casino.getSpecialGuestMap().put("ad-order", adOrder);

        ReflectionTestUtils.invokeMethod(service, "refreshQualitySpecialGuestOffers", casino, List.of(qualityPool));
        assertEquals(List.of(102), casino.getSpecialGuestQualityCfgIds().get(30));
        List<SpecialGuestInfo> infos = ReflectionTestUtils.invokeMethod(service, "buildSpecialGuestList", ctx, base);
        assertEquals(4, infos.size());
        Map<String, SpecialGuestInfo> orders = new HashMap<>();
        for (SpecialGuestInfo info : infos) {
            if (info.orderId != null) {
                assertNull(orders.put(info.orderId, info));
            }
        }
        assertEquals(casino.getSpecialGuestMap().keySet(), orders.keySet());
        assertEquals(2, orders.get("quality-waiting").btnStatus);
        assertEquals(0, orders.get("quality-waiting").guestCountKVInfo.key);
        assertEquals(3, orders.get("quality-ready").btnStatus);
        assertEquals(1, orders.get("quality-ready").guestCountKVInfo.key);
        assertEquals(3, orders.get("ad-order").btnStatus);

        casino.setSpecialGuestQualityCfgIds(Map.of(30, List.of(101)));
        infos = ReflectionTestUtils.invokeMethod(service, "buildSpecialGuestList", ctx, base);
        assertEquals(4, infos.size());
        assertNull(infos.getFirst().orderId);
        assertEquals(0, infos.getFirst().btnStatus);
        assertEquals(4, infos.stream().map(info -> info.orderId).distinct().count());
    }

    @Test
    void manualRefreshShowsThreeNewOffersBeforeThreePurchasedOrders() {
        VisitorGenWatchVideoCfg ad = mock(VisitorGenWatchVideoCfg.class);
        when(ad.getId()).thenReturn(201);
        data.when(() -> GameDataManager.getVisitorGenWatchVideoCfg(201)).thenReturn(ad);
        base.setSpecialGuestAdCfgIds(List.of(201));
        for (int id = 102; id <= 106; id++) {
            quality(id, COST_DIAMOND, Map.of(1001, 1L));
        }
        casino.setSpecialGuestQualityCfgIds(Map.of(30, List.of(101, 102, 103)));
        ReflectionTestUtils.setField(service, "redDotManager", mock(RedDotManager.class));
        doAnswer(invocation -> {
            VisitorQualityAcquisitionCfg bought = invocation.getArgument(3);
            PurchasedGuestData guest = new PurchasedGuestData();
            guest.setUid("guest-" + bought.getId());
            guest.setOrderId(invocation.getArgument(1));
            guest.setPoolId(30);
            guest.setCfgId(bought.getId());
            guest.setDestinations(Map.of(0, new com.jjg.game.sim.pb.struct.DestinationInfo()));
            casino.addPurchasedGuest(guest);
            return Code.SUCCESS;
        }).when(service).inviteQualitySpecialGuests(eq(ctx), anyString(), eq(30), any());
        List<SpecialGuestInfo> initial = ReflectionTestUtils.invokeMethod(service, "buildSpecialGuestList", ctx, base);
        assertEquals(4, initial.size());
        assertTrue(initial.stream().allMatch(info -> info.btnStatus == 0 && info.orderId == null));
        try (var items = mockStatic(ItemUtils.class)) {
            items.when(ItemUtils::getDiamondItemId).thenReturn(5);
            when(pack.removeItem(ctx.getPlayer(), 5, 10, AddType.SIM_SPECIAL_GUEST_BUY)).thenReturn(new CommonResult<>(Code.SUCCESS));
            for (int id = 101; id <= 103; id++) {
                service.buySpecialGuest(ctx, 30, id, COST_DIAMOND, 0);
            }
        }
        List<SpecialGuestInfo> purchased = ReflectionTestUtils.invokeMethod(service, "buildSpecialGuestList", ctx, base);
        assertEquals(4, purchased.size());
        assertEquals(0, purchased.getFirst().btnStatus);
        assertTrue(purchased.subList(1, 4).stream().allMatch(info -> info.btnStatus == 2 && info.orderId != null));
        Set<String> orderIds = new HashSet<>(casino.getSpecialGuestMap().keySet());

        when(qualityPool.getDisplayCount()).thenReturn(3);
        when(qualityPool.getVisitorWeight()).thenReturn(List.of(List.of(104, 1), List.of(105, 1), List.of(106, 1)));
        when(cache.getSpecialGuestRefreshCosts()).thenReturn(List.of(new Item(1980000, 0)));
        clearInvocations(ctx);
        service.refreshSpecialGuests(ctx);
        ArgumentCaptor<ResSpecialGuestList> response = ArgumentCaptor.forClass(ResSpecialGuestList.class);
        verify(ctx).send(response.capture());
        assertEquals(Code.SUCCESS, response.getValue().code);
        List<SpecialGuestInfo> refreshed = response.getValue().specialGuestList;
        assertEquals(7, refreshed.size());
        assertEquals(201, refreshed.getFirst().id);
        assertTrue(refreshed.subList(0, 4).stream().allMatch(info -> info.btnStatus == 0 && info.orderId == null));
        assertEquals(Set.of(104, 105, 106), refreshed.subList(1, 4).stream().map(info -> info.id).collect(java.util.stream.Collectors.toSet()));
        assertTrue(refreshed.subList(4, 7).stream().allMatch(info -> info.btnStatus == 2));
        assertEquals(orderIds, refreshed.subList(4, 7).stream().map(info -> info.orderId).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void paidOrdersRemainListedWithoutChangingPaidButtonRules() {
        VisitorGenPaidCfg paid = mock(VisitorGenPaidCfg.class);
        when(paid.getId()).thenReturn(201);
        when(paid.getCostType()).thenReturn(COST_DIAMOND);
        data.when(() -> GameDataManager.getVisitorGenPaidCfg(201)).thenReturn(paid);
        BuySpecialGuestLogInfo order = new BuySpecialGuestLogInfo();
        order.setPoolId(20);
        order.setCfgId(201);
        order.setGuestIds(Set.of("removed"));
        casino.setSpecialGuestMap(Map.of("paid-order", order));
        List<SpecialGuestInfo> infos = ReflectionTestUtils.invokeMethod(service, "buildSpecialGuestList", ctx, base);
        SpecialGuestInfo info = infos.stream().filter(item -> "paid-order".equals(item.orderId)).findFirst().orElseThrow();
        assertEquals(0, info.btnStatus);
        assertNull(info.guestCountKVInfo);
    }

    @Test
    void manualRefreshIncludesAllCurrentQualityPoolsWithoutPaidPoolOrManualSwitch() {
        when(cache.getVisitorTargetListCfg(1, POOL_PAID)).thenReturn(null);
        casino.setSpecialGuestPaidCfgIds(null);
        base.setSpecialGuestAdRefreshDay(20260917);
        base.setSpecialGuestAdCfgIds(List.of(999));
        VisitorTargetListCfg second = pool(31, POOL_QUALITY, 100);
        when(cache.getVisitorTargetListCfg(1, POOL_QUALITY)).thenReturn(List.of(qualityPool, second));
        when(cache.getSpecialGuestRefreshCosts()).thenReturn(List.of(new Item(1980000, 0)));
        ReflectionTestUtils.setField(service, "redDotManager", mock(RedDotManager.class));
        casino.getSpecialGuestPurchaseCounts().put("30:101", 1);

        service.refreshSpecialGuests(ctx);

        ArgumentCaptor<ResSpecialGuestList> response = ArgumentCaptor.forClass(ResSpecialGuestList.class);
        verify(ctx).send(response.capture());
        assertEquals(Code.SUCCESS, response.getValue().code);
        assertEquals(Map.of(30, List.of(101), 31, List.of(101)), casino.getSpecialGuestQualityCfgIds());
        assertEquals(List.of(999), base.getSpecialGuestAdCfgIds());
        assertEquals(1, casino.getSpecialGuestRefreshCount());
        assertEquals(5, casino.getSpecialGuestOfferVersion());
        assertTrue(casino.getSpecialGuestPurchaseCounts().isEmpty());
        assertNotNull(response.getValue().nextRefreshCost);
        assertEquals(true, ReflectionTestUtils.invokeMethod(service, "hasFreeSpecialGuestRefresh", casino, System.currentTimeMillis()));
        verifyNoInteractions(pack);
    }

    @Test
    void missingQualityOffersInitializeEvenWhenPaidOffersAlreadyExist() {
        casino.setSpecialGuestQualityCfgIds(null);
        casino.setSpecialGuestNextRefreshTime(Long.MAX_VALUE);
        casino.getSpecialGuestPurchaseCounts().put("20:201", 2);

        assertEquals(true, ReflectionTestUtils.invokeMethod(service, "ensureSpecialGuestOffers", ctx, 1L));

        assertEquals(Map.of(30, List.of(101)), casino.getSpecialGuestQualityCfgIds());
        List<SpecialGuestInfo> list = ReflectionTestUtils.invokeMethod(service, "buildSpecialGuestList", ctx, base);
        assertTrue(list.stream().anyMatch(info -> info.poolId == 30 && info.id == 101));
        assertEquals(4, casino.getSpecialGuestOfferVersion());
        assertEquals(Long.MAX_VALUE, casino.getSpecialGuestNextRefreshTime());
        assertEquals(2, casino.getSpecialGuestPurchaseCounts().get("20:201"));
    }

    @Test
    void initializedEmptyQualityOffersDoNotRerollBeforeRefreshTime() {
        casino.setSpecialGuestQualityCfgIds(null);
        when(qualityPool.getRate()).thenReturn(0);
        assertEquals(true, ReflectionTestUtils.invokeMethod(service, "ensureSpecialGuestOffers", ctx, 1L));
        assertTrue(casino.getSpecialGuestQualityCfgIds().isEmpty());
        assertFalse(casino.needsSpecialGuestQualityInitialization());

        when(qualityPool.getRate()).thenReturn(100);
        assertEquals(true, ReflectionTestUtils.invokeMethod(service, "ensureSpecialGuestOffers", ctx, 2L));

        assertTrue(casino.getSpecialGuestQualityCfgIds().isEmpty());
        assertEquals(4, casino.getSpecialGuestOfferVersion());
    }

    @Test
    void qualityOffersInitializeAndRefreshWithoutPaidPool() {
        when(cache.getVisitorTargetListCfg(1, POOL_PAID)).thenReturn(null);
        casino.setSpecialGuestPaidCfgIds(null);
        casino.setSpecialGuestQualityCfgIds(null);
        when(qualityPool.getIsRefreshByTimePeriod()).thenReturn(true);
        when(cache.getSpecialGuestDailyRefreshHours()).thenReturn(List.of(0, 12));
        long now = LocalDateTime.of(2026, 9, 15, 12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        assertEquals(true, ReflectionTestUtils.invokeMethod(service, "ensureSpecialGuestOffers", ctx, now));
        assertEquals(Map.of(30, List.of(101)), casino.getSpecialGuestQualityCfgIds());
        assertNull(casino.getSpecialGuestPaidCfgIds());
        long next = now + 12 * 60 * 60 * 1000L;
        assertEquals(next, casino.getSpecialGuestNextRefreshTime());
        assertEquals(5, casino.getSpecialGuestOfferVersion());

        casino.getSpecialGuestPurchaseCounts().put("30:101", 1);
        when(qualityPool.getRate()).thenReturn(0);
        assertEquals(true, ReflectionTestUtils.invokeMethod(service, "ensureSpecialGuestOffers", ctx, next));
        assertTrue(casino.getSpecialGuestQualityCfgIds().isEmpty());
        assertTrue(casino.getSpecialGuestPurchaseCounts().isEmpty());
        assertEquals(6, casino.getSpecialGuestOfferVersion());
    }

    @Test
    void missingPaidOffersInitializeWhenQualityOffersAlreadyExist() {
        casino.setSpecialGuestPaidCfgIds(null);
        assertEquals(true, ReflectionTestUtils.invokeMethod(service, "ensureSpecialGuestOffers", ctx, 1L));
        assertNotNull(casino.getSpecialGuestPaidCfgIds());
        assertEquals(5, casino.getSpecialGuestOfferVersion());
    }

    @Test
    void rateZeroAndHundredAreExactAndFailedPoolDoesNotLeaveStaleOffers() {
        when(qualityPool.getRate()).thenReturn(0);
        refresh();
        assertTrue(casino.getSpecialGuestQualityCfgIds().isEmpty());
        when(qualityPool.getRate()).thenReturn(100);
        refresh();
        assertEquals(Map.of(30, List.of(101)), casino.getSpecialGuestQualityCfgIds());
        when(qualityPool.getRate()).thenReturn(0);
        refresh();
        assertTrue(casino.getSpecialGuestQualityCfgIds().isEmpty());
    }

    @Test
    void multipleQualityPoolsRetainSourceAndSelectionHasNoDuplicatesOrZeroWeightItems() {
        quality(102, COST_DIAMOND, Map.of(1001, 1L));
        VisitorTargetListCfg second = pool(31, POOL_QUALITY, 100);
        when(cache.getVisitorTargetListCfg(1, POOL_QUALITY)).thenReturn(List.of(qualityPool, second));
        when(qualityPool.getDisplayCount()).thenReturn(10);
        when(qualityPool.getVisitorWeight()).thenReturn(List.of(List.of(101, 1), List.of(101, 2), List.of(102, 0)));
        refresh();
        assertEquals(Map.of(30, List.of(101), 31, List.of(101)), casino.getSpecialGuestQualityCfgIds());
    }

    @Test
    void listUsesSavedRateDecisionUntilPaidRefreshAndReturnsSourcePool() {
        when(qualityPool.getRate()).thenReturn(0);
        List<SpecialGuestInfo> first = ReflectionTestUtils.invokeMethod(service, "buildSpecialGuestList", ctx, base);
        List<SpecialGuestInfo> second = ReflectionTestUtils.invokeMethod(service, "buildSpecialGuestList", ctx, base);
        assertEquals(1, first.size());
        assertEquals(30, first.getFirst().poolId);
        assertEquals(101, second.getFirst().id);
        assertEquals(4, casino.getSpecialGuestOfferVersion());
    }

    @Test
    void refreshClearsCountsAndAdvancesSharedVersion() {
        casino.getSpecialGuestPurchaseCounts().put("30:101", 2);
        refresh();
        assertTrue(casino.getSpecialGuestPurchaseCounts().isEmpty());
        assertEquals(5, casino.getSpecialGuestOfferVersion());
    }

    @Test
    void timedPaidRefreshRerollsQualityOnceAndUsesPaidSchedule() {
        long now = LocalDateTime.of(2026, 9, 15, 12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        when(paidPool.getIsRefreshByTimePeriod()).thenReturn(true);
        when(cache.getSpecialGuestDailyRefreshHours()).thenReturn(List.of(0, 12));
        casino.setSpecialGuestNextRefreshTime(now);
        casino.getSpecialGuestPurchaseCounts().put("30:101", 2);
        assertEquals(true, ReflectionTestUtils.invokeMethod(service, "ensureSpecialGuestOffers", ctx, now));
        assertEquals(5, casino.getSpecialGuestOfferVersion());
        assertEquals(now + 12 * 60 * 60 * 1000L, casino.getSpecialGuestNextRefreshTime());
        assertEquals(List.of(101), casino.getSpecialGuestQualityCfgIds().get(30));
        when(qualityPool.getRate()).thenReturn(0);
        assertEquals(true, ReflectionTestUtils.invokeMethod(service, "ensureSpecialGuestOffers", ctx, now + 1));
        assertEquals(5, casino.getSpecialGuestOfferVersion());
        assertEquals(List.of(101), casino.getSpecialGuestQualityCfgIds().get(30));
        assertTrue(casino.getSpecialGuestPurchaseCounts().isEmpty());
    }

    @Test
    void rejectsProductFromUnmatchedPoolOrWrongPriceType() {
        casino.setSpecialGuestQualityCfgIds(Map.of());
        assertEquals(Code.PARAM_ERROR, buy(COST_DIAMOND).code);
        casino.setSpecialGuestQualityCfgIds(Map.of(30, List.of(101)));
        assertEquals(Code.PARAM_ERROR, buy(COST_CASH).code);
        when(qualityPool.getRegionID()).thenReturn(2);
        assertEquals(Code.PARAM_ERROR, buy(COST_DIAMOND).code);
        verifyNoInteractions(pack, orders);
    }

    @Test
    void dailyAndPerOfferLimitsAreCheckedBeforeCharging() {
        when(product.getDailyLimitCount()).thenReturn(2);
        when(counts.getPaidCount(1, POOL_QUALITY, 101)).thenReturn(2);
        assertEquals(Code.TODAY_CLIAM_LIMIT, buy(COST_DIAMOND).code);
        when(counts.getPaidCount(1, POOL_QUALITY, 101)).thenReturn(0);
        when(purchaseLimit.getIntValue()).thenReturn(1);
        casino.getSpecialGuestPurchaseCounts().put("30:101", 1);
        ResBuySpecialGuest res = buy(COST_DIAMOND);
        assertEquals(Code.FAIL, res.code);
        assertEquals(1, res.specialGuest.maxPurchasePerRefresh);
        verifyNoInteractions(pack, orders);
    }

    @Test
    void diamondSuccessSharesPaymentFlowButKeepsPaidAndQualityCountersSeparate() {
        casino.getSpecialGuestPurchaseCounts().put("20:101", 7);
        when(purchaseLimit.getIntValue()).thenReturn(1);
        doReturn(Code.SUCCESS).when(service).inviteQualitySpecialGuests(eq(ctx), anyString(), eq(30), eq(product));
        try (var items = mockStatic(ItemUtils.class)) {
            items.when(ItemUtils::getDiamondItemId).thenReturn(5);
            when(pack.removeItem(ctx.getPlayer(), 5, 10, AddType.SIM_SPECIAL_GUEST_BUY)).thenReturn(new CommonResult<>(Code.SUCCESS));
            ResBuySpecialGuest result = buy(COST_DIAMOND);
            assertEquals(Code.SUCCESS, result.code);
            assertNotNull(result.orderId);
            verify(service).inviteQualitySpecialGuests(ctx, result.orderId, 30, product);
        }
        assertEquals(Map.of("20:101", 7, "30:101", 1), casino.getSpecialGuestPurchaseCounts());
        verify(counts).addPaidCount(1, POOL_QUALITY, 101);
        verify(service).inviteQualitySpecialGuests(eq(ctx), anyString(), eq(30), eq(product));
        verify(service, never()).invitePurchasedSpecialGuest(any(), anyString(), anyInt(), anyInt(), anyInt(), anyLong());
    }

    @Test
    void failedGenerationRefundsDiamondsWithoutCountingPurchase() {
        doReturn(Code.FAIL).when(service).inviteQualitySpecialGuests(eq(ctx), anyString(), eq(30), eq(product));
        try (var items = mockStatic(ItemUtils.class)) {
            items.when(ItemUtils::getDiamondItemId).thenReturn(5);
            when(pack.removeItem(ctx.getPlayer(), 5, 10, AddType.SIM_SPECIAL_GUEST_BUY)).thenReturn(new CommonResult<>(Code.SUCCESS));
            when(pack.addItem(1, 5, 10, AddType.FAIL_ROLLBACK)).thenReturn(new CommonResult<>(Code.SUCCESS));
            assertEquals(Code.FAIL, buy(COST_DIAMOND).code);
            verify(pack).addItem(1, 5, 10, AddType.FAIL_ROLLBACK);
        }
        verify(counts, never()).addPaidCount(anyLong(), anyInt(), anyInt());
        assertTrue(casino.getSpecialGuestPurchaseCounts().isEmpty());
    }

    @Test
    void cashOrderStoresPoolAndVersionWithoutDrawingOrReservingLimit() {
        when(product.getCostType()).thenReturn(COST_CASH);
        cashPrice();
        Order order = new Order();
        order.setUuid("ios-order");
        when(orders.generateOrder(ctx.getPlayer(), PayType.IOS, "101", BigDecimal.TEN, BUY_GUEST, "1:4:30")).thenReturn(order);
        ResBuySpecialGuest res = buy(COST_CASH);
        assertEquals(Code.SUCCESS, res.code);
        assertEquals("ios-order", res.orderId);
        verify(service, never()).inviteQualitySpecialGuests(any(), anyString(), anyInt(), any());
        verify(counts, never()).addPaidCount(anyLong(), anyInt(), anyInt());
        assertTrue(casino.getSpecialGuestPurchaseCounts().isEmpty());
    }

    @Test
    void qualityCashReceiptIgnoresChangedDisplayAndLimitsAndSkipsOldOfferCount() {
        when(product.getCostType()).thenReturn(COST_CASH);
        when(qualityPool.getIsEnabled()).thenReturn(false);
        casino.setSpecialGuestQualityCfgIds(Map.of());
        Order order = cashOrder("1:3:30");
        doReturn(Code.SUCCESS).when(service).inviteQualitySpecialGuests(eq(ctx), anyString(), eq(30), eq(product));
        assertTrue(receipts().onReceivedRecharge(player(), order));
        verify(service).inviteQualitySpecialGuests(eq(ctx), anyString(), eq(30), eq(product));
        verify(counts).addPaidCount(1, POOL_QUALITY, 101);
        verify(counts, never()).getPaidCount(anyLong(), anyInt(), anyInt());
        assertTrue(casino.getSpecialGuestPurchaseCounts().isEmpty());
    }

    @Test
    void failedCashGenerationDoesNotRecordPurchase() {
        when(product.getCostType()).thenReturn(COST_CASH);
        doReturn(Code.SAMPLE_ERROR).when(service).inviteQualitySpecialGuests(eq(ctx), anyString(), eq(30), eq(product));
        assertFalse(receipts().onReceivedRecharge(player(), cashOrder("1:4:30")));
        verify(counts, never()).addPaidCount(anyLong(), anyInt(), anyInt());
        assertTrue(casino.getSpecialGuestPurchaseCounts().isEmpty());
    }

    @Test
    void cashReceiptUsesPoolTypeEvenWhenProductIdsOverlap() {
        VisitorGenPaidCfg paid = mock(VisitorGenPaidCfg.class);
        when(paid.getCostType()).thenReturn(COST_CASH);
        when(paid.getPriceValue1()).thenReturn(10);
        when(paid.getVisitorID()).thenReturn(1001);
        when(paid.getVisitorCount()).thenReturn(2);
        data.when(() -> GameDataManager.getVisitorGenPaidCfg(101)).thenReturn(paid);
        doReturn(Code.SUCCESS).when(service).invitePurchasedSpecialGuest(ctx, "cash-order", 20, 101, 1001, 2);
        assertTrue(receipts().onReceivedRecharge(player(), cashOrder("1:4:20")));
        assertEquals(1, casino.getSpecialGuestPurchaseCounts().get("20:101"));
        verify(counts).addPaidCount(1, POOL_PAID, 101);
        verify(service, never()).inviteQualitySpecialGuests(any(), anyString(), anyInt(), any());
    }

    @Test
    void weightedDrawAllowsRepeatsAndGeneratesOneBatchWithCorrectTotal() {
        when(product.getQuantityPerPurchase()).thenReturn(3);
        WeightRandom<Integer> random = mock(WeightRandom.class);
        when(random.next()).thenReturn(1001, 1002, 1001);
        when(cache.getVisitorQualityRandom(101)).thenReturn(random);
        guest(1002);
        SimBuildingService buildings = mock(SimBuildingService.class);
        when(buildings.computeProsperity(casino)).thenReturn(1);
        ReflectionTestUtils.setField(service, "buildingService", buildings);
        ReflectionTestUtils.setField(service, "employeeRedDotService", mock(SimEmployeeRedDotService.class));
        SimTaskService tasks = mock(SimTaskService.class);
        AllianceEventService alliance = mock(AllianceEventService.class);
        ReflectionTestUtils.setField(service, "simTaskService", tasks);
        ReflectionTestUtils.setField(service, "allianceEventService", alliance);
        when(cache.getCasinoStatsSheetCfg(1, 1)).thenReturn(mock(CasinoStatsSheetCfg.class));
        BuildingData building = new BuildingData();
        building.setId(50);
        building.setLevel(1);
        casino.setBuildingData(Map.of(50, building));
        BuildingUnlockEquipmentData equipment = new BuildingUnlockEquipmentData();
        equipment.setMaxLevel(1);
        equipment.setLevelUnlockEquipment(1, List.of(501));
        when(cache.getBuildingUnlockEquipmentDataByBuildId(50)).thenReturn(equipment);
        data.when(() -> GameDataManager.getBuildingAreaTableCfg(50)).thenReturn(mock(BuildingAreaTableCfg.class));
        assertEquals(Code.SUCCESS, service.inviteQualitySpecialGuests(ctx, "order", 30, product));
        ArgumentCaptor<ResGenPurchasedGuest> response = ArgumentCaptor.forClass(ResGenPurchasedGuest.class);
        verify(ctx).send(response.capture());
        assertEquals(3, response.getValue().guests.size());
        assertEquals(casino.getPurchasedGuestMap().keySet(), casino.getSpecialGuestMap().get("order").getGuestIds());
        assertEquals(3, casino.getSpecialGuestMap().get("order").getGuestIds().size());
        assertEquals(30, casino.getSpecialGuestMap().get("order").getPoolId());
        assertEquals(101, casino.getSpecialGuestMap().get("order").getCfgId());
        assertEquals(2, response.getValue().guests.stream().filter(g -> g.id == 1001).count());
        verify(alliance).onGuestGenerated(1, true, 3);
        verify(tasks).onConditionEvent(eq(ctx), any());
        verify(random, times(3)).next();
    }

    @Test
    void missingVisitorFailsBeforeGeneratingAnyGuests() {
        when(product.getQuantityPerPurchase()).thenReturn(2);
        WeightRandom<Integer> random = mock(WeightRandom.class);
        when(random.next()).thenReturn(1001, 9999);
        when(cache.getVisitorQualityRandom(101)).thenReturn(random);
        assertEquals(Code.SAMPLE_ERROR, service.inviteQualitySpecialGuests(ctx, "order", 30, product));
        assertNull(casino.findGuestData(1001));
        verify(ctx, never()).send(any());
    }

    @Test
    void qualityWeightCacheIgnoresZeroWeightsAndReplacesRemovedProductsOnReload() {
        SimConfigCacheService actual = new SimConfigCacheService(null, null);
        when(product.getVisitorWeight()).thenReturn(Map.of(1001, 1L, 1002, 0L));
        data.when(GameDataManager::getVisitorQualityAcquisitionCfgList).thenReturn(List.of(product));
        ReflectionTestUtils.invokeMethod(actual, "loadVisitorQualityAcquisitionConfig");
        WeightRandom<Integer> random = actual.getVisitorQualityRandom(101);
        assertSame(random, actual.getVisitorQualityRandom(101));
        assertEquals(1001, random.next());
        data.when(GameDataManager::getVisitorQualityAcquisitionCfgList).thenReturn(List.of());
        ReflectionTestUtils.invokeMethod(actual, "loadVisitorQualityAcquisitionConfig");
        assertNull(actual.getVisitorQualityRandom(101));
    }

    @Test
    void qualityConfigUsesSharedRefreshAndRejectsInvalidRatesBeforePublishing() {
        SimConfigCacheService actual = new SimConfigCacheService(null, null);
        when(qualityPool.getIsRefreshByTimePeriod()).thenReturn(true);
        data.when(GameDataManager::getVisitorTargetListCfgList).thenReturn(List.of(qualityPool));
        ReflectionTestUtils.invokeMethod(actual, "loadVisitorTargetListConfig");
        assertEquals(List.of(qualityPool), actual.getVisitorTargetListCfg(1, POOL_QUALITY));
        when(qualityPool.getRate()).thenReturn(101);
        assertThrows(IllegalArgumentException.class, () -> ReflectionTestUtils.invokeMethod(actual, "loadVisitorTargetListConfig"));
        when(qualityPool.getIsEnabled()).thenReturn(false);
        ReflectionTestUtils.invokeMethod(actual, "loadVisitorTargetListConfig");
        assertNull(actual.getVisitorTargetListCfg(1, POOL_QUALITY));
    }

    @Test
    void dailyCounterSeparatesTablesWithSameProductIdAndRetainsExpiry() {
        CountDao dao = mock(CountDao.class);
        when(dao.getCount(anyString(), eq("1"))).thenReturn(BigDecimal.ZERO);
        when(dao.incrementWithoutExpireRefresh(anyString(), eq("1"), eq(BigDecimal.ONE), anyLong())).thenReturn(BigDecimal.ONE);
        SpecialGuestDailyCountService actual = new SpecialGuestDailyCountService(dao);
        actual.getPaidCount(1, POOL_PAID, 101);
        actual.getPaidCount(1, POOL_QUALITY, 101);
        actual.addPaidCount(1, POOL_QUALITY, 101);
        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(dao, times(2)).getCount(keys.capture(), eq("1"));
        assertNotEquals(keys.getAllValues().get(0), keys.getAllValues().get(1));
        verify(dao).incrementWithoutExpireRefresh(keys.getAllValues().get(1), "1", BigDecimal.ONE, 2 * 24 * 60 * 60L);
    }

    @Test
    void buyingOneProductDoesNotConsumeAnotherProductsLimitInTheSamePool() {
        quality(102, COST_DIAMOND, Map.of(1001, 1L));
        casino.setSpecialGuestQualityCfgIds(Map.of(30, List.of(101, 102)));
        casino.getSpecialGuestPurchaseCounts().put("30:102", 1);
        when(purchaseLimit.getIntValue()).thenReturn(1);
        doReturn(Code.SUCCESS).when(service).inviteQualitySpecialGuests(eq(ctx), anyString(), eq(30), eq(product));
        try (var items = mockStatic(ItemUtils.class)) {
            items.when(ItemUtils::getDiamondItemId).thenReturn(5);
            when(pack.removeItem(ctx.getPlayer(), 5, 10, AddType.SIM_SPECIAL_GUEST_BUY)).thenReturn(new CommonResult<>(Code.SUCCESS));
            assertEquals(Code.SUCCESS, buy(COST_DIAMOND).code);
            assertEquals(Code.FAIL, buy(COST_DIAMOND).code);
            verify(pack, times(1)).removeItem(ctx.getPlayer(), 5, 10, AddType.SIM_SPECIAL_GUEST_BUY);
        }
        assertEquals(Map.of("30:101", 1, "30:102", 1), casino.getSpecialGuestPurchaseCounts());
    }

    @Test
    void listReturnsIndependentCountsAndTheGlobalLimitForEveryPool() {
        when(purchaseLimit.getIntValue()).thenReturn(3);
        VisitorTargetListCfg second = pool(31, POOL_QUALITY, 100);
        casino.setSpecialGuestQualityCfgIds(Map.of(30, List.of(101), 31, List.of(101)));
        casino.getSpecialGuestPurchaseCounts().put("30:101", 3);
        casino.getSpecialGuestPurchaseCounts().put("31:101", 1);
        List<SpecialGuestInfo> infos = ReflectionTestUtils.invokeMethod(service, "buildSpecialGuestList", ctx, base);
        assertEquals(2, infos.size());
        for (SpecialGuestInfo info : infos) {
            assertEquals(3, info.maxPurchasePerRefresh);
            assertEquals(info.poolId == second.getId() ? 1 : 3, info.purchaseCount);
        }
    }

    @Test
    void zeroGlobalPurchaseLimitAllowsFurtherPurchases() {
        when(purchaseLimit.getIntValue()).thenReturn(0);
        casino.getSpecialGuestPurchaseCounts().put("30:101", 100);
        doReturn(Code.SUCCESS).when(service).inviteQualitySpecialGuests(eq(ctx), anyString(), eq(30), eq(product));
        try (var items = mockStatic(ItemUtils.class)) {
            items.when(ItemUtils::getDiamondItemId).thenReturn(5);
            when(pack.removeItem(ctx.getPlayer(), 5, 10, AddType.SIM_SPECIAL_GUEST_BUY)).thenReturn(new CommonResult<>(Code.SUCCESS));
            assertEquals(Code.SUCCESS, buy(COST_DIAMOND).code);
        }
        assertEquals(101, casino.getSpecialGuestPurchaseCounts().get("30:101"));
    }

    @Test
    void cashOrderChecksOnlyItsOwnCompletedPurchasesBeforeCreatingTheOrder() {
        when(product.getCostType()).thenReturn(COST_CASH);
        when(purchaseLimit.getIntValue()).thenReturn(1);
        cashPrice();
        casino.getSpecialGuestPurchaseCounts().put("30:101", 1);
        assertEquals(Code.FAIL, buy(COST_CASH).code);
        verifyNoInteractions(orders);
        casino.getSpecialGuestPurchaseCounts().clear();
        casino.getSpecialGuestPurchaseCounts().put("30:102", 1);
        Order order = new Order();
        order.setUuid("ios-order");
        when(orders.generateOrder(ctx.getPlayer(), PayType.IOS, "101", BigDecimal.TEN, BUY_GUEST, "1:4:30")).thenReturn(order);
        assertEquals(Code.SUCCESS, buy(COST_CASH).code);
        assertFalse(casino.getSpecialGuestPurchaseCounts().containsKey("30:101"));
    }

    @Test
    void currentCashReceiptStillDeliversAndCountsWhenTheItemHasReachedItsLimit() {
        when(product.getCostType()).thenReturn(COST_CASH);
        when(purchaseLimit.getIntValue()).thenReturn(1);
        casino.getSpecialGuestPurchaseCounts().put("30:101", 1);
        casino.getSpecialGuestPurchaseCounts().put("30:102", 1);
        doReturn(Code.SUCCESS).when(service).inviteQualitySpecialGuests(eq(ctx), anyString(), eq(30), eq(product));
        assertTrue(receipts().onReceivedRecharge(player(), cashOrder("1:4:30")));
        assertEquals(Map.of("30:101", 2, "30:102", 1), casino.getSpecialGuestPurchaseCounts());
        verify(counts).addPaidCount(1, POOL_QUALITY, 101);
        verify(purchaseLimit, never()).getIntValue();
    }

    private VisitorTargetListCfg pool(int id, int type, int rate) {
        VisitorTargetListCfg cfg = mock(VisitorTargetListCfg.class);
        when(cfg.getId()).thenReturn(id);
        when(cfg.getPoolType()).thenReturn(type);
        when(cfg.getRegionID()).thenReturn(1);
        when(cfg.getIsEnabled()).thenReturn(true);
        when(cfg.getRate()).thenReturn(rate);
        when(cfg.getDisplayCount()).thenReturn(1);
        when(cfg.getVisitorWeight()).thenReturn(List.of(List.of(101, 1)));
        when(cache.getVisitorTargetListCfg(1, type)).thenReturn(List.of(cfg));
        data.when(() -> GameDataManager.getVisitorTargetListCfg(id)).thenReturn(cfg);
        return cfg;
    }

    private VisitorQualityAcquisitionCfg quality(int id, int costType, Map<Integer, Long> weights) {
        VisitorQualityAcquisitionCfg cfg = mock(VisitorQualityAcquisitionCfg.class);
        when(cfg.getId()).thenReturn(id);
        when(cfg.getCostType()).thenReturn(costType);
        when(cfg.getPriceValue1()).thenReturn(10);
        when(cfg.getQuantityPerPurchase()).thenReturn(1);
        when(cfg.getVisitorWeight()).thenReturn(weights);
        WeightRandom<Integer> random = WeightRandom.create();
        weights.forEach(random::add);
        when(cache.getVisitorQualityRandom(id)).thenReturn(random);
        weights.keySet().forEach(this::guest);
        data.when(() -> GameDataManager.getVisitorQualityAcquisitionCfg(id)).thenReturn(cfg);
        return cfg;
    }

    private void guest(int id) {
        VisitorQuestCfg cfg = mock(VisitorQuestCfg.class);
        when(cfg.getId()).thenReturn(id);
        when(cfg.getServiceCapacity()).thenReturn(1);
        when(cfg.getTargetArea()).thenReturn(List.of(50));
        when(cache.getVisitorQuestCfgByItemId(id)).thenReturn(cfg);
        data.when(() -> GameDataManager.getVisitorQuestCfg(id)).thenReturn(cfg);
    }

    private void refresh() {
        ReflectionTestUtils.invokeMethod(service, "refreshPaidSpecialGuestOffers", casino, paidPool, Set.of());
    }

    private ResBuySpecialGuest buy(int costType) {
        clearInvocations(ctx);
        service.buySpecialGuest(ctx, 30, 101, costType, PayType.IOS.getValue());
        ArgumentCaptor<ResBuySpecialGuest> response = ArgumentCaptor.forClass(ResBuySpecialGuest.class);
        verify(ctx).send(response.capture());
        return response.getValue();
    }

    private void cashPrice() {
        ShopRechargeListCfg shop = mock(ShopRechargeListCfg.class);
        when(shop.getPrice()).thenReturn(BigDecimal.TEN);
        data.when(() -> GameDataManager.getShopRechargeListCfg(10)).thenReturn(shop);
    }

    @Test
    void cashReceiptUsesTheOrderIdReturnedForItsPaymentChannel() {
        when(product.getCostType()).thenReturn(COST_CASH);
        doReturn(Code.SUCCESS).when(service).inviteQualitySpecialGuests(eq(ctx), anyString(), eq(30), eq(product));
        Order order = cashOrder("1:4:30");
        order.setPayChannel(PayType.GOOGLE.getValue());
        order.setUuid("ios-order");
        assertTrue(receipts().onReceivedRecharge(player(), order));
        verify(service).inviteQualitySpecialGuests(ctx, "cash-order", 30, product);

        order.setPayChannel(PayType.IOS.getValue());
        assertTrue(receipts().onReceivedRecharge(player(), order));
        verify(service).inviteQualitySpecialGuests(ctx, "ios-order", 30, product);
    }

    private Order cashOrder(String desc) {
        Order order = new Order();
        order.setId("cash-order");
        order.setRechargeType(BUY_GUEST);
        order.setProductId("101");
        order.setDesc(desc);
        return order;
    }

    private Player player() {
        Player player = mock(Player.class);
        when(player.getId()).thenReturn(1L);
        return player;
    }

    private SpecialGuestOrderService receipts() {
        SpecialGuestOrderService receipts = new SpecialGuestOrderService(counts);
        SimPlayerContextRegistry contexts = mock(SimPlayerContextRegistry.class);
        when(contexts.getContext(1)).thenReturn(ctx);
        ReflectionTestUtils.setField(receipts, "simGuestService", service);
        ReflectionTestUtils.setField(receipts, "simPlayerContextRegistry", contexts);
        return receipts;
    }
}
