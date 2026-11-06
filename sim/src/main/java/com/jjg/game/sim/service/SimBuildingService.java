package com.jjg.game.sim.service;

import com.alibaba.fastjson.JSON;
import com.jjg.game.alliance.service.AllianceEventService;
import com.jjg.game.common.constant.CoreConst;
import com.jjg.game.common.pb.ItemInfo;
import com.jjg.game.common.utils.CommonUtil;
import com.jjg.game.common.utils.TimeHelper;
import com.jjg.game.core.base.condition.numeric.ActionConditionEvent;
import com.jjg.game.core.constant.AddType;
import com.jjg.game.core.constant.Code;
import com.jjg.game.core.data.CommonResult;
import com.jjg.game.core.data.ItemOperationResult;
import com.jjg.game.core.logger.CoreLogger;
import com.jjg.game.core.pb.KVInfo;
import com.jjg.game.core.pb.KVLongInfo;
import com.jjg.game.core.service.PlayerPackService;
import com.jjg.game.core.utils.ItemUtils;
import com.jjg.game.sampledata.GameDataManager;
import com.jjg.game.sampledata.bean.BuildingAreaTableCfg;
import com.jjg.game.sampledata.bean.BuildingSkinCfg;
import com.jjg.game.sampledata.bean.BuildingUpgradeAreaCfg;
import com.jjg.game.sampledata.bean.BuildingUpgradeTableCfg;
import com.jjg.game.sampledata.bean.BuffCfg;
import com.jjg.game.sampledata.bean.CasinoStatsSheetCfg;
import com.jjg.game.sampledata.bean.EmployeeProfileCfg;
import com.jjg.game.sim.constant.BuildingOutputType;
import com.jjg.game.sim.constant.BuildingType;
import com.jjg.game.sim.constant.SimConstant;
import com.jjg.game.sim.constant.SimStatKey;
import com.jjg.game.sim.data.*;
import com.jjg.game.sim.listener.SimPlayerTickListener;
import com.jjg.game.sim.listener.SimTaskStateReporter;
import com.jjg.game.sim.pb.SimPbConverter;
import com.jjg.game.sim.pb.res.*;
import com.jjg.game.sim.pb.struct.BonusInfo;
import com.jjg.game.sim.pb.struct.BuildingTips;
import com.jjg.game.sim.pb.struct.OfflineReward;
import com.jjg.game.sim.tools.SimTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Consumer;

/**
 * 建筑服务: 解锁、升级、属性与产出
 *
 * @author 11
 * @date 2026/5/28
 */
@Service
public class SimBuildingService implements SimPlayerTickListener, SimTaskStateReporter {
    private static final Logger log = LoggerFactory.getLogger(SimBuildingService.class);

    //初始等级 (解锁后)
    private static final int INITIAL_LEVEL = 1;

    @Autowired
    private SimConfigCacheService configCache;
    @Autowired
    private SimEmployeeAttributeService employeeAttributeService;
    @Autowired
    private PlayerPackService playerPackService;
    @Autowired
    private CoreLogger coreLogger;
    @Autowired
    private AllianceEventService allianceEventService;
    @Autowired
    private SimMedalService medalService;
    //懒加载打破与 SimCasinoService 的循环依赖 (对方持有本服务)
    @Autowired
    @Lazy
    private SimCasinoService simCasinoService;
    //懒加载打破与 SimTaskService 的循环依赖 (对方持有本服务作状态补报口)
    @Autowired
    @Lazy
    private SimTaskService simTaskService;

    @Override
    public void onTick(SimPlayerContext ctx, long now) {
        output(ctx, now);
    }

    @Override
    public int order() {
        return SimPlayerTickListener.super.order();
    }

    /** 领取当前场景指定建筑的游客奖励，buildingId 为 0 时领取全部；入账成功后扣除领取快照再推进统计。 */
    public ResClaimBuildingGuestRewards claimGuestRewards(SimPlayerContext ctx, int buildingId) {
        ResClaimBuildingGuestRewards res = new ResClaimBuildingGuestRewards(Code.SUCCESS);
        res.buildingId = buildingId;
        if (buildingId < 0) {
            res.code = Code.PARAM_ERROR;
            log.warn("领取建筑游客奖励失败, 建筑id不能为负数 playerId={},buildingId={}", ctx.playerId(), buildingId);
            return res;
        }
        SimCasinoData casino = ctx.getCurrentCasino();
        if (casino == null) {
            res.code = Code.NOT_FOUND;
            log.warn("领取建筑游客奖励失败, 当前场景不存在 playerId={},buildingId={}", ctx.playerId(), buildingId);
            return res;
        }
        Collection<BuildingData> buildings;
        if (buildingId == 0) {
            buildings = casino.getBuildingData() == null ? Collections.emptyList() : casino.getBuildingData().values();
        } else {
            BuildingData building = casino.findBuilding(buildingId);
            if (building == null) {
                res.code = Code.NOT_FOUND;
                log.warn("领取建筑游客奖励失败, 当前场景中建筑不存在 playerId={},casinoId={},buildingId={}",
                        ctx.playerId(), casino.getCasinoId(), buildingId);
                return res;
            }
            buildings = Collections.singletonList(building);
        }
        Map<BuildingData, Map<Integer, Long>> snapshots = new HashMap<>();
        Map<Integer, Long> rewards = new HashMap<>();
        for (BuildingData building : buildings) {
            Map<Integer, Long> snapshot = new HashMap<>(building.getGuestRewards());
            if (!snapshot.isEmpty()) {
                snapshots.put(building, snapshot);
                snapshot.forEach((itemId, count) -> rewards.merge(itemId, count, Long::sum));
            }
        }
        if (rewards.isEmpty()) {
            res.rewards = Collections.emptyList();
            return res;
        }
        CommonResult<ItemOperationResult> result = playerPackService.addItems(
                ctx.playerId(), rewards, AddType.SIM_GUEST_REWARDS, null, true);
        if (result == null || !result.success()) {
            res.code = result == null ? Code.FAIL : result.code;
            log.warn("领取建筑游客奖励入账失败 playerId={},casinoId={},buildingId={},rewards={},nullResult={},code={}",
                    ctx.playerId(), casino.getCasinoId(), buildingId, rewards, result == null, res.code);
            return res;
        }
        snapshots.forEach((building, snapshot) -> snapshot.forEach((itemId, count) ->
                building.getGuestRewards().computeIfPresent(itemId,
                        (id, current) -> current > count ? current - count : null)));
        ctx.setLastSaveTime(0);
        res.rewards = ItemUtils.buildItemInfo(rewards);
        SimBaseData base = ctx.getSimBaseData();
        base.addBusinessIncome(rewards.getOrDefault(ItemUtils.getGoldItemId(), 0L));
        base.recordGuestOutputRewards(rewards, System.currentTimeMillis());
        allianceEventService.onBusinessIncome(ctx.playerId(), rewards);
        return res;
    }

