package com.jjg.game.minigame.service;

import com.jjg.game.common.utils.RandomUtils;
import com.jjg.game.common.utils.TimeHelper;
import com.jjg.game.core.constant.AddType;
import com.jjg.game.core.constant.Code;
import com.jjg.game.core.data.CommonResult;
import com.jjg.game.core.data.ItemOperationResult;
import com.jjg.game.core.service.PlayerPackService;
import com.jjg.game.core.utils.ItemUtils;
import com.jjg.game.minigame.constant.MiniGameConstant;
import com.jjg.game.minigame.constant.MiniGameConstant.RoundState;
import com.jjg.game.minigame.dao.CheaterHuntDao;
import com.jjg.game.minigame.data.CheaterHuntData;
import com.jjg.game.minigame.data.CheaterHuntRound;
import com.jjg.game.minigame.pb.res.*;
import com.jjg.game.minigame.pb.struct.CheaterHuntInfo;
import com.jjg.game.minigame.pb.struct.CheaterHuntSimpleInfo;
import com.jjg.game.sampledata.GameDataManager;
import com.jjg.game.sampledata.bean.GlobalConfigCfg;
import com.jjg.game.sampledata.bean.MinigameCheaterHuntLevelCfg;
import com.jjg.game.sampledata.bean.MinigameCheaterHuntMapCfg;
import com.jjg.game.sim.data.SimPlayerContext;
import com.jjg.game.sim.randomevent.SimRandomEventService;
import org.springframework.beans.factory.annotation.Autowired;
import com.jjg.game.sim.service.SimAutoSaveService;
import com.jjg.game.sim.service.SimChapterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 找老千玩法：关卡进度持久化，单局只保存在玩家会话中。
 * 沿用玩家串行队列及统一背包接口，不额外创建锁、事务或定时器。
 */
@Service
public class SimMiniGameService {
    @Autowired
    private SimRandomEventService randomEvents;
    private static final Logger log = LoggerFactory.getLogger(SimMiniGameService.class);
    private final CheaterHuntDao dao;
    private final SimAutoSaveService autoSaveService;
    private final PlayerPackService playerPackService;
    private final SimChapterService chapterService;
    private final MiniGameConditionService conditionService;

    public SimMiniGameService(CheaterHuntDao dao, SimAutoSaveService autoSaveService,
                              PlayerPackService playerPackService, SimChapterService chapterService,
                              MiniGameConditionService conditionService) {
        this.dao = dao;
        this.autoSaveService = autoSaveService;
        this.playerPackService = playerPackService;
        this.chapterService = chapterService;
        this.conditionService = conditionService;
    }

    /** 登录检查当天道具补足，离线日期不累计补发。 */
    public void onLogin(SimPlayerContext ctx) {
        grantDaily(ctx, data(ctx), System.currentTimeMillis());
    }

    /** 零点事件在玩家队列内调用，只检查仍在线玩家的当日道具补足。 */
    public void onDaily(SimPlayerContext ctx, long now) {
        if (ctx.getPlayerController() != null && ctx.getPlayerController().isOnline()) {
            grantDaily(ctx, data(ctx), now);
        }
    }

    /** 进入只返回总览，不创建单局或消耗解锁道具。 */
    public ResEnterCheaterHuntGame enter(SimPlayerContext ctx) {
        CheaterHuntData data = data(ctx);
        ResEnterCheaterHuntGame res = new ResEnterCheaterHuntGame(grantDaily(ctx, data, System.currentTimeMillis()));
        res.openOneClickPass = openOneClickPass(ctx);
        List<MinigameCheaterHuntLevelCfg> levels = levels();
        res.allLevels = levels.size();
        res.passLevels = levels.indexOf(GameDataManager.getMinigameCheaterHuntLevelCfg(data.getMaxPassedLevelId())) + 1;
        int unlockedIndex = levels.indexOf(GameDataManager.getMinigameCheaterHuntLevelCfg(data.getLevelId()));
        res.levelSimpleInfoList = new ArrayList<>(levels.size());
        for (int i = 0; i < levels.size(); i++) {
            MinigameCheaterHuntLevelCfg cfg = levels.get(i);
            CheaterHuntSimpleInfo info = new CheaterHuntSimpleInfo();
            info.levelId = cfg.getId();
            info.levelLangId = cfg.getMapNameLocalization();
            info.unlock = i <= unlockedIndex;
            info.costItems = ItemUtils.buildItemInfo(cfg.getCostItemID());
            info.unlockItems = ItemUtils.buildItemInfo(cfg.getClearCount());
            res.levelSimpleInfoList.add(info);
        }
        return res;
    }

