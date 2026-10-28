package com.jjg.game.sim.manager;

import com.jjg.game.core.constant.Code;
import com.jjg.game.core.data.ExitType;
import com.jjg.game.minigame.dao.SpeedyDeliveryDao;
import com.jjg.game.minigame.data.SpeedyDeliveryData;
import com.jjg.game.minigame.data.SpeedyDeliveryRound;
import com.jjg.game.minigame.service.SpeedyDeliveryService;
import com.jjg.game.season.service.SeasonService;
import com.jjg.game.sim.dao.*;
import com.jjg.game.sim.data.*;
import com.jjg.game.sim.service.SimAutoSaveService;
import com.jjg.game.sim.service.SimNodeService;
import com.jjg.game.sim.service.SlotsStatisticsService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SpeedyDeliveryLifecycleTest {
    private final SimManager manager = spy(new SimManager());
    private final SimPlayerContextRegistry registry = new SimPlayerContextRegistry();
    private final SpeedyDeliveryService delivery = mock(SpeedyDeliveryService.class);
    private final SimAutoSaveService saves = mock(SimAutoSaveService.class);
    private final SimPlayerContext ctx = new SimPlayerContext();

    SpeedyDeliveryLifecycleTest() {
        ctx.setPlayerId(123);
        ctx.setSpeedyDeliveryRound(new SpeedyDeliveryRound(null, 0));
        ctx.setSimBaseData(new SimBaseData());
        ctx.setCurrentCasino(new SimCasinoData());
        registry.putContext(ctx);
        ReflectionTestUtils.setField(manager, "simPlayerContextRegistry", registry);
        ReflectionTestUtils.setField(manager, "speedyDeliveryService", delivery);
        ReflectionTestUtils.setField(manager, "autoSaveService", saves);
        ReflectionTestUtils.setField(manager, "seasonService", mock(SeasonService.class));
    }

    @Test
    void leavingCasinoDoesNotSettleRound() {
        SpeedyDeliveryRound round = ctx.getSpeedyDeliveryRound();
        manager.exitCasino(123);
        assertFalse(ctx.getInCasino().get());
        assertSame(round, ctx.getSpeedyDeliveryRound());
        verifyNoInteractions(delivery);
    }

    @Test
    void finalLogoutSettlesBeforeUnloading() {
        when(delivery.onExit(ctx)).thenReturn(Code.SUCCESS);
        doNothing().when(manager).exitSaveData(123);
        assertTrue(manager.onExitGame(123, ExitType.DROPPED));
        var order = inOrder(delivery, manager);
        order.verify(delivery).onExit(ctx);
        order.verify(manager).exitSaveData(123);
    }

    @Test
    void failedFinalAwardDoesNotBlockOtherSimLogoutAndSaveWork() {
        when(delivery.onExit(ctx)).thenReturn(Code.FAIL);
        doNothing().when(manager).exitSaveData(123);
        assertTrue(manager.onExitGame(123, ExitType.DROPPED));
        verify(delivery).onExit(ctx);
        verify(manager).exitSaveData(123);
    }

    @Test
    void exitSavePersistsDailyMarkerAndNeverSerializesRound() {
        SpeedyDeliveryData marker = new SpeedyDeliveryData();
        marker.setPlayerId(123);
        ctx.setSpeedyDeliveryData(marker);
        SpeedyDeliveryDao dao = mock(SpeedyDeliveryDao.class);
        ReflectionTestUtils.setField(manager, "speedyDeliveryDao", dao);
        ReflectionTestUtils.setField(manager, "simPlayerGameDao", mock(SimPlayerGameDao.class));
        ReflectionTestUtils.setField(manager, "simCasinoDao", mock(SimCasinoDao.class));
        ReflectionTestUtils.setField(manager, "simEmployeeDao", mock(SimEmployeeDao.class));
        ReflectionTestUtils.setField(manager, "simSkillsDao", mock(SimSkillsDao.class));
        ReflectionTestUtils.setField(manager, "slotsStatisticsService", mock(SlotsStatisticsService.class));
        ReflectionTestUtils.setField(manager, "simNodeService", mock(SimNodeService.class));
        doAnswer(call -> { call.<Runnable>getArgument(0).run(); return null; }).when(saves).enqueueTask(any());
        manager.exitSaveData(123);
        assertNull(registry.getContext(123));
        verify(dao).save(marker);
        verifyNoInteractions(delivery);
    }
}