    /**
     * 获取建筑信息
     *
     * @param ctx
     * @param buildingId
     */
    public void onBuildingInfo(SimPlayerContext ctx, int buildingId) {
        ResBuildingInfo res = new ResBuildingInfo(Code.SUCCESS);
        try {
            Map<Integer, BuildingData> buildingDataMap = ctx.getCurrentCasino().getBuildingData();
            if (buildingDataMap == null || buildingDataMap.isEmpty()) {
                log.warn("获取建筑信息失败, 建筑列表为空 playerId={}", ctx.playerId());
                res.code = Code.NOT_FOUND;
                ctx.send(res);
                return;
            }

            BuildingData buildingData = buildingDataMap.get(buildingId);
            if (buildingData == null) {
                log.warn("获取建筑信息失败, 该建筑不存在 playerId={},buildingId={}", ctx.playerId(), buildingId);
                res.code = Code.NOT_FOUND;
                ctx.send(res);
                return;
            }

            long now = System.currentTimeMillis();
            BuildingAreaTableCfg areaCfg = GameDataManager.getBuildingAreaTableCfg(buildingData.getId());

            BuildingUpgradeTableCfg buildingUpgradeCfg = configCache.getBuildingUpgradeCfg(buildingId, buildingData.getLevel());

            res.buildingInfo = SimPbConverter.toBuildingInfo(ctx, configCache, buildingData, buildingUpgradeCfg,
                    areaCfg.getUnlockGameId(), now);
            res.shareHelp = false;
            res.watchAdLimit = 0;
            //主管id
            res.managerId = managerId(ctx, buildingData);
            if (res.managerId > 0) {
                SimEmployeeData employee = ctx.getEmployee(res.managerId);
                if (employee != null) {
                    res.managerLevel = employee.getLevel();
                }
            } else {
                //找到雇员中是否有可以为该建筑设置为主管的
                int employeeProfile = areaCfg == null ? 0 : areaCfg.getEmployeeProfile();
                res.canSetManager = employeeProfile > 0 && ctx.getEmployeeMap().keySet().stream().map(GameDataManager::getEmployeeProfileCfg).filter(Objects::nonNull).anyMatch(cfg -> cfg.getProfessionID() == employeeProfile);
            }
            log.info("返回建筑信息 playerId={},res={}", ctx.playerId(), JSON.toJSONString(res));

            //这里为测试代码
            log.info("建筑id={},受影响的雇员类型 ={}", buildingData.getId(), areaCfg.getEmployeeProfile());
            log.info("已有雇员对应的技能：");
            List<EmployeeProfileCfg> tmpList = configCache.getEmployeeProfileCfgByProfile(areaCfg.getEmployeeProfile());
            for (EmployeeProfileCfg cfg : tmpList) {
                SimEmployeeData employee = ctx.getEmployee(cfg.getId());
                if (employee != null) {
                    log.info("雇员信息={}", JSON.toJSONString(employee));
                }
            }
        } catch (Exception e) {
            log.error("", e);
        }
        ctx.send(res);
    }

    /**
     * 获取当前场景的所有建筑信息
     *
     * @param ctx 玩家上下文
     */
    public void onAllBuildingInfo(SimPlayerContext ctx) {
        ResAllBuildingInfo res = new ResAllBuildingInfo(Code.SUCCESS);
        try {
            SimCasinoData casino = ctx.getCurrentCasino();
            if (casino == null) {
                log.warn("获取所有建筑信息失败, 当前场景为空 playerId={}", ctx.playerId());
                res.code = Code.NOT_FOUND;
            } else {
                res.buildings = SimPbConverter.toBuildingInfos(ctx, configCache);
            }
        } catch (Exception e) {
            log.error("获取所有建筑信息异常 playerId={}", ctx.playerId(), e);
            res.code = Code.EXCEPTION;
        }
        ctx.send(res);
    }

    /**
     * 获取主管id
     *
     * @param ctx
     * @param buildingData
     * @return
     */
    private int managerId(SimPlayerContext ctx, BuildingData buildingData) {
        BuildingAreaTableCfg areaCfg = GameDataManager.getBuildingAreaTableCfg(buildingData.getId());
        if (areaCfg == null || areaCfg.getEmployeeProfile() < 1) {
            return 0;
        }
        return ctx.getCurrentCasino().manageEmploy(areaCfg.getEmployeeProfile());
    }

    /**
     * 领取离线收益
     */
    public void onClaimOfflineReward(SimPlayerContext ctx, boolean watchAd) {
        ResClaimOfflineReward res = new ResClaimOfflineReward(Code.SUCCESS);
        try {
            SimOfflineReward reward = ctx.getPendingOffline();
            res.code = claimOfflineReward(ctx, watchAd);
            if (res.code == Code.SUCCESS && reward != null) {
                res.watchAd = watchAd;
                Map<BuildingOutputType, Long> finalReward = computeFinalReward(ctx, reward, watchAd);
//                res.rewards = ItemUtils.buildItemInfo(finalReward);

                res.rewards = new ArrayList<>();
                for (Map.Entry<BuildingOutputType, Long> en : finalReward.entrySet()) {
                    KVInfo kvInfo = new KVInfo();
                    kvInfo.key = en.getKey().getCode();
                    kvInfo.value = en.getValue().intValue();
                    res.rewards.add(kvInfo);
                }
            }
        } catch (Exception e) {
            log.error("", e);
            res.code = Code.EXCEPTION;
        }
        ctx.send(res);
    }