    /** 按当前已解锁关卡随机场景及不重复点位，每回合开局扣一次配置道具。 */
    public ResCheaterHuntGameInfo gameInfo(SimPlayerContext ctx) {
        CheaterHuntData data = data(ctx);
        ResCheaterHuntGameInfo res = new ResCheaterHuntGameInfo(grantDaily(ctx, data, System.currentTimeMillis()));
        if (res.code == Code.SUCCESS) {
            if (canStart(ctx)) {
                MinigameCheaterHuntLevelCfg cfg = level(ctx, data.getLevelId());
                if (cfg == null) {
                    res.code = Code.SAMPLE_ERROR;
                } else {
                    int mapId = RandomUtils.randomEle(cfg.getUseCase());
                    MinigameCheaterHuntMapCfg map = map(ctx, cfg, mapId);
                    if (map == null) {
                        res.code = Code.SAMPLE_ERROR;
                    } else {
                        CommonResult<ItemOperationResult> cost = playerPackService.removeItems(
                                ctx.getPlayer(), cfg.getCostItemID(), AddType.SIM_CHEATER_HUNT);
                        res.code = cost.code;
                        if (!cost.success()) {
                            log.warn("找老千开局道具扣除失败 playerId={},levelId={},mapId={},costItems={},code={}",
                                    ctx.playerId(), cfg.getId(), mapId, cfg.getCostItemID(), cost.code);
                        } else {
                            CheaterHuntRound round = newRound(ctx, data);
                            round.setMapId(mapId);
                            //复用已有洗牌工具，只保留抽中的点位，不为每个点位创建状态对象。
                            int[] spots = RandomUtils.randomInts(map.getTotalSpotCount());
                            List<Integer> drawn = new ArrayList<>(cfg.getDrawCount());
                            for (int i = 0; i < cfg.getDrawCount(); i++) {
                                drawn.add(spots[i]);
                            }
                            round.setCheaterSpots(drawn);
                            //玩家首局额外增加120秒，不限时关卡保持-1。
                            round.setEndTime(cfg.getTimeLimit() == -1 ? -1
                                    : System.currentTimeMillis() + cfg.getTimeLimit() * 1000L
                                    + (round.getRoundId() == 1 ? 120_000L : 0));
                            log.info("找老千开局成功 playerId={},roundId={},levelId={},mapId={},targetCount={},endTime={},costItems={}",
                                    ctx.playerId(), round.getRoundId(), cfg.getId(), mapId, cfg.getTargetCount(),
                                    round.getEndTime(), cfg.getCostItemID());
                            conditionService.onRoundStarted(ctx, 1);
                        }
                    }
                }
            } else {
                res.code = Code.REPEAT_OP;
            }
        }
        res.info = assemble(ctx, data);
        return res;
    }

