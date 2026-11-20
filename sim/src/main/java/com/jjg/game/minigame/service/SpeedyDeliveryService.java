package com.jjg.game.minigame.service;

import com.alibaba.fastjson.JSON;
import com.jjg.game.common.utils.TimeHelper;
import com.jjg.game.core.constant.AddType;
import com.jjg.game.core.constant.Code;
import com.jjg.game.core.constant.GameConstant;
import com.jjg.game.core.data.CommonResult;
import com.jjg.game.core.data.ItemOperationResult;
import com.jjg.game.core.service.PlayerPackService;
import com.jjg.game.core.utils.ItemUtils;
import com.jjg.game.minigame.constant.MiniGameConstant;
import com.jjg.game.minigame.constant.MiniGameConstant.RoundState;
import com.jjg.game.minigame.dao.SpeedyDeliveryDao;
import com.jjg.game.minigame.data.MiniGameSkillEffect;
import com.jjg.game.minigame.data.MiniGameSkillTree;
import com.jjg.game.minigame.data.SpeedyDeliveryData;
import com.jjg.game.minigame.data.SpeedyDeliveryRound;
import com.jjg.game.minigame.pb.res.*;
import com.jjg.game.minigame.pb.struct.SpeedyDeliveryShelfGoods;
import com.jjg.game.minigame.pb.struct.SpeedyDeliverySkillInfo;
import com.jjg.game.sampledata.GameDataManager;
import com.jjg.game.sampledata.bean.GlobalConfigCfg;
import com.jjg.game.sampledata.bean.MinigameSpeedyDeliveryCfg;
import com.jjg.game.sampledata.bean.MinigameSpeedySkilCfg;
import com.jjg.game.sampledata.bean.MinigameTruckCfg;
import com.jjg.game.sampledata.bean.MinigamegoodsCfg;
import com.jjg.game.sim.constant.SimConstant;
import com.jjg.game.sim.data.BuildingData;
import com.jjg.game.sim.data.SimCasinoData;
import com.jjg.game.sim.data.SimPlayerContext;
import com.jjg.game.sim.randomevent.SimRandomEventService;
import org.springframework.beans.factory.annotation.Autowired;
import com.jjg.game.sim.listener.SimPlayerTickListener;
import com.jjg.game.sim.service.SimAutoSaveService;
import com.jjg.game.sim.service.SimChapterService;
import com.jjg.game.sim.service.SimConfigCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 飞驰快送：客户端负责操作，服务器负责时限、计分、扣费和发奖。 */
@Service
public class SpeedyDeliveryService implements SimPlayerTickListener {
    @Autowired
    private SimRandomEventService randomEvents;
    private static final Logger log = LoggerFactory.getLogger(SpeedyDeliveryService.class);
    private static final long END_TOLERANCE_MILLIS = 10_000L;
    private static final int DURATION_BONUS = 1;
    private static final int SCORE_BONUS = 4;
    private static final int BACKPACK_CAPACITY_BONUS = 5;
    private static final int GOODS_REWARD_BONUS = 9;

    private final SpeedyDeliveryDao dao;
    private final SimAutoSaveService autoSaveService;
    private final PlayerPackService playerPackService;
    private final SimChapterService chapterService;
    private final MiniGameConditionService conditionService;
    private final SimConfigCacheService configCacheService;

    public SpeedyDeliveryService(SpeedyDeliveryDao dao, SimAutoSaveService autoSaveService,
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
        loadData(ctx);
        //退出发奖失败时重试原会话中的交付，不恢复游戏过程。
        SpeedyDeliveryRound round = ctx.getSpeedyDeliveryRound();
        if (round != null && round.getState() == RoundState.REWARD_PENDING) {
            onExit(ctx);
        }
        grantDaily(ctx, config(ctx), System.currentTimeMillis());
    }

    public void onDaily(SimPlayerContext ctx, long now) {
        if (ctx.getPlayerController() != null && ctx.getPlayerController().isOnline()) {
            grantDaily(ctx, config(ctx), now);
        }
    }

