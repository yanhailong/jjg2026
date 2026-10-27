package com.jjg.game.minigame.service;

import com.jjg.game.common.utils.TimeHelper;
import com.jjg.game.core.constant.AddType;
import com.jjg.game.core.constant.Code;
import com.jjg.game.core.data.CommonResult;
import com.jjg.game.core.data.ItemOperationResult;
import com.jjg.game.core.data.PlayerPack;
import com.jjg.game.core.service.PlayerPackService;
import com.jjg.game.core.utils.ItemUtils;
import com.jjg.game.minigame.constant.MiniGameConstant;
import com.jjg.game.minigame.constant.MiniGameConstant.RoundState;
import com.jjg.game.minigame.dao.HappyHappyFishDao;
import com.jjg.game.minigame.data.HappyHappyFishData;
import com.jjg.game.minigame.data.HappyHappyFishRound;
import com.jjg.game.minigame.pb.res.*;
import com.jjg.game.sampledata.GameDataManager;
import com.jjg.game.sampledata.bean.GlobalConfigCfg;
import com.jjg.game.sampledata.bean.MinigameFishCfg;
import com.jjg.game.sampledata.bean.MinigameHappyHappyFishCfg;
import com.jjg.game.sim.data.SimPlayerContext;
import com.jjg.game.sim.service.SimAutoSaveService;
import com.jjg.game.sim.service.SimChapterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/** 欢乐捉鱼：客户端负责操作，服务器负责单局时限、积分和道具结算。 */
@Service
public class HappyHappyFishService {
    private static final Logger log = LoggerFactory.getLogger(HappyHappyFishService.class);
    private static final long END_TOLERANCE_MILLIS = 2000L;

    private final HappyHappyFishDao dao;
    private final SimAutoSaveService autoSaveService;
    private final PlayerPackService playerPackService;
    private final SimChapterService chapterService;

    public HappyHappyFishService(HappyHappyFishDao dao, SimAutoSaveService autoSaveService,
                                PlayerPackService playerPackService, SimChapterService chapterService) {
        this.dao = dao;
        this.autoSaveService = autoSaveService;
        this.playerPackService = playerPackService;
        this.chapterService = chapterService;
    }

    public void onLogin(SimPlayerContext ctx) {
        HappyHappyFishRound round = ctx.getHappyHappyFishRound();
        //掉线发奖失败仍留在原会话，重登先重试交付，不恢复游戏过程。
        if (round != null && round.getState() == RoundState.REWARD_PENDING) {
            onExit(ctx);
        }
        grantDaily(ctx, System.currentTimeMillis());
    }

    public void onDaily(SimPlayerContext ctx, long now) {
        if (ctx.getPlayerController() != null && ctx.getPlayerController().isOnline()) {
            grantDaily(ctx, now);
        }
    }

    /** 进入只获取费用、持有量及章节解锁状态，不重置已有单局。 */
    public ResEnterHappyHappyFishGame enter(SimPlayerContext ctx) {
        ResEnterHappyHappyFishGame res = new ResEnterHappyHappyFishGame(grantDaily(ctx, System.currentTimeMillis()));
        MinigameHappyHappyFishCfg cfg = config();
        if (cfg == null) {
            res.code = Code.SAMPLE_ERROR;
            return res;
        }
        res.costItems = ItemUtils.buildItemInfo(cfg.getConsumeCount());
        res.ownedItems = new ArrayList<>(cfg.getConsumeCount().size());

        PlayerPack playerPack = null;
        for (Integer itemId : cfg.getConsumeCount().keySet()) {
            if (itemId == ItemUtils.getGoldItemId()) {
                res.ownedItems.add(ItemUtils.buildItemInfo(itemId, ctx.getPlayer().getGold()));
            } else if (itemId == ItemUtils.getDiamondItemId()) {
                res.ownedItems.add(ItemUtils.buildItemInfo(itemId, ctx.getPlayer().getDiamond()));
            } else if (itemId == ItemUtils.getShellItemId()) {
                res.ownedItems.add(ItemUtils.buildItemInfo(itemId, ctx.getPlayer().getShell()));
            } else {
                if (playerPack == null) {
                    playerPack = playerPackService.getFromAllDB(ctx.playerId());
                }
                res.ownedItems.add(ItemUtils.buildItemInfo(itemId, playerPack.getItemCount(itemId)));
            }
        }
        res.openOneClickPass = openOneClickPass(ctx);
        return res;
    }

