package com.jjg.game.minigame.service;

import com.jjg.game.common.utils.RandomUtils;
import com.jjg.game.common.utils.TimeHelper;
import com.jjg.game.core.constant.AddType;
import com.jjg.game.core.constant.Code;
import com.jjg.game.core.data.CommonResult;
import com.jjg.game.core.data.ItemOperationResult;
import com.jjg.game.core.service.PlayerPackService;
import com.jjg.game.core.utils.ItemUtils;
import com.jjg.game.minigame.constant.MiniGameConstant.RoundState;
import com.jjg.game.minigame.dao.CheaterHuntDao;
import com.jjg.game.minigame.data.CheaterHuntData;
import com.jjg.game.minigame.data.CheaterHuntRound;
import com.jjg.game.minigame.pb.res.*;
import com.jjg.game.minigame.pb.struct.CheaterHuntInfo;
import com.jjg.game.sampledata.GameDataManager;
import com.jjg.game.sampledata.bean.CheaterHuntLevelCfg;
import com.jjg.game.sampledata.bean.CheaterHuntMapCfg;
import com.jjg.game.sim.data.SimPlayerContext;
import com.jjg.game.sim.service.SimAutoSaveService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 找老千玩法：关卡进度持久化，单局只保存在玩家会话中。
 * 沿用玩家串行队列及统一背包接口，不额外创建锁、事务或定时器。
 */
@Service
public class SimMiniGameService {
    private static final Logger log = LoggerFactory.getLogger(SimMiniGameService.class);
    private final CheaterHuntDao dao;
    private final SimAutoSaveService autoSaveService;
    private final PlayerPackService playerPackService;

    public SimMiniGameService(CheaterHuntDao dao, SimAutoSaveService autoSaveService,
                              PlayerPackService playerPackService) {
        this.dao = dao;
        this.autoSaveService = autoSaveService;
        this.playerPackService = playerPackService;
    }

    /** 登录结算当天赠送，离线日期不累计补发。 */
    public void onLogin(SimPlayerContext ctx) {
        grantDaily(ctx, data(ctx), System.currentTimeMillis());
    }

    /** 零点事件在玩家队列内调用，只给仍在线的玩家发放当日道具。 */
    public void onDaily(SimPlayerContext ctx, long now) {
        if (ctx.getPlayerController() != null && ctx.getPlayerController().isOnline()) {
            grantDaily(ctx, data(ctx), now);
        }
    }

    /** 开局按关卡随机场景及不重复点位，进入本身不扣道具。 */
    public ResEnterCheaterHuntGame enter(SimPlayerContext ctx) {
        CheaterHuntData data = data(ctx);
        ResEnterCheaterHuntGame res = new ResEnterCheaterHuntGame(grantDaily(ctx, data, System.currentTimeMillis()));
        if (res.code == Code.SUCCESS) {
            if (canStart(ctx)) {
                CheaterHuntLevelCfg cfg = level(ctx, data.getLevelId());
                if (cfg == null) {
                    res.code = Code.SAMPLE_ERROR;
                } else {
                    int mapId = RandomUtils.randomEle(cfg.getUseCase());
                    CheaterHuntMapCfg map = map(ctx, cfg, mapId);
                    if (map == null) {
                        res.code = Code.SAMPLE_ERROR;
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
                        round.setEndTime(cfg.getTimeLimit() == -1 ? -1
                                : System.currentTimeMillis() + cfg.getTimeLimit() * 1000L);
                    }
                }
            } else {
                res.code = Code.REPEAT_OP;
            }
        }
        res.info = assemble(ctx, data);
        return res;
    }

