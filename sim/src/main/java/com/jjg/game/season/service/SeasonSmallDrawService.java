package com.jjg.game.season.service;

import com.jjg.game.common.utils.TimeHelper;
import com.jjg.game.common.utils.WeightRandom;
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
import com.jjg.game.season.pb.res.ResSeasonSmallDraw;
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

import java.time.DateTimeException;
import java.util.List;
import java.util.Map;

import static com.jjg.game.season.service.SeasonSmallDrawConfigService.DrawDefinition;
import static com.jjg.game.season.service.SeasonSmallDrawConfigService.RewardDefinition;

/** 赛季小额抽奖：登录资格、大奖选择、每日福利、付费机会与主动抽奖。 */
@Service
public class SeasonSmallDrawService implements SimPlayerTickListener {
    private static final Logger log = LoggerFactory.getLogger(SeasonSmallDrawService.class);
    private final SeasonSmallDrawConfigService configService;
    private final SeasonLifecycleService lifecycleService;
    private final PlayerPackService playerPackService;
    private final SimAutoSaveService autoSaveService;
    private final SocialSender sender;
    private final SeasonSmallDrawRedDotService redDotService;
    private final SeasonSmallDrawClock clock;

    public SeasonSmallDrawService(SeasonSmallDrawConfigService configService,
                                  SeasonLifecycleService lifecycleService, PlayerPackService playerPackService,
                                  SimAutoSaveService autoSaveService, SocialSender sender,
                                  SeasonSmallDrawRedDotService redDotService, SeasonSmallDrawClock clock) {
        this.configService = configService;
        this.lifecycleService = lifecycleService;
        this.playerPackService = playerPackService;
        this.autoSaveService = autoSaveService;
        this.sender = sender;
        this.redDotService = redDotService;
        this.clock = clock;
    }

    public DrawDefinition current(SimPlayerContext ctx) {
        long now = System.currentTimeMillis();
        lifecycleService.ensureCurrent(ctx, now);
        DrawDefinition draw = configService.activeDraw(ctx.getSeasonPlayerData().getSeasonId());
        if (draw != null) {
            updateLogin(ctx, draw, clock.dayKey(ctx.getSeasonPlayerData(), now));
        }
        return draw;
    }

    public void onLogin(SimPlayerContext ctx) {
        current(ctx);
        redDotService.onLogin(ctx);
    }

    @Override
    public void onTick(SimPlayerContext ctx, long now) {
        if (ctx.getPlayerController() == null || !ctx.getPlayerController().isOnline()) {
            return;
        }
        //生命周期已在order=100更新赛季和自然日，普通tick只比较当天标记。
        SeasonPlayerData season = ctx.getSeasonPlayerData();
        SeasonSmallDrawData state = season == null ? null : season.getSmallDraw();
        if (season == null) {
            return;
        }
        int dayKey = clock.dayKey(season, now);
        if (state != null && state.getLastLoginDay() == dayKey) {
            return;
        }
        DrawDefinition draw = configService.activeDraw(season.getSeasonId());
        if (draw != null) {
            updateLogin(ctx, draw, dayKey);
        }
    }

    @Override
    public int order() { return 101; }

