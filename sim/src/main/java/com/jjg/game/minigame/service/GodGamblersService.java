package com.jjg.game.minigame.service;

import com.jjg.game.common.pb.ItemInfo;
import com.jjg.game.common.utils.RandomUtils;
import com.jjg.game.common.utils.TimeHelper;
import com.jjg.game.core.constant.AddType;
import com.jjg.game.core.constant.Code;
import com.jjg.game.core.data.CommonResult;
import com.jjg.game.core.data.ItemOperationResult;
import com.jjg.game.core.data.PlayerPack;
import com.jjg.game.core.manager.SnowflakeManager;
import com.jjg.game.core.service.PlayerPackService;
import com.jjg.game.core.utils.ItemUtils;
import com.jjg.game.minigame.constant.MiniGameConstant;
import com.jjg.game.minigame.constant.MiniGameConstant.RoundState;
import com.jjg.game.minigame.dao.GodGamblersDao;
import com.jjg.game.minigame.data.GodGamblersData;
import com.jjg.game.minigame.data.GodGamblersRound;
import com.jjg.game.minigame.pb.res.*;
import com.jjg.game.minigame.pb.struct.GodGamblersInfo;
import com.jjg.game.sampledata.GameDataManager;
import com.jjg.game.sampledata.bean.GlobalConfigCfg;
import com.jjg.game.sampledata.bean.MinigameGodofGamblersRubbingCfg;
import com.jjg.game.sim.data.SimPlayerContext;
import com.jjg.game.sim.randomevent.SimRandomEventService;
import org.springframework.beans.factory.annotation.Autowired;
import com.jjg.game.sim.service.SimAutoSaveService;
import com.jjg.game.sim.service.SimChapterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 赌神搓牌玩法；沿用玩家串行队列、背包和保存服务，不额外加锁或创建事务。
 */
@Service
public class GodGamblersService {
    @Autowired
    private SimRandomEventService randomEvents;
    private static final Logger log = LoggerFactory.getLogger(GodGamblersService.class);
    //扑克基本规则：每花色13张，2最小、A按14点比较，点差不含花色。
    private static final int RANKS_PER_SUIT = 13;
    private static final int MIN_RANK = 2;
    private static final int MAX_RANK = 14;
    private static final int SUIT_COUNT = 4;

    private final GodGamblersDao dao;
    private final SimAutoSaveService autoSaveService;
    private final PlayerPackService playerPackService;
    private final SnowflakeManager snowflakeManager;
    private final SimChapterService chapterService;
    private final MiniGameConditionService conditionService;

    public GodGamblersService(GodGamblersDao dao, SimAutoSaveService autoSaveService,
                              PlayerPackService playerPackService, SnowflakeManager snowflakeManager,
                              SimChapterService chapterService, MiniGameConditionService conditionService) {
        this.dao = dao;
        this.autoSaveService = autoSaveService;
        this.playerPackService = playerPackService;
        this.snowflakeManager = snowflakeManager;
        this.chapterService = chapterService;
        this.conditionService = conditionService;
    }

    /**
     * 登录检查当天道具补足，离线日期不累计。
     */
    public void onLogin(SimPlayerContext ctx) {
        grantDaily(ctx, System.currentTimeMillis());
    }

    /**
     * 零点事件投递到玩家队列，执行前再次确认仍在线。
     */
    public void onDaily(SimPlayerContext ctx, long now) {
        if (ctx.getPlayerController() != null && ctx.getPlayerController().isOnline()) {
            grantDaily(ctx, now);
        }
    }

    /**
     * 重新进入视为放弃旧局，不返还消耗，不发放旧局奖励。
     */
    public ResEnterGodGamblersGame enter(SimPlayerContext ctx) {
        GodGamblersRound previous = ctx.getGodGamblersRound();
        ctx.setGodGamblersRound(null);
        if (previous != null && previous.getState() != RoundState.CLEARED) {
            log.info("赌神搓牌重新进入时放弃旧局 playerId={},roundId={},state={},rubCount={}",
                    ctx.playerId(), previous.getRoundId(), previous.getState(), previous.getRubCount());
        }
        ResEnterGodGamblersGame res = new ResEnterGodGamblersGame(grantDaily(ctx, System.currentTimeMillis()));
        MinigameGodofGamblersRubbingCfg cfg = config(ctx);
        if (cfg == null) {
            res.code = Code.SAMPLE_ERROR;
            return res;
        }
        res.costItems = ItemUtils.buildItemInfo(cfg.getItemCost());
        res.ownedItems = ownedItems(ctx, cfg);
        res.maxRubCount = cfg.getRubCount();
        res.openOneClickPass = openOneClickPass(ctx);
        return res;
    }

