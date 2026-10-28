package com.jjg.game.minigame.handler;

import com.jjg.game.common.concurrent.BaseHandler;
import com.jjg.game.common.concurrent.PlayerExecutorGroupDisruptor;
import com.jjg.game.core.base.gameevent.ClockEvent;
import com.jjg.game.core.base.gameevent.EGameEventType;
import com.jjg.game.core.base.gameevent.GameEvent;
import com.jjg.game.core.base.gameevent.GameEventListener;
import com.jjg.game.minigame.service.SpeedyDeliveryService;
import com.jjg.game.sim.data.SimPlayerContext;
import com.jjg.game.sim.manager.SimPlayerContextRegistry;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/** 零点赠送沿用玩家串行队列，与开局扣费保持顺序。 */
@Component
public class SpeedyDeliveryDailyEventListener implements GameEventListener {
    private final SimPlayerContextRegistry contextRegistry;
    private final SpeedyDeliveryService service;

    public SpeedyDeliveryDailyEventListener(SimPlayerContextRegistry contextRegistry, SpeedyDeliveryService service) {
        this.contextRegistry = contextRegistry;
        this.service = service;
    }

    @Override
    public <T extends GameEvent> void handleEvent(T gameEvent) {
        if (!(gameEvent instanceof ClockEvent event) || event.getHour() != 0) {
            return;
        }
        long now = event.getBusinessDate() == null ? System.currentTimeMillis()
                : event.getBusinessDate().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        for (Map.Entry<Long, SimPlayerContext> entry : contextRegistry.getContextMap().entrySet()) {
            long playerId = entry.getKey();
            SimPlayerContext scheduledContext = entry.getValue();
            PlayerExecutorGroupDisruptor.getDefaultExecutor().getPlayerWorker(playerId).publishBlocking(0,
                    new BaseHandler<String>() {
                        @Override
                        public void action() {
                            if (contextRegistry.getContext(playerId) == scheduledContext) {
                                service.onDaily(scheduledContext, now);
                            }
                        }
                    }.setHandlerParamWithSelf("speedy delivery daily grant"));
        }
    }

    @Override
    public List<EGameEventType> needMonitorEvents() {
        return List.of(EGameEventType.CLOCK_EVENT);
    }
}