    /**
     * 解锁建筑
     */
    public ResUnlockBuilding onUnlockBuilding(SimPlayerContext ctx, int buildingId) {
        ResUnlockBuilding res = new ResUnlockBuilding(Code.SUCCESS);
        res.id = buildingId;
        try {
            SimCasinoData casino = ctx.getCurrentCasino();
            if (casino == null) {
                log.warn("解锁建筑失败, 当前场景不存在 playerId={}", ctx.playerId());
                res.code = Code.NOT_FOUND;
                return res;
            }
            BuildingAreaTableCfg cfg = GameDataManager.getBuildingAreaTableCfg(buildingId);
            if (cfg == null) {
                log.warn("解锁建筑失败, 配置不存在 playerId={},buildingId={}", ctx.playerId(), buildingId);
                res.code = Code.NOT_FOUND;
                return res;
            }
            if (cfg.getRegionID() != casino.getCasinoId()) {
                log.warn("解锁建筑失败, 建筑不属于当前场景 playerId={},buildingId={},casinoId={}", ctx.playerId(), buildingId, casino.getCasinoId());
                res.code = Code.PARAM_ERROR;
                return res;
            }

            if (casino.findBuilding(buildingId) != null) {
                log.warn("解锁建筑失败, 已解锁 playerId={},buildingId={}", ctx.playerId(), buildingId);
                res.code = Code.PARAM_ERROR;
                return res;
            }

            List<BuildingTips> tips = new ArrayList<>();
            //条件1
            if (cfg.getCasinoLevel() > 0 && ctx.getSimBaseData().getAllLevel() < cfg.getCasinoLevel()) {
                log.warn("解锁建筑失败, 场景等级不足 playerId={},buildingId={},casinoId={},cfgCasinoLevel={},ctxAllLevel={}", ctx.playerId(), buildingId, casino.getCasinoId(), cfg.getCasinoLevel(), ctx.getSimBaseData().getAllLevel());

                int languageId = getBuildingUnlockLangId(cfg.getLanguageID(), 0);
                if (languageId < 1) {
                    res.code = Code.LEVEL_NOT_ENOUGH;
                    return res;
                }
                tips.add(SimTool.buildTips(languageId, String.valueOf(cfg.getCasinoLevel())));
            }

            //条件2
            if (cfg.getUnlockMethod() != null) {
                int languageId = getBuildingUnlockLangId(cfg.getLanguageID(), 1);

                for (Map.Entry<Integer, Integer> en : cfg.getUnlockMethod().entrySet()) {
                    BuildingData building = casino.findBuilding(en.getKey());
                    if (building == null) {
                        if (languageId < 1) {
                            log.warn("解锁建筑失败, 解锁方式未通过,建筑未解锁 playerId={},buildingId={},unLockBuildingId={}", ctx.playerId(), buildingId, en.getKey());
                            res.code = Code.PARAM_ERROR;
                            return res;
                        }
                        BuildingAreaTableCfg tmpCfg = GameDataManager.getBuildingAreaTableCfg(en.getKey());
                        tips.add(SimTool.buildTips(languageId, tmpCfg.getBuildingNameId(), String.valueOf(en.getValue())));
                        continue;
                    }
                    if (building.getLevel() < en.getValue()) {
                        if (languageId < 1) {
                            log.warn("解锁建筑失败, 解锁方式未通过,等级不足 playerId={},buildingId={},level={},unLockBuildingId={},cfgLevel={}", ctx.playerId(), buildingId, building.getLevel(), en.getKey(), en.getValue());
                            res.code = Code.PARAM_ERROR;
                            return res;
                        }
                        BuildingAreaTableCfg tmpCfg = GameDataManager.getBuildingAreaTableCfg(en.getKey());
                        tips.add(SimTool.buildTips(languageId, tmpCfg.getBuildingNameId(), String.valueOf(en.getValue())));
                    }
                }
            }

            //条件3
            int costLanguageId = getBuildingUnlockLangId(cfg.getLanguageID(), 2);
            if (tips.isEmpty()) {
                //资源足够?
                boolean remove = playerPackService.removeItems(ctx.getPlayer(), cfg.getUnlockCost(), AddType.SIM_BUILDING_UPGRADE, null).success();
                if (!remove) {
                    if (costLanguageId < 1) {
                        log.warn("解锁建筑失败, 资源不足 playerId={},buildingId={},cost={}", ctx.playerId(), buildingId, cfg.getUnlockCost());
                        res.code = Code.NOT_ENOUGH;
                        //这里不需要通知到客户端
                        return null;
                    }
                    Long l = cfg.getUnlockCost().get(ItemUtils.getGoldItemId());
                    if (l == null) {
                        log.warn("解锁建筑失败, 解析金币时获取错误 playerId={},buildingId={},cost={}", ctx.playerId(), buildingId, cfg.getUnlockCost());
                        res.code = Code.NOT_ENOUGH;
                        return null;
                    }
                    tips.add(SimTool.buildTips(costLanguageId, String.valueOf(l)));
                }
            }

//            else {
//                boolean has = playerPackService.checkHasItems(ctx.getPlayer(), cfg.getUnlockCost());
//                if (!has) {
//                    if (costLanguageId < 1) {
//                        log.warn("解锁建筑失败, 资源不足1 playerId={},buildingId={},cost={}", ctx.playerId(), buildingId, cfg.getUnlockCost());
//                        res.code = Code.NOT_ENOUGH;
//                        return res;
//                    }
//                    Long l = cfg.getUnlockCost().get(ItemUtils.getGoldItemId());
//                    if (l == null) {
//                        log.warn("解锁建筑失败, 解析金币时获取错误1 playerId={},buildingId={},cost={}", ctx.playerId(), buildingId, cfg.getUnlockCost());
//                        res.code = Code.NOT_ENOUGH;
//                        return res;
//                    }
//                    tips.add(SimTool.buildTips(costLanguageId, String.valueOf(l)));
//                }
//            }

            if (!tips.isEmpty()) {
                NotifyBuildingTips notify = new NotifyBuildingTips();
                notify.tips = tips;
                ctx.send(notify);
                return null;
            }

            unlockAndUpdateBuildData(ctx, buildingId, cfg);
            simTaskService.onBuildingUnlocked(ctx, buildingId);
            log.info("解锁建筑成功 playerId={},buildingId={}", ctx.playerId(), buildingId);
        } catch (Exception e) {
            log.error("", e);
            res.code = Code.EXCEPTION;
        }
        return res;
    }


    /** 是否具备完成一次建筑升级的条件和资源。 */
    public boolean canUpgradeBuilding(SimPlayerContext ctx, int buildingId) {
        Map<Integer, Long> cost = redDotUpgradeCost(ctx, buildingId);
        return cost != null && playerPackService.checkHasItems(ctx.getPlayer(), cost);
    }

    /** 红点与单级升级使用同一份条件和消耗。 */
    public Map<Integer, Long> redDotUpgradeCost(SimPlayerContext ctx, int buildingId) {
        SimCasinoData casino = ctx.getCurrentCasino();
        BuildingData data = casino == null ? null : casino.findBuilding(buildingId);
        if (data == null || ctx.getPlayer() == null || ctx.getSimBaseData() == null) {
            return null;
        }
        BuildingUpgradeTableCfg cfg = configCache.getBuildingUpgradeCfg(buildingId, data.getLevel());
        return checkBuildingUpgrade(ctx, buildingId, data.getLevel(), cfg) == Code.SUCCESS
                ? cfg.getCostPerLevel() : null;
    }

    public void onUpgradeBuilding(SimPlayerContext ctx, int buildingId, boolean up10Level) {
        ResUpgradeBuilding res = new ResUpgradeBuilding(Code.SUCCESS);
        try {
            SimCasinoData casino = ctx.getCurrentCasino();
            BuildingData data = casino == null ? null : casino.findBuilding(buildingId);
            BuildingAreaTableCfg areaCfg = GameDataManager.getBuildingAreaTableCfg(buildingId);
            if (data == null || areaCfg == null) {
                res.code = Code.NOT_FOUND;
                return;
            }
            if (data.getLevel() >= areaCfg.getMaxLevel()
                    || (up10Level && data.getLevel() < areaCfg.getUnlockUp10Level())) {
                res.code = Code.PARAM_ERROR;
                return;
            }

            int beforeLevel = data.getLevel();
            int targetLevel = Math.min(areaCfg.getMaxLevel(), beforeLevel + (up10Level ? 10 : 1));
            Map<Integer, Long> totalCost = new HashMap<>();
            Map<Integer, Long> totalReward = new HashMap<>();
            //消耗按升级前等级选档，奖励按到达等级精确匹配；整次预检通过前不扣费、不修改等级。
            for (int level = beforeLevel; level < targetLevel; level++) {
                BuildingUpgradeTableCfg cfg = configCache.getBuildingUpgradeCfg(buildingId, level);
                res.code = checkBuildingUpgrade(ctx, buildingId, level, cfg);
                if (res.code != Code.SUCCESS) {
                    return;
                }
                cfg.getCostPerLevel().forEach((id, count) -> totalCost.merge(id, count, Long::sum));
                configCache.getBuildingUpgradeReward(buildingId, level + 1)
                        .forEach((id, count) -> totalReward.merge(id, count, Long::sum));
            }
            CommonResult<ItemOperationResult> removed = playerPackService.removeItems(
                    ctx.getPlayer(), totalCost, AddType.SIM_BUILDING_UPGRADE, null);
            if (!removed.success()) {
                res.code = removed.code;
                return;
            }
            if (!totalReward.isEmpty()) {
                CommonResult<ItemOperationResult> awarded = playerPackService.addItems(
                        ctx.playerId(), totalReward, AddType.SIM_BUILDING_UPGRADE);
                if (!awarded.success()) {
                    CommonResult<ItemOperationResult> refunded = playerPackService.addItems(
                            ctx.playerId(), totalCost, AddType.FAIL_ROLLBACK);
                    if (!refunded.success()) {
                        log.error("建筑升级发奖失败后退还消耗失败 playerId={},buildingId={},cost={}",
                                ctx.playerId(), buildingId, totalCost);
                    }
                    res.code = awarded.code;
                    return;
                }
            }

            data.setLevel(targetLevel);
            data.setCdEndTime(0);
            data.setProgress(0);
            data.setAdClearCount(0);
            unlockBuildingAreas(data);
            updateCacheGuestSize(casino);
            simCasinoService.addCasinoExp(ctx, 0);
            for (int level = beforeLevel + 1; level <= targetLevel; level++) {
                allianceEventService.onBuildingUpgrade(ctx.playerId(), buildingId, level);
            }
            reportBuildingCounts(ctx);
            res.buildingInfo = SimPbConverter.toBuildingInfo(ctx, configCache, data,
                    configCache.getBuildingUpgradeCfg(buildingId, targetLevel),
                    areaCfg.getUnlockGameId(), System.currentTimeMillis());
            log.info("建筑升级完成 playerId={},buildingId={},beforeLevel={},level={},cost={},rewards={}",
                    ctx.playerId(), buildingId, beforeLevel, targetLevel, totalCost, totalReward);
        } catch (Exception e) {
            log.error("建筑升级异常 playerId={},buildingId={}", ctx.playerId(), buildingId, e);
            res.code = Code.EXCEPTION;
        } finally {
            ctx.send(res);
        }
    }

