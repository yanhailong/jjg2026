package com.jjg.game.season.service;

import com.jjg.game.core.dao.OrderDao;
import com.jjg.game.core.data.*;
import com.jjg.game.core.pb.RechargeType;
import com.jjg.game.core.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContinuousGiftOrderTest {
    @Test void newPreOrderContractKeepsMoneyPriceAndReservationToken() {
        var gifts=mock(ContinuousGiftService.class);
        when(gifts.locked(anyLong(),any())).thenAnswer(i->((java.util.function.Supplier<?>)i.getArgument(1)).get());
        var config=mock(ContinuousGiftConfigService.class);
        var contexts=mock(com.jjg.game.sim.manager.SimPlayerContextRegistry.class);
        var ctx=mock(com.jjg.game.sim.data.SimPlayerContext.class);
        var player=new Player();player.setId(7);player.setChannel(ChannelType.GOOGLE);
        when(contexts.getContext(7)).thenReturn(ctx);
        var definition=mock(ContinuousGiftConfigService.Definition.class);
        var chest=new ContinuousGiftConfigService.Chest(10101,1,1,99,1001,java.util.Map.of(1,1L));
        when(config.find(57062)).thenReturn(definition);when(definition.chest(1)).thenReturn(chest);
        var shop=mock(com.jjg.game.sampledata.bean.ShopRechargeListCfg.class);
        when(shop.getGoogleShopId()).thenReturn("google99");when(config.shop(1001)).thenReturn(shop);
        var reservation=new com.jjg.game.season.data.ContinuousGiftData.Reservation();
        reservation.token="7:57062:1234/reserved";reservation.price=99;
        when(gifts.reserve(eq(ctx),eq(definition),eq(1),anyLong())).thenReturn(reservation);
        var provider=new ContinuousGiftOrderService(gifts,config,contexts,mock(OrderDao.class));
        var req=new com.jjg.game.core.pb.ReqGenerateOrder();req.productId="57062_1";
        var result=provider.generateOrderDetailInfo(player,req);
        assertTrue(result.success());assertEquals(com.jjg.game.core.pb.RechargeCfgType.MONEY,result.data.getType());
        assertEquals(new BigDecimal("0.99"),result.data.getPrice());assertEquals(reservation.token,req.productId);
    }
    @Test void retryReturnsTheExistingPaymentOrderWithoutAnotherInsert() {
        OrderDao dao=mock(OrderDao.class);
        OrderService service=new OrderService(); ReflectionTestUtils.setField(service,"orderDao",dao);
        String token="7:1001:1234/token";
        Order original=new Order(); original.setId(OrderService.continuousGiftOrderId(7,token));
        when(dao.getOrderById(original.getId())).thenReturn(original);
        Order returned=service.generateOrder(7,ChannelType.GOOGLE,PayType.GOOGLE,token,
                BigDecimal.ONE,RechargeType.CONTINUOUS_GIFT,"");
        assertSame(original,returned);
        verify(dao,never()).insertContinuousGiftOrder(any());
    }
    @Test void deterministicOrderKeysSeparatePlayersAndReservations() {
        assertEquals(OrderService.continuousGiftOrderId(7,"token-a"),OrderService.continuousGiftOrderId(7,"token-a"));
        assertNotEquals(OrderService.continuousGiftOrderId(7,"token-a"),OrderService.continuousGiftOrderId(8,"token-a"));
        assertNotEquals(OrderService.continuousGiftOrderId(7,"token-a"),OrderService.continuousGiftOrderId(7,"token-b"));
    }
}
