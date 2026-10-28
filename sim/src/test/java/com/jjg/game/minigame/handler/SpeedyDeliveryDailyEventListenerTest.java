package com.jjg.game.minigame.handler;

import com.jjg.game.common.concurrent.BaseHandler;
import com.jjg.game.common.concurrent.PlayerExecutorGroupDisruptor;
import com.jjg.game.common.concurrent.PlayerWorker;
import com.jjg.game.core.base.gameevent.ClockEvent;
import com.jjg.game.core.base.gameevent.EGameEventType;
import com.jjg.game.core.base.gameevent.GameEvent;
import com.jjg.game.minigame.service.SpeedyDeliveryService;
import com.jjg.game.sim.data.SimPlayerContext;
import com.jjg.game.sim.manager.SimPlayerContextRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 验证飞驰快送零点赠送进入玩家队列，且不会发给已离开的旧会话。 */
class SpeedyDeliveryDailyEventListenerTest {
    private static final long PLAYER_ID = 123L;

    private MockedConstruction<PlayerWorker> workerConstruction;
    private MockedStatic<PlayerExecutorGroupDisruptor> defaultExecutor;
    private PlayerExecutorGroupDisruptor executor;
    private PlayerWorker worker;
    private SimPlayerContextRegistry registry;
    private SpeedyDeliveryService service;
    private SpeedyDeliveryDailyEventListener listener;
    private SimPlayerContext ctx;

    @BeforeEach
    void setUp() {
        //默认执行器初始化时会创建worker，隔离替身防止测试启动真实玩家线程。
        workerConstruction = mockConstruction(PlayerWorker.class);
        defaultExecutor = mockStatic(PlayerExecutorGroupDisruptor.class);
        executor = mock(PlayerExecutorGroupDisruptor.class);
        worker = mock(PlayerWorker.class);
        defaultExecutor.when(PlayerExecutorGroupDisruptor::getDefaultExecutor).thenReturn(executor);
        when(executor.getPlayerWorker(PLAYER_ID)).thenReturn(worker);
        registry = new SimPlayerContextRegistry();
        service = mock(SpeedyDeliveryService.class);
        listener = new SpeedyDeliveryDailyEventListener(registry, service);
        ctx = new SimPlayerContext();
        ctx.setPlayerId(PLAYER_ID);
        registry.putContext(ctx);
    }

    @AfterEach
    void tearDown() {
        defaultExecutor.close();
        workerConstruction.close();
    }

    @Test
    void midnightClockQueuesGrantUsingCurrentTime() throws Exception {
        assertEquals(List.of(EGameEventType.CLOCK_EVENT), listener.needMonitorEvents());
        long before = System.currentTimeMillis();

        listener.handleEvent(new ClockEvent(EGameEventType.CLOCK_EVENT, 0));

        long after = System.currentTimeMillis();
        verifyNoInteractions(service);
        queuedTask().action();
        ArgumentCaptor<Long> now = ArgumentCaptor.forClass(Long.class);
        verify(service).onDaily(eq(ctx), now.capture());
        assertTrue(now.getValue() >= before && now.getValue() <= after);
    }

    @Test
    void businessDateUsesLocalStartOfDay() throws Exception {
        LocalDate businessDate = LocalDate.of(2026, 10, 23);
        long expected = businessDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();

        listener.handleEvent(new ClockEvent(EGameEventType.CLOCK_EVENT, 0, businessDate));
        queuedTask().action();

        verify(service).onDaily(ctx, expected);
    }

    @Test
    void otherHoursAndNonClockEventsDoNotDispatch() {
        listener.handleEvent(new ClockEvent(EGameEventType.CLOCK_EVENT, 12));
        listener.handleEvent(new GameEvent(EGameEventType.CLOCK_EVENT));

        verifyNoInteractions(executor, worker, service);
    }

    @Test
    void replacedContextDoesNotReceiveQueuedGrant() throws Exception {
        listener.handleEvent(new ClockEvent(EGameEventType.CLOCK_EVENT, 0));
        BaseHandler<?> task = queuedTask();
        SimPlayerContext replacement = new SimPlayerContext();
        replacement.setPlayerId(PLAYER_ID);
        registry.putContext(replacement);

        task.action();

        verifyNoInteractions(service);
    }

    @Test
    void removedContextDoesNotReceiveQueuedGrant() throws Exception {
        listener.handleEvent(new ClockEvent(EGameEventType.CLOCK_EVENT, 0));
        BaseHandler<?> task = queuedTask();
        registry.removeContext(PLAYER_ID);

        task.action();

        verifyNoInteractions(service);
    }

    private BaseHandler<?> queuedTask() {
        ArgumentCaptor<BaseHandler<?>> task = ArgumentCaptor.captor();
        verify(worker).publishBlocking(eq(0), task.capture());
        return task.getValue();
    }
}