    public ResEnterSpeedyDeliveryGame enter(SimPlayerContext ctx) {
        MinigameSpeedyDeliveryCfg cfg = config(ctx);
        ResEnterSpeedyDeliveryGame res = new ResEnterSpeedyDeliveryGame(grantDaily(ctx, cfg, System.currentTimeMillis()));
        if (res.code != Code.SUCCESS) {
            return res;
        }
        res.costItems = ItemUtils.buildItemInfo(cfg.getConsumeCount());
        res.ownedItems = new ArrayList<>(cfg.getConsumeCount().size());
        for (int itemId : cfg.getConsumeCount().keySet()) {
            long count;
            if (itemId == ItemUtils.getGoldItemId()) {
                count = ctx.getPlayer().getGold();
            } else if (itemId == ItemUtils.getDiamondItemId()) {
                count = ctx.getPlayer().getDiamond();
            } else if (itemId == ItemUtils.getShellItemId()) {
                count = ctx.getPlayer().getShell();
            } else {
                count = playerPackService.getItemCount(ctx.playerId(), itemId);
            }
            res.ownedItems.add(ItemUtils.buildItemInfo(itemId, count));
        }
        res.openOneClickPass = openOneClickPass(ctx);
        return res;
    }

    public ResStartSpeedyDeliveryGame start(SimPlayerContext ctx) {
        ResStartSpeedyDeliveryGame res = new ResStartSpeedyDeliveryGame(Code.SUCCESS);
        MinigameSpeedyDeliveryCfg cfg = config(ctx);
        res.code = cfg == null ? Code.SAMPLE_ERROR : prepareStart(ctx);
        if (res.code == Code.SUCCESS) {
            res.code = charge(ctx, cfg, false);
            if (res.code == Code.SUCCESS) {
                res.endTime = ctx.getSpeedyDeliveryRound().getEndTime();
            }
        }
        return res;
    }

    public ResSpeedyDeliverySkills skills(SimPlayerContext ctx) {
        SpeedyDeliveryData data = loadData(ctx);
        ResSpeedyDeliverySkills res = new ResSpeedyDeliverySkills(Code.SUCCESS);
        res.skills = configCacheService.getMinigameSpeedySkills().groupIds().stream()
                .map(groupId -> new SpeedyDeliverySkillInfo(groupId, data.getSkillLevel(groupId)))
                .toList();
        res.skillPoints = data.getSkillPoints();
        if (log.isInfoEnabled()) {
            log.info("飞驰快送查询技能 playerId={},skillCount={},skillPoints={},skills={}",
                    ctx.playerId(), res.skills.size(), res.skillPoints, JSON.toJSONString(res.skills));
        }
        return res;
    }

    public ResUpgradeSpeedyDeliverySkill upgradeSkill(SimPlayerContext ctx, int groupId) {
        SpeedyDeliveryData data = loadData(ctx);
        int level = data.getSkillLevel(groupId);
        var skillTree = configCacheService.getMinigameSpeedySkills();
        MiniGameSkillTree.Upgrade upgrade = skillTree.checkUpgrade(ctx.playerId(), groupId,
                data.getSkillLevels(), data.getSkillPoints(), configCacheService.getMinigameSpeedyInitialSkillGroupIds()::contains);
        if (upgrade.code() == Code.SUCCESS) {
            data.setSkillPoints(data.getSkillPoints() - upgrade.cost());
            data.setSkillLevel(groupId, ++level);
            autoSaveService.enqueueSave(data);
            log.info("飞驰快送技能升级成功 playerId={},groupId={},level={},cost={},skillPoints={}",
                    ctx.playerId(), groupId, level, upgrade.cost(), data.getSkillPoints());
        }
        ResUpgradeSpeedyDeliverySkill res = new ResUpgradeSpeedyDeliverySkill(upgrade.code());
        res.skill = new SpeedyDeliverySkillInfo(groupId, level);
        res.skillPoints = data.getSkillPoints();
        return res;
    }