    /** 当前关通关后扣除下一关的解锁道具，成功后推进解锁进度，不创建单局。 */
    public ResUnlockNextLevel unlockNextLevel(SimPlayerContext ctx) {
        CheaterHuntData data = data(ctx);
        ResUnlockNextLevel res = new ResUnlockNextLevel(Code.SUCCESS);
        MinigameCheaterHuntLevelCfg current = GameDataManager.getMinigameCheaterHuntLevelCfg(data.getLevelId());
        List<MinigameCheaterHuntLevelCfg> levels = levels();
        int index = levels.indexOf(current);
        if (index < 0) {
            log.warn("找老千解锁进度配置不存在 playerId={},levelId={}", ctx.playerId(), data.getLevelId());
            res.code = Code.SAMPLE_ERROR;
        } else if (index + 1 >= levels.size()) {
            log.warn("找老千解锁下一关被拒绝，已到最后一关 playerId={},levelId={}", ctx.playerId(), data.getLevelId());
            res.code = Code.REPEAT_OP;
        } else if (!canStart(ctx)) {
            res.code = Code.REPEAT_OP;
        } else if (data.getMaxPassedLevelId() != data.getLevelId()) {
            log.warn("找老千解锁下一关被拒绝，当前关卡尚未通关 playerId={},levelId={},maxPassedLevelId={}",
                    ctx.playerId(), data.getLevelId(), data.getMaxPassedLevelId());
            res.code = Code.NOT_UNLOCKED;
        } else {
            MinigameCheaterHuntLevelCfg next = level(ctx, levels.get(index + 1).getId());
            if (next == null || !validUnlockItems(next.getClearCount())) {
                log.warn("找老千解锁条件配置错误 playerId={},levelId={}", ctx.playerId(), levels.get(index + 1).getId());
                res.code = Code.SAMPLE_ERROR;
            } else {
                CommonResult<ItemOperationResult> cost = playerPackService.removeItems(
                        ctx.getPlayer(), next.getClearCount(), AddType.SIM_CHEATER_HUNT);
                res.code = cost.code;
                if (cost.success()) {
                    data.setLevelId(next.getId());
                    autoSaveService.enqueueSave(data);
                    log.info("找老千解锁下一关成功 playerId={},previousLevelId={},levelId={},costItems={}",
                            ctx.playerId(), current.getId(), next.getId(), next.getClearCount());
                } else {
                    log.warn("找老千解锁道具扣除失败 playerId={},levelId={},costItems={},code={}",
                            ctx.playerId(), next.getId(), next.getClearCount(), cost.code);
                }
            }
        }
        res.levelId = data.getLevelId();
        return res;
    }

    /** 回合内寻找不再扣道具；请求发现超时或错误点位耗尽时间时结算失败奖励。 */
    public ResFindCheaterHuntSpot find(SimPlayerContext ctx, long roundId, int spotId) {
        CheaterHuntData data = data(ctx);
        ResFindCheaterHuntSpot res = new ResFindCheaterHuntSpot(Code.SUCCESS);
        CheaterHuntRound round = ctx.getCheaterHuntRound();
        res.code = checkRound(ctx, round, roundId);
        if (res.code == Code.SUCCESS) {
            MinigameCheaterHuntLevelCfg cfg = level(ctx, round.getLevelId());
            MinigameCheaterHuntMapCfg map = cfg == null ? null : map(ctx, cfg, round.getMapId());
            if (map == null) {
                res.code = Code.SAMPLE_ERROR;
            } else if (pendingReward(round)) {
                res.code = settle(ctx, data, round);
            } else if (spotId < 0 || spotId >= map.getTotalSpotCount()) {
                log.warn("找老千点位越界 playerId={},roundId={},spotId={},total={}",
                        ctx.playerId(), roundId, spotId, map.getTotalSpotCount());
                res.code = Code.PARAM_ERROR;
            } else if (round.getFoundSpots().contains(spotId)) {
                log.warn("找老千重复寻找已命中点位 playerId={},roundId={},spotId={}", ctx.playerId(), roundId, spotId);
                res.code = Code.REPEAT_OP;
            } else {
                res.code = grantDaily(ctx, data, System.currentTimeMillis());
                //每日发放可能调用背包，处理点位前再次以服务器当前时间校验截止时间。
                if (res.code == Code.SUCCESS && expire(ctx, round, System.currentTimeMillis())) {
                    res.code = Code.EXPIRE;
                }
                if (res.code == Code.SUCCESS) {
                    res.hit = round.getCheaterSpots().contains(spotId);
                    log.debug("找老千处理点位 playerId={},roundId={},levelId={},mapId={},spotId={},hit={}",
                            ctx.playerId(), roundId, cfg.getId(), map.getId(), spotId, res.hit);
                    if (res.hit) {
                        round.getFoundSpots().add(spotId);
                        if (round.getFoundSpots().size() >= cfg.getTargetCount()) {
                            //先保留达标状态，发奖失败后可重试结算。
                            round.setState(RoundState.REWARD_PENDING);
                            res.code = settle(ctx, data, round);
                        }
                    } else if (round.getEndTime() != -1) {
                        round.setEndTime(Math.max(0, round.getEndTime() - cfg.getCostItemCount() * 1000L));
                        expire(ctx, round, System.currentTimeMillis());
                    }
                }
            }
            if (res.code == Code.SUCCESS && round.getState() == RoundState.CLEARED) {
                res.rewards = ItemUtils.buildItemInfo(cfg.getClearReward());
            }
        }
        if (round != null && round.getRoundId() == roundId && round.getState() == RoundState.FAILED) {
            //倒计时结束属于正常失败结算，已结算的结束通知也成功返回，但不重复发奖。
            res.code = Code.SUCCESS;
            if (pendingFailureReward(round)) {
                res.code = settleFailureReward(ctx, round);
                if (res.code == Code.SUCCESS) {
                    res.rewards = ItemUtils.buildItemInfo(
                            GameDataManager.getMinigameCheaterHuntLevelCfg(round.getLevelId()).getLoseReward());
                }
            }
        }
        res.info = assemble(ctx, data);
        return res;
    }

