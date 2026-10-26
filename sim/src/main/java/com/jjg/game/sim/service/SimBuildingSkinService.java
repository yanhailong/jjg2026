package com.jjg.game.sim.service;

import com.jjg.game.alliance.service.AllianceTaskService;
import com.jjg.game.core.constant.AddType;
import com.jjg.game.core.constant.Code;
import com.jjg.game.core.constant.TaskConstant;
import com.jjg.game.core.data.CommonResult;
import com.jjg.game.core.service.GameFunctionService;
import com.jjg.game.core.service.PlayerPackService;
import com.jjg.game.core.task.manager.TaskManager;
import com.jjg.game.sampledata.GameDataManager;
import com.jjg.game.sampledata.bean.BuildingAreaTableCfg;
import com.jjg.game.sampledata.bean.BuildingSkinCfg;
import com.jjg.game.sampledata.bean.BuildingUpgradeAreaCfg;
import com.jjg.game.sampledata.bean.TaskCfg;
import com.jjg.game.season.service.SeasonLifecycleService;
import com.jjg.game.season.service.SeasonTrialService;
import com.jjg.game.sim.data.BuildingData;
import com.jjg.game.sim.data.SimCasinoData;
import com.jjg.game.sim.data.SimPlayerContext;
import com.jjg.game.sim.pb.SimPbConverter;
import com.jjg.game.sim.pb.res.ResUnlockBuildingSkin;
import com.jjg.game.sim.pb.res.ResUseBuildingSkin;
import com.jjg.game.sim.pb.struct.BuildingInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** 建筑区域皮肤的解锁与使用；皮肤 Buff 由建筑统一收益链计算。 */
@Service
public class SimBuildingSkinService {
    private static final Logger log = LoggerFactory.getLogger(SimBuildingSkinService.class);

    @Autowired private SimBuildingService buildings;
    @Autowired private SimConfigCacheService configCache;
    @Autowired private PlayerPackService packs;
    @Autowired private GameFunctionService functions;
    @Autowired private TaskManager coreTasks;
    @Autowired private SimTaskService simTasks;
    @Autowired private SimCoopTaskService coopTasks;
    @Autowired private AllianceTaskService allianceTasks;
    @Autowired private SeasonLifecycleService seasons;
    @Autowired private SeasonTrialService trialTasks;
    @Autowired private ActivePassService activePassTasks;

    public ResUnlockBuildingSkin unlock(SimPlayerContext ctx, int buildingId, int areaId, int skinId) {
        ResUnlockBuildingSkin res = new ResUnlockBuildingSkin(Code.SUCCESS);
        try {
            BuildingUpgradeAreaCfg area = findSkinArea(ctx, buildingId, areaId, skinId);
            BuildingData building = area == null ? null : ctx.getCurrentCasino().findBuilding(buildingId);
            if (building == null) {
                res.code = Code.PARAM_ERROR;
                return res;
            }
            if (!building.getUnlockedSkinIds().contains(skinId)) {
                BuildingSkinCfg skin = GameDataManager.getBuildingSkinCfg(skinId);
                Map<Integer, Long> cost = Map.of();
                //背包凭证只需拥有，优先于可能消耗材料的条件。
                if (skin.getNeedItemId() <= 0
                        || !packs.checkHasItems(ctx.getPlayer(), Map.of(skin.getNeedItemId(), 1L))) {
                    CommonResult<Map<Integer, Long>> condition = checkUnlockCondition(ctx, building, skin.getUnlockCondition());
                    res.code = condition.code;
                    if (res.code != Code.SUCCESS) {
                        return res;
                    }
                    cost = condition.data;
                }
                //新 Buff 不追溯影响此前已经累积的整分钟产出。
                if (!buildings.trySettleOnlineOutput(ctx)) {
                    res.code = Code.FAIL;
                    return res;
                }
                if (!cost.isEmpty()) {
                    var removed = packs.removeItems(ctx.getPlayer(), cost, AddType.SIM_BUILDING_SKIN_UNLOCK, null);
                    if (!removed.success()) {
                        res.code = removed.code;
                        return res;
                    }
                }
                building.getUnlockedSkinIds().add(skinId);
                ctx.setLastSaveTime(0);
            }
            res.buildingInfo = buildingInfo(ctx, building);
        } catch (Exception e) {
            log.error("解锁建筑皮肤异常 playerId={},buildingId={},areaId={},skinId={}",
                    ctx.playerId(), buildingId, areaId, skinId, e);
            res.code = Code.EXCEPTION;
        }
        return res;
    }