    /** 按货物类别加分后乘以技能提升后的货车匹配倍率，整批合计再向上取整。 */
    public ResDeliverSpeedyDeliveryGoods deliver(SimPlayerContext ctx, List<Integer> goodsIds, int truckId) {
        SpeedyDeliveryRound round = ctx.getSpeedyDeliveryRound();
        ResDeliverSpeedyDeliveryGoods res = new ResDeliverSpeedyDeliveryGoods(checkPlaying(round));
        if (round != null) {
            res.score = round.getScore();
        }
        if (res.code != Code.SUCCESS) {
            log.warn("飞驰快送交货失败，当前对局不可交货 playerId={},truckId={},state={},endTime={},code={}",
                    ctx.playerId(), truckId, round == null ? RoundState.NONE : round.getState(),
                    round == null ? 0 : round.getEndTime(), res.code);
            return res;
        }
        int capacity = round.getBackpackCapacity();
        if (goodsIds == null || goodsIds.isEmpty() || goodsIds.size() > capacity) {
            log.warn("飞驰快送交货失败，货物数量无效 playerId={},truckId={},count={},capacity={}",
                    ctx.playerId(), truckId, goodsIds == null ? 0 : goodsIds.size(), capacity);
            res.code = Code.PARAM_ERROR;
            return res;
        }
        MinigameTruckCfg truck = GameDataManager.getMinigameTruckCfg(truckId);
        if (truck == null) {
            log.warn("飞驰快送交货失败，卡车配置不存在 playerId={},truckId={}", ctx.playerId(), truckId);
            res.code = Code.PARAM_ERROR;
            return res;
        }
        long numerator = 0;
        long limit = (Integer.MAX_VALUE - (long) round.getScore()) * GameConstant.TEN_THOUSAND;
        for (Integer goodsId : goodsIds) {
            MinigamegoodsCfg goods = goodsId == null ? null : GameDataManager.getMinigamegoodsCfg(goodsId);
            if (goods == null) {
                log.warn("飞驰快送交货失败，货物配置不存在 playerId={},truckId={},goodsId={}", ctx.playerId(), truckId, goodsId);
                res.code = Code.PARAM_ERROR;
                return res;
            }
            // Minigamegoods.type：1 糕点、2 酒水、3 生鲜。
            int goodsType = goods.getType();
            int baseMultiplier = switch (goodsType) {
                case 1 -> truck.getFragileMultiplier();
                case 2 -> truck.getDangerMultiplier();
                case 3 -> truck.getNormalMultiplier();
                default -> -1;
            };
            if (goods.getScore() < 0 || baseMultiplier < 0) {
                log.warn("飞驰快送交货失败，计分配置无效 playerId={},truckId={},goodsId={},type={},score={},multiplier={}",
                        ctx.playerId(), truckId, goodsId, goodsType, goods.getScore(), baseMultiplier);
                res.code = Code.SAMPLE_ERROR;
                return res;
            }
            long goodsScore = round.scoreForGoods(goodsType, goods.getScore());
            long multiplier = round.truckMultiplier(truckId, goodsType, baseMultiplier);
            if (goodsScore < 0 || multiplier < 0) {
                log.warn("飞驰快送交货失败，技能加成后计分配置无效 playerId={},truckId={},goodsId={},type={},baseScore={},baseMultiplier={},goodsScore={},multiplierBasisPoints={}",
                        ctx.playerId(), truckId, goodsId, goodsType, goods.getScore(), baseMultiplier, goodsScore, multiplier);
                res.code = Code.SAMPLE_ERROR;
                return res;
            }
            if (goodsScore > 0 && multiplier > (limit - numerator) / goodsScore) {
                log.warn("飞驰快送交货失败，积分超出上限 playerId={},truckId={},goodsId={},score={},goodsScore={},multiplierBasisPoints={},remainingNumerator={}",
                        ctx.playerId(), truckId, goodsId, round.getScore(), goodsScore, multiplier, limit - numerator);
                res.code = Code.PARAM_ERROR;
                return res;
            }
            numerator += goodsScore * multiplier;
        }
        long deliveryScore = Math.ceilDiv(numerator, GameConstant.TEN_THOUSAND);
        SpeedyDeliveryData data = loadData(ctx);
        for (int goodsId : goodsIds) {
            if (data.addWarehouseGoods(goodsId, 1) > 0) {
                round.recordDeliveredGood(goodsId);
            }
        }
        res.deliveryScore = (int) deliveryScore;
        round.setScore(round.getScore() + res.deliveryScore);
        res.score = round.getScore();
        log.debug("飞驰快送交货成功 playerId={},truckId={},count={},goodsIds={},deliveryScore={},score={},endTime={}",
                ctx.playerId(), truckId, goodsIds.size(), goodsIds, res.deliveryScore, res.score, round.getEndTime());
        return res;
    }

