package com.jjg.game.season.service;

import com.jjg.game.core.base.reddot.IRedDotService;
import com.jjg.game.core.manager.RedDotManager;
import com.jjg.game.core.pb.reddot.RedDotDetails;
import com.jjg.game.season.data.SeasonPlayerData;
import com.jjg.game.season.data.SeasonSmallDrawData;
import com.jjg.game.sim.data.SimPlayerContext;
import com.jjg.game.sim.listener.SimPlayerTickListener;
import com.jjg.game.sim.manager.SimPlayerContextRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/** 小额抽奖入口：每日福利可领取或有已付款可抽奖机会时显示普通红点。 */
@Service
public class SeasonSmallDrawRedDotService implements IRedDotService, SimPlayerTickListener {
    public static final int RED_DOT_SUBMODULE = 3;
    private static final Logger log = LoggerFactory.getLogger(SeasonSmallDrawRedDotService.class);
    private final SimPlayerContextRegistry contexts;
    private final SeasonSmallDrawConfigService configService;
    private final SeasonLifecycleService lifecycleService;
    private final RedDotManager redDotManager;

    public SeasonSmallDrawRedDotService(SimPlayerContextRegistry contexts,
                                        SeasonSmallDrawConfigService configService,
                                        SeasonLifecycleService lifecycleService, RedDotManager redDotManager) {
        this.contexts = contexts;
        this.configService = configService;
        this.lifecycleService = lifecycleService;
        this.redDotManager = redDotManager;
    }

    @Override
    public RedDotDetails.RedDotModule getModule() { return RedDotDetails.RedDotModule.SEASON; }

    @Override
    public int getSubmodule() { return RED_DOT_SUBMODULE; }

    @Override
    public List<RedDotDetails> initialize(long playerId, int submodule) {
        if (submodule != 0 && submodule != RED_DOT_SUBMODULE) return List.of();
        SimPlayerContext ctx = contexts.getContext(playerId);
        if (ctx == null) return List.of(build(false));
        lifecycleService.ensureCurrent(ctx, System.currentTimeMillis());
        boolean available = available(ctx);
        ctx.setSeasonSmallDrawRedDotSnapshot(available);
        return List.of(build(available));
    }

    public void onLogin(SimPlayerContext ctx) {
        //重连可能复用内存上下文，客户端仍需要重新收到当前红点。
        ctx.setSeasonSmallDrawRedDotSnapshot(null);
        refresh(ctx);
    }

    @Override
    public void onTick(SimPlayerContext ctx, long now) {
        if (ctx.getPlayerController() == null || !ctx.getPlayerController().isOnline()) return;
        refresh(ctx);
    }

    /** 复用玩家tick，在赛季切换和每日资格更新之后检查，兼顾配置热更新。 */
    @Override
    public int order() { return 102; }

    public void refresh(SimPlayerContext ctx) {
        try {
            boolean available = available(ctx);
            Boolean previous = ctx.getSeasonSmallDrawRedDotSnapshot();
            if (previous == null || previous != available) {
                redDotManager.updateRedDot(List.of(build(available)), ctx.playerId());
                ctx.setSeasonSmallDrawRedDotSnapshot(available);
            }
        } catch (Exception e) {
            log.error("刷新赛季小额抽奖红点失败 playerId={}", ctx.playerId(), e);
        }
    }

    private boolean available(SimPlayerContext ctx) {
        if (!contexts.isFunctionUnlocked(ctx.playerId(), 5004)) return false;
        SeasonPlayerData season = ctx.getSeasonPlayerData();
        if (season == null) return false;
        var draw = configService.activeDraw(season.getSeasonId());
        if (draw == null) return false;
        SeasonSmallDrawData state = season.getSmallDraw();
        boolean dailyReward = !draw.config().getDailyReward().isEmpty()
                && (state == null || state.getDailyRewardDay() != season.getDailyKey());
        return dailyReward || state != null && state.drawChances() > 0
                && draw.remainingRewardCount(state.getObtainedRewards()) > 0;
    }

    private RedDotDetails build(boolean available) {
        return redDotManager.buildRedDotDetails(getModule(), RED_DOT_SUBMODULE, available ? 1 : 0);
    }
}
