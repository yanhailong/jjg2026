package com.jjg.game.sim.pb;

import com.jjg.game.common.utils.CommonUtil;
import com.jjg.game.core.pb.KVInfo;
import com.jjg.game.core.pb.KVLongInfo;
import com.jjg.game.core.utils.ItemUtils;
import com.jjg.game.sampledata.GameDataManager;
import com.jjg.game.sampledata.bean.*;
import com.jjg.game.sim.constant.BuildingType;
import com.jjg.game.sim.constant.ServerBuildingType;
import com.jjg.game.sim.data.*;
import com.jjg.game.sim.pb.struct.*;
import com.jjg.game.sim.service.SimBuildingService;
import com.jjg.game.sim.service.SimConfigCacheService;
import com.jjg.game.sim.service.SimEmployeeAttributeService;
import com.jjg.game.sim.service.SimEmployeeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据 → 协议 转换工具
 *
 * @author 11
 * @date 2026/5/26
 */
public final class SimPbConverter {

    private static final Logger log = LoggerFactory.getLogger(SimPbConverter.class);

    private SimPbConverter() {
    }

    /**
     * GuestData + destinations → GuestInfo
     */
    public static GuestInfo toGuestInfo(GuestData guestData, List<DestinationInfo> destinations, VisitorQuestCfg visitorQuestCfg) {
        GuestInfo info = new GuestInfo();
        info.id = visitorQuestCfg.getResource() > 0 ? visitorQuestCfg.getResource() : guestData.getId();
        info.destinations = destinations;
        return info;
    }

    /**
     * PurchasedGuestData → GuestInfo (uid + 目的地; 用于生成下发 / 重连补发, 目的地已含预生成奖励)
     */
    public static GuestInfo toGuestInfo(PurchasedGuestData data, VisitorQuestCfg visitorQuestCfg) {
        GuestInfo info = new GuestInfo();
        info.id = visitorQuestCfg.getResource() > 0 ? visitorQuestCfg.getResource() : data.getGuestId();
        info.uid = data.getUid();
        if (data.getDestinations() != null && !data.getDestinations().isEmpty()) {
            info.destinations = data.getDestinations().values().stream().toList();
        }
        return info;
    }

    public static BuildingInfo toBuildingInfo(SimPlayerContext ctx, SimConfigCacheService simConfigCacheService,
                                              BuildingData buildingData, BuildingUpgradeTableCfg currentLevelCfg, int unlockGameId, long now) {
        return toBuildingInfo(ctx, simConfigCacheService, buildingData, currentLevelCfg, unlockGameId, now,
                CommonUtil.getContext().getBean(SimEmployeeAttributeService.class).snapshot(ctx));
    }