    public ResStockSpeedyDeliveryShelf stockShelf(SimPlayerContext ctx, int type, int slotId, int goodsId) {
        if (type <= 0 || slotId < 0 || slotId >= MiniGameConstant.SPEEDY_DELIVERY_SHELF_SLOT_COUNT || goodsId <= 0) {
            log.warn("货物上架失败，货架类型、格子或货物ID无效 playerId={},type={},slotId={},goodsId={},slotCount={}",
                    ctx.playerId(), type, slotId, goodsId, MiniGameConstant.SPEEDY_DELIVERY_SHELF_SLOT_COUNT);
            return new ResStockSpeedyDeliveryShelf(Code.PARAM_ERROR);
        }
        MinigamegoodsCfg goods = GameDataManager.getMinigamegoodsCfg(goodsId);
        if (goods == null || goods.getType() != type) {
            log.warn("货物上架失败，货物配置不存在或类型与货架不匹配 playerId={},type={},slotId={},goodsId={},goodsType={}",
                    ctx.playerId(), type, slotId, goodsId, goods == null ? null : goods.getType());
            return new ResStockSpeedyDeliveryShelf(Code.PARAM_ERROR);
        }
        SimCasinoData casino = ctx.getCurrentCasino();
        BuildingData tavern = casino == null ? null : casino.findBuilding(SimConstant.TAVERN_BUILDING_ID);
        if (tavern == null || tavern.getLevel() <= 0) {
            log.warn("货物上架失败，当前场景未解锁温馨酒馆 playerId={},casinoId={},buildingId={},level={},type={},slotId={},goodsId={}",
                    ctx.playerId(), casino == null ? null : casino.getCasinoId(), SimConstant.TAVERN_BUILDING_ID,
                    tavern == null ? 0 : tavern.getLevel(), type, slotId, goodsId);
            return new ResStockSpeedyDeliveryShelf(Code.NOT_UNLOCKED);
        }
        if (!loadData(ctx).stockShelf(type, slotId, goodsId, tavern.getLevel())) {
            log.warn("货物上架失败，仓库无该货物 playerId={},type={},slotId={},goodsId={},available=0,capacity={}",
                    ctx.playerId(), type, slotId, goodsId, tavern.getLevel());
            return new ResStockSpeedyDeliveryShelf(Code.NOT_ENOUGH_ITEM);
        }
        log.debug("飞驰快送货物上架成功 playerId={},casinoId={},type={},slotId={},goodsId={},capacity={}",
                ctx.playerId(), casino.getCasinoId(), type, slotId, goodsId, tavern.getLevel());
        return new ResStockSpeedyDeliveryShelf(Code.SUCCESS);
    }

    public ResSpeedyDeliveryGoodsInfo goodsInfo(SimPlayerContext ctx, int type) {
        Map<Integer, MinigamegoodsCfg> configs = GameDataManager.getMinigamegoodsCfgMap();
        if (type <= 0 || configs.values().stream().noneMatch(goods -> goods.getType() == type)) {
            log.warn("查询货物失败，货架类型无效 playerId={},type={}", ctx.playerId(), type);
            return new ResSpeedyDeliveryGoodsInfo(Code.PARAM_ERROR);
        }
        SpeedyDeliveryData data = loadData(ctx);
        ResSpeedyDeliveryGoodsInfo res = new ResSpeedyDeliveryGoodsInfo(Code.SUCCESS);
        res.houseGoods = new ArrayList<>();
        data.warehouseGoods().forEach((goodsId, count) -> {
            MinigamegoodsCfg goods = configs.get(goodsId);
            if (goods != null && goods.getType() == type) {
                res.houseGoods.add(ItemUtils.buildItemInfo(goodsId, count));
            }
        });
        Map<Integer, int[]> shelf = data.shelfGoods(type);
        res.goods = new ArrayList<>(shelf.size());
        shelf.forEach((slotId, stock) -> {
            SpeedyDeliveryShelfGoods info = new SpeedyDeliveryShelfGoods();
            info.slotId = slotId;
            info.goodsId = stock[0];
            info.count = stock[1];
            res.goods.add(info);
        });
        return res;
    }

    public ResSetSpeedyDeliveryPaused setPaused(SimPlayerContext ctx, boolean paused) {
        SpeedyDeliveryRound round = ctx.getSpeedyDeliveryRound();
        long now = System.currentTimeMillis();
        int code = round == null ? Code.ERROR_REQ : round.setPaused(paused, now);
        ResSetSpeedyDeliveryPaused res = new ResSetSpeedyDeliveryPaused(code);
        if (code == Code.SUCCESS) {
            res.paused = round.isPaused();
            res.remainingMillis = round.getRemainingMillis(now);
            res.endTime = res.paused ? 0 : round.getEndTime();
            log.debug("飞驰快送切换暂停状态成功 playerId={},paused={},remainingMillis={},endTime={}",
                    ctx.playerId(), res.paused, res.remainingMillis, round.getEndTime());
        } else {
            log.warn("飞驰快送切换暂停状态失败 playerId={},paused={},state={},endTime={},now={},code={}",
                    ctx.playerId(), paused,
                    round == null ? RoundState.NONE : round.getState(), round == null ? 0 : round.getEndTime(), now, code);
        }
        return res;
    }