    private void updateLogin(SimPlayerContext ctx, DrawDefinition draw, int dayKey) {
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
        if (state.getLastLoginDay() != dayKey) {
            int limit = Math.max(0, draw.config().getMaxCumulativeDays());
            state.setPurchaseChances(Math.min(limit, Math.addExact(state.getPurchaseChances(), 1)));
            state.setLastLoginDay(dayKey);
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
        int dayKey = clock.dayKey(season, System.currentTimeMillis());
        if (state.getDailyRewardDay() == dayKey) {
            response.code = Code.REPEAT_OP;
            return response;
        }
        if (draw.config().getDailyReward().isEmpty()) {
            response.code = Code.SAMPLE_ERROR;
            return response;
        }
        CommonResult<ItemOperationResult> result = playerPackService.addItems(ctx.playerId(), draw.config().getDailyReward(),
                AddType.SIM_SEASON_SMALL_DRAW, "season-small-draw-daily:" + season.getSeasonKey()
                        + ':' + dayKey, true);
        if (result == null || !result.success()) {
            response.code = result == null ? Code.EXCEPTION : result.code;
            return response;
        }
        state.setDailyRewardDay(dayKey);
        autoSaveService.saveAndWait(season);
        redDotService.refresh(ctx);
        response.code = Code.SUCCESS;
        response.rewards = ItemUtils.buildItemInfo(draw.config().getDailyReward());
        response.info = assemble(ctx, draw);
        return response;
    }

    /** 同一订单只增加一次抽奖机会；已付款订单不再检查当日资格。 */
    public boolean receive(SimPlayerContext ctx, Order order) {
        DrawDefinition draw = current(ctx);
        SeasonPlayerData season = ctx.getSeasonPlayerData();
        SeasonSmallDrawData state = season.getSmallDraw();
        if (draw == null || state == null || !orderToken(season, draw.id()).equals(order.getProductId())) {
            log.error("小额抽奖到账失败，订单不属于当前赛季奖池 playerId={},orderId={},productId={},seasonKey={}",
                    ctx.playerId(), order.getId(), order.getProductId(), season.getSeasonKey());
            return false;
        }
        if (!state.getReceivedOrders().contains(order.getId())) {
            if (draw.remainingRewardCount(state.getObtainedRewards()) <= state.drawChances()) {
                log.error("小额抽奖到账失败，剩余奖池已全部购买 playerId={},orderId={}", ctx.playerId(), order.getId());
                return false;
            }
            state.getReceivedOrders().add(order.getId());
            state.setPurchaseChances(Math.max(0, state.getPurchaseChances() - 1));
        }
        autoSaveService.saveAndWait(season);
        redDotService.refresh(ctx);
        ResSeasonSmallDrawInfo notification = new ResSeasonSmallDrawInfo(Code.SUCCESS);
        notification.info = assemble(ctx, draw);
        try {
            sender.sendTo(ctx.playerId(), notification);
        } catch (Exception e) {
            log.error("小额抽奖到账状态通知失败 playerId={},orderId={}", ctx.playerId(), order.getId(), e);
        }
        log.info("小额抽奖到账成功 playerId={},seasonKey={},orderId={},drawChances={}",
                ctx.playerId(), season.getSeasonKey(), order.getId(), state.drawChances());
        return true;
    }

    /** 非充值购买按份数增加抽奖机会，不创建订单或去重记录。 */
    public boolean receivePurchase(SimPlayerContext ctx, String productId, int buyCount) {
        DrawDefinition draw = current(ctx);
        SeasonPlayerData season = ctx.getSeasonPlayerData();
        SeasonSmallDrawData state = season.getSmallDraw();
        if (draw == null || state == null || !orderToken(season, draw.id()).equals(productId)
                || buyCount < 1 || buyCount > state.getPurchaseChances()
                || buyCount > draw.remainingRewardCount(state.getObtainedRewards()) - state.drawChances()) {
            return false;
        }
        int previousChances = state.getPurchaseChances();
        int previousCount = state.getNonMoneyPurchaseCount();
        int purchasedCount = Math.addExact(previousCount, buyCount);
        state.setPurchaseChances(previousChances - buyCount);
        state.setNonMoneyPurchaseCount(purchasedCount);
        try {
            autoSaveService.saveAndWait(season);
        } catch (RuntimeException e) {
            state.setPurchaseChances(previousChances);
            state.setNonMoneyPurchaseCount(previousCount);
            log.error("小额抽奖非充值购买保存失败 playerId={},productId={}", ctx.playerId(), productId, e);
            return false;
        }
        try {
            redDotService.refresh(ctx);
            ResSeasonSmallDrawInfo notification = new ResSeasonSmallDrawInfo(Code.SUCCESS);
            notification.info = assemble(ctx, draw);
            sender.sendTo(ctx.playerId(), notification);
        } catch (Exception e) {
            log.error("小额抽奖非充值购买通知失败 playerId={},productId={}", ctx.playerId(), productId, e);
        }
        return true;
    }

    /** 客户端主动请求，一次成功抽奖消耗一次已购买机会。 */
    public ResSeasonSmallDraw draw(SimPlayerContext ctx) {
        DrawDefinition draw = current(ctx);
        ResSeasonSmallDraw response = new ResSeasonSmallDraw(Code.NOT_FOUND);
        if (draw == null) {
            return response;
        }
        SeasonPlayerData season = ctx.getSeasonPlayerData();
        SeasonSmallDrawData state = season.getSmallDraw();
        if (state.drawChances() <= 0) {
            response.code = Code.NOT_ENOUGH;
            return response;
        }
        WeightRandom<RewardDefinition> random = WeightRandom.create();
        for (RewardDefinition candidate : draw.rewards()) {
            if (!state.getObtainedRewards().contains(candidate.id())) {
                random.add(candidate, candidate.weight());
            }
        }
        RewardDefinition reward = random.next();
        if (reward == null) {
            log.error("小额抽奖失败，无剩余正权重奖励 playerId={},drawId={}", ctx.playerId(), draw.id());
            response.code = Code.SAMPLE_ERROR;
            return response;
        }
        Map<Integer, Long> items = reward.items(selectedOption(state, reward));
        CommonResult<ItemOperationResult> result = playerPackService.addItems(ctx.playerId(), items,
                AddType.SIM_SEASON_SMALL_DRAW, "season-small-draw:" + orderToken(season, draw.id())
                        + ':' + reward.id(), true);
        if (result == null || !result.success()) {
            response.code = result == null ? Code.EXCEPTION : result.code;
            log.error("小额抽奖发奖失败 playerId={},drawId={},rewardId={},code={}",
                    ctx.playerId(), draw.id(), reward.id(), response.code);
            return response;
        }
        state.getObtainedRewards().add(reward.id());
        autoSaveService.saveAndWait(season);
        redDotService.refresh(ctx);
        response.code = Code.SUCCESS;
        response.rewardId = reward.id();
        response.rewards = ItemUtils.buildItemInfo(items);
        response.info = assemble(ctx, draw);
        log.info("小额抽奖成功 playerId={},seasonKey={},drawId={},rewardId={},items={}",
                ctx.playerId(), season.getSeasonKey(), draw.id(), reward.id(), items);
        return response;
    }

    public static String orderToken(SeasonPlayerData season, int drawId) {
        return season.getSeasonKey() + ':' + drawId;
    }

    public CommonResult<String> gmTime(SimPlayerContext ctx, String[] orders) {
        if (ctx == null || orders == null || orders.length != 2) {
            return new CommonResult<>(Code.PARAM_ERROR);
        }
        try {
            clock.setDate(orders[1]);
        } catch (DateTimeException | IllegalArgumentException e) {
            return new CommonResult<>(Code.PARAM_ERROR);
        }
        current(ctx);
        redDotService.refresh(ctx);
        return new CommonResult<>(Code.SUCCESS, "time=" + TimeHelper.getLocalDateTime(clock.currentTime()));
    }

    private SeasonSmallDrawInfo assemble(SimPlayerContext ctx, DrawDefinition draw) {
        SeasonPlayerData season = ctx.getSeasonPlayerData();
        SeasonSmallDrawData state = season.getSmallDraw();
        SeasonSmallDrawInfo info = new SeasonSmallDrawInfo();
        info.drawId = draw.id();
        info.seasonKey = season.getSeasonKey();
        info.endTime = season.getEndTime();
        info.drawChances = state.drawChances();
        info.purchaseChances = Math.max(0, Math.min(state.getPurchaseChances(),
                draw.remainingRewardCount(state.getObtainedRewards()) - info.drawChances));
        info.drawCount = state.getObtainedRewards().size();
        ShopRechargeListCfg shop = GameDataManager.getShopRechargeListCfg(draw.config().getPricePerUse());
        if (shop != null) {
            info.price = shop.getPrice() == null ? null : shop.getPrice().toPlainString();
            info.channelProductId = SeasonPassService.channelProductId(ctx.getPlayer(), shop);
        }
        info.dailyRewardClaimed = state.getDailyRewardDay() == clock.dayKey(season, System.currentTimeMillis());
        info.dailyReward = ItemUtils.buildItemInfo(draw.config().getDailyReward());
        info.coreItems = draw.rewards().stream().filter(RewardDefinition::core).findFirst()
                .map(reward -> reward.options().stream()
                        .flatMap(items -> ItemUtils.buildItemInfo(items).stream()).toList())
                .orElse(List.of());
        info.rewards = draw.rewards().stream().map(reward -> {
            SeasonSmallDrawRewardInfo entry = new SeasonSmallDrawRewardInfo();
            entry.rewardId = reward.id();
            entry.core = reward.core();
            entry.quality = reward.quality();
            entry.obtained = state.getObtainedRewards().contains(reward.id());
            entry.items = ItemUtils.buildItemInfo(reward.items(selectedOption(state, reward)));
            return entry;
        }).toList();
        return info;
    }

    private int selectedOption(SeasonSmallDrawData state, RewardDefinition reward) {
        int selected = reward.core() ? state.getSelectedOptions().getOrDefault(reward.id(), 0) : 0;
        return selected >= 0 && selected < reward.options().size() ? selected : 0;
    }
}