    private static BuildingInfo toBuildingInfo(SimPlayerContext ctx, SimConfigCacheService simConfigCacheService,
                                               BuildingData buildingData, BuildingUpgradeTableCfg currentLevelCfg,
                                               int unlockGameId, long now,
                                               SimEmployeeAttributeService.Snapshot attributes) {
        BuildingInfo info = new BuildingInfo();
        info.id = buildingData.getId();
        info.level = buildingData.getLevel();
        info.guestRewards = ItemUtils.buildItemInfo(buildingData.getGuestRewards());
        BuildingAreaTableCfg buildingAreaTableCfg = GameDataManager.getBuildingAreaTableCfg(buildingData.getId());
        info.maxLevel = buildingAreaTableCfg.getMaxLevel();
        BuildingUpgradeTableCfg nextRewardCfg = simConfigCacheService.getNextBuildingUpgradeRewardCfg(info.id, info.level);
        info.targetLevel = nextRewardCfg == null ? 0 : nextRewardCfg.getLevel();
        info.nextRewards = nextRewardCfg == null ? List.of() : ItemUtils.buildItemInfo(nextRewardCfg.getUpgradeReward());
        info.buildingUpgrade1ConditonInfo = toBuildingUpgradeConditonInfo(simConfigCacheService, buildingData, 1);
        info.buildingUpgrade10ConditonInfo = info.level >= buildingAreaTableCfg.getUnlockUp10Level()
                ? toBuildingUpgradeConditonInfo(simConfigCacheService, buildingData, 10) : null;

        //交互次数
//        info.interactCount = buildingData.getReceptCount();
        int operateBuildId = simConfigCacheService.getCasinoManageBuildId(ctx.getCurrentCasino().getCasinoId(), ServerBuildingType.OPERATIONS);
        if (operateBuildId == info.id) {
            //交互次数
            if (buildingData.getId() == operateBuildId) {
                CasinoStatsSheetCfg casinoCfg = simConfigCacheService.getCasinoStatsSheetCfg(
                        ctx.getCurrentCasino().getCasinoId(), ctx.getCurrentCasino().getCasinoLevel());
                //运营看板每分钟获客量
//                info.interactCount = Math.toIntExact(CommonUtil.getContext().getBean(SimOperationDashboardService.class).customerAcquisitionPerMinute(ctx, casinoCfg, ctx.getCurrentCasino(), System.currentTimeMillis()));
            }
        }

        info.bonusInfos = new ArrayList<>();
        SimBuildingService buildingService = CommonUtil.getContext().getBean(SimBuildingService.class);
        buildingService.computeDashboardBuildingValues(ctx, buildingData, attributes, info.bonusInfos);

        info.areaSkinInfos = new ArrayList<>();
        for (BuildingUpgradeAreaCfg area : simConfigCacheService.getBuildingUpgradeAreaCfgs(buildingData.getId())) {
            BuildingAreaSkinInfo skins = new BuildingAreaSkinInfo();
            skins.areaId = area.getAreaId();
            skins.areaLanguageId = area.getLanguageId();
            skins.unlocked = buildingData.getUnlockedAreaIds().contains(area.getId());
            List<Integer> skinIds = area.getSkinId() == null ? List.of() : area.getSkinId();
            skins.skinOriginInfos = skinIds.stream().map(id -> toSkinOriginInfo(buildingData, id)).toList();
            int using = buildingData.getUsingSkinIds().getOrDefault(area.getId(), 0);
            skins.usingSkinId = skinIds.contains(using) && buildingData.getUnlockedSkinIds().contains(using) ? using : 0;
            info.areaSkinInfos.add(skins);
        }

        if (buildingAreaTableCfg.getType() == BuildingType.GAME.code() || buildingAreaTableCfg.getType() == BuildingType.REST.code()) {
            for (BonusInfo bonusInfo : info.bonusInfos) {
                bonusInfo.baseValue *= 60L;
                bonusInfo.totalValue *= 60L;
                for (KVLongInfo bonus : bonusInfo.fixedValueAddList) {
                    bonus.value *= 60L;
                }
            }
        }
        return info;
    }

    private static SkinOriginInfo toSkinOriginInfo(BuildingData building, int skinId) {
        BuildingSkinCfg cfg = GameDataManager.getBuildingSkinCfg(skinId);
        SkinOriginInfo info = new SkinOriginInfo();
        info.id = skinId;
        info.languageId = cfg.getLanguageId();
        info.skinLanguageId = cfg.getSkinLanguageId();
        info.unlock = building.getUnlockedSkinIds().contains(skinId);
        info.unlockTypeItems = List.of();
        info.needItems = cfg.getNeedItemId() > 0
                ? List.of(ItemUtils.buildItemInfo(cfg.getNeedItemId(), 1L)) : List.of();
        List<Integer> condition = cfg.getUnlockCondition();
        if (condition != null && !condition.isEmpty()) {
            info.unlockType = condition.getFirst();
            if (info.unlockType == 2 && condition.size() == 3) {
                info.unlockTypeItems = List.of(ItemUtils.buildItemInfo(condition.get(1), condition.get(2).longValue()));
            } else if (info.unlockType >= 3 && info.unlockType <= 6 && condition.size() > 1) {
                info.unlockTypeValue = condition.get(1);
            }
        }
        return info;
    }

