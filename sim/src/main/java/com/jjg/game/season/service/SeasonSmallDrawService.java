package com.jjg.game.season.service;

import com.jjg.game.core.constant.AddType;
import com.jjg.game.core.constant.Code;
import com.jjg.game.core.data.CommonResult;
import com.jjg.game.core.data.ItemOperationResult;
import com.jjg.game.core.data.Order;
import com.jjg.game.core.service.PlayerPackService;
import com.jjg.game.core.utils.ItemUtils;
import com.jjg.game.sampledata.GameDataManager;
import com.jjg.game.sampledata.bean.ShopRechargeListCfg;
import com.jjg.game.season.data.SeasonPlayerData;
import com.jjg.game.season.data.SeasonSmallDrawData;
import com.jjg.game.season.pb.res.NotifySeasonSmallDrawResult;
import com.jjg.game.season.pb.res.ResSeasonSmallDrawDailyReward;
import com.jjg.game.season.pb.res.ResSeasonSmallDrawInfo;
import com.jjg.game.season.pb.res.ResSeasonSmallDrawSelect;
import com.jjg.game.season.pb.struct.SeasonSmallDrawInfo;
import com.jjg.game.season.pb.struct.SeasonSmallDrawRewardInfo;
import com.jjg.game.sim.data.SimPlayerContext;
import com.jjg.game.sim.listener.SimPlayerTickListener;
import com.jjg.game.sim.service.SimAutoSaveService;
import com.jjg.game.social.service.SocialSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static com.jjg.game.season.service.SeasonSmallDrawConfigService.DrawDefinition;
import static com.jjg.game.season.service.SeasonSmallDrawConfigService.RewardDefinition;

/** 赛季小额抽奖：登录资格、大奖选择、每日福利与付费到账抽奖。 */
@Service
public class SeasonSmallDrawService implements SimPlayerTickListener {
    private static final Logger log = LoggerFactory.getLogger(SeasonSmallDrawService.class);
    private final SeasonSmallDrawConfigService configService;
    private final SeasonLifecycleService lifecycleService;
    private final PlayerPackService playerPackService;
    private final SimAutoSaveService autoSaveService;
    private final SocialSender sender;

    public SeasonSmallDrawService(SeasonSmallDrawConfigService configService,
                                  SeasonLifecycleService lifecycleService, PlayerPackService playerPackService,
                                  SimAutoSaveService autoSaveService, SocialSender sender) {
        this.configService = configService;
        this.lifecycleService = lifecycleService;
        this.playerPackService = playerPackService;
        this.autoSaveService = autoSaveService;
        this.sender = sender;
    }

    public DrawDefinition current(SimPlayerContext ctx) {
        lifecycleService.ensureCurrent(ctx, System.currentTimeMillis());
        DrawDefinition draw = configService.activeDraw(ctx.getSeasonPlayerData().getSeasonId());
        if (draw != null) {
            updateLogin(ctx, draw);
        }
        return draw;
    }

    public void onLogin(SimPlayerContext ctx) {
        current(ctx);
    }

    @Override
    public void onTick(SimPlayerContext ctx, long now) {
        if (ctx.getPlayerController() == null || !ctx.getPlayerController().isOnline()) {
            return;
        }
        //生命周期已在order=100更新赛季和自然日，普通tick只比较当天标记。
        SeasonPlayerData season = ctx.getSeasonPlayerData();
        SeasonSmallDrawData state = season == null ? null : season.getSmallDraw();
        if (season == null || state != null && state.getLastLoginDay() == season.getDailyKey()) {
            return;
        }
        DrawDefinition draw = configService.activeDraw(season.getSeasonId());
        if (draw != null) {
            updateLogin(ctx, draw);
        }
    }

    @Override
    public int order() { return 101; }

    private void updateLogin(SimPlayerContext ctx, DrawDefinition draw) {
        SeasonPlayerData season = ctx.getSeasonPlayerData();
        SeasonSmallDrawData state = season.getSmallDraw();
        if (state == null) {
            state = new SeasonSmallDrawData();
            state.setDrawId(draw.id());
            season.setSmallDraw(state);
        }
        if (state.getDrawId() != draw.id()) {
            throw new IllegalStateException("同一赛季小额抽奖配置ID发生变化");
        }
        if (state.getLastLoginDay() != season.getDailyKey()) {
            int limit = Math.max(0, draw.config().getMaxCumulativeDays());
            state.setPurchaseChances(Math.min(limit, Math.addExact(state.getPurchaseChances(), 1)));
            state.setLastLoginDay(season.getDailyKey());
            autoSaveService.enqueueSave(season);
        }
    }