    /** 一键通关从开始页发起，校验章节门槛后消耗道具，共用正常通关的发奖路径。 */
    public ResQuickClearCheaterHuntGame quickClear(SimPlayerContext ctx) {
        CheaterHuntData data = data(ctx);
        ResQuickClearCheaterHuntGame res = new ResQuickClearCheaterHuntGame(Code.SUCCESS);
        CheaterHuntRound round = ctx.getCheaterHuntRound();
        if (!pendingReward(round)) {
            res.code = grantDaily(ctx, data, System.currentTimeMillis());
            if (res.code == Code.SUCCESS && !canStart(ctx)) {
                res.code = Code.REPEAT_OP;
            }
            if (res.code == Code.SUCCESS && !openOneClickPass(ctx)) {
                GlobalConfigCfg gate = GameDataManager.getGlobalConfigCfg(MiniGameConstant.QUICK_CLEAR_CHAPTER_GLOBAL_ID);
                log.warn("找老千一键通关未开放 playerId={},levelId={},globalId={},chapterLevelId={},requiredLevelId={}",
                        ctx.playerId(), data.getLevelId(), MiniGameConstant.QUICK_CLEAR_CHAPTER_GLOBAL_ID,
                        ctx.getSimChapterData() == null ? null : ctx.getSimChapterData().getLevelId(),
                        gate == null ? null : gate.getIntValue());
                res.code = Code.NOT_UNLOCKED;
            }
            if (res.code == Code.SUCCESS) {
                MinigameCheaterHuntLevelCfg cfg = level(ctx, data.getLevelId());
                if (cfg == null) {
                    res.code = Code.SAMPLE_ERROR;
                } else {
                    CommonResult<ItemOperationResult> cost = playerPackService.removeItems(
                            ctx.getPlayer(), cfg.getCostItemID(), AddType.SIM_CHEATER_HUNT);
                    res.code = cost.code;
                    if (!cost.success()) {
                        log.warn("找老千一键通关扣除失败 playerId={},levelId={},costItems={},code={}",
                                ctx.playerId(), cfg.getId(), cfg.getCostItemID(), cost.code);
                    } else {
                        //跳过场景与点位生成，避免为一键通关分配无用的随机数据。
                        round = newRound(ctx, data);
                        round.setState(RoundState.REWARD_PENDING);
                        log.info("找老千一键通关扣费成功 playerId={},roundId={},levelId={},costItems={}",
                                ctx.playerId(), round.getRoundId(), cfg.getId(), cfg.getCostItemID());
                        conditionService.onRoundStarted(ctx, 1);
                    }
                }
            }
        }
        if (res.code == Code.SUCCESS) {
            res.code = settle(ctx, data, round);
            if (res.code == Code.SUCCESS) {
                res.rewards = ItemUtils.buildItemInfo(GameDataManager.getMinigameCheaterHuntLevelCfg(round.getLevelId()).getClearReward());
            }
        }
        res.info = assemble(ctx, data);
        return res;
    }

