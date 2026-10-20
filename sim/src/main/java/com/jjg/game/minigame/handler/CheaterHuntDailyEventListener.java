package com.jjg.game.minigame.handler;

import com.jjg.game.common.concurrent.BaseHandler;
import com.jjg.game.common.concurrent.PlayerExecutorGroupDisruptor;
import com.jjg.game.core.base.gameevent.ClockEvent;
import com.jjg.game.core.base.gameevent.EGameEventType;
import com.jjg.game.core.base.gameevent.GameEvent;
import com.jjg.game.core.base.gameevent.GameEventListener;
import com.jjg.game.minigame.service.SimMiniGameService;
import com.jjg.game.sim.data.SimPlayerContext;
import com.jjg.game.sim.manager.SimPlayerContextRegistry;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/** 响应系统零点事件，在线赠送回到玩家队列执行，不轮询、不新增定时任务。 */
@Component
public class CheaterHuntDailyEventListener implements GameEventListener {
    private final SimPlayerContextRegistry contextRegistry;
    private final SimMiniGameService service;

    public CheaterHuntDailyEventListener(SimPlayerContextRegistry contextRegistry, SimMiniGameService service) {
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
            //ClockEvent由事件框架的虚拟线程处理，可等待入队；不在事件线程直接修改玩家数据。
            PlayerExecutorGroupDisruptor.getDefaultExecutor().getPlayerWorker(playerId).publishBlocking(0,
                    new BaseHandler<String>() {
                        @Override
                        public void action() {
                            //排队期间可能登出或重登，旧会话不再处理赠送；离线由下次登录结算。
                            if (contextRegistry.getContext(playerId) == scheduledContext) {
                                service.onDaily(scheduledContext, now);
                            }
                        }
                    }.setHandlerParamWithSelf("cheater hunt daily grant"));
        }
    }

    @Override
    public List<EGameEventType> needMonitorEvents() {
        return List.of(EGameEventType.CLOCK_EVENT);
    }
}