    public ResSeasonSmallDrawInfo info(SimPlayerContext ctx) {
        DrawDefinition draw = current(ctx);
        ResSeasonSmallDrawInfo response = new ResSeasonSmallDrawInfo(draw == null ? Code.NOT_FOUND : Code.SUCCESS);
        if (draw != null) {
            response.info = assemble(ctx, draw);
        }
        return response;
    }

    public ResSeasonSmallDrawSelect select(SimPlayerContext ctx, int drawId, int rewardId, int optionIndex) {
        DrawDefinition draw = current(ctx);
        ResSeasonSmallDrawSelect response = new ResSeasonSmallDrawSelect(Code.NOT_FOUND);
        if (draw == null || draw.id() != drawId) {
            return response;
        }
        RewardDefinition reward = draw.reward(rewardId);
        if (reward == null || !reward.core() || optionIndex < 0 || optionIndex >= reward.options().size()) {
            response.code = Code.PARAM_ERROR;
            return response;
        }
        SeasonSmallDrawData state = ctx.getSeasonPlayerData().getSmallDraw();
        if (state.getSelectedOptions().getOrDefault(rewardId, 0) != optionIndex) {
            Integer previous = state.getSelectedOptions().put(rewardId, optionIndex);
            try {
                autoSaveService.saveAndWait(ctx.getSeasonPlayerData());
            } catch (RuntimeException e) {
                if (previous == null) {
                    state.getSelectedOptions().remove(rewardId);
                } else {
                    state.getSelectedOptions().put(rewardId, previous);
                }
                autoSaveService.enqueueSave(ctx.getSeasonPlayerData());
                log.error("核心大奖选择保存失败 playerId={},rewardId={},optionIndex={}",
                        ctx.playerId(), rewardId, optionIndex, e);
                response.code = Code.EXCEPTION;
                return response;
            }
        }
        response.code = Code.SUCCESS;
        response.info = assemble(ctx, draw);
        return response;
    }

    public ResSeasonSmallDrawDailyReward claimDaily(SimPlayerContext ctx, int drawId) {
        DrawDefinition draw = current(ctx);
        ResSeasonSmallDrawDailyReward response = new ResSeasonSmallDrawDailyReward(Code.NOT_FOUND);
        if (draw == null || draw.id() != drawId) {
            return response;
        }
        SeasonPlayerData season = ctx.getSeasonPlayerData();
        SeasonSmallDrawData state = season.getSmallDraw();
        if (state.getDailyRewardDay() == season.getDailyKey()) {
            response.code = Code.REPEAT_OP;
            return response;
        }
        Map<Integer, Long> items = dailyReward(draw);
        if (items.isEmpty()) {
            response.code = Code.SAMPLE_ERROR;
            return response;
        }
        CommonResult<ItemOperationResult> result = playerPackService.addItems(ctx.playerId(), items,
                AddType.SIM_SEASON_SMALL_DRAW, "season-small-draw-daily:" + season.getSeasonKey()
                        + ':' + season.getDailyKey(), true);
        if (result == null || !result.success()) {
            response.code = result == null ? Code.EXCEPTION : result.code;
            return response;
        }
        state.setDailyRewardDay(season.getDailyKey());
        autoSaveService.saveAndWait(season);
        response.code = Code.SUCCESS;
        response.rewards = ItemUtils.buildItemInfo(items);
        response.info = assemble(ctx, draw);
        return response;
    }