    private int checkBuildingUpgrade(SimPlayerContext ctx, int buildingId, int level,
                                     BuildingUpgradeTableCfg cfg) {
        if (cfg == null || cfg.getCostPerLevel() == null || cfg.getCostPerLevel().isEmpty()
                || level >= configCache.getBuildingMaxLevel(buildingId)) {
            return Code.PARAM_ERROR;
        }
        return cfg.getNeedLevel() > ctx.getSimBaseData().getAllLevel()
                ? Code.SIM_CASINO_LEVEL_LOW : Code.SUCCESS;
    }

    /** 升级已在 ReqUpgradeBuilding 中完成，旧完成请求不再推进等级。 */
    public void onCompleteBuildingUpgrade(SimPlayerContext ctx, int buildingId) {
        ctx.send(new ResCompleteBuildingUpgrade(Code.PARAM_ERROR));
    }

    /** 即时升级没有 CD，旧清除请求不再扣费。 */
    public void onClearBuildingCD(SimPlayerContext ctx, int buildingId, int costCount, boolean watchAd, int costItemId) {
        ctx.send(new ResClearBuildingCD(Code.PARAM_ERROR));
    }

    /**
     * 立即结算当前场景已积累的整分钟在线产出 (切换场景前调用, 避免余量丢失)
     */
    public void settleOnlineOutput(SimPlayerContext ctx) {
        trySettleOnlineOutput(ctx);
    }

    /** 返回此前所有整分钟产出是否已成功结算。 */
    public boolean trySettleOnlineOutput(SimPlayerContext ctx) {
        long now = System.currentTimeMillis();
        output(ctx, now);
        SimCasinoData casino = ctx.getCurrentCasino();
        return casino != null && now - casino.getLastOutputTime() < TimeHelper.ONE_MINUTE_OF_MILLIS;
    }

    /**
     * 在线产出: 每分钟累计到各建筑待领取奖励, 不产出经验, 保留不足 1 分钟的余量时间
     */
    private void output(SimPlayerContext ctx, long now) {
        try {
            SimCasinoData casino = ctx.getCurrentCasino();
            if (casino == null) {
                return;
            }
            if (casino.getLastOutputTime() == 0) {
                casino.setLastOutputTime(now);
                return;
            }
            long elapsed = now - casino.getLastOutputTime();
            long fullMinutes = elapsed / TimeHelper.ONE_MINUTE_OF_MILLIS;
            if (fullMinutes <= 0) {
                return;
            }
            long settledOutputTime = casino.getLastOutputTime() + fullMinutes * TimeHelper.ONE_MINUTE_OF_MILLIS;
            Map<BuildingOutputType, Long> total = new EnumMap<>(BuildingOutputType.class);
            Map<BuildingData, Map<Integer, Long>> rewardsByBuilding = new HashMap<>();
            if (casino.getBuildingData() != null && !casino.getBuildingData().isEmpty()) {
                SimEmployeeAttributeService.Snapshot attributes = employeeAttributeService.snapshot(ctx);
                for (BuildingData building : casino.getBuildingData().values()) {
                    Map<BuildingOutputType, Long> rewards = multiply(buildingActualPerMinute(ctx, building, attributes), fullMinutes);
                    rewards.remove(BuildingOutputType.CASINO_LEVEL_EXP);
                    rewards.forEach((type, count) -> total.merge(type, count, Long::sum));
                    rewardsByBuilding.put(building, toItemMap(rewards));
                }
            }
            if (!total.isEmpty()) {
                Map<Integer, Long> items = toItemMap(total);
                if (!items.isEmpty()) {
                    rewardsByBuilding.forEach((building, rewards) -> rewards.forEach((itemId, count) ->
                            building.getGuestRewards().merge(itemId, count, Long::sum)));
                }
                //先提交结算时间，避免后续统计或通知异常导致同一时间段重复累计
                casino.setLastOutputTime(settledOutputTime);
                //经营信息: 累加每分钟自产金币收益
                long minuteGold = total.getOrDefault(BuildingOutputType.GOLD, 0L);
                if (minuteGold > 0) {
                    ctx.getSimBaseData().addBusinessIncome(minuteGold);
                }

                if (!items.isEmpty()) {
                    allianceEventService.onBusinessIncome(ctx.playerId(), items);
                }

                if (!total.isEmpty()) {
                    NotifyBuildingOutput notify = new NotifyBuildingOutput();
                    notify.rewards = new ArrayList<>();

                    for (Map.Entry<BuildingOutputType, Long> en : total.entrySet()) {
                        ItemInfo itemInfo = new ItemInfo();
                        itemInfo.itemId = en.getKey().getCode();
                        itemInfo.count = en.getValue();
                        notify.rewards.add(itemInfo);
                    }
                    if (!notify.rewards.isEmpty()) {
                        ctx.send(notify);
                    }
                }
                return;
            }
            //仅推进已结算的整分钟, 保留余量
            casino.setLastOutputTime(settledOutputTime);
        } catch (Exception e) {
            log.error("在线产出结算异常 playerId={}", ctx.playerId(), e);
        }
    }

    /**
     * 计算当前场景所有游戏区/休息区建筑每分钟产出 (含雇员加成) 之和
     *
     * @return itemId -> 每分钟数量 (金币/能量混合)
     */
    public Map<BuildingOutputType, Long> computePerMinuteOutput(SimPlayerContext ctx, SimCasinoData casino) {
        return computePerMinuteOutput(ctx, casino, employeeAttributeService.snapshot(ctx));
    }

    public Map<BuildingOutputType, Long> computePerMinuteOutput(SimPlayerContext ctx, SimCasinoData casino,
                                                                SimEmployeeAttributeService.Snapshot attributes) {
        if (casino.getBuildingData() == null || casino.getBuildingData().isEmpty()) {
            return Collections.emptyMap();
        }
        Map<BuildingOutputType, Long> total = new EnumMap<>(BuildingOutputType.class);
        for (BuildingData building : casino.getBuildingData().values()) {
            Map<BuildingOutputType, Long> actual = buildingActualPerMinute(ctx, building, attributes);
            actual.forEach((buildingOutputType, count) -> total.merge(buildingOutputType, count, Long::sum));
        }
        return total;
    }