    /**
     * 开局只扣一次费用，并先抽奖励点差，再构造两张合法牌；仅返回对手牌。
     */
    public ResStartGodGamblersGame start(SimPlayerContext ctx) {
        ResStartGodGamblersGame res = new ResStartGodGamblersGame(Code.SUCCESS);
        res.code = startRound(ctx, false);
        res.info = info(ctx.getGodGamblersRound());
        MinigameGodofGamblersRubbingCfg cfg = GameDataManager.getMinigameGodofGamblersRubbingCfg(MiniGameConstant.GOD_GAMBLERS_CONFIG_ID);
        if (cfg != null) {
            res.ownedItems = ownedItems(ctx, cfg);
        }
        return res;
    }

    /**
     * 首次仅亮出开局已决定的牌；后续搓牌扣费并按固定对手牌过滤奖励档位。
     */
    public ResGodGamblersSelfPoker selfPoker(SimPlayerContext ctx, long roundId, int rubCount) {
        ResGodGamblersSelfPoker res = new ResGodGamblersSelfPoker(Code.SUCCESS);
        GodGamblersRound round = ctx.getGodGamblersRound();
        res.code = checkRound(ctx, round, roundId);
        if (res.code != Code.SUCCESS) {
            return res;
        }
        MinigameGodofGamblersRubbingCfg cfg = config(ctx);
        if (cfg == null) {
            res.code = Code.SAMPLE_ERROR;
        } else if (round.getState() != RoundState.PLAYING || rubCount != round.getRubCount()) {
            log.warn("赌神取牌状态或次数不匹配 playerId={},roundId={},state={},rubCount={},actual={}",
                    ctx.playerId(), roundId, round.getState(), rubCount, round.getRubCount());
            res.code = Code.REPEAT_OP;
        } else if (round.getRubCount() >= cfg.getRubCount()) {
            log.warn("赌神搓牌次数已用完 playerId={},roundId={},rubCount={},limit={}",
                    ctx.playerId(), roundId, rubCount, cfg.getRubCount());
            res.code = Code.REPEAT_OP;
        } else if (round.getRubCount() == 0) {
            round.setRubCount(1);
        } else {
            int difference = drawDifference(ctx, cfg, MAX_RANK - rank(round.getOpponentPoker()));
            if (difference < 0) {
                res.code = Code.SAMPLE_ERROR;
            } else {
                res.code = charge(ctx, cfg, round);
                if (res.code == Code.SUCCESS) {
                    setSelfPoker(round, cfg, difference);
                    round.setRubCount(round.getRubCount() + 1);
                }
            }
        }
        if (res.code == Code.SUCCESS) {
            log.debug("赌神搓牌取牌成功 playerId={},roundId={},rubCount={}", ctx.playerId(), roundId, round.getRubCount());
        }
        res.info = info(round);
        if (cfg != null) {
            res.ownedItems = ownedItems(ctx, cfg);
        }
        return res;
    }

    /**
     * 仅在玩家取过自己的牌后结算；发奖失败保留待发奖状态供原局重试。
     */
    public ResGodGamblersConfirmBattle confirmBattle(SimPlayerContext ctx, long roundId) {
        ResGodGamblersConfirmBattle res = new ResGodGamblersConfirmBattle(Code.SUCCESS);
        GodGamblersRound round = ctx.getGodGamblersRound();
        res.code = checkRound(ctx, round, roundId);
        if (res.code != Code.SUCCESS) {
            return res;
        }
        if (round.getRubCount() == 0) {
            log.warn("赌神比拼尚未取自己的牌 playerId={},roundId={}", ctx.playerId(), roundId);
            res.code = Code.ERROR_REQ;
        } else {
            res.code = settle(ctx, round);
            if (res.code == Code.SUCCESS) {
                res.rewards = ItemUtils.buildItemInfo(round.getRewards());
            }
        }
        res.info = info(round);
        return res;
    }