    private static BuildingUpgradeConditonInfo toBuildingUpgradeConditonInfo(SimConfigCacheService config,
                                                                            BuildingData building, int levelCount) {
        int targetLevel = Math.min(config.getBuildingMaxLevel(building.getId()), building.getLevel() + levelCount);
        if (targetLevel <= building.getLevel()) {
            return null;
        }
        BuildingUpgradeConditonInfo info = new BuildingUpgradeConditonInfo();
        Map<Integer, Long> totalCost = new HashMap<>();
        //与实际升级一致，每一级按升级前等级选档，跨档时累计消耗并取最高场景等级要求。
        for (int level = building.getLevel(); level < targetLevel; level++) {
            BuildingUpgradeTableCfg cfg = config.getBuildingUpgradeCfg(building.getId(), level);
            if (cfg == null || cfg.getCostPerLevel() == null || cfg.getCostPerLevel().isEmpty()) {
                return null;
            }
            info.needLevel = Math.max(info.needLevel, cfg.getNeedLevel());
            cfg.getCostPerLevel().forEach((id, count) -> totalCost.merge(id, count, Long::sum));
        }
        info.needItems = ItemUtils.buildItemInfo(totalCost);
        return info;
    }

    public static EmployeeInfo toEmployeeInfo(SimEmployeeData data) {
        EmployeeInfo info = new EmployeeInfo();
        info.id = data.getEmployeeId();
        info.level = data.getLevel();
        info.star = data.getStar();
        info.skills = CommonUtil.getContext().getBean(SimEmployeeService.class).skillInfos(data);
        return info;
    }

    public static KVInfo toManageEmpInfo(int buildingType, int id) {
        KVInfo info = new KVInfo();
        info.key = buildingType;
        info.value = id;
        return info;
    }

    /**
     * SimSkillsData → GameSkills
     */
    public static GameSkills toGameSkills(SimSkillsData data) {
        GameSkills gs = new GameSkills();
        gs.gameType = data.getGameType();

        if (data.getSkillsMap() != null && !data.getSkillsMap().isEmpty()) {
            gs.skillInfos = new ArrayList<>();
            for (Map.Entry<Integer, SkillDetailData> en : data.getSkillsMap().entrySet()) {
                SkillInfo info = new SkillInfo();
                info.propId = en.getKey();
                info.level = en.getValue().getLevel();
                info.addOutPut = en.getValue().getAddOutPut();
                gs.skillInfos.add(info);
            }
        }
        return gs;
    }

    /**
     * 场景建筑列表 -> 协议结构 (无建筑返回 null)
     */
    public static List<BuildingInfo> toBuildingInfos(SimPlayerContext ctx, SimConfigCacheService simConfigCacheService) {
        if (ctx.getCurrentCasino() == null || ctx.getCurrentCasino().getBuildingData() == null || ctx.getCurrentCasino().getBuildingData().isEmpty()) {
            return null;
        }
        List<BuildingInfo> list = new ArrayList<>(ctx.getCurrentCasino().getBuildingData().size());
        long now = System.currentTimeMillis();
        SimEmployeeAttributeService.Snapshot attributes = CommonUtil.getContext()
                .getBean(SimEmployeeAttributeService.class).snapshot(ctx);
        for (BuildingData b : ctx.getCurrentCasino().getBuildingData().values()) {
            BuildingAreaTableCfg buildingAreaTableCfg = GameDataManager.getBuildingAreaTableCfg(b.getId());
            if(buildingAreaTableCfg == null){
                log.warn("获取建筑配置失败 playerId={},id={}",ctx.getPlayer().getId(),b.getId());
                continue;
            }
            BuildingUpgradeTableCfg buildingUpgradeCfg = simConfigCacheService.getBuildingUpgradeCfg(b.getId(), b.getLevel());
            list.add(toBuildingInfo(ctx, simConfigCacheService, b, buildingUpgradeCfg,
                    buildingAreaTableCfg.getUnlockGameId(), now, attributes));
        }
        return list;
    }

    /**
     * 场景主管列表 -> 协议结构 (无主管返回 null)
     */
    public static List<KVInfo> toManagerInfos(SimCasinoData casino) {
        if (casino == null || casino.getManagerEmployMap() == null || casino.getManagerEmployMap().isEmpty()) {
            return null;
        }
        List<KVInfo> list = new ArrayList<>(casino.getManagerEmployMap().size());
        for (Map.Entry<Integer, Integer> en : casino.getManagerEmployMap().entrySet()) {
            list.add(SimPbConverter.toManageEmpInfo(en.getKey(), en.getValue()));
        }
        return list;
    }
}