    /** 主动退出前交付已达标或已超时单局的奖励，成功后清除本局，不返还消耗道具。 */
    public ResExitCheaterHuntGame exit(SimPlayerContext ctx, long roundId) {
        CheaterHuntData data = data(ctx);
        ResExitCheaterHuntGame res = new ResExitCheaterHuntGame(Code.SUCCESS);
        CheaterHuntRound round = ctx.getCheaterHuntRound();
        if (round == null || roundId != round.getRoundId()) {
            log.warn("找老千退出局号不匹配 playerId={},roundId={},currentRoundId={},state={}",
                    ctx.playerId(), roundId, round == null ? null : round.getRoundId(),
                    round == null ? RoundState.NONE : round.getState());
            res.code = Code.ERROR_REQ;
        } else {
            expire(ctx, round, System.currentTimeMillis());
            //已达标或已超时的单局先交付奖励，交付失败保留现场供客户端重试。
            if (pendingReward(round)) {
                res.code = settle(ctx, data, round);
            } else if (pendingFailureReward(round)) {
                res.code = settleFailureReward(ctx, round);
            }
            if (res.code == Code.SUCCESS) {
                ctx.setCheaterHuntRound(null);
                log.info("找老千退出成功 playerId={},roundId={},levelId={},state={}",
                        ctx.playerId(), roundId, round.getLevelId(), round.getState());
            }
        }
        res.info = assemble(ctx, data);
        return res;
    }

    /** 玩家数据仅加载一次；新玩家的默认关卡由数据实体设为1001。 */
    private CheaterHuntData data(SimPlayerContext ctx) {
        CheaterHuntData data = ctx.getCheaterHuntData();
        if (data == null) {
            data = dao.findById(ctx.playerId()).orElseGet(() -> {
                CheaterHuntData created = new CheaterHuntData();
                created.setPlayerId(ctx.playerId());
                return created;
            });
            ctx.setCheaterHuntData(data);
        }
        return data;
    }

    /** 每日首次处理时补足道具；已充足也记录当天，补足失败则保留重试机会。 */
    private int grantDaily(SimPlayerContext ctx, CheaterHuntData data, long now) {
        //事件入队时刻可能早于已处理的登录/请求，不让旧事件回退发放日期。
        if (data.getLastDailyGrantTime() >= now || TimeHelper.inSameDay(data.getLastDailyGrantTime(), now)) {
            return Code.SUCCESS;
        }
        MinigameCheaterHuntLevelCfg cfg = level(ctx, data.getLevelId());
        if (cfg == null) {
            return Code.SAMPLE_ERROR;
        }
        CommonResult<ItemOperationResult> result = MiniGameDailyItems.replenish(
                ctx, cfg.getDailyFreeItemCount(), AddType.SIM_CHEATER_HUNT, playerPackService);
        if (!result.success()) {
            log.warn("找老千每日补足失败 playerId={},levelId={},targetItems={},code={}",
                    ctx.playerId(), cfg.getId(), cfg.getDailyFreeItemCount(), result.code);
            return result.code;
        }
        data.setLastDailyGrantTime(now);
        autoSaveService.enqueueSave(data);
        return Code.SUCCESS;
    }

    /** 已结束的局可再次开始；进行中及待发奖的局禁止重复开局重置时间。 */
    private boolean canStart(SimPlayerContext ctx) {
        CheaterHuntRound round = ctx.getCheaterHuntRound();
        expire(ctx, round, System.currentTimeMillis());
        if (round != null && (round.getState() == RoundState.PLAYING
                || pendingReward(round) || pendingFailureReward(round))) {
            log.warn("找老千已有未结束单局 playerId={},roundId={},state={}",
                    ctx.playerId(), round.getRoundId(), round.getState());
            return false;
        }
        return true;
    }

    /** 递增局号区分迟到请求，单局内容不提交持久化。 */
    private CheaterHuntRound newRound(SimPlayerContext ctx, CheaterHuntData data) {
        CheaterHuntRound round = new CheaterHuntRound();
        data.setRoundSequence(data.getRoundSequence() + 1);
        round.setRoundId(data.getRoundSequence());
        round.setLevelId(data.getLevelId());
        round.setState(RoundState.PLAYING);
        ctx.setCheaterHuntRound(round);
        autoSaveService.enqueueSave(data);
        return round;
    }

    /** 寻找前校验局号和状态，倒计时结束后交由find结算，不再处理点位。 */
    private int checkRound(SimPlayerContext ctx, CheaterHuntRound round, long roundId) {
        if (round == null || round.getRoundId() != roundId) {
            log.warn("找老千寻找局号不匹配 playerId={},roundId={},currentRoundId={},state={}",
                    ctx.playerId(), roundId, round == null ? null : round.getRoundId(),
                    round == null ? RoundState.NONE : round.getState());
            return Code.ERROR_REQ;
        }
        expire(ctx, round, System.currentTimeMillis());
        if (round.getState() == RoundState.FAILED) {
            return Code.EXPIRE;
        }
        if (round.getState() != RoundState.PLAYING && !pendingReward(round)) {
            log.warn("找老千寻找已结束单局 playerId={},roundId={},state={}", ctx.playerId(), roundId, round.getState());
            return Code.REPEAT_OP;
        }
        return Code.SUCCESS;
    }