    public ResUseBuildingSkin use(SimPlayerContext ctx, int buildingId, int areaId, int skinId) {
        ResUseBuildingSkin res = new ResUseBuildingSkin(Code.SUCCESS);
        try {
            BuildingUpgradeAreaCfg area = findSkinArea(ctx, buildingId, areaId, skinId);
            BuildingData building = area == null ? null : ctx.getCurrentCasino().findBuilding(buildingId);
            if (building == null || !building.getUnlockedSkinIds().contains(skinId)) {
                res.code = Code.PARAM_ERROR;
                return res;
            }
            building.getUsingSkinIds().put(area.getId(), skinId);
            ctx.setLastSaveTime(0);
            res.buildingInfo = buildingInfo(ctx, building);
        } catch (Exception e) {
            log.error("使用建筑皮肤异常 playerId={},buildingId={},areaId={},skinId={}",
                    ctx.playerId(), buildingId, areaId, skinId, e);
            res.code = Code.EXCEPTION;
        }
        return res;
    }

    private BuildingUpgradeAreaCfg findSkinArea(SimPlayerContext ctx, int buildingId, int areaId, int skinId) {
        SimCasinoData casino = ctx.getCurrentCasino();
        BuildingData building = casino == null ? null : casino.findBuilding(buildingId);
        BuildingUpgradeAreaCfg area = configCache.getBuildingUpgradeAreaCfgs(buildingId).stream()
                .filter(cfg -> cfg.getAreaId() == areaId).findFirst().orElse(null);
        if (building == null || area == null || area.getBuildingId() != buildingId
                || (area.getCasinId() > 0 && area.getCasinId() != casino.getCasinoId())
                || area.getSkinId() == null || !area.getSkinId().contains(skinId)
                || GameDataManager.getBuildingSkinCfg(skinId) == null) {
            return null;
        }
        buildings.initializeBuildingAreas(casino);
        return building.getUnlockedAreaIds().contains(area.getId()) ? area : null;
    }

    private CommonResult<Map<Integer, Long>> checkUnlockCondition(
            SimPlayerContext ctx, BuildingData building, List<Integer> condition) {
        if (condition == null || condition.isEmpty()) {
            return new CommonResult<>(Code.FORBID);
        }
        int type = condition.getFirst();
        if (type == 1) {
            return new CommonResult<>(Code.SUCCESS, Map.of());
        }
        if (condition.size() < 2) {
            return new CommonResult<>(Code.PARAM_ERROR);
        }
        int parameter = condition.get(1);
        if (type == 2) {
            if (condition.size() != 3 || parameter <= 0 || condition.get(2) <= 0) {
                return new CommonResult<>(Code.PARAM_ERROR);
            }
            return new CommonResult<>(Code.SUCCESS, Map.of(parameter, condition.get(2).longValue()));
        }
        boolean satisfied = switch (type) {
            case 3 -> building.getLevel() >= parameter;
            case 4 -> ctx.getCurrentCasino().getCasinoLevel() >= parameter;
            case 5 -> functions.checkGameFunctionOpen(ctx.getPlayer(),
                    GameDataManager.getGameFunctionCfg(parameter), true, false);
            case 6 -> hasRewardedTask(ctx, parameter);
            default -> false;
        };
        return new CommonResult<>(satisfied ? Code.SUCCESS : Code.FORBID, Map.of());
    }

    private boolean hasRewardedTask(SimPlayerContext ctx, int taskId) {
        TaskCfg task = GameDataManager.getTaskCfg(taskId);
        if (task == null) {
            return false;
        }
        return switch (task.getTaskType()) {
            case TaskConstant.TaskType.POINTS_AWARD -> coreTasks.hasRewardedTask(ctx.playerId(), taskId);
            case TaskConstant.TaskType.MAIN_LINE, TaskConstant.TaskType.ACHIEVEMENT ->
                    simTasks.hasRewardedTask(ctx, taskId);
            case TaskConstant.TaskType.COOP -> coopTasks.hasRewardedTask(ctx, taskId);
            case TaskConstant.TaskType.ALLIANCE -> allianceTasks.hasRewardedTask(ctx.playerId(), taskId);
            case TaskConstant.TaskType.SEASON_TRIAL -> seasons.ensureCurrent(ctx, System.currentTimeMillis()) != null
                    && trialTasks.hasRewardedTask(ctx, taskId);
            case TaskConstant.TaskType.ACTIVE_PASS_DAILY, TaskConstant.TaskType.ACTIVE_PASS_PERIOD ->
                    activePassTasks.hasRewardedTask(ctx, taskId);
            default -> false;
        };
    }

    private BuildingInfo buildingInfo(SimPlayerContext ctx, BuildingData building) {
        BuildingAreaTableCfg area = GameDataManager.getBuildingAreaTableCfg(building.getId());
        return SimPbConverter.toBuildingInfo(ctx, configCache, building,
                configCache.getBuildingUpgradeCfg(building.getId(), building.getLevel()),
                area.getUnlockGameId(), System.currentTimeMillis());
    }
}