    /** 每次有效寻找请求扣一次配置道具；错误点位另扣时间，已命中的点位不重复扣费。 */
    public ResFindCheaterHuntSpot find(SimPlayerContext ctx, long roundId, int spotId) {
        CheaterHuntData data = data(ctx);
        ResFindCheaterHuntSpot res = new ResFindCheaterHuntSpot(Code.SUCCESS);
        CheaterHuntRound round = ctx.getCheaterHuntRound();
        res.code = checkRound(ctx, round, roundId);
        if (res.code == Code.SUCCESS) {
            CheaterHuntLevelCfg cfg = level(ctx, round.getLevelId());
            CheaterHuntMapCfg map = cfg == null ? null : map(ctx, cfg, round.getMapId());
            if (map == null) {
                res.code = Code.SAMPLE_ERROR;
            } else if (spotId < 0 || spotId >= map.getTotalSpotCount()) {
                log.warn("找老千点位越界 playerId={},roundId={},spotId={},total={}",
                        ctx.playerId(), roundId, spotId, map.getTotalSpotCount());
                res.code = Code.PARAM_ERROR;
            } else if (pendingReward(round)) {
                res.code = settle(ctx, data, round);
            } else if (round.getFoundSpots().contains(spotId)) {
                log.warn("找老千重复寻找已命中点位 playerId={},roundId={},spotId={}", ctx.playerId(), roundId, spotId);
                res.code = Code.REPEAT_OP;
            } else {
                res.code = grantDaily(ctx, data, System.currentTimeMillis());
                //每日发放可能调用背包，扣费前再次以服务器当前时间校验截止时间。
                if (res.code == Code.SUCCESS && expire(ctx, round, System.currentTimeMillis())) {
                    res.code = Code.EXPIRE;
                }
                if (res.code == Code.SUCCESS) {
                    CommonResult<ItemOperationResult> cost = playerPackService.removeItems(
                            ctx.getPlayer(), cfg.getCostItemID(), AddType.SIM_CHEATER_HUNT);
                    res.code = cost.code;
                    if (!cost.success()) {
                        log.warn("找老千寻找道具扣除失败 playerId={},roundId={},spotId={},code={}",
                                ctx.playerId(), roundId, spotId, cost.code);
                    } else {
                        res.hit = round.getCheaterSpots().contains(spotId);
                        if (res.hit) {
                            round.getFoundSpots().add(spotId);
                            if (round.getFoundSpots().size() >= cfg.getTargetCount()) {
                                //先保留达标状态；发奖失败后重试不会重复扣最后一次道具。
                                round.setState(RoundState.REWARD_PENDING);
                                res.code = settle(ctx, data, round);
                            }
                        } else if (round.getEndTime() != -1) {
                            round.setEndTime(Math.max(0, round.getEndTime() - cfg.getCostItemCount() * 1000L));
                            expire(ctx, round, System.currentTimeMillis());
                        }
                    }
                }
            }
            if (res.code == Code.SUCCESS && round.getState() == RoundState.CLEARED) {
                res.rewards = ItemUtils.buildItemInfo(cfg.getClearReward());
            }
        }
        res.info = assemble(ctx, data);
        return res;
    }

    /** 一键通关从开始页发起，消耗一次配置道具，共用正常通关的发奖和晋级。 */
    public ResQuickClearCheaterHuntGame quickClear(SimPlayerContext ctx) {
        CheaterHuntData data = data(ctx);
        ResQuickClearCheaterHuntGame res = new ResQuickClearCheaterHuntGame(Code.SUCCESS);
        CheaterHuntRound round = ctx.getCheaterHuntRound();
        if (!pendingReward(round)) {
            res.code = grantDaily(ctx, data, System.currentTimeMillis());
            if (res.code == Code.SUCCESS && !canStart(ctx)) {
                res.code = Code.REPEAT_OP;
            }
            if (res.code == Code.SUCCESS) {
                CheaterHuntLevelCfg cfg = level(ctx, data.getLevelId());
                if (cfg == null) {
                    res.code = Code.SAMPLE_ERROR;
                } else {
                    CommonResult<ItemOperationResult> cost = playerPackService.removeItems(
                            ctx.getPlayer(), cfg.getCostItemID(), AddType.SIM_CHEATER_HUNT);
                    res.code = cost.code;
                    if (!cost.success()) {
                        log.warn("找老千一键通关扣除失败 playerId={},levelId={},code={}",
                                ctx.playerId(), data.getLevelId(), cost.code);
                    } else {
                        //跳过场景与点位生成，避免为一键通关分配无用的随机数据。
                        round = newRound(ctx, data);
                        round.setState(RoundState.REWARD_PENDING);
                    }
                }
            }
        }
        if (res.code == Code.SUCCESS) {
            res.code = settle(ctx, data, round);
            if (res.code == Code.SUCCESS) {
                res.rewards = ItemUtils.buildItemInfo(GameDataManager.getCheaterHuntLevelCfg(round.getLevelId()).getClearReward());
            }
        }
        res.info = assemble(ctx, data);
        return res;
    }

