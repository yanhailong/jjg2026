package com.jjg.game.season.service;

import com.jjg.game.core.base.player.IPlayerLoginSuccess;
import com.jjg.game.core.data.*;
import com.jjg.game.season.dao.LuckyDrawDao;
import com.jjg.game.sim.data.SimPlayerContext;
import com.jjg.game.sim.listener.SimPlayerTickListener;
import org.springframework.stereotype.Component;
import java.time.ZoneId;

/** 登录与在线跨日事实单独记录，不要求打开活动；离线 RPC 上下文不算登录。 */
@Component
public class LuckyDrawPresence implements IPlayerLoginSuccess, SimPlayerTickListener {
    private final LuckyDrawDao dao;
    private final LuckyDrawCatalog catalog;
    public LuckyDrawPresence(LuckyDrawDao dao, LuckyDrawCatalog catalog) { this.dao = dao; this.catalog = catalog; }
    @Override public void onPlayerLoginSuccess(PlayerController pc, Player player, Account account, boolean firstLogin) {
        record(player.getId(), System.currentTimeMillis());
    }
    public void record(long playerId, long now) {
        if (catalog.stage(1) != null) dao.recordLogin(playerId, LuckyDrawService.dayStart(now, ZoneId.systemDefault()));
    }
    public void authenticatedRequest(SimPlayerContext ctx, long now) {
        long day = LuckyDrawService.dayStart(now, ZoneId.systemDefault());
        if (ctx.getLuckyDrawObservedDay() != day && catalog.stage(1) != null) {
            dao.recordLogin(ctx.playerId(), day);
            ctx.setLuckyDrawObservedDay(day);
        }
    }
    @Override public void onTick(SimPlayerContext ctx, long now) {
        if (ctx.getPlayerController() != null && ctx.getPlayerController().isOnline()) authenticatedRequest(ctx, now);
    }
    @Override public int order() { return 110; }
}