    /**
     * 计算单个建筑每分钟实际产出 (含普通雇员 + 主管 + 技能加成); 非每分钟产出类型返回空。
     *
     * @param attributes 本次计算共用的雇员最终属性和生效技能
     */
    private Map<BuildingOutputType, Long> buildingActualPerMinute(SimPlayerContext ctx, BuildingData building,
                                                                  SimEmployeeAttributeService.Snapshot attributes) {
        BuildingAreaTableCfg areaCfg = GameDataManager.getBuildingAreaTableCfg(building.getId());
        if (areaCfg == null) {
            return Collections.emptyMap();
        }
        //只有游戏区和休息区才有每分钟产出
        BuildingType buildingType = BuildingType.fromCode(areaCfg.getType());
        if (buildingType != BuildingType.GAME && buildingType != BuildingType.REST) {
            return Collections.emptyMap();
        }

        return computeDashboardBuildingValues(ctx, building, attributes, null);
    }

    /**
     * 计算细分运营看板中单栋建筑的实际属性。
     * 游戏区和休息区返回每分钟产出（含雇员、主管、技能加成），管理区返回当前部门属性。
     */
    public Map<BuildingOutputType, Long> computeDashboardBuildingValues(SimPlayerContext ctx, BuildingData building) {
        if (ctx == null || building == null) {
            return Collections.emptyMap();
        }
        return computeDashboardBuildingValues(ctx, building, employeeAttributeService.snapshot(ctx), null);
    }

    /**
     * 计算总值时按需收集各属性的来源加成，结算和普通看板不创建明细对象。
     */
    public Map<BuildingOutputType, Long> computeDashboardBuildingValues(SimPlayerContext ctx, BuildingData building,
                                                                        SimEmployeeAttributeService.Snapshot attributes,
                                                                        List<BonusInfo> bonusInfos) {
        if (ctx == null || building == null) {
            return Collections.emptyMap();
        }
        BuildingAreaTableCfg areaCfg = GameDataManager.getBuildingAreaTableCfg(building.getId());
        if (areaCfg == null) {
            return Collections.emptyMap();
        }
        Map<BuildingOutputType, Long> base = getBaseOutput(building.getId(), building.getLevel());
        if (base.isEmpty()) {
            return Collections.emptyMap();
        }
        unlockBuildingAreas(building);
        return applyBuildingBonus(base, areaCfg, building, attributes, bonusInfos);
    }

    /**
     * 固定收益相加后，统一应用万分比；明细仅在建筑详情请求时创建。
     */
    private Map<BuildingOutputType, Long> applyBuildingBonus(Map<BuildingOutputType, Long> base,
                                                             BuildingAreaTableCfg areaCfg,
                                                             BuildingData building,
                                                             SimEmployeeAttributeService.Snapshot attributes,
                                                             List<BonusInfo> bonusInfos) {
        long employeeOutput = attributes.professionAttribute(areaCfg.getEmployeeProfile()) / 60;
        Map<BuildingOutputType, SimAttributeBonus> skinBonuses = buildingSkinBonuses(building);
        Map<BuildingOutputType, Long> result = new EnumMap<>(BuildingOutputType.class);
        for (Map.Entry<BuildingOutputType, Long> en : base.entrySet()) {
            BuildingOutputType type = en.getKey();
            long baseVal = en.getValue();
            SimAttributeBonus bonus = new SimAttributeBonus();
            bonus.addFixed(employeeOutput);
            attributes.addBuildingBonus(areaCfg.getId(), type, bonus);
            long employeeFixed = bonus.fixed();
            long employeePercent = bonus.percent();
            SimAttributeBonus skinBonus = skinBonuses.get(type);
            if (skinBonus != null) {
                bonus.addFixed(skinBonus.fixed());
                bonus.addPercent(skinBonus.percent());
            }
            long actual = bonus.apply(baseVal);
            result.put(type, actual);
            if (bonusInfos != null) {
                BonusInfo info = new BonusInfo();
                info.typeValue = type.getCode();
                info.baseValue = baseVal;
                info.totalValue = actual;
                info.fixedValueAddList = new ArrayList<>();
                info.fixedValueAddList.add(new KVLongInfo(SimConstant.BonusSource.EMPLOYEE, employeeFixed));
                info.propValueAddList = new ArrayList<>();
                info.propValueAddList.add(new KVLongInfo(SimConstant.BonusSource.EMPLOYEE, employeePercent));
                if (skinBonus != null && skinBonus.fixed() != 0) {
                    info.fixedValueAddList.add(new KVLongInfo(SimConstant.BonusSource.SKIN_BUFF, skinBonus.fixed()));
                }
                if (skinBonus != null && skinBonus.percent() != 0) {
                    info.propValueAddList.add(new KVLongInfo(SimConstant.BonusSource.SKIN_BUFF, skinBonus.percent()));
                }
                bonusInfos.add(info);
            }
        }
        return result;
    }

    private Map<BuildingOutputType, SimAttributeBonus> buildingSkinBonuses(BuildingData building) {
        Set<Integer> activeSkins = new HashSet<>();
        for (BuildingUpgradeAreaCfg area : configCache.getBuildingUpgradeAreaCfgs(building.getId())) {
            if (building.getUnlockedAreaIds().contains(area.getId()) && area.getSkinId() != null) {
                for (int skinId : area.getSkinId()) {
                    if (building.getUnlockedSkinIds().contains(skinId)) {
                        activeSkins.add(skinId);
                    }
                }
            }
        }
        Map<BuildingOutputType, SimAttributeBonus> bonuses = new EnumMap<>(BuildingOutputType.class);
        for (int skinId : activeSkins) {
            BuildingSkinCfg skin = GameDataManager.getBuildingSkinCfg(skinId);
            if (skin == null || skin.getBuffId() == null) {
                continue;
            }
            for (int buffId : skin.getBuffId()) {
                BuffCfg buff = GameDataManager.getBuffCfg(buffId);
                if (buff == null) {
                    continue;
                }
                BuildingOutputType type = BuildingOutputType.fromCode(buff.getTypeId());
                if (type == null) {
                    continue;
                }
                SimAttributeBonus bonus = bonuses.computeIfAbsent(type, ignored -> new SimAttributeBonus());
                if (buff.getValueType() == 1) {
                    bonus.addPercent(buff.getBuffParam());
                } else if (buff.getValueType() == 2) {
                    bonus.addFixed(buff.getBuffParam());
                }
            }
        }
        return bonuses;
    }

    /**
     * 经营信息-按房间分类统计每分钟产量:
     * 能量房间(休息区 POWER) / SLOT房间 / 扑克房间 / 捕鱼房间(游戏区 GOLD)。
     * <p>
     * 用于看板展示, 取各建筑配置的每分钟产出 (含加成), 不受在线产出结算的 isMin 门控影响。
     *
     * @return 房间统计KEY ({@link com.jjg.game.sim.constant.SimStatKey.Operation}) -> 每分钟产量
     */
    public Map<Integer, Long> computeRoomOutputs(SimPlayerContext ctx, SimCasinoData casino) {
        Map<Integer, Long> result = new HashMap<>();
        if (casino.getBuildingData() == null || casino.getBuildingData().isEmpty()) {
            return result;
        }
        SimEmployeeAttributeService.Snapshot attributes = employeeAttributeService.snapshot(ctx);

        for (BuildingData building : casino.getBuildingData().values()) {
            BuildingAreaTableCfg areaCfg = GameDataManager.getBuildingAreaTableCfg(building.getId());
            if (areaCfg == null) {
                continue;
            }
            BuildingType buildingType = BuildingType.fromCode(areaCfg.getType());
            if (buildingType != BuildingType.REST && buildingType != BuildingType.GAME) {
                continue;
            }
            Map<BuildingOutputType, Long> actual = buildingActualPerMinute(ctx, building, attributes);
            if (buildingType == BuildingType.REST) {
                //能量房间: 休息区 POWER 产量
                result.merge(SimStatKey.Operation.ENERGY_ROOM, actual.getOrDefault(BuildingOutputType.POWER, 0L), Long::sum);
            } else {
                //按解锁游戏的大类区分 SLOT、扑克和捕鱼，各房间产量互不重叠
                long gold = actual.getOrDefault(BuildingOutputType.GOLD, 0L);
                int statKey = resolveGameRoomStatKey(areaCfg);
                if (statKey > 0) {
                    result.merge(statKey, gold, Long::sum);
                }
            }
        }
        return result;
    }