    public ResDeductSpeedyDeliveryTime deductTime(SimPlayerContext ctx, int seconds) {
        SpeedyDeliveryRound round = ctx.getSpeedyDeliveryRound();
        ResDeductSpeedyDeliveryTime res = new ResDeductSpeedyDeliveryTime(checkPlaying(round));
        if (res.code == Code.SUCCESS) {
            if (seconds <= 0) {
                res.code = Code.PARAM_ERROR;
            } else {
                round.setEndTime(Math.max(System.currentTimeMillis(), round.getEndTime() - seconds * 1000L));
                log.debug("飞驰快送扣时成功 playerId={},seconds={},endTime={}", ctx.playerId(), seconds, round.getEndTime());
            }
        }
        if (round != null) {
            res.endTime = round.getEndTime();
        }
        if (res.code != Code.SUCCESS) {
            log.warn("飞驰快送扣时失败 playerId={},seconds={},state={},endTime={},code={}",
                    ctx.playerId(), seconds, round == null ? RoundState.NONE : round.getState(), res.endTime, res.code);
        }
        return res;
    }

    /** 中途退出也走结束协议；待发奖重试不再受游戏时限限制。 */
    public ResEndSpeedyDeliveryGame end(SimPlayerContext ctx) {
        SpeedyDeliveryRound round = ctx.getSpeedyDeliveryRound();
        if (round == null) {
            log.warn("飞驰快送结束请求被拒绝，当前没有对局 playerId={}", ctx.playerId());
            return new ResEndSpeedyDeliveryGame(Code.ERROR_REQ);
        }
        int code;
        if (round.getState() == RoundState.CLEARED) {
            log.warn("飞驰快送结束请求被拒绝，对局已结算 playerId={},score={},endTime={}",
                    ctx.playerId(), round.getScore(), round.getEndTime());
            code = Code.REPEAT_OP;
        } else if (round.getState() == RoundState.PLAYING
                && System.currentTimeMillis() > round.getEndTime() + END_TOLERANCE_MILLIS) {
            log.warn("飞驰快送结束请求超时 playerId={},score={},endTime={}", ctx.playerId(), round.getScore(), round.getEndTime());
            code = Code.EXPIRE;
        } else {
            code = settle(ctx, round);
        }
        return endResponse(ctx, round, code);
    }

    public ResQuickClearSpeedyDeliveryGame quickClear(SimPlayerContext ctx) {
        ResQuickClearSpeedyDeliveryGame res = new ResQuickClearSpeedyDeliveryGame(Code.SUCCESS);
        SpeedyDeliveryRound round = ctx.getSpeedyDeliveryRound();
        if (round == null || round.getState() != RoundState.REWARD_PENDING || !round.isQuickClear()) {
            MinigameSpeedyDeliveryCfg cfg = config(ctx);
            GlobalConfigCfg scoreCfg = GameDataManager.getGlobalConfigCfg(MiniGameConstant.SPEEDY_DELIVERY_QUICK_CLEAR_SCORE_GLOBAL_ID);
            if (cfg == null || scoreCfg == null || scoreCfg.getIntValue() < 0) {
                log.warn("飞驰快送一键通关配置无效 playerId={},gameConfigValid={},globalId={},configuredScore={}",
                        ctx.playerId(), cfg != null, MiniGameConstant.SPEEDY_DELIVERY_QUICK_CLEAR_SCORE_GLOBAL_ID,
                        scoreCfg == null ? null : scoreCfg.getIntValue());
                res.code = Code.SAMPLE_ERROR;
            } else if (!openOneClickPass(ctx)) {
                GlobalConfigCfg gate = GameDataManager.getGlobalConfigCfg(MiniGameConstant.SPEEDY_DELIVERY_QUICK_CLEAR_CHAPTER_GLOBAL_ID);
                log.warn("飞驰快送一键通关未开放 playerId={},globalId={},chapterLevelId={},requiredLevelId={}",
                        ctx.playerId(), MiniGameConstant.SPEEDY_DELIVERY_QUICK_CLEAR_CHAPTER_GLOBAL_ID,
                        ctx.getSimChapterData() == null ? null : ctx.getSimChapterData().getLevelId(),
                        gate == null ? null : gate.getIntValue());
                res.code = Code.NOT_UNLOCKED;
            } else {
                res.code = prepareStart(ctx);
                if (res.code == Code.SUCCESS) {
                    res.code = charge(ctx, cfg, true);
                    if (res.code == Code.SUCCESS) {
                        round = ctx.getSpeedyDeliveryRound();
                        round.setScore(scoreCfg.getIntValue());
                    }
                }
            }
        }
        if (res.code == Code.SUCCESS) {
            res.code = settle(ctx, round);
            res.score = round.getScore();
            res.skillPoints = loadData(ctx).getSkillPoints();
            if (res.code == Code.SUCCESS) {
                res.rewards = ItemUtils.buildItemInfo(round.getRewards());
            }
        }
        return res;
    }