    /** 主动退出清除点位与寻找进度，不返还已消耗道具。 */
    public ResExitCheaterHuntGame exit(SimPlayerContext ctx, long roundId) {
        CheaterHuntData data = data(ctx);
        ResExitCheaterHuntGame res = new ResExitCheaterHuntGame(Code.SUCCESS);
        CheaterHuntRound round = ctx.getCheaterHuntRound();
        if (round == null || roundId != round.getRoundId()) {
            log.warn("找老千退出局号不匹配 playerId={},roundId={}", ctx.playerId(), roundId);
            res.code = Code.ERROR_REQ;
        } else {
            //已达标的通关先交付奖励，交付失败保留现场供客户端重试。
            if (pendingReward(round)) {
                res.code = settle(ctx, data, round);
            }
            if (res.code == Code.SUCCESS) {
                ctx.setCheaterHuntRound(null);
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

    /** 当天首次登录/在线跨日发放，成功后才更新持久化标记，不增加每日缓存。 */
    private int grantDaily(SimPlayerContext ctx, CheaterHuntData data, long now) {
        //事件入队时刻可能早于已处理的登录/请求，不让旧事件回退发放日期。
        if (data.getLastDailyGrantTime() >= now || TimeHelper.inSameDay(data.getLastDailyGrantTime(), now)) {
            return Code.SUCCESS;
        }
        CheaterHuntLevelCfg cfg = level(ctx, data.getLevelId());
        if (cfg == null) {
            return Code.SAMPLE_ERROR;
        }
        CommonResult<ItemOperationResult> result = playerPackService.addItems(
                ctx.playerId(), cfg.getDailyFreeItemCount(), AddType.SIM_CHEATER_HUNT, true);
        if (!result.success()) {
            log.warn("找老千每日赠送失败 playerId={},levelId={},code={}", ctx.playerId(), cfg.getId(), result.code);
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
        if (round != null && (round.getState() == RoundState.PLAYING || pendingReward(round))) {
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

    /** 扣费前拒绝旧局请求、已结束局及超时局。 */
    private int checkRound(SimPlayerContext ctx, CheaterHuntRound round, long roundId) {
        if (round == null || round.getRoundId() != roundId) {
            log.warn("找老千寻找局号不匹配 playerId={},roundId={}", ctx.playerId(), roundId);
            return Code.ERROR_REQ;
        }
        expire(ctx, round, System.currentTimeMillis());
        if (round.getState() == RoundState.FAILED) {
            log.warn("找老千寻找已超时 playerId={},roundId={}", ctx.playerId(), roundId);
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
            log.info("找老千单局超时 playerId={},roundId={},endTime={}", ctx.playerId(), round.getRoundId(), round.getEndTime());
            return true;
        }
        return round != null && round.getState() == RoundState.FAILED;
    }

    /** 已达成通关但尚未成功交付奖励，此时仅允许重试结算。 */
    private boolean pendingReward(CheaterHuntRound round) {
        return round != null && round.getState() == RoundState.REWARD_PENDING;
    }

    /** 达标即发奖并计次；末关保留进度供重复挑战，下一关完全沿配置列表顺序。 */
    private int settle(SimPlayerContext ctx, CheaterHuntData data, CheaterHuntRound round) {
        CheaterHuntLevelCfg cfg = level(ctx, round.getLevelId());
        if (cfg == null) {
            return Code.SAMPLE_ERROR;
        }
        List<CheaterHuntLevelCfg> levels = GameDataManager.getCheaterHuntLevelCfgList();
        int index = levels.indexOf(cfg);
        if (index < 0) {
            log.warn("找老千关卡不在配置列表 playerId={},levelId={}", ctx.playerId(), cfg.getId());
            return Code.SAMPLE_ERROR;
        }
        CommonResult<ItemOperationResult> reward = playerPackService.addItems(
                ctx.playerId(), cfg.getClearReward(), AddType.SIM_CHEATER_HUNT, true);
        if (!reward.success()) {
            log.warn("找老千通关发奖失败 playerId={},roundId={},levelId={},code={}",
                    ctx.playerId(), round.getRoundId(), cfg.getId(), reward.code);
            return reward.code;
        }
        round.setState(RoundState.CLEARED);
        int cleared = Math.min(data.getClearCount(), cfg.getClearCount() - 1) + 1;
        if (cleared >= cfg.getClearCount() && index + 1 < levels.size()) {
            data.setLevelId(levels.get(index + 1).getId());
            data.setClearCount(0);
        } else {
            data.setClearCount(cleared);
        }
        autoSaveService.enqueueSave(data);
        return Code.SUCCESS;
    }

    /** 集中校验影响玩法的配置关系，避免非法配置形成免费通关或无解局。 */
    private CheaterHuntLevelCfg level(SimPlayerContext ctx, int levelId) {
        CheaterHuntLevelCfg cfg = GameDataManager.getCheaterHuntLevelCfg(levelId);
        if (cfg == null || cfg.getUseCase() == null || cfg.getUseCase().isEmpty()
                || cfg.getDrawCount() <= 0 || cfg.getTargetCount() <= 0 || cfg.getTargetCount() > cfg.getDrawCount()
                || cfg.getClearCount() <= 0 || (cfg.getTimeLimit() != -1 && cfg.getTimeLimit() <= 0)
                || cfg.getCostItemCount() < 0 || !validItems(cfg.getCostItemID())
                || !validItems(cfg.getDailyFreeItemCount()) || !validItems(cfg.getClearReward())) {
            log.warn("找老千关卡配置错误 playerId={},levelId={}", ctx.playerId(), levelId);
            return null;
        }
        return cfg;
    }

    /** 场景必须容纳配置要求的全部随机作弊点。 */
    private CheaterHuntMapCfg map(SimPlayerContext ctx, CheaterHuntLevelCfg cfg, int mapId) {
        CheaterHuntMapCfg map = GameDataManager.getCheaterHuntMapCfg(mapId);
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

    /** 返回点位列表快照，避免后续寻找操作改变正在发送的响应。 */
    private CheaterHuntInfo assemble(SimPlayerContext ctx, CheaterHuntData data) {
        CheaterHuntInfo info = new CheaterHuntInfo();
        info.levelId = data.getLevelId();
        info.clearCount = data.getClearCount();
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