    static int resolveGameRoomStatKey(BuildingAreaTableCfg areaCfg) {
        if (areaCfg == null) {
            return 0;
        }
        int majorType = CommonUtil.getMajorTypeByGameType(areaCfg.getUnlockGameId());
        if (majorType == CoreConst.GameMajorType.SLOTS) {
            return SimStatKey.Operation.GOLD_INCOME;
        } else if (majorType == CoreConst.GameMajorType.TABLE || majorType == CoreConst.GameMajorType.POKER) {
            return SimStatKey.Operation.POKER_ROOM;
        } else if (majorType == CoreConst.GameMajorType.PLOY) {
            return SimStatKey.Operation.FISHING_ROOM;
        }
        return 0;
    }

    /**
     * 经营信息-当前可容纳游客人数: 各已解锁建筑当前等级的最大交互数量之和。
     */
    public int computeCurrentCapacity(SimCasinoData casino) {
        if (casino.getBuildingData() == null || casino.getBuildingData().isEmpty()) {
            return 0;
        }
        int sum = 0;
        for (BuildingData building : casino.getBuildingData().values()) {
            BuildingUpgradeTableCfg cfg = configCache.getBuildingUpgradeCfg(building.getId(), building.getLevel());
            if (cfg == null) {
                continue;
            }
            sum += cfg.getMaxInteractionCount();
        }
        return sum;
    }

    /**
     * 计算当前娱乐城总繁荣度：累加所有已解锁建筑当前等级的 BuildingUpgradeTable.Prosperity。
     * 补充策划案已将该字段从 CasinoStatsSheet 迁移到建筑升级表，旧字段不再参与计算。
     */
    public int computeProsperity(SimCasinoData casino) {
        if (casino == null || casino.getBuildingData() == null || casino.getBuildingData().isEmpty()) {
            return 0;
        }
        long sum = 0;
        for (BuildingData building : casino.getBuildingData().values()) {
            BuildingUpgradeTableCfg cfg = configCache.getBuildingUpgradeCfg(building.getId(), building.getLevel());
            if (cfg != null) {
                sum += Math.max(0, SimConfigCacheService.getBuildingLevelValue(
                        cfg.getProsperity(), building.getLevel(), cfg.getLevel()));
            }
        }
        return Math.toIntExact(Math.min(Integer.MAX_VALUE, sum));
    }

    /**
     * 计算标准交互次数的百分之一单位值。
     * BuildingUpgradeTable.InteractCount 的配置和为 100 时，实际标准交互次数为 1 次。
     */
    public int computeStandardInteractionCount(SimCasinoData casino) {
        if (casino == null || casino.getBuildingData() == null || casino.getBuildingData().isEmpty()) {
            return 0;
        }
        long sum = 0;
        for (BuildingData building : casino.getBuildingData().values()) {
            BuildingUpgradeTableCfg cfg = configCache.getBuildingUpgradeCfg(building.getId(), building.getLevel());
            if (cfg != null) {
                sum += Math.max(0, SimConfigCacheService.getBuildingLevelValue(
                        cfg.getInteractCount(), building.getLevel(), cfg.getLevel()));
            }
        }
        return Math.toIntExact(Math.min(Integer.MAX_VALUE, sum));
    }

    /**
     * 经营信息-升满级最大容纳游客人数: 该场景全部建筑满级的最大交互数量之和。
     */
    public int computeMaxCapacity(int casinoId) {
        int sum = 0;
        for (BuildingAreaTableCfg areaCfg : GameDataManager.getBuildingAreaTableCfgList()) {
            if (areaCfg.getRegionID() != casinoId) {
                continue;
            }
            BuildingUpgradeTableCfg cfg = configCache.getBuildingUpgradeCfg(areaCfg.getId(), configCache.getBuildingMaxLevel(areaCfg.getId()));
            if (cfg == null) {
                continue;
            }
            sum += cfg.getMaxInteractionCount();
        }
        return sum;
    }

    /**
     *
     * @param outputType 部门对应的产出类型 (按建筑 typeValue 匹配)
     * @return 部门属性值; 未解锁对应建筑返回 0
     */
    public long computeDeptValue(SimPlayerContext ctx, SimCasinoData casino, BuildingOutputType outputType) {
        return computeDeptValue(ctx, casino, outputType, employeeAttributeService.snapshot(ctx));
    }

    public long computeDeptValue(SimPlayerContext ctx, SimCasinoData casino, BuildingOutputType outputType,
                                 SimEmployeeAttributeService.Snapshot attributes) {
        BuildingData dept = findDeptBuilding(casino, outputType);
        if (dept == null) {
            return 0;
        }
        Map<BuildingOutputType, Long> actual = computeDashboardBuildingValues(ctx, dept, attributes, null);
        return actual.getOrDefault(outputType, 0L);
    }

    /**
     * 查找已解锁的管理区建筑中 typeValue 匹配指定产出类型的部门建筑
     */
    private BuildingData findDeptBuilding(SimCasinoData casino, BuildingOutputType outputType) {
        if (casino.getBuildingData() == null || casino.getBuildingData().isEmpty()) {
            return null;
        }
        for (BuildingData building : casino.getBuildingData().values()) {
            BuildingAreaTableCfg areaCfg = GameDataManager.getBuildingAreaTableCfg(building.getId());
            if (areaCfg == null) {
                continue;
            }
            if (BuildingType.fromCode(areaCfg.getType()) != BuildingType.MANAGE) {
                continue;
            }
            List<Integer> typeValues = areaCfg.getTypeValue();
            if (typeValues != null && typeValues.contains(outputType.getCode())) {
                return building;
            }
        }
        return null;
    }

    /**
     * 离线收益结算: 计算并存入待领取快照
     */
    public OfflineReward settleOfflineReward(SimPlayerContext ctx) {
        SimCasinoData casino = ctx.getCurrentCasino();
        if (casino == null) {
            return null;
        }
        long lastOfflineTime = ctx.getSimBaseData().getLastOfflineTime();
        long now = System.currentTimeMillis();
        //重置在线产出结算游标, 避免离线时段被在线 tick 重复计算
        casino.setLastOutputTime(now);
        if (lastOfflineTime <= 0) {
            return null;
        }
        SimOfflineReward reward = computeOfflineReward(ctx, casino, now - lastOfflineTime);
        if (reward == null) {
            return null;
        }
        ctx.setPendingOffline(reward);
        log.info("离线收益结算 playerId={},effectiveMinutes={},capMinutes={},reward={}", ctx.playerId(), reward.getEffectiveMinutes(), reward.getCapMinutes(), reward.getBaseReward());
        return buildOfflineRewardPb(reward);
    }

    /**
     * 由离线收益快照构建下发结构 (无快照返回 null)
     */
    public OfflineReward buildOfflineRewardPb(SimOfflineReward reward) {
        if (reward == null) {
            return null;
        }
        OfflineReward rewards = new OfflineReward();
        if (reward.getBaseReward() != null && !reward.getBaseReward().isEmpty()) {
            rewards.rewards = buildRewardInfos(reward.getBaseReward());
        }
        rewards.offlineMinutes = reward.getEffectiveMinutes();
        rewards.capMinutes = reward.getCapMinutes();
        rewards.adMultiplier = reward.getAdMultiplier();
        return rewards;
    }

