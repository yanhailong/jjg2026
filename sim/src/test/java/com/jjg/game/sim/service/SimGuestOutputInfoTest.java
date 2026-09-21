package com.jjg.game.sim.service;

import com.jjg.game.alliance.service.AllianceEventService;
import com.jjg.game.common.utils.TimeHelper;
import com.jjg.game.core.constant.Code;
import com.jjg.game.core.data.CommonResult;
import com.jjg.game.core.data.PlayerController;
import com.jjg.game.core.pb.NotifyOpenFunction;
import com.jjg.game.core.service.PlayerPackService;
import com.jjg.game.core.utils.ItemUtils;
import com.jjg.game.sampledata.bean.CasinoStatsSheetCfg;
import com.jjg.game.sim.data.SimBaseData;
import com.jjg.game.sim.data.SimCasinoData;
import com.jjg.game.sim.data.SimPlayerContext;
import com.jjg.game.sim.pb.struct.DestinationInfo;
import com.jjg.game.sim.pb.struct.GuestInfo;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SimGuestOutputInfoTest {
    @Test
    void timeLimitNotificationFiresAtEightyPercentOncePerDay() {
        SimGuestService service = new SimGuestService();
        SimPlayerContext ctx = context();
        PlayerController pc = mock(PlayerController.class);
        ctx.setPlayerController(pc);
        CasinoStatsSheetCfg cfg = mock(CasinoStatsSheetCfg.class);
        when(cfg.getVisitorDailyLimit()).thenReturn(10);
        SimBaseData base = ctx.getSimBaseData();
        long now = System.currentTimeMillis();
        base.accumulateOnlineTime(now);
        base.setDailyOnlineTime(480_000 - 1);
        ReflectionTestUtils.invokeMethod(service, "notifyGuestOutputLimit", ctx, cfg, now);
        verifyNoInteractions(pc);
        assertFalse(base.isGuestOutputLimitNotified());

        base.setDailyOnlineTime(480_000);
        ReflectionTestUtils.invokeMethod(service, "notifyGuestOutputLimit", ctx, cfg, now);
        verify(pc).send(argThat(message -> message instanceof NotifyOpenFunction notify
                && notify.functionIdList.equals(List.of(5015))));
        assertTrue(base.isGuestOutputLimitNotified());
        ReflectionTestUtils.invokeMethod(service, "notifyGuestOutputLimit", ctx, cfg, now);
        verifyNoMoreInteractions(pc);

        SimPlayerContext reconnected = context();
        reconnected.setSimBaseData(base);
        reconnected.setPlayerController(pc);
        ReflectionTestUtils.invokeMethod(service, "notifyGuestOutputLimit", reconnected, cfg, now);
        verifyNoMoreInteractions(pc);

        long nextDay = TimeHelper.getDaysTime(now, 1);
        base.accumulateOnlineTime(nextDay);
        assertFalse(base.isGuestOutputLimitNotified());
        base.setDailyOnlineTime(480_000);
        ReflectionTestUtils.invokeMethod(service, "notifyGuestOutputLimit", reconnected, cfg, nextDay);
        verify(pc, times(2)).send(any(NotifyOpenFunction.class));
    }

    @Test
    void timeLimitNotificationSkipsZeroLimitAndMissingController() {
        SimGuestService service = new SimGuestService();
        SimPlayerContext ctx = context();
        CasinoStatsSheetCfg cfg = mock(CasinoStatsSheetCfg.class);
        long now = System.currentTimeMillis();
        ctx.getSimBaseData().accumulateOnlineTime(now);
        ctx.getSimBaseData().setDailyOnlineTime(600_000);
        when(cfg.getVisitorDailyLimit()).thenReturn(10);
        ReflectionTestUtils.invokeMethod(service, "notifyGuestOutputLimit", ctx, cfg, now);
        assertFalse(ctx.getSimBaseData().isGuestOutputLimitNotified());
        PlayerController pc = mock(PlayerController.class);
        ctx.setPlayerController(pc);
        when(cfg.getVisitorDailyLimit()).thenReturn(0);
        ReflectionTestUtils.invokeMethod(service, "notifyGuestOutputLimit", ctx, cfg, now);
        verifyNoInteractions(pc);
        assertFalse(ctx.getSimBaseData().isGuestOutputLimitNotified());
    }

    @Test
    void countsAndRewardsResetAtMidnightWithOnlineTime() {
        SimBaseData base = new SimBaseData();
        long now = System.currentTimeMillis();
        base.recordGuestGenerated(false, now);
        base.recordGuestGenerated(false, now);
        base.recordGuestGenerated(true, now);
        base.recordGuestOutputRewards(Map.of(1, 5L), now);
        base.recordGuestOutputRewards(Map.of(1, 7L, 2, 3L), now);
        assertEquals(2, base.getNormalGuestCount());
        assertEquals(1, base.getSpecialGuestCount());
        assertEquals(Map.of(1, 12L, 2, 3L), base.getGuestOutputRewards());

        long midnight = TimeHelper.getDaysTime(now, 1);
        base.setLoginTime(midnight - 1000);
        base.accumulateOnlineTime(midnight + 2000);
        assertEquals(2000, base.getDailyOnlineTime());
        assertEquals(0, base.getNormalGuestCount());
        assertEquals(0, base.getSpecialGuestCount());
        assertTrue(base.getGuestOutputRewards().isEmpty());
        base.recordGuestGenerated(true, midnight + 3000);
        assertEquals(1, base.getSpecialGuestCount());
    }

    @Test
    void responseUsesCurrentSceneLimitAndDoesNotConsumeCounters() {
        SimPlayerContext ctx = context();
        SimBaseData base = ctx.getSimBaseData();
        long now = System.currentTimeMillis();
        base.recordGuestGenerated(false, now);
        base.recordGuestGenerated(true, now);
        base.recordGuestOutputRewards(Map.of(123, 456L), now);
        base.setDailyOnlineTime(123456);
        SimGuestService service = serviceWithLimit(90);

        var res = service.guestOutputInfo(ctx);
        assertEquals(Code.SUCCESS, res.code);
        assertEquals(123, res.allTime);
        assertEquals(5400, res.timeLimit);
        assertEquals(1, res.normalGuestCount);
        assertEquals(1, res.specialGuestCount);
        assertEquals(123, res.allRewards.getFirst().itemId);
        assertEquals(456L, res.allRewards.getFirst().count);
        assertEquals(1, service.guestOutputInfo(ctx).normalGuestCount);
    }

    @Test
    void newPlayerReturnsEmptyRewards() {
        var res = serviceWithLimit(60).guestOutputInfo(context());
        assertEquals(Code.SUCCESS, res.code);
        assertEquals(0, res.allTime);
        assertEquals(0, res.normalGuestCount);
        assertEquals(0, res.specialGuestCount);
        assertTrue(res.allRewards.isEmpty());
    }

    @Test
    void cachedRewardsCountOnlyAfterSuccessfulCreditWithoutCountingGuestAgain() {
        SimPlayerContext ctx = context();
        SimGuestService service = new SimGuestService();
        PlayerPackService pack = mock(PlayerPackService.class);
        ReflectionTestUtils.setField(service, "playerPackService", pack);
        ReflectionTestUtils.setField(service, "allianceEventService", mock(AllianceEventService.class));
        when(pack.addItems(anyLong(), anyMap(), any(), isNull(), eq(false)))
                .thenReturn(new CommonResult<>(Code.FAIL)).thenReturn(new CommonResult<>(Code.SUCCESS));
        GuestInfo guest = new GuestInfo();
        DestinationInfo destination = new DestinationInfo();
        destination.rewards = List.of(ItemUtils.buildItemInfo(123, 99L));
        guest.destinations = List.of(destination);

        try (var itemUtils = mockStatic(ItemUtils.class)) {
            itemUtils.when(ItemUtils::getGoldItemId).thenReturn(123);
            service.handCacheGuest(ctx, guest);
            assertNull(ctx.getSimBaseData().getGuestOutputRewards());
            service.handCacheGuest(ctx, guest);
        }
        assertEquals(Map.of(123, 99L), ctx.getSimBaseData().getGuestOutputRewards());
        assertEquals(0, ctx.getSimBaseData().getNormalGuestCount());
    }

    private SimGuestService serviceWithLimit(int minutes) {
        SimGuestService service = new SimGuestService();
        SimConfigCacheService config = mock(SimConfigCacheService.class);
        CasinoStatsSheetCfg cfg = mock(CasinoStatsSheetCfg.class);
        when(cfg.getVisitorDailyLimit()).thenReturn(minutes);
        when(config.getCasinoStatsSheetCfg(anyInt(), anyInt())).thenReturn(cfg);
        ReflectionTestUtils.setField(service, "configCache", config);
        return service;
    }

    private SimPlayerContext context() {
        SimPlayerContext ctx = new SimPlayerContext();
        ctx.setPlayerId(123L);
        ctx.setSimBaseData(new SimBaseData());
        ctx.setCurrentCasino(new SimCasinoData());
        return ctx;
    }
}