    public ResStartHappyHappyFishGame start(SimPlayerContext ctx) {
        ResStartHappyHappyFishGame res = new ResStartHappyHappyFishGame(Code.SUCCESS);
        MinigameHappyHappyFishCfg cfg = config();
        if (cfg == null) {
            res.code = Code.SAMPLE_ERROR;
        } else if (!canStart(ctx)) {
            res.code = Code.REPEAT_OP;
        } else {
            res.code = grantDaily(ctx, System.currentTimeMillis());
            if (res.code == Code.SUCCESS) {
                res.code = charge(ctx, cfg);
                if (res.code == Code.SUCCESS) {
                    res.endTime = newRound(ctx, cfg).getEndTime();
                }
            }
        }
        return res;
    }

    /** 每次合法捕获按鱼表累加积分；仅保存各配置的累计次数，不模拟鱼群。 */
    public ResCatchHappyHappyFish catchFish(SimPlayerContext ctx, long roundId, int fishId) {
        ResCatchHappyHappyFish res = new ResCatchHappyHappyFish(Code.SUCCESS);
        HappyHappyFishRound round = ctx.getHappyHappyFishRound();
        res.code = checkRound(round, roundId);
        if (res.code != Code.SUCCESS) {
            return res;
        }
        MinigameFishCfg fish = GameDataManager.getMinigameFishCfg(fishId);
        if (round.getState() != RoundState.PLAYING) {
            res.code = Code.REPEAT_OP;
        } else if (System.currentTimeMillis() > round.getEndTime() + END_TOLERANCE_MILLIS) {
            res.code = Code.EXPIRE;
        } else if (fish == null) {
            res.code = Code.PARAM_ERROR;
        } else if (fish.getScore() < 0 || fish.getMaxCount() <= 0) {
            res.code = Code.SAMPLE_ERROR;
        } else if (round.getCaughtCounts().getOrDefault(fishId, 0) >= fish.getMaxCount()
                || round.getScore() > Integer.MAX_VALUE - fish.getScore()) {
            res.code = Code.PARAM_ERROR;
        } else {
            long extraTime = 0;
            if (fishId == MiniGameConstant.HAPPY_HAPPY_FISH_HOURGLASS_ID) {
                MinigameHappyHappyFishCfg cfg = config();
                if (cfg == null) {
                    res.code = Code.SAMPLE_ERROR;
                } else {
                    extraTime = cfg.getExtraTime() * 1000L;
                }
            }
            if (res.code == Code.SUCCESS) {
                round.setScore(round.getScore() + fish.getScore());
                round.setEndTime(round.getEndTime() + extraTime);
                round.getCaughtCounts().merge(fishId, 1, Integer::sum);
            }
        }
        res.score = round.getScore();
        res.endTime = round.getEndTime();
        return res;
    }

    /** 正常结束仅接受当前截止时间前后两秒；已提交的发奖失败重试不再受时限影响。 */
    public ResEndHappyHappyFishGame end(SimPlayerContext ctx, long roundId) {
        ResEndHappyHappyFishGame res = new ResEndHappyHappyFishGame(Code.SUCCESS);
        HappyHappyFishRound round = ctx.getHappyHappyFishRound();
        res.code = checkRound(round, roundId);
        if (res.code != Code.SUCCESS) {
            return res;
        }
        long now = System.currentTimeMillis();
        if (round.getState() == RoundState.PLAYING && now < round.getEndTime() - END_TOLERANCE_MILLIS) {
            res.code = Code.ERROR_REQ;
        } else if (round.getState() == RoundState.PLAYING && now > round.getEndTime() + END_TOLERANCE_MILLIS) {
            res.code = Code.EXPIRE;
        } else {
            res.code = settle(ctx, round);
            if (res.code == Code.SUCCESS) {
                res.rewards = ItemUtils.buildItemInfo(round.getRewards());
            }
        }
        res.score = round.getScore();
        return res;
    }