    /**
     * 计算离线收益 (上线时调用; 不入账, 仅生成待领取快照)
     *
     * @param offlineMs 离线总时长(ms)
     * @return 待领取离线收益; 不足 1 分钟或无产出返回 null
     */
    public SimOfflineReward computeOfflineReward(SimPlayerContext ctx, SimCasinoData casino, long offlineMs) {
        long offlineMinutes = offlineMs / TimeHelper.ONE_MINUTE_OF_MILLIS;
        if (offlineMinutes < 1) {
            return null;
        }
        //离线上限时长 (按当前经营等级)
        CasinoStatsSheetCfg statsCfg = configCache.getCasinoStatsSheetCfg(casino.getCasinoId(), casino.getCasinoLevel());
        int capMinutes = statsCfg == null ? 0 : statsCfg.getOfflineDuration();
        int effectiveMinutes = capMinutes > 0 ? (int) Math.min(offlineMinutes, capMinutes) : (int) offlineMinutes;
        if (effectiveMinutes <= 0) {
            return null;
        }
        Map<BuildingOutputType, Long> baseReward = computeDurationOutput(ctx, casino, effectiveMinutes);
        if (baseReward.isEmpty()) {
            return null;
        }
        String adMultiplier = configCache.pickAdMultiplier();
        return new SimOfflineReward(baseReward, effectiveMinutes, capMinutes, adMultiplier);
    }

    /**
     * 领取离线收益 (1倍直接领取 / 看广告领取广告倍数), 入账并清空待领取快照
     *
     * @param watchAd 是否看广告 (true 走广告倍数)
     * @return Code; 无待领取返回 PARAM_ERROR
     */
    public int claimOfflineReward(SimPlayerContext ctx, boolean watchAd) {
        SimOfflineReward reward = ctx.getPendingOffline();
        if (reward == null) {
            log.warn("领取离线收益失败, 无待领取收益 playerId={}", ctx.playerId());
            return Code.PARAM_ERROR;
        }
        SimCasinoData casino = ctx.getCurrentCasino();
        if (casino == null) {
            return Code.NOT_FOUND;
        }

        Map<BuildingOutputType, Long> finalReward = computeFinalReward(ctx, reward, watchAd);
        Map<Integer, Long> items = toItemMap(finalReward);
        if (!items.isEmpty()) {
            CommonResult<ItemOperationResult> addResult = playerPackService.addItems(ctx.playerId(), items, AddType.SIM_BUILD_OFFLINE_REWARDS, null, false);
            if (addResult == null || !addResult.success()) {
                int code = addResult == null ? Code.FAIL : addResult.code;
                log.warn("离线收益入账失败 playerId={},code={}", ctx.playerId(), code);
                return code;
            }
        }
        long casinoExp = finalReward.getOrDefault(BuildingOutputType.CASINO_LEVEL_EXP, 0L);
        long beforeCasinoExp = casino.getExp();
        simCasinoService.addCasinoExp(ctx, casinoExp);
        //仅补充 Kafka 道具流水，不改变娱乐城经验原有的发放路径。
        if (casinoExp > 0) {
            coreLogger.addItems(
                    ctx.playerId(),
                    Map.of(SimConstant.Item.CASINO_EXP, beforeCasinoExp),
                    Map.of(SimConstant.Item.CASINO_EXP, casinoExp),
                    Map.of(SimConstant.Item.CASINO_EXP, (long) casino.getExp()),
                    AddType.SIM_BUILD_OFFLINE_REWARDS,
                    "离线收益娱乐城经验");
        }
        //经营信息: 离线产出金币计入经营收益; 看广告领取计入观看广告数
        long offlineGold = finalReward.getOrDefault(BuildingOutputType.GOLD, 0L);
        ctx.getSimBaseData().addBusinessIncome(offlineGold);
        if (!items.isEmpty()) {
            allianceEventService.onBusinessIncome(ctx.playerId(), items);
        }
        if (watchAd) {
            ctx.getSimBaseData().incWatchAdCount();
            //主线任务: 观看广告一次 -> 推进 12209
            allianceEventService.onAdWatch(ctx.playerId());
        }
        //领取后重置
        ctx.setPendingOffline(null);
        log.info("领取离线收益 playerId={},watchAd={},reward={}", ctx.playerId(), watchAd, finalReward);
        return Code.SUCCESS;
    }

    /**
     * 上报当前场景各等级建筑的持有量, 推进 12208 "拥有 N 个 ≥X 级建筑"。
     * 内存仅驻留当前场景数据, 主线新手阶段玩家通常仅一座娱乐城, 故按当前场景统计。
     * 直接用 ctx 投递: 登录期补报时 ctx 尚未入 registry, 走 playerId 查找会被丢弃。
     */
    @Override
    public void reportTaskState(SimPlayerContext ctx, Consumer<ActionConditionEvent> sink) {
        reportBuildingCounts(ctx, sink);
    }

    private void reportBuildingCounts(SimPlayerContext ctx) {
        reportBuildingCounts(ctx, e -> simTaskService.onConditionEvent(ctx, e));
    }

    private void reportBuildingCounts(SimPlayerContext ctx, Consumer<ActionConditionEvent> sink) {
        SimCasinoData casino = ctx.getCurrentCasino();
        if (casino == null || casino.getBuildingData() == null || casino.getBuildingData().isEmpty()) {
            return;
        }
        SortedMap<Integer, Long> countByLevel = new TreeMap<>();
        for (BuildingData building : casino.getBuildingData().values()) {
            countByLevel.merge(building.getLevel(), 1L, Long::sum);
        }
        SimConditionEventFactory.emitOwnershipCounts(sink, ActionConditionEvent.Type.BUILDING_COUNT, 0, countByLevel);
    }

    Map<BuildingOutputType, Long> computeDurationOutput(SimPlayerContext ctx, SimCasinoData casino, int minutes) {
        return multiply(computePerMinuteOutput(ctx, casino), minutes);
    }

    /**
     * 与离线收益共用计算和协议构建，不应用离线上限和广告倍率。
     */
    public List<ItemInfo> computeRewardItems(SimPlayerContext ctx, int minutes) {
        return buildRewardInfos(computeDurationOutput(ctx, ctx.getCurrentCasino(), minutes));
    }

    /**
     * 收益协议统一使用产出对应的道具 ID，与 OfflineReward.rewards 一致。
     */
    public List<ItemInfo> buildRewardInfos(Map<BuildingOutputType, Long> rewards) {
        List<ItemInfo> items = new ArrayList<>(rewards.size());
        for (Map.Entry<BuildingOutputType, Long> en : rewards.entrySet()) {
            ItemInfo item = new ItemInfo();
            item.itemId = en.getKey().getItemId();
            item.count = en.getValue();
            items.add(item);
        }
        return items;
    }

    private Map<BuildingOutputType, Long> multiply(Map<BuildingOutputType, Long> src, long factor) {
        Map<BuildingOutputType, Long> result = new HashMap<>(src.size());
        src.forEach((k, v) -> result.put(k, v * factor));
        return result;
    }