    /** 复用玩家tick，在十秒宽限结束后兜底结算；失败保留状态供结束协议或退出重试。 */
    @Override
    public void onTick(SimPlayerContext ctx, long now) {
        SpeedyDeliveryRound round = ctx.getSpeedyDeliveryRound();
        if (round != null && round.getState() == RoundState.PLAYING
                && now > round.getEndTime() + END_TOLERANCE_MILLIS) {
            log.info("飞驰快送对局超时，触发自动结算 playerId={},score={},endTime={},now={}",
                    ctx.playerId(), round.getScore(), round.getEndTime(), now);
            ctx.send(endResponse(ctx, round, settle(ctx, round)));
        }
    }

    private ResEndSpeedyDeliveryGame endResponse(SimPlayerContext ctx, SpeedyDeliveryRound round, int code) {
        ResEndSpeedyDeliveryGame res = new ResEndSpeedyDeliveryGame(code);
        res.score = round.getScore();
        res.skillPoints = loadData(ctx).getSkillPoints();
        if (code == Code.SUCCESS) {
            res.rewards = ItemUtils.buildItemInfo(round.getRewards());
            res.extraRewards = ItemUtils.buildItemInfo(round.getDeliveredGoods());
        }
        return res;
    }

    public int onExit(SimPlayerContext ctx) {
        SpeedyDeliveryRound round = ctx.getSpeedyDeliveryRound();
        if (round != null && round.getState() != RoundState.CLEARED) {
            log.debug("飞驰快送退出时触发结算 playerId={},state={},score={},endTime={}",
                    ctx.playerId(), round.getState(), round.getScore(), round.getEndTime());
            round.randomEventConfigId = 0;
            int code = settle(ctx, round);
            if (code != Code.SUCCESS) {
                return code;
            }
        }
        ctx.setSpeedyDeliveryRound(null);
        return Code.SUCCESS;
    }

    private int checkPlaying(SpeedyDeliveryRound round) {
        if (round == null) {
            return Code.ERROR_REQ;
        }
        if (round.getState() != RoundState.PLAYING) {
            return Code.REPEAT_OP;
        }
        return System.currentTimeMillis() > round.getEndTime() ? Code.EXPIRE : Code.SUCCESS;
    }

    /** 已过期的上一局必须先发奖成功，才能扣除下一局费用。 */
    private int prepareStart(SimPlayerContext ctx) {
        SpeedyDeliveryRound round = ctx.getSpeedyDeliveryRound();
        if (round == null || round.getState() == RoundState.CLEARED) {
            return Code.SUCCESS;
        }
        if (round.getState() == RoundState.PLAYING
                && System.currentTimeMillis() > round.getEndTime() + END_TOLERANCE_MILLIS) {
            log.info("飞驰快送开新局前结算超时旧局 playerId={},score={},endTime={}",
                    ctx.playerId(), round.getScore(), round.getEndTime());
            return settle(ctx, round);
        }
        log.warn("飞驰快送已有未结束单局 playerId={},state={},score={},endTime={}",
                ctx.playerId(), round.getState(), round.getScore(), round.getEndTime());
        return Code.REPEAT_OP;
    }

    private int charge(SimPlayerContext ctx, MinigameSpeedyDeliveryCfg cfg, boolean quickClear) {
        int code = grantDaily(ctx, cfg, System.currentTimeMillis());
        if (code != Code.SUCCESS) {
            return code;
        }
        code = playerPackService.removeItems(ctx.getPlayer(), cfg.getConsumeCount(), AddType.SIM_SPEEDY_DELIVERY).code;
        if (code == Code.SUCCESS) {
            long baseDuration = cfg.getDuration() * 1000L;
            SpeedyDeliveryRound round = new SpeedyDeliveryRound(cfg, System.currentTimeMillis() + baseDuration);
            round.setQuickClear(quickClear);
            if (!quickClear) {
                applySkillEffects(loadData(ctx), round, baseDuration);
            }
            ctx.setSpeedyDeliveryRound(round);
            log.info("飞驰快送开局成功 playerId={},quickClear={},endTime={},capacity={},costItems={}",
                    ctx.playerId(), quickClear, round.getEndTime(), round.getBackpackCapacity(), cfg.getConsumeCount());
            conditionService.onRoundStarted(ctx, 4);
        } else {
            log.warn("飞驰快送开局道具扣除失败 playerId={},quickClear={},costItems={},code={}",
                    ctx.playerId(), quickClear, cfg.getConsumeCount(), code);
        }
        return code;
    }