    /** 一键通关不生成鱼群，发奖失败重试本协议时复用积分及奖励，不再扣费。 */
    public ResQuickClearHappyHappyFishGame quickClear(SimPlayerContext ctx) {
        ResQuickClearHappyHappyFishGame res = new ResQuickClearHappyHappyFishGame(Code.SUCCESS);
        HappyHappyFishRound round = ctx.getHappyHappyFishRound();
        if (round == null || round.getState() != RoundState.REWARD_PENDING || !round.isQuickClear()) {
            MinigameHappyHappyFishCfg cfg = config();
            GlobalConfigCfg scoreCfg = GameDataManager.getGlobalConfigCfg(MiniGameConstant.HAPPY_HAPPY_FISH_QUICK_CLEAR_SCORE_GLOBAL_ID);
            if (cfg == null || scoreCfg == null || scoreCfg.getIntValue() < 0) {
                res.code = Code.SAMPLE_ERROR;
            } else if (!canStart(ctx)) {
                res.code = Code.REPEAT_OP;
            } else if (!openOneClickPass(ctx)) {
                res.code = Code.NOT_UNLOCKED;
            } else {
                res.code = grantDaily(ctx, System.currentTimeMillis());
                if (res.code == Code.SUCCESS) {
                    res.code = charge(ctx, cfg);
                    if (res.code == Code.SUCCESS) {
                        round = newRound(ctx, cfg);
                        round.setQuickClear(true);
                        round.setScore(scoreCfg.getIntValue());
                    }
                }
            }
        }
        if (res.code == Code.SUCCESS) {
            res.code = settle(ctx, round);
            res.score = round.getScore();
            if (res.code == Code.SUCCESS) {
                res.rewards = ItemUtils.buildItemInfo(round.getRewards());
            }
        }
        return res;
    }

    /** 断线或退出立即按当前积分结算；失败保留会话内待发奖状态，不持久化单局。 */
    public int onExit(SimPlayerContext ctx) {
        HappyHappyFishRound round = ctx.getHappyHappyFishRound();
        if (round != null) {
            if (round.getState() != RoundState.CLEARED) {
                int code = settle(ctx, round);
                if (code != Code.SUCCESS) {
                    return code;
                }
            }
            ctx.setHappyHappyFishRound(null);
        }
        return Code.SUCCESS;
    }

    private int charge(SimPlayerContext ctx, MinigameHappyHappyFishCfg cfg) {
        return playerPackService.removeItems(ctx.getPlayer(), cfg.getConsumeCount(), AddType.SIM_HAPPY_HAPPY_FISH).code;
    }

    private boolean canStart(SimPlayerContext ctx) {
        HappyHappyFishRound round = ctx.getHappyHappyFishRound();
        return round == null || round.getState() == RoundState.CLEARED
                || (round.getState() == RoundState.PLAYING
                && System.currentTimeMillis() > round.getEndTime() + END_TOLERANCE_MILLIS);
    }

    private HappyHappyFishRound newRound(SimPlayerContext ctx, MinigameHappyHappyFishCfg cfg) {
        HappyHappyFishRound previous = ctx.getHappyHappyFishRound();
        HappyHappyFishRound round = new HappyHappyFishRound();
        long endTime = System.currentTimeMillis() + cfg.getDuration() * 1000L;
        //即使同一毫秒内连续一键通关，局号也不会复用。
        round.setRoundId(previous == null ? endTime : Math.max(endTime, previous.getRoundId() + 1));
        round.setEndTime(round.getRoundId());
        ctx.setHappyHappyFishRound(round);
        return round;
    }

    private int checkRound(HappyHappyFishRound round, long roundId) {
        if (round == null || round.getRoundId() != roundId) {
            return Code.ERROR_REQ;
        }
        return round.getState() == RoundState.CLEARED ? Code.REPEAT_OP : Code.SUCCESS;
    }

