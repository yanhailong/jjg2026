package com.jjg.game.season.service;

import com.jjg.game.common.redis.RedisLock;
import com.jjg.game.core.constant.*;
import com.jjg.game.core.data.*;
import com.jjg.game.core.data.Order;
import com.jjg.game.core.service.*;
import com.jjg.game.core.utils.ItemUtils;
import com.jjg.game.sampledata.bean.ShopRechargeListCfg;
import com.jjg.game.season.config.*;
import com.jjg.game.season.dao.LuckyDrawDao;
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

class LuckyDrawServiceTest {
    final long start = LocalDate.of(2026,10,1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    final long day = 86400000L, end = start + 14 * day;
    final LuckyDrawCatalog catalog = mock(LuckyDrawCatalog.class);
    final LuckyDrawDao dao = mock(LuckyDrawDao.class);
    final RedisLock locks = mock(RedisLock.class);
    final LuckyDrawRandom random = mock(LuckyDrawRandom.class);
    final PlayerPackService pack = mock(PlayerPackService.class);
    final MailService mail = mock(MailService.class);
    final SimPlayerContext ctx = mock(SimPlayerContext.class);
    final Player player = new Player();
    final SeasonPlayerData season = new SeasonPlayerData();
    final Map<String,LuckyDrawData> store = new HashMap<>();
    final TreeSet<Long> logins = new TreeSet<>();
    LuckyDrawService service;
    MockedStatic<ItemUtils> items;
    static LuckyDrawDefinition definition(int priceType, int stage) {
        var rewards = new ArrayList<LuckyDrawDefinition.Reward>();
        for(int i=1;i<=13;i++) rewards.add(new LuckyDrawDefinition.Reward(i,i,false,Map.of(100,10L)));
        rewards.add(new LuckyDrawDefinition.Reward(14,0,true,Map.of()));
        return new LuckyDrawDefinition(1000+stage,stage,"draw","",priceType,100,9,3,rewards,
                List.of(new LuckyDrawDefinition.Grand(1,Map.of(200,1L)),new LuckyDrawDefinition.Grand(2,Map.of(201,2L))),Map.of(300,5L));
    }
    @BeforeEach void setup() {
        player.setId(7); player.setChannel(ChannelType.GOOGLE);
        when(ctx.playerId()).thenReturn(7L);when(ctx.getPlayer()).thenReturn(player);
        season.setPhase("LOOP");season.setCycleIndex(1);season.setSeasonKey("loop-1");season.setStartTime(start);season.setEndTime(end);
        when(ctx.getSeasonPlayerData()).thenReturn(season);
        when(locks.lockAndGet(anyString(),anyInt(),any())).thenAnswer(i->((Supplier<?>)i.getArgument(2)).get());
        when(dao.get(anyString())).thenAnswer(i->store.get(i.getArgument(0)));
        doAnswer(i->{LuckyDrawData d=i.getArgument(0);store.put(d.id,d);return null;}).when(dao).save(any());
        when(dao.loginDays(anyLong(),anyLong(),anyLong(),anyLong())).thenAnswer(i->logins.stream()
                .filter(d->d>=(long)i.getArgument(1)&&d<(long)i.getArgument(2)&&d>(long)i.getArgument(3)).toList());
        when(catalog.stage(anyInt())).thenReturn(definition(2,1));
        when(pack.removeItems(any(Player.class),anyMap(),eq(AddType.ACTIVITY),anyString())).thenReturn(new CommonResult<>(Code.SUCCESS));
        when(pack.addItems(anyLong(),anyMap(),eq(AddType.ACTIVITY),anyString(),eq(true))).thenReturn(new CommonResult<>(Code.SUCCESS));
        when(mail.addMailIfAbsent(anyLong(),anyString(),anyString(),anyList(),eq(AddType.ACTIVITY),anyString())).thenReturn(true);
        items=mockStatic(ItemUtils.class);items.when(ItemUtils::getDiamondItemId).thenReturn(900);
        service=new LuckyDrawService(catalog,dao,locks,random,pack,mail);
        logins.add(start);
    }
    @AfterEach void close(){items.close();}
    LuckyDrawData data(){return store.get("7:loop-1:1");}
    @Test void actualLoginDaysOnlyAndCapFour() {
        assertEquals(1,service.info(ctx,0,1,false,start).info.availableDraws);
        assertEquals(1,service.info(ctx,0,1,false,start+5*day).info.availableDraws);
        for(int n=1;n<=6;n++)logins.add(start+n*day);
        assertEquals(4,service.info(ctx,0,1,false,start+6*day).info.availableDraws);
        for(int n=0;n<4;n++)assertEquals(Code.SUCCESS,service.buy(ctx,1001,1,"r"+n,start+6*day).code);
        assertEquals(Code.FORBID,service.buy(ctx,1001,1,"fifth",start+6*day).code);
    }
    @Test void redrawReplayDoesNotChargeOrRollAgain() {
        var first=service.buy(ctx,1001,1,"one",start);
        var replay=service.buy(ctx,1001,1,"one",start+1);
        assertTrue(replay.replay);assertEquals(first.rewardId,replay.rewardId);
        verify(random).nextIndex(14);verify(pack).removeItems(eq(player),eq(Map.of(900,100L)),eq(AddType.ACTIVITY),anyString());
        verify(pack).addItems(eq(7L),anyMap(),eq(AddType.ACTIVITY),anyString(),eq(true));
    }
    @Test void fourteenDistinctSlotsAndNoFifteenthCharge() {
        for(int n=0;n<14;n++){logins.add(start+n*day);assertEquals(n+1,service.buy(ctx,1001,1,"r"+n,start+n*day).rewardId);}
        assertEquals(14,data().obtainedRewards.size());
        assertEquals(Code.FORBID,service.buy(ctx,1001,1,"extra",end-1).code);
        verify(pack,times(14)).removeItems(any(Player.class),anyMap(),eq(AddType.ACTIVITY),anyString());
    }
    @Test void grandUsesCurrentSelectionAndLocksAfterWin() {
        assertEquals(Code.SUCCESS,service.select(ctx,1001,1,2,start).code);
        when(random.nextIndex(14)).thenReturn(13);
        var r=service.buy(ctx,1001,1,"core",start);assertEquals(2,r.grandId);
        assertEquals(Map.of(201,2L),data().receipts.get("draw-core").items);
        assertEquals(Code.FORBID,service.select(ctx,1001,1,1,start).code);
    }
    @Test void insufficientFundsRestoresEligibility() {
        when(pack.removeItems(any(Player.class),anyMap(),eq(AddType.ACTIVITY),anyString())).thenReturn(new CommonResult<>(Code.NOT_ENOUGH));
        assertEquals(Code.NOT_ENOUGH,service.buy(ctx,1001,1,"r",start).code);
        assertEquals(1,data().availableDraws);assertTrue(data().receipts.isEmpty());verifyNoInteractions(random);
    }
    @Test void unknownDebitBlocksRetryAndOtherDraws() {
        when(pack.removeItems(any(Player.class),anyMap(),eq(AddType.ACTIVITY),anyString())).thenReturn(null);
        assertEquals(Code.FAIL,service.buy(ctx,1001,1,"r",start).code);
        assertEquals(Code.FAIL,service.buy(ctx,1001,1,"r",start).code);
        logins.add(start+day);assertEquals(Code.FORBID,service.buy(ctx,1001,1,"other",start+day).code);
        verify(pack).removeItems(any(Player.class),anyMap(),eq(AddType.ACTIVITY),anyString());verifyNoInteractions(random);
    }
    @Test void dailyOncePerMidnightIndependentOfDrawBalance() {
        assertEquals(Code.SUCCESS,service.daily(ctx,1001,1,start+day-1).code);
        assertTrue(service.daily(ctx,1001,1,start+day-1).replay);
        assertFalse(service.daily(ctx,1001,1,start+day).replay);
        verify(pack,times(2)).addItems(eq(7L),eq(Map.of(300,5L)),eq(AddType.ACTIVITY),anyString(),eq(true));
        verify(pack,never()).removeItems(any(Player.class),anyMap(),eq(AddType.ACTIVITY),anyString());
    }
    @Test void enteringClearsDotUntilNextRealLoginDay() {
        assertTrue(service.info(ctx,0,1,false,start).info.redDot);
        assertFalse(service.info(ctx,0,1,true,start).info.redDot);
        assertFalse(service.info(ctx,0,1,false,start+day).info.redDot);
        logins.add(start+day);assertTrue(service.info(ctx,0,1,false,start+day).info.redDot);
    }
    @Test void invalidTypeAndExpiredSeasonCannotBuy() {
        assertEquals(Code.NOT_FOUND,service.buy(ctx,1001,2,"r",start).code);
        assertEquals(Code.NOT_FOUND,service.buy(ctx,1001,1,"r",end).code);
        verifyNoInteractions(pack);
    }
    @Test void newSeasonGetsFreshSnapshot() {
        service.buy(ctx,1001,1,"r",start);
        season.setSeasonKey("loop-2");season.setCycleIndex(2);season.setStartTime(end);season.setEndTime(end+14*day);
        logins.add(end);var r=service.info(ctx,0,1,false,end);
        assertEquals(0,r.info.totalDrawn);assertEquals(1,r.info.availableDraws);assertEquals(2,store.size());
    }
    void cash(){when(catalog.stage(anyInt())).thenReturn(definition(1,1));var shop=mock(ShopRechargeListCfg.class);when(shop.getGoogleShopId()).thenReturn("sku");when(catalog.shop(9)).thenReturn(shop);}
    Order order(String token){var o=new Order();o.setId("paid-1");o.setPlayerId(7);o.setProductId(token);o.setChannelProductId("sku");o.setPrice(new BigDecimal("1.00"));return o;}
    @Test void cashReservationAndCallbackAreIdempotent() {
        cash();var r=service.reserve(ctx,1001,1,"r",start);assertNotNull(r);
        assertSame(r,service.reserve(ctx,1001,1,"r",start));assertNull(service.reserve(ctx,1001,1,"other",start));
        assertTrue(service.paid(player,order(r.token),start));assertTrue(service.paid(player,order(r.token),start));
        verify(random).nextIndex(14);verify(pack,never()).removeItems(any(Player.class),anyMap(),eq(AddType.ACTIVITY),anyString());
    }
    @Test void badCashPriceOrSkuCannotDraw() {
        cash();var r=service.reserve(ctx,1001,1,"r",start);var o=order(r.token);
        o.setPrice(BigDecimal.TEN);assertFalse(service.paid(player,o,start));
        o.setPrice(BigDecimal.ONE);o.setChannelProductId("wrong");assertFalse(service.paid(player,o,start));verifyNoInteractions(random);
    }
    @Test void heldCashTicketCountsTowardDailyCapAndCanRelease() {
        cash();var r=service.reserve(ctx,1001,1,"r",start);
        for(int n=1;n<6;n++)logins.add(start+n*day);
        assertEquals(3,service.info(ctx,0,1,false,start+5*day).info.availableDraws);
        service.release(7,r.token);assertEquals(4,data().availableDraws);
        service.release(7,r.token);assertEquals(4,data().availableDraws);
    }
    @Test void latePaidCallbackMailsOldSeasonRewardOnly() {
        cash();var r=service.reserve(ctx,1001,1,"r",start);
        assertTrue(service.paid(player,order(r.token),end));assertTrue(service.paid(player,order(r.token),end+1));
        assertEquals("MAIL",data().receipts.get("draw-r").status);
        verify(mail).addMailIfAbsent(eq(7L),anyString(),anyString(),anyList(),eq(AddType.ACTIVITY),contains("loop-1"));verifyNoInteractions(pack);
    }
    @Test void knownFailedDeliveryRetriesSamePrizeWithoutCharge() {
        when(pack.addItems(anyLong(),anyMap(),eq(AddType.ACTIVITY),anyString(),eq(true)))
                .thenReturn(new CommonResult<>(Code.FAIL),new CommonResult<>(Code.SUCCESS));
        assertTrue(service.buy(ctx,1001,1,"r",start).rewardPending);
        assertFalse(service.buy(ctx,1001,1,"r",start).rewardPending);
        verify(random).nextIndex(14);verify(pack).removeItems(any(Player.class),anyMap(),eq(AddType.ACTIVITY),anyString());
    }
    @Test void expiryMailsOnlyAlreadyEarnedRewards() {
        when(pack.addItems(anyLong(),anyMap(),eq(AddType.ACTIVITY),anyString(),eq(true))).thenReturn(new CommonResult<>(Code.FAIL));
        service.buy(ctx,1001,1,"r",start);
        when(dao.expired(eq(end),isNull())).thenReturn(List.of(data()));when(dao.expired(eq(end),eq(data().id))).thenReturn(List.of());
        service.settleExpired(end);service.settleExpired(end);
        assertTrue(data().settled);assertEquals(1,data().receipts.size());
        verify(mail).addMailIfAbsent(eq(7L),anyString(),anyString(),anyList(),eq(AddType.ACTIVITY),anyString());
    }
    @Test void catalogDisabledWithoutSourceAndFinalStageRepeats() {
        assertNull(new LuckyDrawCatalog(List.of()).stage(1));
        var catalog=new LuckyDrawCatalog(List.of(()->List.of(definition(2,1),definition(2,2))));
        assertEquals(1002,catalog.stage(30).activityId());
        assertThrows(IllegalArgumentException.class,()->new LuckyDrawCatalog(List.of(()->List.of(definition(2,2)))).stage(1));
    }
    @Test void offlineTickDoesNotGrantLogin() {
        var presence=new LuckyDrawPresence(dao,catalog);presence.onTick(ctx,start);
        verify(dao,never()).recordLogin(anyLong(),anyLong());
        presence.authenticatedRequest(ctx,start);verify(dao).recordLogin(7,start);
    }
    @Test void orderRequestParsingRejectsSkinAndUnsafeNonce() {
        assertNotNull(LuckyDrawOrderService.parse("1001_1_nonce-1"));
        assertNull(LuckyDrawOrderService.parse("1001_2_nonce"));assertNull(LuckyDrawOrderService.parse("1001_1_a.b"));
    }
}
