package com.jjg.game.minigame.handler;

import com.jjg.game.common.concurrent.BaseHandler;
import com.jjg.game.common.concurrent.PlayerExecutorGroupDisruptor;
import com.jjg.game.core.base.gameevent.ClockEvent;
import com.jjg.game.core.base.gameevent.EGameEventType;
import com.jjg.game.core.base.gameevent.GameEvent;
import com.jjg.game.core.base.gameevent.GameEventListener;
import com.jjg.game.minigame.service.HappyHappyFishService;
import com.jjg.game.sim.data.SimPlayerContext;
import com.jjg.game.sim.manager.SimPlayerContextRegistry;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/** 复用系统零点事件，通过玩家串行队列发放欢乐捉鱼每日道具。 */
@Component
public class HappyHappyFishDailyEventListener implements GameEventListener {
    private final SimPlayerContextRegistry contextRegistry;
    private final HappyHappyFishService service;

    public HappyHappyFishDailyEventListener(SimPlayerContextRegistry contextRegistry, HappyHappyFishService service) {
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
            //事件线程只入队，赠送与该玩家的其他道具操作保持串行。
            PlayerExecutorGroupDisruptor.getDefaultExecutor().getPlayerWorker(playerId).publishBlocking(0,
                    new BaseHandler<String>() {
                        @Override
                        public void action() {
                            //排队期间已登出或更换会话时不处理旧会话，交由下次登录补发当天赠送。
                            if (contextRegistry.getContext(playerId) == scheduledContext) {
                                service.onDaily(scheduledContext, now);
                            }
                        }
                    }.setHandlerParamWithSelf("happy happy fish daily grant"));
        }
    }

    @Override
    public List<EGameEventType> needMonitorEvents() {
        return List.of(EGameEventType.CLOCK_EVENT);
    }
}