    /** 无序map中选择不大于积分的最高阈值，只扫描一次，不排序或复制整张奖励表。 */
    private int settle(SimPlayerContext ctx, HappyHappyFishRound round) {
        round.setState(RoundState.REWARD_PENDING);
        if (round.getRewards() == null) {
            MinigameHappyHappyFishCfg cfg = config();
            if (cfg == null) {
                return Code.SAMPLE_ERROR;
            }
            int threshold = -1;
            for (int key : cfg.getReward().keySet()) {
                if (key <= round.getScore() && key > threshold) {
                    threshold = key;
                }
            }
            Map<Integer, Long> rewards = new HashMap<>(cfg.getReward().get(threshold).size());
            cfg.getReward().get(threshold).forEach((itemId, count) -> rewards.put(itemId, count.longValue()));
            round.setRewards(rewards);
        }
        CommonResult<ItemOperationResult> result = playerPackService.addItems(
                ctx.playerId(), round.getRewards(), AddType.SIM_HAPPY_HAPPY_FISH, true);
        if (result.success()) {
            round.setState(RoundState.CLEARED);
        } else {
            log.warn("欢乐捉鱼发奖失败 playerId={},roundId={},score={},code={}",
                    ctx.playerId(), round.getRoundId(), round.getScore(), result.code);
        }
        return result.code;
    }

    private boolean openOneClickPass(SimPlayerContext ctx) {
        GlobalConfigCfg cfg = GameDataManager.getGlobalConfigCfg(MiniGameConstant.HAPPY_HAPPY_FISH_QUICK_CLEAR_CHAPTER_GLOBAL_ID);
        if (cfg == null || cfg.getIntValue() <= 0) {
            return false;
        }
        if (ctx.getSimChapterData() == null && chapterService.getResChapterInfo(ctx).code != Code.SUCCESS) {
            return false;
        }
        return ctx.getSimChapterData().getLevelId() >= cfg.getIntValue();
    }

    /** 一天只赠送一次，失败不推进日期，离线日期不累计补发。 */
    private int grantDaily(SimPlayerContext ctx, long now) {
        HappyHappyFishData data = ctx.getHappyHappyFishData();
        if (data == null) {
            data = dao.findById(ctx.playerId()).orElseGet(() -> {
                HappyHappyFishData created = new HappyHappyFishData();
                created.setPlayerId(ctx.playerId());
                return created;
            });
            ctx.setHappyHappyFishData(data);
        }
        if (data.getLastDailyGrantTime() >= now || TimeHelper.inSameDay(data.getLastDailyGrantTime(), now)) {
            return Code.SUCCESS;
        }
        MinigameHappyHappyFishCfg cfg = config();
        if (cfg == null || !validItems(cfg.getGrantCount())) {
            return Code.SAMPLE_ERROR;
        }
        if (!cfg.getGrantCount().isEmpty()) {
            CommonResult<ItemOperationResult> result = playerPackService.addItems(
                    ctx.playerId(), cfg.getGrantCount(), AddType.SIM_HAPPY_HAPPY_FISH, true);
            if (!result.success()) {
                log.warn("欢乐捉鱼每日赠送失败 playerId={},code={}", ctx.playerId(), result.code);
                return result.code;
            }
        }
        data.setLastDailyGrantTime(now);
        autoSaveService.enqueueSave(data);
        return Code.SUCCESS;
    }

    /** 单行配置按需读取，扣费前保证零分奖励及各档奖励合法。 */
    private MinigameHappyHappyFishCfg config() {
        Map<Integer, MinigameHappyHappyFishCfg> configs = GameDataManager.getMinigameHappyHappyFishCfgMap();
        if (configs.size() != 1) {
            return null;
        }
        MinigameHappyHappyFishCfg cfg = configs.values().iterator().next();
        if (cfg.getDuration() <= 0 || cfg.getExtraTime() < 0 || !validItems(cfg.getConsumeCount())
                || cfg.getConsumeCount().isEmpty() || cfg.getReward() == null || !cfg.getReward().containsKey(0)) {
            return null;
        }
        for (Map.Entry<Integer, Map<Integer, Integer>> entry : cfg.getReward().entrySet()) {
            if (entry.getKey() < 0 || entry.getValue() == null || entry.getValue().entrySet().stream().anyMatch(e ->
                    e.getKey() <= 0 || e.getValue() <= 0)) {
                return null;
            }
        }
        return cfg;
    }

    private boolean validItems(Map<Integer, Long> items) {
        return items != null && items.entrySet().stream().allMatch(e -> e.getKey() > 0 && e.getValue() > 0);
    }
}