    private void applySkillEffects(SpeedyDeliveryData data, SpeedyDeliveryRound round, long baseDuration) {
        if (data.getSkillLevels().isEmpty()) {
            return;
        }
        long durationFlat = 0;
        long durationPermille = 0;
        long capacityFlat = 0;
        long capacityPermille = 0;
        var skillTree = configCacheService.getMinigameSpeedySkills();
        for (var skill : data.getSkillLevels().entrySet()) {
            MinigameSpeedySkilCfg cfg = skillTree.levelConfig(skill.getKey(), skill.getValue());
            if (cfg == null) {
                log.warn("飞驰快送开局缺少已升级技能配置 playerId={},groupId={},level={}",
                        data.getPlayerId(), skill.getKey(), skill.getValue());
                continue;
            }
            if (cfg.getType() == null) {
                continue;
            }
            int flat = cfg.getFlatValue();
            int permille = cfg.getPermille();
            for (List<Integer> effect : cfg.getType()) {
                if (effect.isEmpty()) {
                    continue;
                }
                int target = effect.size() >= 2 ? effect.get(1) : 0;
                switch (effect.getFirst()) {
                    case DURATION_BONUS -> {
                        durationFlat += flat;
                        durationPermille += permille;
                    }
                    case SCORE_BONUS -> {
                        if (!round.addScoreBonus(target, flat, permille)) {
                            log.warn("飞驰快送加分技能的类别配置无效 playerId={},skillId={},effect={},required=1..3",
                                    data.getPlayerId(), cfg.getId(), effect);
                        }
                    }
                    case 1001, 1002, 1003 -> round.addTruckMultiplierBonus(effect.getFirst(), flat, permille);
                    case BACKPACK_CAPACITY_BONUS -> {
                        if (target == 1) {
                            capacityFlat += flat;
                            capacityPermille += permille;
                        }
                    }
                    case GOODS_REWARD_BONUS -> {
                        if (target == 1) {
                            round.addGoodsRewardBonus(flat, permille);
                        }
                    }
                }
            }
        }
        long duration = baseDuration + durationFlat * 1000 + MiniGameSkillEffect.scaleByPermille(baseDuration, durationPermille);
        if (duration > 0) {
            round.setEndTime(round.getEndTime() - baseDuration + duration);
        } else {
            log.warn("飞驰快送技能加成后时长无效 playerId={},baseMillis={},durationMillis={}",
                    data.getPlayerId(), baseDuration, duration);
        }
        int baseCapacity = round.getBackpackCapacity();
        long capacity = baseCapacity + capacityFlat + MiniGameSkillEffect.scaleByPermille(baseCapacity, capacityPermille);
        round.setBackpackCapacity(Math.clamp(capacity, baseCapacity, Integer.MAX_VALUE));
    }

    /** 首次结算锁定奖励档位，入账技能积分和货物加成，发奖重试沿用本局结果。 */
    private int settle(SimPlayerContext ctx, SpeedyDeliveryRound round) {
        round.setState(RoundState.REWARD_PENDING);
        if (round.getRewards() == null) {
            int threshold = 0;
            for (int key : round.getConfig().getReward().keySet()) {
                if (key <= round.getScore() && key > threshold) {
                    threshold = key;
                }
            }
            Map<Integer, Integer> selected = round.getConfig().getReward().get(threshold);
            Map<Integer, Long> rewards = new HashMap<>(selected.size());
            selected.forEach((itemId, count) -> rewards.put(itemId, count.longValue()));
            round.setRewards(rewards);
            log.debug("飞驰快送锁定奖励档位 playerId={},score={},rewardThreshold={},endTime={}",
                    ctx.playerId(), round.getScore(), threshold, round.getEndTime());
            SpeedyDeliveryData data = loadData(ctx);
            Map<Integer, Long> deliveredGoods = round.getDeliveredGoods();
            if (!deliveredGoods.isEmpty()) {
                deliveredGoods.replaceAll((goodsId, count) ->
                        count + data.addWarehouseGoods(goodsId, round.goodsRewardBonus(count)));
            }
            if (round.getScore() > 0) {
                data.setSkillPoints(data.getSkillPoints() + round.getScore());
                autoSaveService.enqueueSave(data);
            }
        }
        CommonResult<ItemOperationResult> result = playerPackService.addItems(
                ctx.playerId(), round.getRewards(), AddType.SIM_SPEEDY_DELIVERY, true);
        if (result.success()) {
            round.setState(RoundState.CLEARED);
            log.info("飞驰快送结算成功 playerId={},quickClear={},score={},endTime={},randomEventConfigId={},rewards={},extraRewards={}",
                    ctx.playerId(), round.isQuickClear(), round.getScore(), round.getEndTime(),
                    round.randomEventConfigId, round.getRewards(), round.getDeliveredGoods());
            if (round.randomEventConfigId != 0) {
                randomEvents.roomFinished(ctx, round.randomEventConfigId);
            }
        } else {
            log.warn("飞驰快送发奖失败 playerId={},quickClear={},score={},endTime={},rewards={},code={}",
                    ctx.playerId(), round.isQuickClear(), round.getScore(), round.getEndTime(), round.getRewards(), result.code);
        }
        return result.code;
    }