    /**
     * 一键比拼复用开局及结算，只扣一次费用；失败时可携局号走确定比拼重试发奖。
     */
    public ResGodGamblersQuickBattle quickBattle(SimPlayerContext ctx) {
        ResGodGamblersQuickBattle res = new ResGodGamblersQuickBattle(Code.SUCCESS);
        if (!openOneClickPass(ctx)) {
            GlobalConfigCfg unlockCfg = GameDataManager.getGlobalConfigCfg(MiniGameConstant.GOD_GAMBLERS_QUICK_CLEAR_CHAPTER_GLOBAL_ID);
            log.warn("赌神一键比拼未开放 playerId={},globalId={},passedLevelId={},requiredLevelId={}",
                    ctx.playerId(), MiniGameConstant.GOD_GAMBLERS_QUICK_CLEAR_CHAPTER_GLOBAL_ID,
                    ctx.getSimChapterData() == null ? null : ctx.getSimChapterData().getLevelId(),
                    unlockCfg == null ? null : unlockCfg.getIntValue());
            res.code = Code.NOT_UNLOCKED;
        } else {
            res.code = startRound(ctx, true);
        }
        GodGamblersRound round = ctx.getGodGamblersRound();
        if (res.code == Code.SUCCESS) {
            round.setRubCount(1);
            res.code = settle(ctx, round);
            if (res.code == Code.SUCCESS) {
                res.rewards = ItemUtils.buildItemInfo(round.getRewards());
            }
        }
        res.info = info(round);
        MinigameGodofGamblersRubbingCfg cfg = GameDataManager.getMinigameGodofGamblersRubbingCfg(MiniGameConstant.GOD_GAMBLERS_CONFIG_ID);
        if (cfg != null) {
            res.ownedItems = ownedItems(ctx, cfg);
        }
        return res;
    }

    /**
     * 先校验并准备合法结果，扣费成功后才发布新局，失败不覆盖原局。
     */
    private int startRound(SimPlayerContext ctx, boolean quickClear) {
        GodGamblersRound current = ctx.getGodGamblersRound();
        if (current != null && current.getState() != RoundState.CLEARED) {
            log.warn("赌神已有未结束单局 playerId={},roundId={},state={}",
                    ctx.playerId(), current.getRoundId(), current.getState());
            return Code.REPEAT_OP;
        }
        MinigameGodofGamblersRubbingCfg cfg = config(ctx);
        if (cfg == null) {
            return Code.SAMPLE_ERROR;
        }
        int difference = drawDifference(ctx, cfg, MAX_RANK - MIN_RANK);
        if (difference < 0) {
            return Code.SAMPLE_ERROR;
        }
        GodGamblersRound round = new GodGamblersRound();
        round.setRoundId(snowflakeManager.nextId());
        //对手最大点数由已选点差限定，确保自己的牌不超过A且严格获胜。
        int opponentRank = RandomUtils.randomMinMax(MIN_RANK, MAX_RANK - difference);
        round.setOpponentPoker(poker(opponentRank));
        setSelfPoker(round, cfg, difference);
        int code = charge(ctx, cfg, round);
        if (code == Code.SUCCESS) {
            ctx.setGodGamblersRound(round);
            log.info("赌神搓牌开局成功 playerId={},roundId={},quickClear={},costItems={}",
                    ctx.playerId(), round.getRoundId(), quickClear, cfg.getItemCost());
            conditionService.onRoundStarted(ctx, 2);
        }
        return code;
    }

    /**
     * 开局与重搓共用配置费用，扣费失败不改变牌及已用次数。
     */
    private int charge(SimPlayerContext ctx, MinigameGodofGamblersRubbingCfg cfg, GodGamblersRound round) {
        CommonResult<ItemOperationResult> cost = playerPackService.removeItems(ctx.getPlayer(), cfg.getItemCost(), AddType.SIM_GOD_GAMBLERS);
        if (!cost.success()) {
            log.warn("赌神搓牌道具扣除失败 playerId={},roundId={},rubCount={},costItems={},code={}",
                    ctx.playerId(), round.getRoundId(), round.getRubCount(), cfg.getItemCost(), cost.code);
        }
        return cost.code;
    }

    /**
     * 只抽可形成有效牌面的正点差；不做重复抽取直到命中的循环。
     */
    private int drawDifference(SimPlayerContext ctx, MinigameGodofGamblersRubbingCfg cfg, int maxDifference) {
        List<Integer> choices = new ArrayList<>(cfg.getWinReward().size());
        for (Integer difference : cfg.getWinReward().keySet()) {
            if (difference == null || difference <= 0 || difference > MAX_RANK - MIN_RANK) {
                log.warn("赌神奖励点差无法生成牌面，忽略该档位 playerId={},difference={},max={}",
                        ctx.playerId(), difference, MAX_RANK - MIN_RANK);
            } else if (difference <= maxDifference) {
                choices.add(difference);
            }
        }
        if (choices.isEmpty()) {
            log.warn("赌神没有可用奖励点差 playerId={},maxDifference={}", ctx.playerId(), maxDifference);
            return -1;
        }
        return choices.get(RandomUtils.randomInt(choices.size()));
    }

