package com.jjg.game.sim.service;

import com.jjg.game.common.utils.TimeHelper;
import com.jjg.game.core.base.reddot.IRedDotService;
import com.jjg.game.core.data.Item;
import com.jjg.game.core.manager.RedDotManager;
import com.jjg.game.core.pb.reddot.RedDotDetails;
import com.jjg.game.sampledata.GameDataManager;
import com.jjg.game.sampledata.bean.GlobalConfigCfg;
import com.jjg.game.sim.constant.SimConstant;
import com.jjg.game.sim.data.SimBaseData;
import com.jjg.game.sim.data.SimPlayerContext;
import com.jjg.game.sim.listener.SimPlayerTickListener;
import com.jjg.game.sim.manager.SimPlayerContextRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/** 获取资源的广告入口：剩余次数大于零，且冷却结束时显示普通红点。 */
@Service
public class SimOnlineRewardRedDotService implements IRedDotService, SimPlayerTickListener {
    public static final int AD_AVAILABLE = 26;
    private static final Logger log = LoggerFactory.getLogger(SimOnlineRewardRedDotService.class);

    @Autowired private SimPlayerContextRegistry contexts;
    @Autowired private RedDotManager redDotManager;
    @Autowired private SimConfigCacheService configCache;

    @Override
    public RedDotDetails.RedDotModule getModule() {
        return RedDotDetails.RedDotModule.ACTIVITY;
    }

    @Override
    public int getSubmodule() {
        return AD_AVAILABLE;
    }

    @Override
    public List<RedDotDetails> initialize(long playerId, int submodule) {
        if (submodule != 0 && submodule != AD_AVAILABLE) return List.of();
        SimPlayerContext ctx = contexts.getContext(playerId);
        if (ctx == null || ctx.getPlayer() == null || ctx.getCurrentCasino() == null
                || ctx.getSimBaseData() == null) return List.of();
        boolean available = available(ctx, System.currentTimeMillis());
        ctx.setOnlineRewardAdRedDotSnapshot(available);
        return List.of(build(available));
    }

    @Override
    public void onTick(SimPlayerContext ctx, long now) {
        if (ctx.getPlayer() == null || ctx.getCurrentCasino() == null || ctx.getSimBaseData() == null) return;
        boolean available = available(ctx, now);
        Boolean previous = ctx.getOnlineRewardAdRedDotSnapshot();
        if (previous == null || previous != available) {
            ctx.setOnlineRewardAdRedDotSnapshot(available);
            redDotManager.updateRedDot(List.of(build(available)), ctx.playerId());
        }
    }

    public void refresh(SimPlayerContext ctx) {
        if (ctx.getPlayer() == null || ctx.getCurrentCasino() == null || ctx.getSimBaseData() == null) return;
        try {
            boolean available = available(ctx, System.currentTimeMillis());
            ctx.setOnlineRewardAdRedDotSnapshot(available);
            redDotManager.updateRedDot(List.of(build(available)), ctx.playerId());
        } catch (Exception e) {
            log.error("刷新获取资源广告红点失败 playerId={}", ctx.playerId(), e);
        }
    }

    private boolean available(SimPlayerContext ctx, long now) {
        SimBaseData base = ctx.getSimBaseData();
        int today = Integer.parseInt(TimeHelper.getDate(now, "yyyyMMdd"));
        if (base.getOnlineRewardDay() != today) {
            base.resetOnlineRewardDay(today);
            ctx.setLastSaveTime(0);
        }
        GlobalConfigCfg limit = GameDataManager.getGlobalConfigCfg(SimConstant.Global.ONLINE_REWARD_AD_LIMIT);
        GlobalConfigCfg hours = GameDataManager.getGlobalConfigCfg(SimConstant.Global.ONLINE_REWARD_AD_HOURS);
        GlobalConfigCfg cooldown = GameDataManager.getGlobalConfigCfg(SimConstant.Global.ONLINE_REWARD_AD_CD);
        GlobalConfigCfg diamondLimit = GameDataManager.getGlobalConfigCfg(SimConstant.Global.ONLINE_REWARD_DIAMOND_LIMIT);
        GlobalConfigCfg diamondHours = GameDataManager.getGlobalConfigCfg(SimConstant.Global.ONLINE_REWARD_DIAMOND_HOURS);
        List<Item> costs = configCache.getOnlineRewardDiamondCosts();
        return limit != null && hours != null && cooldown != null
                && diamondLimit != null && diamondHours != null
                && hours.getIntValue() > 0 && cooldown.getIntValue() >= 0
                && diamondLimit.getIntValue() >= 0 && diamondHours.getIntValue() > 0
                && costs != null && costs.size() >= diamondLimit.getIntValue()
                && canClaimAd(base.getOnlineRewardAdCount(), limit.getIntValue(),
                        base.getOnlineRewardAdCdEndTime(), now);
    }

    static boolean canClaimAd(int used, int limit, long cdEndTime, long now) {
        return limit > 0 && used < limit && now >= cdEndTime;
    }

    private RedDotDetails build(boolean available) {
        return redDotManager.buildRedDotDetails(getModule(), AD_AVAILABLE,
                available ? 1 : 0, RedDotDetails.RedDotType.COMMON);
    }
}