    /** 只记录失败状态；不调度任务、不逐秒更新剩余时间。 */
    private boolean expire(SimPlayerContext ctx, CheaterHuntRound round, long now) {
        if (round != null && round.getState() == RoundState.PLAYING
                && round.getEndTime() != -1 && now >= round.getEndTime()) {
            round.setState(RoundState.FAILED);
            log.info("找老千单局倒计时结束 playerId={},roundId={},endTime={}", ctx.playerId(), round.getRoundId(), round.getEndTime());
            return true;
        }
        return round != null && round.getState() == RoundState.FAILED;
    }

    /** 已达成通关但尚未成功交付奖励，此时仅允许重试结算。 */
    private boolean pendingReward(CheaterHuntRound round) {
        return round != null && round.getState() == RoundState.REWARD_PENDING;
    }

    /** 超时失败但奖励尚未成功交付，禁止新局覆盖，允许找点或退出请求重试。 */
    private boolean pendingFailureReward(CheaterHuntRound round) {
        return round != null && round.getState() == RoundState.FAILED && !round.isFailureRewardGranted();
    }

    /** 失败奖励成功入账后才标记已发放，不改变通关或解锁进度。 */
    private int settleFailureReward(SimPlayerContext ctx, CheaterHuntRound round) {
        MinigameCheaterHuntLevelCfg cfg = level(ctx, round.getLevelId());
        if (cfg == null) {
            return Code.SAMPLE_ERROR;
        }
        CommonResult<ItemOperationResult> reward = playerPackService.addItems(
                ctx.playerId(), cfg.getLoseReward(), AddType.SIM_CHEATER_HUNT, true);
        if (!reward.success()) {
            log.warn("找老千失败奖励发放失败 playerId={},roundId={},levelId={},rewards={},code={}",
                    ctx.playerId(), round.getRoundId(), cfg.getId(), cfg.getLoseReward(), reward.code);
            return reward.code;
        }
        round.setFailureRewardGranted(true);
        if (round.randomEventConfigId != 0) {
            randomEvents.roomFinished(ctx, round.randomEventConfigId);
        }
        log.info("找老千失败结算成功 playerId={},roundId={},levelId={},randomEventConfigId={},rewards={}",
                ctx.playerId(), round.getRoundId(), cfg.getId(), round.randomEventConfigId, cfg.getLoseReward());
        return Code.SUCCESS;
    }

    /** 达标即发奖并记录实际通关，解锁进度只能由解锁请求推进。 */
    private int settle(SimPlayerContext ctx, CheaterHuntData data, CheaterHuntRound round) {
        MinigameCheaterHuntLevelCfg cfg = level(ctx, round.getLevelId());
        if (cfg == null) {
            return Code.SAMPLE_ERROR;
        }
        CommonResult<ItemOperationResult> reward = playerPackService.addItems(
                ctx.playerId(), cfg.getClearReward(), AddType.SIM_CHEATER_HUNT, true);
        if (!reward.success()) {
            log.warn("找老千通关发奖失败 playerId={},roundId={},levelId={},rewards={},code={}",
                    ctx.playerId(), round.getRoundId(), cfg.getId(), cfg.getClearReward(), reward.code);
            return reward.code;
        }
        round.setState(RoundState.CLEARED);
        if (round.randomEventConfigId != 0) {
            randomEvents.roomFinished(ctx, round.randomEventConfigId);
        }
        data.setMaxPassedLevelId(cfg.getId());
        autoSaveService.enqueueSave(data);
        log.info("找老千通关结算成功 playerId={},roundId={},levelId={},randomEventConfigId={},rewards={}",
                ctx.playerId(), round.getRoundId(), cfg.getId(), round.randomEventConfigId, cfg.getClearReward());
        return Code.SUCCESS;
    }

