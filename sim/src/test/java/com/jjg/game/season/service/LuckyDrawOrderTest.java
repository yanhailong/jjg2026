package com.jjg.game.season.service;

import com.jjg.game.core.dao.*;
import com.jjg.game.core.data.*;
import com.jjg.game.core.pb.RechargeType;
import com.jjg.game.core.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LuckyDrawOrderTest {
    @Test void skuAndBusinessTokenPersistSeparatelyAndRetriesReuseOrder() {
        var orders=mock(OrderDao.class);var accounts=mock(AccountDao.class);
        var service=new OrderService();ReflectionTestUtils.setField(service,"orderDao",orders);ReflectionTestUtils.setField(service,"accountDao",accounts);
        var player=new Player();player.setId(7);var account=new Account();account.setChannel(ChannelType.GOOGLE);
        when(accounts.queryAccountByPlayerId(7)).thenReturn(account);
        when(orders.insertContinuousGiftOrder(any())).thenAnswer(i->i.getArgument(0));
        String token="7:loop-1:1/nonce";
        var first=service.generateOrder(player,PayType.GOOGLE,token,BigDecimal.ONE,RechargeType.SEASON_LUCKY_DRAW,"","google-sku");
        assertNotNull(first);assertEquals(token,first.getProductId());assertEquals("google-sku",first.getChannelProductId());
        assertEquals(OrderService.luckyDrawOrderId(7,token),first.getId());
        when(orders.getOrderById(first.getId())).thenReturn(first);
        assertSame(first,service.generateOrder(player,PayType.GOOGLE,token,BigDecimal.ONE,RechargeType.SEASON_LUCKY_DRAW,"","google-sku"));
        verify(orders).insertContinuousGiftOrder(any());
    }
}