    /**
     * 取新牌时一并保存奖励，不向客户端暴露开局尚未亮出的自己牌。
     */
    private void setSelfPoker(GodGamblersRound round, MinigameGodofGamblersRubbingCfg cfg, int difference) {
        round.setSelfPoker(poker(rank(round.getOpponentPoker()) + difference));
        Map<Integer, Long> rewards = new HashMap<>(cfg.getWinReward().get(difference).size());
        cfg.getWinReward().get(difference).forEach((id, count) -> rewards.put(id, count.longValue()));
        round.setRewards(rewards);
    }

    /**
     * 花色只影响显示；ID 0..12、13..25、26..38、39..51分别为四种花色的2..A。
     */
    private int poker(int rank) {
        return RandomUtils.randomInt(SUIT_COUNT) * RANKS_PER_SUIT + rank - MIN_RANK;
    }

    private int rank(int poker) {
        return poker % RANKS_PER_SUIT + MIN_RANK;
    }

    /**
     * 已完成的局保留在会话用于拦截重复结算，下次开局覆盖。
     */
    private int checkRound(SimPlayerContext ctx, GodGamblersRound round, long roundId) {
        if (round == null || round.getRoundId() != roundId) {
            log.warn("赌神局号不匹配 playerId={},roundId={},currentRoundId={},state={}",
                    ctx.playerId(), roundId, round == null ? null : round.getRoundId(),
                    round == null ? RoundState.NONE : round.getState());
            return Code.ERROR_REQ;
        }
        if (round.getState() == RoundState.CLEARED) {
            log.warn("赌神单局已经结算 playerId={},roundId={}", ctx.playerId(), roundId);
            return Code.REPEAT_OP;
        }
        return Code.SUCCESS;
    }

    /**
     * 发奖失败不退回进行中，避免玩家重搓已提交比拼的结果。
     */
    private int settle(SimPlayerContext ctx, GodGamblersRound round) {
        round.setState(RoundState.REWARD_PENDING);
        CommonResult<ItemOperationResult> reward = playerPackService.addItems(
                ctx.playerId(), round.getRewards(), AddType.SIM_GOD_GAMBLERS, true);
        if (!reward.success()) {
            log.warn("赌神比拼发奖失败 playerId={},roundId={},rewards={},code={}",
                    ctx.playerId(), round.getRoundId(), round.getRewards(), reward.code);
            return reward.code;
        }
        round.setState(RoundState.CLEARED);
        log.info("赌神搓牌结算成功 playerId={},roundId={},rubCount={},randomEventConfigId={},rewards={}",
                ctx.playerId(), round.getRoundId(), round.getRubCount(), round.randomEventConfigId, round.getRewards());
        if (round.randomEventConfigId != 0) {
            randomEvents.roomFinished(ctx, round.randomEventConfigId);
        }
        return Code.SUCCESS;
    }

    private boolean openOneClickPass(SimPlayerContext ctx) {
        GlobalConfigCfg cfg = GameDataManager.getGlobalConfigCfg(MiniGameConstant.GOD_GAMBLERS_QUICK_CLEAR_CHAPTER_GLOBAL_ID);
        if (cfg == null || cfg.getIntValue() <= 0) {
            return false;
        }
        if (ctx.getSimChapterData() == null) {
            int code = chapterService.getResChapterInfo(ctx).code;
            if (code != Code.SUCCESS) {
                log.warn("赌神一键比拼加载章节进度失败 playerId={},globalId={},requiredLevelId={},code={}",
                        ctx.playerId(), MiniGameConstant.GOD_GAMBLERS_QUICK_CLEAR_CHAPTER_GLOBAL_ID, cfg.getIntValue(), code);
                return false;
            }
        }
        return ctx.getSimChapterData().getLevelId() >= cfg.getIntValue();
    }

    /**
     * 单行配置按需读取；只校验费用、次数和可用档位奖励，不缓存热更新配置。
     */
    private MinigameGodofGamblersRubbingCfg config(SimPlayerContext ctx) {
        MinigameGodofGamblersRubbingCfg cfg = GameDataManager.getMinigameGodofGamblersRubbingCfg(MiniGameConstant.GOD_GAMBLERS_CONFIG_ID);
        if (cfg == null || cfg.getRubCount() < 1 || cfg.getItemCost() == null || cfg.getItemCost().isEmpty()
                || !validItems(cfg.getItemCost()) || cfg.getWinReward() == null || cfg.getWinReward().isEmpty()) {
            log.warn("赌神基础配置错误 playerId={},configId={}", ctx.playerId(), MiniGameConstant.GOD_GAMBLERS_CONFIG_ID);
            return null;
        }
        for (Map.Entry<Integer, Map<Integer, Integer>> entry : cfg.getWinReward().entrySet()) {
            Integer difference = entry.getKey();
            if (difference != null && difference > 0 && difference <= MAX_RANK - MIN_RANK) {
                Map<Integer, Integer> rewards = entry.getValue();
                if (rewards == null || rewards.isEmpty() || rewards.entrySet().stream().anyMatch(e ->
                        e.getKey() == null || e.getKey() <= 0 || e.getValue() == null || e.getValue() <= 0)) {
                    log.warn("赌神奖励配置错误 playerId={},difference={}", ctx.playerId(), difference);
                    return null;
                }
            }
        }
        return cfg;
    }