    /** 集中校验影响玩法的配置关系，避免非法配置形成免费通关或无解局。 */
    private MinigameCheaterHuntLevelCfg level(SimPlayerContext ctx, int levelId) {
        MinigameCheaterHuntLevelCfg cfg = GameDataManager.getMinigameCheaterHuntLevelCfg(levelId);
        if (cfg == null || cfg.getUseCase() == null || cfg.getUseCase().isEmpty()
                || cfg.getDrawCount() <= 0 || cfg.getTargetCount() <= 0 || cfg.getTargetCount() > cfg.getDrawCount()
                || (cfg.getTimeLimit() != -1 && cfg.getTimeLimit() <= 0)
                || cfg.getCostItemCount() < 0 || !validItems(cfg.getCostItemID())
                || !validItems(cfg.getDailyFreeItemCount()) || !validItems(cfg.getClearReward())
                || !validItems(cfg.getLoseReward())) {
            log.warn("找老千关卡配置错误 playerId={},levelId={}", ctx.playerId(), levelId);
            return null;
        }
        return cfg;
    }

    /** 配置容器不保证行序，关卡等级决定解锁顺序，同级以ID确定顺序。 */
    private List<MinigameCheaterHuntLevelCfg> levels() {
        return GameDataManager.getMinigameCheaterHuntLevelCfgList().stream()
                .sorted(Comparator.comparingInt(MinigameCheaterHuntLevelCfg::getLevel)
                        .thenComparingInt(MinigameCheaterHuntLevelCfg::getId)).toList();
    }

    /** 章节服务负责懒加载；已加载的进度直接复用，不触碰章节数据访问层。 */
    private boolean openOneClickPass(SimPlayerContext ctx) {
        GlobalConfigCfg cfg = GameDataManager.getGlobalConfigCfg(MiniGameConstant.QUICK_CLEAR_CHAPTER_GLOBAL_ID);
        if (cfg == null || cfg.getIntValue() <= 0) {
            return false;
        }
        if (ctx.getSimChapterData() == null && chapterService.getResChapterInfo(ctx).code != Code.SUCCESS) {
            return false;
        }
        return ctx.getSimChapterData().getLevelId() >= cfg.getIntValue();
    }

    /** 场景必须容纳配置要求的全部随机作弊点。 */
    private MinigameCheaterHuntMapCfg map(SimPlayerContext ctx, MinigameCheaterHuntLevelCfg cfg, int mapId) {
        MinigameCheaterHuntMapCfg map = GameDataManager.getMinigameCheaterHuntMapCfg(mapId);
        if (map == null || map.getTotalSpotCount() < cfg.getDrawCount()) {
            log.warn("找老千场景配置错误 playerId={},levelId={},mapId={}", ctx.playerId(), cfg.getId(), mapId);
            return null;
        }
        return map;
    }

    /** 空表表示不扣发道具；已配置的道具ID与数量必须有效。 */
    private boolean validItems(Map<Integer, Long> items) {
        return items != null && items.entrySet().stream().allMatch(e ->
                e.getKey() != null && e.getKey() > 0 && e.getValue() != null && e.getValue() > 0);
    }

    /** 解锁条件允许零消耗，已配置的道具ID必须有效，数量不能为负。 */
    private boolean validUnlockItems(Map<Integer, Long> items) {
        return items != null && items.entrySet().stream().allMatch(e ->
                e.getKey() != null && e.getKey() > 0 && e.getValue() != null && e.getValue() >= 0);
    }

    /** 返回点位列表快照，避免后续寻找操作改变正在发送的响应。 */
    private CheaterHuntInfo assemble(SimPlayerContext ctx, CheaterHuntData data) {
        CheaterHuntInfo info = new CheaterHuntInfo();
        info.levelId = data.getLevelId();
        info.serverTime = System.currentTimeMillis();
        CheaterHuntRound round = ctx.getCheaterHuntRound();
        if (round != null) {
            info.roundId = round.getRoundId();
            info.roundLevelId = round.getLevelId();
            info.mapId = round.getMapId();
            info.cheaterSpots = new ArrayList<>(round.getCheaterSpots());
            info.foundSpots = new ArrayList<>(round.getFoundSpots());
            info.endTime = round.getEndTime();
            info.state = round.getState();
        }
        return info;
    }

}
