package com.jjg.game.minigame.service;

import com.jjg.game.core.base.condition.numeric.ActionConditionEvent;
import com.jjg.game.core.service.PlayerStatService;
import com.jjg.game.sim.data.SimPlayerContext;
import com.jjg.game.sim.service.SimTaskService;
import org.springframework.stereotype.Service;

/** 小游戏开局扣费成功后，先记录玩家统计，再驱动当前任务。 */
@Service
public class MiniGameConditionService {
    private final PlayerStatService playerStatService;
    private final SimTaskService simTaskService;

    public MiniGameConditionService(PlayerStatService playerStatService, SimTaskService simTaskService) {
        this.playerStatService = playerStatService;
        this.simTaskService = simTaskService;
    }

    public void onRoundStarted(SimPlayerContext ctx, int miniGameId) {
        playerStatService.recordMiniGamePlay(ctx.playerId(), miniGameId);
        simTaskService.onConditionEvent(ctx, new ActionConditionEvent(ActionConditionEvent.Type.MINI_GAME_PLAY,
                miniGameId, 0, 0, 1, 0, false));
    }
}