    private boolean openOneClickPass(SimPlayerContext ctx) {
        GlobalConfigCfg cfg = GameDataManager.getGlobalConfigCfg(MiniGameConstant.SPEEDY_DELIVERY_QUICK_CLEAR_CHAPTER_GLOBAL_ID);
        if (cfg == null || cfg.getIntValue() <= 0) {
            return false;
        }
        if (ctx.getSimChapterData() == null && chapterService.getResChapterInfo(ctx).code != Code.SUCCESS) {
            return false;
        }
        return ctx.getSimChapterData().getLevelId() >= cfg.getIntValue();
    }

    /** 每日首次处理时补足道具；已充足也记录当天，补足失败则保留重试机会。 */
    private int grantDaily(SimPlayerContext ctx, MinigameSpeedyDeliveryCfg cfg, long now) {
        if (cfg == null) {
            return Code.SAMPLE_ERROR;
        }
        SpeedyDeliveryData data = loadData(ctx);
        if (data.getLastDailyGrantTime() >= now || TimeHelper.inSameDay(data.getLastDailyGrantTime(), now)) {
            return Code.SUCCESS;
        }
        CommonResult<ItemOperationResult> result = MiniGameDailyItems.replenish(
                ctx, cfg.getGrantCount(), AddType.SIM_SPEEDY_DELIVERY, playerPackService);
        if (!result.success()) {
            log.warn("飞驰快送每日补足失败 playerId={},targetItems={},code={}", ctx.playerId(), cfg.getGrantCount(), result.code);
            return result.code;
        }
        data.setLastDailyGrantTime(now);
        autoSaveService.enqueueSave(data);
        return Code.SUCCESS;
    }

    private SpeedyDeliveryData loadData(SimPlayerContext ctx) {
        SpeedyDeliveryData data = ctx.getSpeedyDeliveryData();
        if (data == null) {
            data = dao.findById(ctx.playerId()).orElseGet(() -> {
                SpeedyDeliveryData created = new SpeedyDeliveryData();
                created.setPlayerId(ctx.playerId());
                return created;
            });
            data.restoreGoods();
            ctx.setSpeedyDeliveryData(data);
        }
        return data;
    }

    private MinigameSpeedyDeliveryCfg config(SimPlayerContext ctx) {
        Map<Integer, MinigameSpeedyDeliveryCfg> configs = GameDataManager.getMinigameSpeedyDeliveryCfgMap();
        if (configs.size() != 1) {
            log.warn("飞驰快送配置行数无效 playerId={},configCount={},required=1", ctx.playerId(), configs.size());
            return null;
        }
        MinigameSpeedyDeliveryCfg cfg = configs.values().iterator().next();
        if (cfg.getDuration() <= 0 || cfg.getBackpackCapacity() <= 0 || !validItems(cfg.getConsumeCount())
                || cfg.getConsumeCount().isEmpty() || !validItems(cfg.getGrantCount())
                || cfg.getReward() == null || !cfg.getReward().containsKey(0)) {
            log.warn("飞驰快送基础配置无效 playerId={},configId={},duration={},capacity={},costItems={},targetItems={},hasZeroScoreReward={}",
                    ctx.playerId(), cfg.getId(), cfg.getDuration(), cfg.getBackpackCapacity(), cfg.getConsumeCount(),
                    cfg.getGrantCount(), cfg.getReward() != null && cfg.getReward().containsKey(0));
            return null;
        }
        for (var entry : cfg.getReward().entrySet()) {
            if (entry.getKey() < 0 || !validItems(entry.getValue())) {
                log.warn("飞驰快送奖励档位配置无效 playerId={},configId={},rewardThreshold={},rewards={}",
                        ctx.playerId(), cfg.getId(), entry.getKey(), entry.getValue());
                return null;
            }
        }
        return cfg;
    }

    private boolean validItems(Map<Integer, ? extends Number> items) {
        return items != null && items.entrySet().stream().allMatch(e -> e.getKey() > 0 && e.getValue().longValue() > 0);
    }
}