    /** 同一订单只抽一次；已付款订单不再检查当日资格，避免跨日到账被拒绝。 */
    public boolean receive(SimPlayerContext ctx, Order order) {
        DrawDefinition draw = current(ctx);
        SeasonPlayerData season = ctx.getSeasonPlayerData();
        SeasonSmallDrawData state = season.getSmallDraw();
        if (draw == null || state == null || !orderToken(season, draw.id()).equals(order.getProductId())) {
            log.error("小额抽奖到账失败，订单不属于当前赛季奖池 playerId={},orderId={},productId={},seasonKey={}",
                    ctx.playerId(), order.getId(), order.getProductId(), season.getSeasonKey());
            return false;
        }
        if (state.getReceivedOrders().contains(order.getId())) {
            autoSaveService.saveAndWait(season);
            return true;
        }
        List<RewardDefinition> remaining = draw.rewards().stream()
                .filter(reward -> !state.getObtainedRewards().contains(reward.id())).toList();
        if (remaining.isEmpty()) {
            log.error("小额抽奖到账失败，奖池已抽完 playerId={},orderId={}", ctx.playerId(), order.getId());
            return false;
        }
        RewardDefinition reward = remaining.get(ThreadLocalRandom.current().nextInt(remaining.size()));
        Map<Integer, Long> items = reward.items(selectedOption(state, reward));
        CommonResult<ItemOperationResult> result = playerPackService.addItems(ctx.playerId(), items,
                AddType.SIM_SEASON_SMALL_DRAW, "season-small-draw:" + order.getId(), true);
        if (result == null || !result.success()) {
            log.error("小额抽奖发奖失败 playerId={},orderId={},rewardId={},code={}",
                    ctx.playerId(), order.getId(), reward.id(), result == null ? Code.EXCEPTION : result.code);
            return false;
        }
        state.getObtainedRewards().add(reward.id());
        state.getReceivedOrders().add(order.getId());
        state.setPurchaseChances(Math.max(0, state.getPurchaseChances() - 1));
        autoSaveService.saveAndWait(season);
        NotifySeasonSmallDrawResult notification = new NotifySeasonSmallDrawResult(Code.SUCCESS);
        notification.orderId = order.getId();
        notification.rewardId = reward.id();
        notification.rewards = ItemUtils.buildItemInfo(items);
        notification.info = assemble(ctx, draw);
        try {
            sender.sendTo(ctx.playerId(), notification);
        } catch (Exception e) {
            log.error("小额抽奖结果通知失败 playerId={},orderId={}", ctx.playerId(), order.getId(), e);
        }
        log.info("小额抽奖到账成功 playerId={},seasonKey={},orderId={},rewardId={},items={}",
                ctx.playerId(), season.getSeasonKey(), order.getId(), reward.id(), items);
        return true;
    }

    public static String orderToken(SeasonPlayerData season, int drawId) {
        return season.getSeasonKey() + ':' + drawId;
    }

    private SeasonSmallDrawInfo assemble(SimPlayerContext ctx, DrawDefinition draw) {
        SeasonPlayerData season = ctx.getSeasonPlayerData();
        SeasonSmallDrawData state = season.getSmallDraw();
        SeasonSmallDrawInfo info = new SeasonSmallDrawInfo();
        info.drawId = draw.id();
        info.seasonKey = season.getSeasonKey();
        info.endTime = season.getEndTime();
        info.purchaseChances = Math.max(0, Math.min(state.getPurchaseChances(), draw.rewards().size() - state.getObtainedRewards().size()));
        info.drawCount = state.getObtainedRewards().size();
        ShopRechargeListCfg shop = GameDataManager.getShopRechargeListCfg(draw.config().getPricePerUse());
        if (shop != null) {
            info.price = shop.getPrice() == null ? null : shop.getPrice().toPlainString();
            info.channelProductId = SeasonPassService.channelProductId(ctx.getPlayer(), shop);
        }
        info.dailyRewardClaimed = state.getDailyRewardDay() == season.getDailyKey();
        info.dailyReward = ItemUtils.buildItemInfo(dailyReward(draw));
        info.rewards = draw.rewards().stream().map(reward -> {
            SeasonSmallDrawRewardInfo entry = new SeasonSmallDrawRewardInfo();
            entry.rewardId = reward.id();
            entry.core = reward.core();
            entry.obtained = state.getObtainedRewards().contains(reward.id());
            entry.optionIndex = selectedOption(state, reward);
            entry.items = ItemUtils.buildItemInfo(reward.items(entry.optionIndex));
            return entry;
        }).toList();
        return info;
    }

    private int selectedOption(SeasonSmallDrawData state, RewardDefinition reward) {
        int selected = reward.core() ? state.getSelectedOptions().getOrDefault(reward.id(), 0) : 0;
        return selected >= 0 && selected < reward.options().size() ? selected : 0;
    }

    private Map<Integer, Long> dailyReward(DrawDefinition draw) {
        List<Integer> reward = draw.config().getDailyReward();
        return reward != null && reward.size() == 2 && reward.get(0) != null && reward.get(0) > 0
                && reward.get(1) != null && reward.get(1) > 0
                ? Map.of(reward.get(0), reward.get(1).longValue()) : Map.of();
    }
}
