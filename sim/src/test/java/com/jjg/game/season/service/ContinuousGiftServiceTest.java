package com.jjg.game.season.service;

import com.jjg.game.common.redis.RedisLock;
import com.jjg.game.core.constant.*;
import com.jjg.game.core.data.*;
import com.jjg.game.core.data.Order;
import com.jjg.game.core.service.*;
import com.jjg.game.core.utils.ItemUtils;
import com.jjg.game.sampledata.bean.*;
import com.jjg.game.season.service.ContinuousGiftConfigService.Chest;
import com.jjg.game.season.service.ContinuousGiftConfigService.Grand;
import com.jjg.game.season.dao.ContinuousGiftDao;
import com.jjg.game.season.data.*;
import com.jjg.game.sim.data.SimPlayerContext;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ContinuousGiftServiceTest {
    final long start = time("2026-10-01T00:00:00");
    final long end = time("2026-10-08T00:00:00");
    final ContinuousGiftConfigService config = mock(ContinuousGiftConfigService.class);
    final ContinuousGiftDao dao = mock(ContinuousGiftDao.class);
    final RedisLock locks = mock(RedisLock.class);
    final PlayerPackService pack = mock(PlayerPackService.class);
    final MailService mail = mock(MailService.class);
    final SimPlayerContext ctx = mock(SimPlayerContext.class);
    final Player player = new Player();
    final Map<String, ContinuousGiftData> store = new HashMap<>();
    ContinuousGiftService gifts;
    ContinuousGiftConfigService.Definition definition;
    MockedStatic<ItemUtils> items;

    @BeforeEach void setup() {
        player.setId(7);
        player.setChannel(ChannelType.GOOGLE);
        when(ctx.playerId()).thenReturn(7L); when(ctx.getPlayer()).thenReturn(player);
        SeasonPlayerData season = new SeasonPlayerData(); season.setSeasonId(1001);
        when(ctx.getSeasonPlayerData()).thenReturn(season);
        when(locks.lockAndGet(anyString(), anyInt(), any())).thenAnswer(i -> ((Supplier<?>) i.getArgument(2)).get());
        when(dao.get(anyString())).thenAnswer(i -> store.get(i.getArgument(0)));
        doAnswer(i -> { ContinuousGiftData d = i.getArgument(0); store.put(d.id, d); return null; }).when(dao).save(any());
        when(pack.removeItems(any(Player.class), anyMap(), eq(AddType.ACTIVITY), anyString()))
                .thenReturn(new CommonResult<>(Code.SUCCESS));
        when(pack.addItems(anyLong(), anyMap(), eq(AddType.ACTIVITY), anyString(), eq(true)))
                .thenReturn(new CommonResult<>(Code.SUCCESS));
        when(mail.addMailIfAbsent(anyLong(), anyString(), anyString(), anyList(), eq(AddType.ACTIVITY), anyString())).thenReturn(true);
        items = mockStatic(ItemUtils.class);
        items.when(ItemUtils::getDiamondItemId).thenReturn(1980000);
        items.when(ItemUtils::getGoldItemId).thenReturn(1990000);
        var activity = mock(ActivityConfigCfg.class);
        when(activity.getId()).thenReturn(1001);
        var grand = mock(Grand.class);
        when(grand.rewardItems()).thenReturn(Map.of(1990000, 500L));
        var chests = new ArrayList<Chest>();
        for (int n = 1; n <= 3; n++) {
            var chest = mock(Chest.class);
            when(chest.id()).thenReturn(100 + n); when(chest.dayIndex()).thenReturn(n);
            when(chest.priceType()).thenReturn(2); when(chest.price()).thenReturn(100L);
            when(chest.rewardItems()).thenReturn(Map.of(1990000, 1000L));
            chests.add(chest);
        }
        definition = new ContinuousGiftConfigService.Definition(activity,mock(GameFunctionCfg.class),start,end,ZoneId.of("Asia/Shanghai"),chests,grand);
        when(config.find(1001)).thenReturn(definition); when(config.definitions()).thenReturn(List.of(definition));
        when(config.allowed(eq(player),any())).thenReturn(true);
        gifts = new ContinuousGiftService(config, dao, locks, pack, mail);
    }
    @AfterEach void close() { items.close(); }
    static long time(String value) { return LocalDateTime.parse(value).atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli(); }
    ContinuousGiftData data() { return store.values().iterator().next(); }

    @Test void sequentialDailyPurchaseAndNoCatchUp() {
        assertEquals(Code.REPEAT_OP, gifts.buy(ctx,1001,2,start).code);
        assertEquals(Code.SUCCESS, gifts.buy(ctx,1001,1,start).code);
        assertEquals(Code.REPEAT_OP, gifts.buy(ctx,1001,1,start).code);
        assertEquals(Code.REPEAT_OP, gifts.buy(ctx,1001,2,start+1000).code);
        assertEquals(Code.SUCCESS, gifts.buy(ctx,1001,2,time("2026-10-03T12:00:00")).code);
        assertEquals(Code.REPEAT_OP, gifts.buy(ctx,1001,3,time("2026-10-03T23:59:59")).code);
        assertEquals(List.of(1,2), data().purchasedDays);
        verify(pack,times(2)).removeItems(eq(player),eq(Map.of(1980000,100L)),eq(AddType.ACTIVITY),anyString());
    }
    @Test void midnightIsCalendarBoundaryNot24HourCooldown() {
        assertEquals(Code.SUCCESS,gifts.buy(ctx,1001,1,time("2026-10-01T23:59:59")).code);
        assertEquals(Code.SUCCESS,gifts.buy(ctx,1001,2,time("2026-10-02T00:00:00")).code);
    }
    @Test void activityWindowAndParticipationAreAuthoritative() {
        assertEquals(Code.FORBID,gifts.buy(ctx,1001,1,start-1).code);
        assertEquals(Code.FORBID,gifts.buy(ctx,1001,1,end).code);
        when(config.allowed(player,definition)).thenReturn(false);
        assertEquals(Code.FORBID,gifts.buy(ctx,1001,1,start).code);
        verifyNoInteractions(pack);
    }
    @Test void insufficientFundsDoesNotConsumeDay() {
        when(pack.removeItems(any(Player.class),anyMap(),eq(AddType.ACTIVITY),anyString()))
                .thenReturn(new CommonResult<>(Code.NOT_ENOUGH));
        assertEquals(Code.NOT_ENOUGH,gifts.buy(ctx,1001,1,start).code);
        assertTrue(data().purchasedDays.isEmpty()); assertTrue(data().deliveries.isEmpty());
        verify(pack,never()).addItems(anyLong(),anyMap(),any(),anyString(),anyBoolean());
    }
    @Test void grandUnlockAndRepeatedClaim() {
        assertEquals(Code.REPEAT_OP,gifts.claim(ctx,1001,start).code);
        for (int n=1;n<=3;n++) assertEquals(Code.SUCCESS,gifts.buy(ctx,1001,n,start+(n-1)*86_400_000L).code);
        assertEquals(1,gifts.list(ctx,0,start+2*86_400_000L).activities.getFirst().grandStatus);
        assertEquals(Code.SUCCESS,gifts.claim(ctx,1001,start+2*86_400_000L).code);
        assertEquals(Code.REPEAT_OP,gifts.claim(ctx,1001,start+2*86_400_000L).code);
        assertTrue(data().grandClaimed);
        gifts.settle(data(),end);
        verifyNoInteractions(mail);
    }
    @Test void viewingClearsRedDotOnlyForThisPeriod() {
        assertTrue(gifts.list(ctx,0,start).activities.getFirst().redDot);
        assertFalse(gifts.list(ctx,1001,start).activities.getFirst().redDot);
        assertFalse(gifts.list(ctx,0,start+86_400_000L).activities.getFirst().redDot);
        assertTrue(gifts.list(ctx,0,end).activities.isEmpty());
    }
    @Test void failedDeliveryIsMailedAtExpiryAndNeverMailsUnpurchasedChests() {
        when(pack.addItems(anyLong(),anyMap(),eq(AddType.ACTIVITY),anyString(),eq(true)))
                .thenReturn(new CommonResult<>(Code.FAIL));
        assertEquals(Code.SUCCESS,gifts.buy(ctx,1001,1,start).code);
        assertEquals("PENDING",data().deliveries.get("day-1").status);
        gifts.settle(data(),end); gifts.settle(data(),end+1);
        verify(mail,times(1)).addMailIfAbsent(eq(7L),anyString(),anyString(),anyList(),eq(AddType.ACTIVITY),endsWith(":day-1"));
        assertTrue(data().settled); assertFalse(data().grandClaimed);
    }
    @Test void completeUnclaimedGrandMailedExactlyOnce() {
        for(int n=1;n<=3;n++) gifts.buy(ctx,1001,n,start+(n-1)*86_400_000L);
        gifts.settle(data(),end); gifts.settle(data(),end+1);
        verify(mail,times(1)).addMailIfAbsent(eq(7L),anyString(),anyString(),anyList(),eq(AddType.ACTIVITY),endsWith(":grand"));
        assertTrue(data().grandClaimed);
        assertEquals(Code.FORBID,gifts.claim(ctx,1001,end).code);
    }
    @Test void failedMailRetainsPendingForRetry() {
        for(int n=1;n<=3;n++) gifts.buy(ctx,1001,n,start+(n-1)*86_400_000L);
        when(mail.addMailIfAbsent(anyLong(),anyString(),anyString(),anyList(),any(),anyString())).thenReturn(false,true);
        gifts.settle(data(),end); assertFalse(data().settled); assertFalse(data().grandClaimed);
        gifts.settle(data(),end+1); assertTrue(data().settled); assertTrue(data().grandClaimed);
    }
    @Test void unknownBagResultDoesNotBlindlyRepeatOrMail() {
        when(pack.addItems(anyLong(),anyMap(),eq(AddType.ACTIVITY),anyString(),eq(true)))
                .thenThrow(new IllegalStateException("connection lost after write"));
        assertThrows(IllegalStateException.class,()->gifts.buy(ctx,1001,1,start));
        assertEquals("DELIVERING",data().deliveries.get("day-1").status);
        assertEquals(Code.REPEAT_OP,gifts.buy(ctx,1001,1,start).code);
        gifts.settle(data(),end); assertFalse(data().settled); verifyNoInteractions(mail);
    }
    @Test void cashCannotBeBoughtThroughCurrencyRequest() {
        when(definition.chest(1).priceType()).thenReturn(1);
        assertEquals(Code.PARAM_ERROR,gifts.buy(ctx,1001,1,start).code);
        verifyNoInteractions(pack);
    }
    Order cashOrder() {
        when(definition.chest(1).priceType()).thenReturn(1);
        var shop = mock(ShopRechargeListCfg.class);
        when(shop.getGoogleShopId()).thenReturn("gift.sku.1");
        when(config.shop(anyInt())).thenReturn(shop);
        var r=gifts.reserve(ctx,definition,1,start);
        assertNotNull(r);
        Order order=new Order(); order.setPlayerId(7); order.setId("order-a");
        order.setProductId(r.token); order.setChannelProductId("gift.sku.1");
        order.setPrice(BigDecimal.valueOf(100,2)); return order;
    }
    @Test void paidCallbackIsIdempotentAndUsesSuccessDay() {
        Order order=cashOrder(); long now=time("2026-10-02T01:00:00");
        assertTrue(gifts.paid(player,order,now)); assertTrue(gifts.paid(player,order,now+1));
        assertEquals(List.of(1),data().purchasedDays);
        assertEquals(Code.REPEAT_OP,gifts.buy(ctx,1001,2,now+2).code);
        verify(pack,times(1)).addItems(anyLong(),anyMap(),eq(AddType.ACTIVITY),anyString(),eq(true));
    }
    @Test void callbackRejectsForeignPlayerWrongPriceAndDifferentOrder() {
        Order order=cashOrder(); order.setPlayerId(8); assertFalse(gifts.paid(player,order,start));
        order.setPlayerId(7); order.setPrice(BigDecimal.TEN); assertFalse(gifts.paid(player,order,start));
        order.setPrice(BigDecimal.ONE); order.setChannelProductId("cheap.sku"); assertFalse(gifts.paid(player,order,start));
        order.setChannelProductId("gift.sku.1");
        order.setPrice(BigDecimal.ONE); assertTrue(gifts.paid(player,order,start));
        order.setId("another-order"); assertFalse(gifts.paid(player,order,start));
    }
    @Test void latePaidCallbackUsesSnapshotAndMailEvenWithoutLiveConfig() {
        Order order=cashOrder(); when(config.find(1001)).thenReturn(null);
        assertTrue(gifts.paid(player,order,end)); assertTrue(gifts.paid(player,order,end+1));
        verifyNoInteractions(pack);
        verify(mail,times(1)).addMailIfAbsent(eq(7L),anyString(),anyString(),anyList(),eq(AddType.ACTIVITY),endsWith(":day-1"));
    }
    @Test void orderRetryUsesSameReservationAndFailedCreationCanRelease() {
        Order order=cashOrder();
        assertEquals(order.getProductId(),gifts.reserve(ctx,definition,1,start+1).token);
        gifts.release(7,order.getProductId()); assertNull(data().reservation);
        assertNotEquals(order.getProductId(),gifts.reserve(ctx,definition,1,start+2).token);
    }
    @Test void unavailableLockDoesNotMutate() {
        doReturn(null).when(locks).lockAndGet(anyString(),anyInt(),any());
        assertEquals(Code.FAIL,gifts.buy(ctx,1001,1,start).code);
        verifyNoInteractions(pack,dao);
    }
}