    /**
     * 建筑产出映射为 itemId (无对应道具的产出类型不入账)
     */
    public Map<Integer, Long> toItemMap(Map<BuildingOutputType, Long> resources) {
        Map<Integer, Long> items = new HashMap<>(resources.size());
        for (Map.Entry<BuildingOutputType, Long> en : resources.entrySet()) {
            Integer itemId = toItemId(en.getKey());
            if (itemId == null) {
                continue;
            }
            items.merge(itemId, en.getValue(), Long::sum);
        }
        return items;
    }

    private Integer toItemId(BuildingOutputType type) {
        return switch (type) {
            case GOLD -> ItemUtils.getGoldItemId();
            case POWER -> SimConstant.Item.ID_POWER;
            case AWARENESS -> SimConstant.Item.ID_AWARENESS;
            default -> null;
        };
    }

    /**
     * 计算离线收益最终产出 (入账/展示统一口径): 看广告则按广告倍数放大并叠加广告金币固定值, 否则原样。
     */
    private Map<BuildingOutputType, Long> computeFinalReward(SimPlayerContext ctx, SimOfflineReward reward, boolean watchAd) {
        double multiplier = 1.0;
        if (watchAd && reward.getAdMultiplier() != null && !reward.getAdMultiplier().isEmpty()) {
            multiplier = Double.parseDouble(reward.getAdMultiplier());
        }
        Map<BuildingOutputType, Long> finalReward = scale(reward.getBaseReward(), multiplier);
        if (watchAd) {
            applyAdGoldBonus(ctx, finalReward);
        }
        return finalReward;
    }

    /**
     * 看广告领取离线收益时，对金币部分叠加徽章配置的 WATCH_ADS_ADD_GOLD 固定值。
     */
    private void applyAdGoldBonus(SimPlayerContext ctx, Map<BuildingOutputType, Long> reward) {
        int fixed = medalService.getAdGoldBonusFixed(ctx);
        if (fixed == 0) {
            return;
        }
        Long gold = reward.get(BuildingOutputType.GOLD);
        if (gold == null || gold <= 0) {
            return;
        }
        reward.put(BuildingOutputType.GOLD, gold + fixed);
    }

    /**
     * 按倍数放大 (小数倍数向下取整, 文档约定)
     */
    private Map<BuildingOutputType, Long> scale(Map<BuildingOutputType, Long> src, double multiplier) {
        Map<BuildingOutputType, Long> result = new HashMap<>(src.size());
        src.forEach((k, v) -> result.put(k, (long) Math.floor(v * multiplier)));
        return result;
    }

    /** 加载或切换场景时，补齐当前设备和装饰已经满足条件的区域。 */
    public void initializeBuildingAreas(SimCasinoData casino) {
        if (casino == null || casino.getBuildingData() == null) {
            return;
        }
        casino.getBuildingData().values().forEach(this::unlockBuildingAreas);
    }

    private void unlockBuildingAreas(BuildingData data) {
        List<BuildingUpgradeAreaCfg> areas = configCache.getBuildingUpgradeAreaCfgs(data.getId());
        if (areas.isEmpty()) {
            return;
        }
        Set<Integer> unlocked = configCache.getBuildingAreaUnlockIds(data.getId(), data.getLevel());
        for (BuildingUpgradeAreaCfg area : areas) {
            List<Integer> required = area.getUnlockEquipment();
            if (required == null || required.isEmpty() || unlocked.containsAll(required)) {
                data.getUnlockedAreaIds().add(area.getId());
            }
            if (data.getUnlockedAreaIds().contains(area.getId())
                    && area.getSkinId() != null && !area.getSkinId().isEmpty()) {
                int defaultSkinId = area.getSkinId().getFirst();
                data.getUnlockedSkinIds().add(defaultSkinId);
                data.getUsingSkinIds().putIfAbsent(area.getId(), defaultSkinId);
            }
        }
    }

    /**
     * 获取该建筑指定等级的基础值 (不含主管/雇员加成):
     * 游戏区/休息区为每分钟产出, 管理区为部门属性值。
     * 产出类型取建筑 typeValue 与 UpgradeOutput 的交集。
     * 各产出按当前等级段的起始值 + 等级差 × 同类型增量计算。
     */
    public Map<BuildingOutputType, Long> getBaseOutput(int buildingId, int level) {
        BuildingAreaTableCfg areaCfg = GameDataManager.getBuildingAreaTableCfg(buildingId);
        if (areaCfg == null) {
            return Collections.emptyMap();
        }
        List<Integer> typeValues = areaCfg.getTypeValue();
        if (typeValues == null || typeValues.isEmpty()) {
            return Collections.emptyMap();
        }
        BuildingUpgradeTableCfg cfg = configCache.getBuildingUpgradeCfg(buildingId, level);
        if (cfg == null || cfg.getUpgradeOutput() == null || cfg.getUpgradeOutput().isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Integer, Long> additions = cfg.getUpgradeOutputAdd();
        long offset = (long) level - cfg.getLevel();
        Map<BuildingOutputType, Long> map = new EnumMap<>(BuildingOutputType.class);
        for (Integer typeValue : typeValues) {
            BuildingOutputType outputType = BuildingOutputType.fromCode(typeValue);
            Long initial = cfg.getUpgradeOutput().get(typeValue);
            if (outputType == null || initial == null) {
                continue;
            }
            long addition = additions == null ? 0 : additions.getOrDefault(typeValue, 0L);
            map.put(outputType, initial + offset * addition);
        }
        return map;
    }

    private int getBuildingUnlockLangId(List<Integer> list, int index) {
        if (list == null || list.isEmpty()) {
            return 0;
        }

        if (index >= list.size()) {
            return 0;
        }
        return list.get(index);
    }

    public void gmUnlockAllBuilds(SimPlayerContext ctx) {
        for (BuildingAreaTableCfg cfg : GameDataManager.getBuildingAreaTableCfgList()) {
            BuildingData building = ctx.getCurrentCasino().findBuilding(cfg.getId());
            if (building != null) {
                continue;
            }
            unlockAndUpdateBuildData(ctx, cfg.getId(), cfg);

            ResUnlockBuilding res = new ResUnlockBuilding(Code.SUCCESS);
            res.id = cfg.getId();
            ctx.send(res);
            log.info("gm 无视条件解锁建筑 playerId={},buildId={}", ctx.playerId(), cfg.getId());
        }
    }

    public void updateCacheGuestSize(SimCasinoData casino) {
        int count = 0;
        for (BuildingData building : casino.getBuildingData().values()) {
            BuildingUpgradeTableCfg cfg = configCache.getBuildingUpgradeCfg(building.getId(), building.getLevel());
            if (cfg == null) {
                continue;
            }
            count += cfg.getMaxInteractionCount();
        }
        casino.setCacheGuestSize(count * 2);
    }

    public void unlockAndUpdateBuildData(SimPlayerContext ctx, int buildingId, BuildingAreaTableCfg cfg) {
        BuildingData data = new BuildingData();
        data.setId(buildingId);
        data.setLevel(INITIAL_LEVEL);
        ctx.getCurrentCasino().putBuilding(data);
        updateCacheGuestSize(ctx.getCurrentCasino());
        if (cfg.getUnlockGameId() > 0) {
            simCasinoService.updateCasinoUnlock(ctx, ctx.getCurrentCasino().getCasinoId(), Set.of(cfg.getUnlockGameId()));
        }
        simCasinoService.addCasinoExp(ctx, 0);
        allianceEventService.onBuildingLevel(ctx.playerId(), buildingId, data.getLevel());
        //主线任务: 新建筑改变各等级持有量 -> 上报 12208 "拥有 N 个 ≥X 级建筑"
        reportBuildingCounts(ctx);
        unlockBuildingAreas(data);
    }


}
