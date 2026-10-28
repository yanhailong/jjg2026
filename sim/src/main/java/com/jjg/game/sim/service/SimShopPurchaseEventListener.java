package com.jjg.game.sim.service;

import com.jjg.game.core.base.condition.numeric.ActionConditionEvent;
import com.jjg.game.core.base.gameevent.EGameEventType;
import com.jjg.game.core.base.gameevent.GameEvent;
import com.jjg.game.core.base.gameevent.GameEventListener;
import com.jjg.game.core.base.gameevent.PlayerEvent;
import com.jjg.game.sim.data.SimPlayerContext;
import com.jjg.game.sim.manager.SimPlayerContextRegistry;
import org.springframework.stereotype.Component;

import java.util.List;

/** 商城在 core 中记录累计消费；SIM 监听购买事实以刷新当前任务。 */
@Component
public class SimShopPurchaseEventListener implements GameEventListener {
    private final SimPlayerContextRegistry registry;
    private final SimTaskService tasks;

    public SimShopPurchaseEventListener(SimPlayerContextRegistry registry, SimTaskService tasks) {
        this.registry = registry;
        this.tasks = tasks;
    }

    @Override
    public <T extends GameEvent> void handleEvent(T gameEvent) {
        if (gameEvent instanceof PlayerEvent event && event.getPlayer() != null
                && event.getEventChangeValue() instanceof Long amount && amount > 0) {
            SimPlayerContext ctx = registry.getContext(event.getPlayer().getId());
            if (ctx != null) {
                tasks.onConditionEvent(ctx, new ActionConditionEvent(ActionConditionEvent.Type.SHOP_DIAMOND_CONSUME,
                        0, 0, amount, 1, 0, false));
            }
        }
    }

    @Override
    public List<EGameEventType> needMonitorEvents() {
        return List.of(EGameEventType.SHOP_DIAMOND_CONSUME);
    }
}
