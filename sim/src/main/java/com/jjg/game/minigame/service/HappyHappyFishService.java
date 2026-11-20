package com.jjg.game.minigame.service;

import com.alibaba.fastjson.JSON;
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
import com.jjg.game.minigame.data.MiniGameSkillEffect;
import com.jjg.game.minigame.data.MiniGameSkillTree;
import com.jjg.game.minigame.pb.res.*;
import com.jjg.game.minigame.pb.struct.HappyHappyFishSkillInfo;
import com.jjg.game.sampledata.GameDataManager;
import com.jjg.game.sampledata.bean.GlobalConfigCfg;
import com.jjg.game.sampledata.bean.MinigameFishCfg;
import com.jjg.game.sampledata.bean.MinigameFishSkilCfg;
import com.jjg.game.sampledata.bean.MinigameHappyHappyFishCfg;
import com.jjg.game.sim.data.SimPlayerContext;
import com.jjg.game.sim.randomevent.SimRandomEventService;
import org.springframework.beans.factory.annotation.Autowired;
import com.jjg.game.sim.service.SimAutoSaveService;
import com.jjg.game.sim.service.SimChapterService;
import com.jjg.game.sim.service.SimConfigCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/** 欢乐捉鱼：客户端负责操作，服务器负责单局时限、积分和道具结算。 */
@Service
public class HappyHappyFishService {
    @Autowired
    private SimRandomEventService randomEvents;
    private static final Logger log = LoggerFactory.getLogger(HappyHappyFishService.class);
    private static final long END_TOLERANCE_MILLIS = 2000L;
    private static final int DURATION_BONUS = 1;
    private static final int SCORE_BONUS = 3;

    private final HappyHappyFishDao dao;
    private final SimAutoSaveService autoSaveService;
    private final PlayerPackService playerPackService;
    private final SimChapterService chapterService;
    private final MiniGameConditionService conditionService;
    private final SimConfigCacheService configCacheService;

    public HappyHappyFishService(HappyHappyFishDao dao, SimAutoSaveService autoSaveService,
                                PlayerPackService playerPackService, SimChapterService chapterService,
                                MiniGameConditionService conditionService, SimConfigCacheService configCacheService) {
        this.dao = dao;
        this.autoSaveService = autoSaveService;
        this.playerPackService = playerPackService;
        this.chapterService = chapterService;
        this.conditionService = conditionService;
        this.configCacheService = configCacheService;
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

    public ResHappyHappyFishSkills skills(SimPlayerContext ctx) {
        HappyHappyFishData data = getData(ctx);
        ResHappyHappyFishSkills res = new ResHappyHappyFishSkills(Code.SUCCESS);
        res.skills = configCacheService.getMinigameFishSkills().groupIds().stream()
                .map(groupId -> new HappyHappyFishSkillInfo(groupId, data.getSkillLevel(groupId)))
                .toList();
        res.skillPoints = data.getSkillPoints();
        log.warn("打印捉鱼技能 playerId={},res={}",ctx.playerId(), JSON.toJSONString(res));
        return res;
    }

    public ResUpgradeHappyHappyFishSkill upgradeSkill(SimPlayerContext ctx, int groupId) {
        HappyHappyFishData data = getData(ctx);
        int level = data.getSkillLevel(groupId);
        MiniGameSkillTree.Upgrade upgrade = configCacheService.getMinigameFishSkills().checkUpgrade(ctx.playerId(),
                groupId, data.getSkillLevels(), data.getSkillPoints(), configCacheService.getMinigameFishInitialSkillGroupIds()::contains);
        if (upgrade.code() == Code.SUCCESS) {
            data.setSkillPoints(data.getSkillPoints() - upgrade.cost());
            data.getSkillLevels().put(groupId, ++level);
            autoSaveService.enqueueSave(data);
        }
        ResUpgradeHappyHappyFishSkill res = new ResUpgradeHappyHappyFishSkill(upgrade.code());
        res.skill = new HappyHappyFishSkillInfo(groupId, level);
        res.skillPoints = data.getSkillPoints();
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
            res.code = prepareStart(ctx, cfg);
            if (res.code == Code.SUCCESS) {
                res.endTime = newRound(ctx, cfg, false).getEndTime();
            }
        }
        return res;
    }