    /**
     * 返回消耗道具的持有量，复用背包接口，支持配置多种道具。
     */
    private List<ItemInfo> ownedItems(SimPlayerContext ctx, MinigameGodofGamblersRubbingCfg cfg) {
        if (!validItems(cfg.getItemCost())) {
            log.warn("赌神持有量查询的消耗配置错误 playerId={}", ctx.playerId());
            return List.of();
        }
        List<ItemInfo> items = new ArrayList<>(cfg.getItemCost().size());

        PlayerPack playerPack = null;
        for (Integer itemId : cfg.getItemCost().keySet()) {
            if (itemId == ItemUtils.getGoldItemId()) {
                items.add(ItemUtils.buildItemInfo(itemId, ctx.getPlayer().getGold()));
            } else if (itemId == ItemUtils.getDiamondItemId()) {
                items.add(ItemUtils.buildItemInfo(itemId, ctx.getPlayer().getDiamond()));
            } else if (itemId == ItemUtils.getShellItemId()) {
                items.add(ItemUtils.buildItemInfo(itemId, ctx.getPlayer().getShell()));
            } else {
                if (playerPack == null) {
                    playerPack = playerPackService.getFromAllDB(ctx.playerId());
                }
                items.add(ItemUtils.buildItemInfo(itemId, playerPack.getItemCount(itemId)));
            }
        }
        return items;
    }

    /**
     * 响应是本次操作的快照；第一次取牌前不泄露已抽中的自己牌及奖励。
     */
    private GodGamblersInfo info(GodGamblersRound round) {
        if (round == null) {
            return null;
        }
        GodGamblersInfo info = new GodGamblersInfo();
        info.roundId = round.getRoundId();
        info.opponentPoker = round.getOpponentPoker();
        info.selfPoker = round.getRubCount() == 0 ? -1 : round.getSelfPoker();
        info.rubCount = round.getRubCount();
        info.state = round.getState();
        return info;
    }

    /**
     * 每日首次处理时补足道具；已充足也记录当天，补足失败则保留重试机会。
     */
    private int grantDaily(SimPlayerContext ctx, long now) {
        GodGamblersData data = ctx.getGodGamblersData();
        if (data == null) {
            data = dao.findById(ctx.playerId()).orElseGet(() -> {
                GodGamblersData created = new GodGamblersData();
                created.setPlayerId(ctx.playerId());
                return created;
            });
            ctx.setGodGamblersData(data);
        }
        //排队的旧零点事件不能回退已成功发放的日期。
        if (data.getLastDailyGrantTime() >= now || TimeHelper.inSameDay(data.getLastDailyGrantTime(), now)) {
            return Code.SUCCESS;
        }
        MinigameGodofGamblersRubbingCfg cfg = GameDataManager.getMinigameGodofGamblersRubbingCfg(MiniGameConstant.GOD_GAMBLERS_CONFIG_ID);
        if (cfg == null || !validItems(cfg.getItemGift())) {
            log.warn("赌神每日赠送配置错误 playerId={},configId={}", ctx.playerId(), MiniGameConstant.GOD_GAMBLERS_CONFIG_ID);
            return Code.SAMPLE_ERROR;
        }
        CommonResult<ItemOperationResult> result = MiniGameDailyItems.replenish(
                ctx, cfg.getItemGift(), AddType.SIM_GOD_GAMBLERS, playerPackService);
        if (!result.success()) {
            log.warn("赌神每日补足失败 playerId={},targetItems={},code={}", ctx.playerId(), cfg.getItemGift(), result.code);
            return result.code;
        }
        data.setLastDailyGrantTime(now);
        autoSaveService.enqueueSave(data);
        return Code.SUCCESS;
    }

    /**
     * 赠送允许空表；已配置的道具ID和数量必须为正。
     */
    private boolean validItems(Map<Integer, Long> items) {
        return items != null && items.entrySet().stream().allMatch(e ->
                e.getKey() != null && e.getKey() > 0 && e.getValue() != null && e.getValue() > 0);
    }
}
