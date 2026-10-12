package com.jjg.game.season.service;

import com.jjg.game.sampledata.GameDataManager;
import com.jjg.game.sampledata.bean.*;
import com.jjg.game.sampledata.container.*;
import com.jjg.game.core.manager.ConditionManager;
import com.jjg.game.core.data.Player;
import com.jjg.game.core.pb.RechargeType;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.*;
import java.time.ZoneId;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ContinuousGiftConfigTest {
    ActivityConfigCfgContainer activities;
    ConsecutivePackCfgContainer packs;
    GameFunctionCfgContainer functions;
    ShopRechargeListCfgContainer shops;
    ItemCfgContainer items;
    ConditionManager conditions;
    ContinuousGiftConfigService service;
    MockedStatic<GameDataManager> data;
    @BeforeEach void load() throws Exception {
        Path root=Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while(root!=null && !Files.exists(root.resolve("hall/resources/sample/ConsecutivePack.xlsx")))root=root.getParent();
        assertNotNull(root);String dir=root.resolve("hall/resources/sample").toString();
        activities=new ActivityConfigCfgContainer();activities.loadData(dir);
        packs=new ConsecutivePackCfgContainer();packs.loadData(dir);
        functions=new GameFunctionCfgContainer();functions.loadData(dir);
        shops=new ShopRechargeListCfgContainer();shops.loadData(dir);
        items=new ItemCfgContainer();items.loadData(dir);
        var manager=new GameDataManager();ReflectionTestUtils.setField(manager,"cfgBeanContainerRelator",Map.of(
                ActivityConfigCfg.class,activities,ConsecutivePackCfg.class,packs,GameFunctionCfg.class,functions));
        data=mockStatic(GameDataManager.class);
        data.when(GameDataManager::getInstance).thenReturn(manager);
        data.when(()->GameDataManager.getShopRechargeListCfg(anyInt())).thenAnswer(i->shops.getCfgBeanMap().get(i.getArgument(0)));
        data.when(()->GameDataManager.getItemCfg(anyInt())).thenAnswer(i->items.getCfgBeanMap().get(i.getArgument(0)));
        conditions=mock(ConditionManager.class);when(conditions.isAchievement(any(),anyString(),anyString())).thenReturn(true);
        service=new ContinuousGiftConfigService(conditions);
    }
    @AfterEach void close(){if(data!=null)data.close();}
    @Test void realSubmittedTablesProduceEightValidPeriodsAndActualCashPrice() {
        var definitions=service.definitions();assertEquals(8,definitions.size());
        var first=service.find(57062);assertNotNull(first);
        assertEquals(6001,first.function().getId());assertEquals(5,first.chests().size());
        assertEquals(10101,first.chest(1).id());assertEquals(1001,first.chest(1).channelCommodity());
        assertEquals(99L,first.chest(1).price());assertEquals(1,first.chest(1).priceType());
        assertEquals(50000L,first.chest(1).rewardItems().get(1960000));
        assertEquals(200000L,first.grand().rewardItems().get(1960000));
        assertEquals(30200,service.find(57069).grand().id());
        assertNotEquals(service.find(57064).period(),service.find(57065).period());
    }
    @Test void slashDatesAndExactBoundariesFollowActivityConfig() {
        var d=service.find(57062);assertTrue(d.active(d.start()));assertFalse(d.active(d.start()-1));
        assertTrue(d.active(d.end()-1));assertFalse(d.active(d.end()));
        assertEquals(ContinuousGiftConfigService.parseTime("2026-10-12 00:00:00",ZoneId.systemDefault()),d.start());
    }
    @Test void realConfiguredFiveCashPurchasesUnlockExactlyOneGrand() {
        var d=service.find(57062);
        var dao=mock(com.jjg.game.season.dao.ContinuousGiftDao.class);
        var locks=mock(com.jjg.game.common.redis.RedisLock.class);
        when(locks.lockAndGet(anyString(),anyInt(),any())).thenAnswer(i->((java.util.function.Supplier<?>)i.getArgument(2)).get());
        var saved=new HashMap<String,com.jjg.game.season.data.ContinuousGiftData>();
        when(dao.get(anyString())).thenAnswer(i->saved.get(i.getArgument(0)));
        doAnswer(i->{com.jjg.game.season.data.ContinuousGiftData v=i.getArgument(0);saved.put(v.id,v);return null;}).when(dao).save(any());
        var pack=mock(com.jjg.game.core.service.PlayerPackService.class);
        when(pack.addItems(anyLong(),anyMap(),any(),anyString(),eq(true))).thenReturn(new com.jjg.game.core.data.CommonResult<>(com.jjg.game.core.constant.Code.SUCCESS));
        var gifts=new ContinuousGiftService(service,dao,locks,pack,mock(com.jjg.game.core.service.MailService.class));
        var ctx=mock(com.jjg.game.sim.data.SimPlayerContext.class);var player=new Player();player.setId(7);
        player.setChannel(com.jjg.game.core.data.ChannelType.GOOGLE);
        when(ctx.playerId()).thenReturn(7L);when(ctx.getPlayer()).thenReturn(player);
        for(int day=1;day<=5;day++) {
            long now=d.start()+(day-1)*86400000L;
            var reservation=gifts.reserve(ctx,d,day,now);assertNotNull(reservation);assertEquals(99,reservation.price);
            assertEquals("google99",reservation.channelProductId);
            var order=new com.jjg.game.core.data.Order();order.setId("cash-"+day);order.setPlayerId(7);
            order.setProductId(reservation.token);order.setChannelProductId(reservation.channelProductId);order.setPrice(new java.math.BigDecimal("0.99"));
            assertTrue(gifts.paid(player,order,now));assertTrue(gifts.paid(player,order,now));
        }
        long now=d.start()+4*86400000L;
        assertEquals(com.jjg.game.core.constant.Code.SUCCESS,gifts.claim(ctx,57062,now).code);
        assertEquals(com.jjg.game.core.constant.Code.REPEAT_OP,gifts.claim(ctx,57062,now).code);
        verify(pack,times(6)).addItems(eq(7L),anyMap(),any(),anyString(),eq(true));
    }
    @Test void bothActivityAndFunctionConditionsProtectPurchases() {
        var p=new Player();var d=service.find(57062);assertTrue(service.allowed(p,d));
        when(conditions.isAchievement(p,"","simAllLevel(6)")).thenReturn(false);
        assertFalse(service.allowed(p,d));assertFalse(service.isActivityOpen(p,999));
    }
    @Test void disabledFunctionHidesAllPeriods() {
        ReflectionTestUtils.setField(functions.getCfgBeanMap().get(6001),"isOpen",false);
        assertTrue(service.definitions().isEmpty());
    }
    @Test void missingReferenceOrGrandOrMixedGroupRejectsWholePeriod() {
        var a=activities.getCfgBeanMap().get(57062);var f=functions.getCfgBeanMap().get(6001);
        var broken=new HashMap<>(packs.getCfgBeanMap());broken.remove(10103);
        assertThrows(IllegalArgumentException.class,()->service.build(a,f,broken));
        ReflectionTestUtils.setField(a,"value",List.of(10101,10102,10103,10104,10105));
        assertThrows(IllegalArgumentException.class,()->service.build(a,f,packs.getCfgBeanMap()));
        ReflectionTestUtils.setField(a,"value",List.of(10101,10102,10103,10104,10105,20200));
        assertThrows(IllegalArgumentException.class,()->service.build(a,f,packs.getCfgBeanMap()));
    }
    @Test void missingOrNonSequentialDaysAndShortWindowReject() {
        var a=activities.getCfgBeanMap().get(57062);var f=functions.getCfgBeanMap().get(6001);
        ReflectionTestUtils.setField(packs.getCfgBeanMap().get(10102),"PackQuantity",3);
        assertThrows(IllegalArgumentException.class,()->service.build(a,f,packs.getCfgBeanMap()));
        ReflectionTestUtils.setField(packs.getCfgBeanMap().get(10102),"PackQuantity",2);
        ReflectionTestUtils.setField(a,"time_end","2026/10/13 23:59:59");
        assertThrows(IllegalArgumentException.class,()->service.build(a,f,packs.getCfgBeanMap()));
    }
    @Test void missingProductOrNegativePriceRejectBeforeAnyCharge() {
        var a=activities.getCfgBeanMap().get(57062);var f=functions.getCfgBeanMap().get(6001);
        ReflectionTestUtils.setField(packs.getCfgBeanMap().get(10101),"Price",999999);
        assertThrows(IllegalArgumentException.class,()->service.build(a,f,packs.getCfgBeanMap()));
        assertThrows(IllegalArgumentException.class,()->ContinuousGiftConfigService.validateRewards(Map.of(1,-1L)));
    }
    @Test void chargeTypesDoNotCollideWithExistingFeatures() {
        var ids=new HashSet<Integer>();for(var t:RechargeType.values())assertTrue(ids.add(t.getType()));
        assertEquals(RechargeType.ACTIVE_PASS,RechargeType.valueOf(16));
        assertEquals(RechargeType.SHOP_MINI,RechargeType.valueOf(17));
        assertEquals(RechargeType.GOLD_TREASURE,RechargeType.valueOf(18));
        assertEquals(20,RechargeType.CONTINUOUS_GIFT.getType());
    }
    @Test void purchaseInputRequiresExactPositiveActivityAndDay() {
        assertArrayEquals(new int[]{57062,1},ContinuousGiftOrderService.parse("57062_1"));
        for(String s:List.of("","1_0","-1_1","1_1_2","1_x","1_","99999999999_1"))assertNull(ContinuousGiftOrderService.parse(s));
        assertNull(ContinuousGiftOrderService.parse(null));
    }
}