    public ResSetHappyHappyFishPaused setPaused(SimPlayerContext ctx, boolean paused) {
        HappyHappyFishRound round = ctx.getHappyHappyFishRound();
        long now = System.currentTimeMillis();
        int code = round == null ? Code.ERROR_REQ : round.setPaused(paused, now);
        ResSetHappyHappyFishPaused res = new ResSetHappyHappyFishPaused(code);
        if (code == Code.SUCCESS) {
            res.paused = round.isPaused();
            res.remainingMillis = round.getRemainingMillis(now);
            res.endTime = res.paused ? 0 : round.getEndTime();
        } else {
            log.warn("欢乐捉鱼切换暂停状态失败 playerId={},paused={},currentRoundId={},state={},endTime={},now={},code={}",
                    ctx.playerId(), paused, round == null ? 0 : round.getRoundId(),
                    round == null ? RoundState.NONE : round.getState(), round == null ? 0 : round.getEndTime(), now, code);
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
        res.endTime = round.getEndTime();
        if (round.getState() != RoundState.PLAYING) {
            log.warn("欢乐捉鱼当前对局不可捕获 playerId={},roundId={},fishId={},state={},endTime={}",
                    ctx.playerId(), roundId, fishId, round.getState(), round.getEndTime());
            res.code = Code.REPEAT_OP;
            res.score = round.getScore();
            return res;
        }
        MinigameFishCfg fish = GameDataManager.getMinigameFishCfg(fishId);
        if (isExpired(round)) {
            res.code = Code.EXPIRE;
        } else if (fish == null) {
            res.code = Code.PARAM_ERROR;
        } else if (fish.getScore() < 0 || fish.getMaxCount() <= 0) {
            res.code = Code.SAMPLE_ERROR;
        } else {
            long score = round.scoreFor(fishId, fish.getType(), fish.getScore());
            if (score < 0 || score > Integer.MAX_VALUE - round.getScore()) {
                log.warn("欢乐捉鱼捕获得分超出范围 playerId={},roundId={},fishId={},roundScore={},catchScore={}",
                        ctx.playerId(), roundId, fishId, round.getScore(), score);
                res.code = Code.PARAM_ERROR;
            } else if (round.getCaughtCounts().getOrDefault(fishId, 0) >= fish.getMaxCount()) {
                res.code = Code.PARAM_ERROR;
            } else {
                round.setScore(round.getScore() + (int) score);
                round.getCaughtCounts().merge(fishId, 1, Integer::sum);
            }
        }
        res.score = round.getScore();
        return res;
    }

    /** 按已接受的捕获入账积分，单局道具奖励沿用结束请求的时间窗口。 */
    public ResEndHappyHappyFishGame end(SimPlayerContext ctx, long roundId) {
        ResEndHappyHappyFishGame res = new ResEndHappyHappyFishGame(Code.SUCCESS);
        HappyHappyFishRound round = ctx.getHappyHappyFishRound();
        res.code = checkRound(round, roundId);
        if (res.code != Code.SUCCESS) {
            return res;
        }
        if (round.getState() == RoundState.PLAYING && isExpired(round)) {
            creditScore(ctx, round);
            res.code = Code.EXPIRE;
        } else {
            res.code = settle(ctx, round);
            if (res.code == Code.SUCCESS) {
                res.rewards = ItemUtils.buildItemInfo(round.getRewards());
            }
        }
        res.skillPoints = getData(ctx).getSkillPoints();
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
                res.code = prepareStart(ctx, cfg);
                if (res.code == Code.SUCCESS) {
                    round = newRound(ctx, cfg, true);
                    round.setScore(scoreCfg.getIntValue());
                }
            }
        }
        if (res.code == Code.SUCCESS) {
            res.code = settle(ctx, round);
            res.score = round.getScore();
            res.skillPoints = getData(ctx).getSkillPoints();
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
                round.randomEventConfigId = 0;
                int code = settle(ctx, round);
                if (code != Code.SUCCESS) {
                    return code;
                }
            }
            ctx.setHappyHappyFishRound(null);
        }
        return Code.SUCCESS;
    }

    private int prepareStart(SimPlayerContext ctx, MinigameHappyHappyFishCfg cfg) {
        HappyHappyFishRound previous = ctx.getHappyHappyFishRound();
        if (previous != null && previous.getState() == RoundState.PLAYING) {
            creditScore(ctx, previous);
        }
        int code = grantDaily(ctx, System.currentTimeMillis());
        if (code != Code.SUCCESS) {
            return code;
        }
        code = playerPackService.removeItems(ctx.getPlayer(), cfg.getConsumeCount(), AddType.SIM_HAPPY_HAPPY_FISH).code;
        if (code == Code.SUCCESS) {
            conditionService.onRoundStarted(ctx, 3);
        }
        return code;
    }

    private boolean canStart(SimPlayerContext ctx) {
        HappyHappyFishRound round = ctx.getHappyHappyFishRound();
        return round == null || round.getState() == RoundState.CLEARED
                || (round.getState() == RoundState.PLAYING && isExpired(round));
    }

    private boolean isExpired(HappyHappyFishRound round) {
        return System.currentTimeMillis() > round.getEndTime() + END_TOLERANCE_MILLIS;
    }

    private HappyHappyFishRound newRound(SimPlayerContext ctx, MinigameHappyHappyFishCfg cfg, boolean quickClear) {
        HappyHappyFishRound previous = ctx.getHappyHappyFishRound();
        HappyHappyFishRound round = new HappyHappyFishRound();
        round.setQuickClear(quickClear);
        long duration = cfg.getDuration() * 1000L;
        if (!quickClear) {
            duration = applySkillEffects(getData(ctx), round, duration);
        }
        long endTime = System.currentTimeMillis() + duration;
        //即使同一毫秒内连续一键通关，局号也不会复用。
        round.setRoundId(previous == null ? endTime : Math.max(endTime, previous.getRoundId() + 1));
        round.setEndTime(round.getRoundId());
        ctx.setHappyHappyFishRound(round);
        return round;
    }

    private long applySkillEffects(HappyHappyFishData data, HappyHappyFishRound round, long baseDuration) {
        long flatSeconds = 0;
        long permille = 0;
        var skillTree = configCacheService.getMinigameFishSkills();
        for (var skill : data.getSkillLevels().entrySet()) {
            MinigameFishSkilCfg cfg = skillTree.levelConfig(skill.getKey(), skill.getValue());
            if (cfg == null) {
                log.warn("欢乐捉鱼开局缺少已升级技能配置 playerId={},groupId={},level={}",
                        data.getPlayerId(), skill.getKey(), skill.getValue());
                continue;
            }
            if (cfg.getTarget() > 0 || cfg.getType() == null) {
                continue;
            }
            for (List<Integer> type : cfg.getType()) {
                if (type.get(0) == DURATION_BONUS) {
                    flatSeconds += cfg.getFlatValue();
                    permille += cfg.getPermille();
                } else if (type.get(0) == SCORE_BONUS) {
                    round.addScoreBonus(type.get(1), cfg.getFlatValue(), cfg.getPermille());
                }
            }
        }
        return baseDuration + flatSeconds * 1000 + MiniGameSkillEffect.scaleByPermille(baseDuration, permille);
    }

    private int checkRound(HappyHappyFishRound round, long roundId) {
        if (round == null || round.getRoundId() != roundId) {
            return Code.ERROR_REQ;
        }
        return round.getState() == RoundState.CLEARED ? Code.REPEAT_OP : Code.SUCCESS;
    }

    private void creditScore(SimPlayerContext ctx, HappyHappyFishRound round) {
        if (round.isScoreCredited() || round.getScore() == 0) {
            return;
        }
        HappyHappyFishData data = getData(ctx);
        data.setSkillPoints(data.getSkillPoints() + round.getScore());
        round.markScoreCredited();
        autoSaveService.enqueueSave(data);
    }

    /** 无序map中选择不大于积分的最高阈值，只扫描一次，不排序或复制整张奖励表。 */
    private int settle(SimPlayerContext ctx, HappyHappyFishRound round) {
        creditScore(ctx, round);
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
            if (round.randomEventConfigId != 0) {
                randomEvents.roomFinished(ctx, round.randomEventConfigId);
            }
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

    private HappyHappyFishData getData(SimPlayerContext ctx) {
        HappyHappyFishData data = ctx.getHappyHappyFishData();
        if (data == null) {
            data = dao.findById(ctx.playerId()).orElseGet(() -> {
                HappyHappyFishData created = new HappyHappyFishData();
                created.setPlayerId(ctx.playerId());
                return created;
            });
            ctx.setHappyHappyFishData(data);
        }
        return data;
    }

    /** 每日首次处理时补足道具；已充足也记录当天，补足失败则保留重试机会。 */
    private int grantDaily(SimPlayerContext ctx, long now) {
        HappyHappyFishData data = getData(ctx);
        if (data.getLastDailyGrantTime() >= now || TimeHelper.inSameDay(data.getLastDailyGrantTime(), now)) {
            return Code.SUCCESS;
        }
        MinigameHappyHappyFishCfg cfg = config();
        if (cfg == null || !validItems(cfg.getGrantCount())) {
            return Code.SAMPLE_ERROR;
        }
        CommonResult<ItemOperationResult> result = MiniGameDailyItems.replenish(
                ctx, cfg.getGrantCount(), AddType.SIM_HAPPY_HAPPY_FISH, playerPackService);
        if (!result.success()) {
            log.warn("欢乐捉鱼每日补足失败 playerId={},code={}", ctx.playerId(), result.code);
            return result.code;
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
        if (cfg.getDuration() <= 0 || !validItems(cfg.getConsumeCount())
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
