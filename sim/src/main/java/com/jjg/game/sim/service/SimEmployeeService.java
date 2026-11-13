package com.jjg.game.sim.service;

import com.jjg.game.alliance.service.AllianceEventService;
import com.jjg.game.common.utils.WeightRandom;
import com.jjg.game.core.base.condition.numeric.ActionConditionEvent;
import com.jjg.game.core.constant.AddType;
import com.jjg.game.core.constant.Code;
import com.jjg.game.core.data.CommonResult;
import com.jjg.game.core.data.ItemOperationResult;
import com.jjg.game.core.pb.KVLongInfo;
import com.jjg.game.core.service.PlayerPackService;
import com.jjg.game.core.utils.ItemUtils;
import com.jjg.game.sampledata.GameDataManager;
import com.jjg.game.sampledata.bean.*;
import com.jjg.game.sim.constant.SimConstant;
import com.jjg.game.sim.dao.SimEmployeeDao;
import com.jjg.game.sim.data.SimEmployeeData;
import com.jjg.game.sim.data.SimPlayerContext;
import com.jjg.game.sim.listener.SimTaskStateReporter;
import com.jjg.game.sim.pb.res.*;
import com.jjg.game.sim.pb.struct.EmployDetailInfo;
import com.jjg.game.sim.pb.struct.EmployeeSkillInfo;
import com.jjg.game.sim.pb.struct.RecruitItemInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Consumer;

/**
 * 雇员服务: 招募(解锁)、升级、升星、任命主管
 *
 * @author 11
 * @date 2026/5/28
 */
@Service
public class SimEmployeeService implements SimTaskStateReporter {
    private static final Logger log = LoggerFactory.getLogger(SimEmployeeService.class);

    //雇员初始等级 / 星级
    private static final int INITIAL_LEVEL = 1;
    private static final int INITIAL_STAR = 1;
    private static final int FIRST_UNIVERSAL_SHARD_ITEM_ID = 1350101;
    private static final int LAST_UNIVERSAL_SHARD_ITEM_ID = 1350105;

    @Autowired
    private SimConfigCacheService configCache;
    @Autowired
    private SimEmployeeAttributeService attributeService;
    @Autowired
    private SimEmployeeDao simEmployeeDao;
    @Autowired
    private PlayerPackService playerPackService;
    @Autowired
    private AllianceEventService allianceEventService;
    @Autowired
    private SimGuideConfigService guideConfigService;
    //懒加载打破与 SimTaskService 的循环依赖 (对方持有本服务作状态补报口)
    @Autowired
    @Lazy
    private SimTaskService simTaskService;
    @Autowired
    private SimEmployeeRedDotService employeeRedDotService;

    /**
     * 招募雇员 (卡池抽取):
     * 命中未拥有的雇员则解锁, 命中已拥有的雇员则转化为对应碎片
     *
     * @param ctx
     * @param poolId 卡池id
     * @param count  招募次数 (1/10)
     */
    public void onRecruitEmployee(SimPlayerContext ctx, int poolId, int count) {
        ResRecruitEmployee res = new ResRecruitEmployee(Code.SUCCESS);
        try {
            if (count != 1 && count != 10) {
                log.warn("招募雇员失败,次数参数错误 playerId={},count={}", ctx.playerId(), count);
                res.code = Code.PARAM_ERROR;
                ctx.send(res);
                return;
            }

            PoolListCfg poolCfg = configCache.getOpenPoolCfg(poolId, SimConstant.PoolList.TYPE_EMPLOYEE);
            if (poolCfg == null) {
                log.warn("招募雇员失败,卡池不存在、未开启、不在开放时间或类型错误 playerId={},poolId={},count={}", ctx.playerId(), poolId, count);
                res.code = Code.PARAM_ERROR;
                ctx.send(res);
                return;
            }

            WeightRandom<List<Integer>> poolRand = configCache.getEmployeePoolRand(poolCfg.getDropItem());
            if (poolRand == null) {
                log.warn("招募雇员失败,获取卡池权重失败 playerId={},count={},poolId={}", ctx.playerId(), count, poolCfg.getId());
                res.code = Code.PARAM_ERROR;
                ctx.send(res);
                return;
            }

            if (poolCfg.getDrawCost() != null && !poolCfg.getDrawCost().isEmpty()) {
                Map<Integer, Long> costMap = poolCfg.getDrawCost();
                if (count > 1) {
                    costMap = new HashMap<>();
                    for (Map.Entry<Integer, Long> en : poolCfg.getDrawCost().entrySet()) {
                        costMap.put(en.getKey(), en.getValue() * count);
                    }
                }
                boolean remove = playerPackService.removeItems(ctx.getPlayer(), costMap, AddType.SIM_EMPLOYEE_RECRUIT, null).success();
                if (!remove) {
                    log.warn("招募雇员失败,扣除道具失败 playerId={},count={},poolId={}", ctx.playerId(), count, poolCfg.getId());
                    res.code = Code.PARAM_ERROR;
                    ctx.send(res);
                    return;
                }
            }

            EmployeePoolCfg employeePoolCfg = GameDataManager.getEmployeePoolCfg(poolCfg.getDropItem());
            List<Integer> newbieGuideDraw = employeePoolCfg == null ? null : employeePoolCfg.getNewbieGuideDraw();
            int guideItemId = guideConfigService.newbieFixedDrawItemId(ctx.getSimBaseData(), newbieGuideDraw);

            Map<Integer, Long> addAllItems = new HashMap<>();
            List<RecruitItemInfo> recruitItems = new ArrayList<>();

            Map<Integer, Integer> addEmployee = new HashMap<>();
            Map<Integer, Long> recruitedProfessions = new HashMap<>();
            for (int i = 0; i < count; i++) {
                int drawItemId;
                int rewardCount;
                if (guideItemId > 0) {
                    drawItemId = guideItemId;
                    rewardCount = 1;
                } else {
                    List<Integer> next = poolRand.next();
                    if (next == null || next.size() < 3) {
                        log.warn("招募雇员失败,卡池掉落配置异常 playerId={},count={},i={}", ctx.playerId(), count, i);
                        return;
                    }
                    drawItemId = next.get(1);
                    rewardCount = next.get(2);
                }

                EmployeeProfileCfg profileCfg = configCache.getEmployeeProfileCfgByItemId(drawItemId);
                if (profileCfg == null) {
                    log.warn("招募雇员失败,根据itemId获取EmployeeProfileCfg失败 playerId={},count={},itemId={},i={}", ctx.playerId(), count, drawItemId, i);
                    return;
                }

                for (int j = 0; j < rewardCount; j++) {
                    recruitedProfessions.merge(profileCfg.getProfessionID(), 1L, Long::sum);
                    RecruitItemInfo re = new RecruitItemInfo();
                    re.itemId = profileCfg.getDuplicatetoShard().get(0);
                    re.count = profileCfg.getDuplicatetoShard().get(2);

                    if (ctx.getEmployee(profileCfg.getId()) == null) {
                        unlockEmployee(ctx, profileCfg);

                        addEmployee.merge(profileCfg.getId(), 1, Integer::sum);
                    } else { //已拥有,转化成碎片
                        List<Integer> tmpList = profileCfg.getDuplicatetoShard();
                        if (tmpList != null && !tmpList.isEmpty()) {
                            int itemId = tmpList.get(1);
                            long itemCount = tmpList.get(2).longValue();

                            addAllItems.merge(itemId, itemCount, Long::sum);
                            re.breakDown = true;
                        }
                    }
                    recruitItems.add(re);
                }
            }

            if (!addAllItems.isEmpty()) {
                CommonResult<ItemOperationResult> simItemOperationResultCommonResult = playerPackService.addItems(ctx.playerId(), addAllItems, AddType.SIM_GUEST_RECRUIT, count + "", false);
                if (!simItemOperationResultCommonResult.success()) {
                    log.warn("招募雇员后添加碎片道具失败 playerId={},count={},code={}", ctx.playerId(), count, simItemOperationResultCommonResult.code);
                    res.code = simItemOperationResultCommonResult.code;
                    ctx.send(res);
                    return;
                }
            }

            res.shardInfos = recruitItems;
            addEmployee.keySet().forEach(id -> employeeRedDotService.recordNewContent(
                    ctx, SimEmployeeRedDotService.NEW_EMPLOYEE, id));
            //联盟任务: 卡池抽奖次数 (param=卡池ID, 供 0=任意/指定卡池 过滤; 10 连计为 10 次)
            allianceEventService.onCardPoolDraw(ctx.playerId(), poolCfg.getId(), count);
            allianceEventService.onEmployeePoolDraw(ctx.playerId(), count);
            recruitedProfessions.forEach((professionId, recruited) ->
                    allianceEventService.onEmployeeRecruit(ctx.playerId(), professionId, recruited));
            employeeRedDotService.updateRedDots(ctx.playerId(),
                    SimConstant.Employee.RED_DOT_RECRUIT_POOL,
                    SimConstant.Employee.RED_DOT_EMPLOYEE_GROWTH);
            //主线任务: 招募改变各职业各星级持有量 -> 上报 12212 "拥有 N 个 X 星雇员"
            reportEmployeeCounts(ctx);
            log.info("招募雇员成功 playerId={},count={},newEmployee={},addAllItems={}", ctx.playerId(), count, addEmployee, addAllItems);
        } catch (Exception e) {
            log.error("", e);
            res.code = Code.EXCEPTION;
        }
        ctx.send(res);
    }


    /**
     * 升级雇员
     */
    public void onUpgradeEmployee(SimPlayerContext ctx, int employeeId, int levelCount) {
        ResUpgradeEmployee res = new ResUpgradeEmployee(Code.SUCCESS);
        res.employeeId = employeeId;
        try {
            SimEmployeeData data = ctx.getEmployee(employeeId);
            if (data == null) {
                log.warn("升级雇员失败, 未解锁 playerId={},employeeId={},levelCount={}", ctx.playerId(), employeeId, levelCount);
                res.code = Code.NOT_FOUND;
                ctx.send(res);
                return;
            }

            if (levelCount < 1) {
                levelCount = 1;
            } else if (levelCount > 10) {
                levelCount = 10;
            }

            int level = data.getLevel();
            int targetLevel = level + levelCount;
            EmployeeStarCfg starCfg = getStarCfg(employeeId, data.getStar());
            int levelCap = starCfg == null ? 0 : starCfg.getLevelCap();
            if (levelCap > 0 && targetLevel > levelCap) {
                log.warn("升级雇员失败, 目标等级超过星级上限 playerId={},employeeId={},level={},targetLevel={},cap={}",
                        ctx.playerId(), employeeId, level, targetLevel, levelCap);
                res.code = Code.PARAM_ERROR;
                ctx.send(res);
                return;
            }
            Map<Integer, Long> cost = configCache.getEmployeeUpgradeCost(employeeId, level, targetLevel);
            if (cost == null) {
                log.warn("升级雇员失败, 等级配置不存在或目标等级超过配置上限 playerId={},employeeId={},level={},targetLevel={}",
                        ctx.playerId(), employeeId, level, targetLevel);
                res.code = Code.LEVEL_MAX;
                ctx.send(res);
                return;
            }

            Map<Integer, Long> changeEndItemNum = null;
            if (!cost.isEmpty()) {
                CommonResult<ItemOperationResult> removeResult = playerPackService.removeItems(ctx.getPlayer(), cost, AddType.SIM_EMPLOYEE_LEVEL_UP, null);
                if (!removeResult.success()) {
                    log.warn("升级雇员失败, 扣除道具失败 playerId={},employeeId={},level={},targetLevel={},cost={},code={}",
                            ctx.playerId(), employeeId, level, targetLevel, cost, removeResult.code);
                    res.code = Code.NOT_ENOUGH_ITEM;
                    ctx.send(res);
                    return;
                }
                changeEndItemNum = removeResult.data.getChangeEndItemNum();
                data.addLevelUpCost(cost);
            }

            data.setLevel(targetLevel);
            res.level = targetLevel;
            simTaskService.onConditionEvent(ctx, new ActionConditionEvent(ActionConditionEvent.Type.EMPLOYEE_LEVEL,
                    employeeId, 0, targetLevel, 0, 0, false));
            employeeRedDotService.updateRedDots(ctx.playerId(),
                    SimConstant.Employee.RED_DOT_EMPLOYEE_GROWTH);
            log.info("升级雇员成功 playerId={},employeeId={},level={},newLevel={},changeEndItemNum={}",
                    ctx.playerId(), employeeId, level, targetLevel, changeEndItemNum);
        } catch (Exception e) {
            log.error("升级雇员异常 playerId={},employeeId={},levelCount={}", ctx.playerId(), employeeId, levelCount, e);
            res.code = Code.EXCEPTION;
        }
        ctx.send(res);
    }

    /**
     * 重置雇员等级，返还本轮升级实际扣除的道具。
     */
    public ResResetEmployeeLevel resetEmployeeLevel(SimPlayerContext ctx, int employeeId) {
        ResResetEmployeeLevel res = new ResResetEmployeeLevel(Code.SUCCESS);
        res.employeeId = employeeId;
        try {
            SimEmployeeData data = ctx.getEmployee(employeeId);
            if (data == null) {
                log.warn("重置雇员等级失败, 雇员未解锁 playerId={},employeeId={}", ctx.playerId(), employeeId);
                res.code = Code.NOT_FOUND;
                return res;
            }
            int level = data.getLevel();
            res.level = level;
            if (level == INITIAL_LEVEL) {
                return res;
            }

            Map<Integer, Long> refund = data.getLevelUpCost();
            if (!refund.isEmpty()) {
                for (int itemId : refund.keySet()) {
                    if (GameDataManager.getItemCfg(itemId) == null) {
                        log.error("重置雇员等级失败, 缺少返还道具配置 playerId={},employeeId={},level={},itemId={},count={}",
                                ctx.playerId(), employeeId, level, itemId, refund.get(itemId));
                        res.code = Code.NOT_FOUND;
                        return res;
                    }
                }
                CommonResult<ItemOperationResult> added = playerPackService.addItems(ctx.playerId(), refund,
                        AddType.SIM_EMPLOYEE_LEVEL_RESET, "employeeId=" + employeeId);
                if (!added.success()) {
                    log.warn("重置雇员等级失败, 道具返还失败 playerId={},employeeId={},level={},refund={},code={}",
                            ctx.playerId(), employeeId, level, refund, added.code);
                    res.code = added.code;
                    return res;
                }
            }

            data.setLevel(INITIAL_LEVEL);
            data.clearLevelUpCost();
            res.level = INITIAL_LEVEL;
            res.refundItems = ItemUtils.buildItemInfo(refund);
            ctx.setEmployeeRedDotDirty(true);
            int maxLevel = ctx.getEmployeeMap().values().stream().mapToInt(SimEmployeeData::getLevel)
                    .max().orElse(INITIAL_LEVEL);
            if (maxLevel < level) {
                simTaskService.onConditionEvent(ctx, new ActionConditionEvent(ActionConditionEvent.Type.EMPLOYEE_LEVEL,
                        0, 0, maxLevel, 0, 0, false));
            }
            log.info("重置雇员等级成功 playerId={},employeeId={},level={},refund={}",
                    ctx.playerId(), employeeId, level, refund);
        } catch (Exception e) {
            log.error("重置雇员等级异常 playerId={},employeeId={}", ctx.playerId(), employeeId, e);
            res.code = Code.EXCEPTION;
        }
        return res;
    }

    /**
     * 每个雇员独立升级技能，仅当前等级配置参与效果计算。
     */
    public void onUpgradeEmployeeSkill(SimPlayerContext ctx, int employeeId, int groupId) {
        ResUpgradeEmployeeSkill res = new ResUpgradeEmployeeSkill(Code.SUCCESS);
        res.employeeId = employeeId;
        res.groupId = groupId;
        try {
            SimEmployeeData data = ctx.getEmployee(employeeId);
            if (data == null) {
                res.code = Code.NOT_FOUND;
                ctx.send(res);
                return;
            }
            Integer level = data.getSkillLevels().get(groupId);
            EmployeeSkillConfigCfg current = level == null ? null : configCache.getEmployeeSkillCfg(groupId, level);
            if (current == null) {
                res.code = Code.PARAM_ERROR;
                ctx.send(res);
                return;
            }
            Map<Integer, Integer> upCost = current.getUpCost();
            if (upCost == null || upCost.isEmpty()) {
                res.code = Code.LEVEL_MAX;
                ctx.send(res);
                return;
            }
            EmployeeSkillConfigCfg next = configCache.getEmployeeSkillCfg(groupId, level + 1);
            if (next == null) {
                log.warn("升级雇员技能失败, 下一等级配置不存在 playerId={},employeeId={},groupId={},level={}",
                        ctx.playerId(), employeeId, groupId, level);
                res.code = Code.PARAM_ERROR;
                ctx.send(res);
                return;
            }
            Map<Integer, Long> cost = new HashMap<>(upCost.size());
            upCost.forEach((itemId, count) -> cost.put(itemId, count.longValue()));
            CommonResult<ItemOperationResult> removed = playerPackService.removeItems(ctx.getPlayer(), cost,
                    AddType.SIM_EMPLOYEE_SKILL_UPGRADE, null);
            if (!removed.success()) {
                res.code = removed.code;
                ctx.send(res);
                return;
            }
            data.getSkillLevels().put(groupId, next.getSkillLevel());
            SimEmployeeAttributeService.Snapshot snapshot = attributeService.snapshot(ctx);
            res.employee = toEmployeeDetail(ctx, data, snapshot);
            res.totalCombatPower = snapshot.totalCombatPower();
            log.info("升级雇员技能成功 playerId={},employeeId={},groupId={},level={}",
                    ctx.playerId(), employeeId, groupId, next.getSkillLevel());
        } catch (Exception e) {
            log.error("升级雇员技能异常 playerId={},employeeId={},groupId={}", ctx.playerId(), employeeId, groupId, e);
            res.code = Code.EXCEPTION;
        }
        ctx.send(res);
    }

    public ResEmployeeExcFragments exchangeEmployeeFragments(SimPlayerContext ctx, int employeeId, long count) {
        try {
            if (employeeId <= 0 || count <= 0) {
                log.warn("兑换雇员碎片失败, 参数错误 playerId={},employeeId={},count={}", ctx.playerId(), employeeId, count);
                return new ResEmployeeExcFragments(Code.PARAM_ERROR);
            }
            if (ctx.getEmployee(employeeId) == null) {
                log.warn("兑换雇员碎片失败, 雇员未解锁 playerId={},employeeId={},count={}", ctx.playerId(), employeeId, count);
                return new ResEmployeeExcFragments(Code.NOT_FOUND);
            }
            int shardItemId = getEmployeeShardItemId(employeeId);
            int universalShardId = getUniversalShardItemId(shardItemId);
            if (universalShardId == 0) {
                log.warn("兑换雇员碎片失败, 专属碎片配置或同品质万能碎片缺失 playerId={},employeeId={},shardItemId={},count={}",
                        ctx.playerId(), employeeId, shardItemId, count);
                return new ResEmployeeExcFragments(Code.PARAM_ERROR);
            }
            CommonResult<ItemOperationResult> exchanged = playerPackService.exchangePackItems(ctx.getPlayer(), Map.of(),
                    Map.of(universalShardId, count), Map.of(shardItemId, count), AddType.ITEM_EXCHANGE, "雇员碎片兑换");
            if (!exchanged.success()) {
                log.warn("兑换雇员碎片失败, 背包兑换失败 playerId={},employeeId={},shardItemId={},universalShardId={},count={},code={}",
                        ctx.playerId(), employeeId, shardItemId, universalShardId, count, exchanged.code);
            }
            return new ResEmployeeExcFragments(exchanged.code);
        } catch (Exception e) {
            log.error("兑换雇员碎片异常 playerId={},employeeId={},count={}", ctx.playerId(), employeeId, count, e);
            return new ResEmployeeExcFragments(Code.EXCEPTION);
        }
    }

    /**
     * 升星雇员
     */
    public void onStarUpEmployee(SimPlayerContext ctx, int employeeId, boolean auto) {
        ResStarUpEmployee res = new ResStarUpEmployee(Code.SUCCESS);
        res.employeeId = employeeId;
        try {
            SimEmployeeData data = ctx.getEmployee(employeeId);
            if (data == null) {
                log.warn("升星雇员失败, 未解锁 playerId={},employeeId={}", ctx.playerId(), employeeId);
                res.code = Code.NOT_FOUND;
                ctx.send(res);
                return;
            }
            EmployeeStarCfg curCfg = getStarCfg(employeeId, data.getStar());
            if (curCfg == null || curCfg.getStarUpCost() <= 0) {
                log.warn("升星雇员失败, 已达星级上限 playerId={},employeeId={},star={}", ctx.playerId(), employeeId, data.getStar());
                res.code = Code.PARAM_ERROR;
                ctx.send(res);
                return;
            }

            int nextStar = data.getStar() + 1;
            EmployeeStarCfg nextCfg = getStarCfg(employeeId, nextStar);
            if (nextCfg == null) {
                log.warn("升星雇员失败, 目标星级配置不存在 playerId={},employeeId={},star={},nextStar={}",
                        ctx.playerId(), employeeId, data.getStar(), nextStar);
                res.code = Code.PARAM_ERROR;
                ctx.send(res);
                return;
            }

            int shardItemId = getEmployeeShardItemId(employeeId);
            if (shardItemId <= 0) {
                log.warn("升星雇员失败, 缺少专属碎片配置 playerId={},employeeId={},star={}",
                        ctx.playerId(), employeeId, data.getStar());
                res.code = Code.PARAM_ERROR;
                ctx.send(res);
                return;
            }

            int cost = curCfg.getStarUpCost();
            int universalShardId = auto ? getUniversalShardItemId(shardItemId) : 0;
            CommonResult<ItemOperationResult> removed = playerPackService.removePackItemWithSubstitute(
                    ctx.getPlayer(), shardItemId, cost, universalShardId, AddType.SIM_EMPLOYEE_STAR_UP);
            if (!removed.success()) {
                log.warn("升星雇员失败, 扣除碎片失败 playerId={},employeeId={},star={},shardItemId={},universalShardId={},need={},auto={},code={}",
                        ctx.playerId(), employeeId, data.getStar(), shardItemId, universalShardId, cost, auto, removed.code);
                res.code = removed.code == Code.NOT_ENOUGH_ITEM ? Code.NOT_ENOUGH : removed.code;
                ctx.send(res);
                return;
            }
            data.setStar(nextStar);
            int groupId = nextCfg.getUnlockSkill();
            if (groupId > 0) {
                data.getSkillLevels().putIfAbsent(groupId, 0);
            }
            res.star = nextStar;
            employeeRedDotService.updateRedDots(ctx.playerId(),
                    SimConstant.Employee.RED_DOT_EMPLOYEE_GROWTH);
            //主线任务: 升星改变各星级持有量 -> 上报 12212 "拥有 N 个 X 星雇员"
            reportEmployeeCounts(ctx);
            log.info("升星雇员成功 playerId={},employeeId={},newStar={}", ctx.playerId(), employeeId, data.getStar());
        } catch (Exception e) {
            log.error("升星雇员异常 playerId={},employeeId={},auto={},newStar={}", ctx.playerId(), employeeId, auto, res.star, e);
            if (res.star == 0) {
                res.code = Code.EXCEPTION;
            }
        }
        ctx.send(res);
    }

    private int getEmployeeShardItemId(int employeeId) {
        EmployeeProfileCfg profile = GameDataManager.getEmployeeProfileCfg(employeeId);
        List<Integer> shards = profile == null ? null : profile.getDuplicatetoShard();
        return shards != null && shards.size() > 1 ? shards.get(1) : 0;
    }

    private int getUniversalShardItemId(int shardItemId) {
        if (shardItemId <= 0 || (shardItemId >= FIRST_UNIVERSAL_SHARD_ITEM_ID && shardItemId <= LAST_UNIVERSAL_SHARD_ITEM_ID)) {
            return 0;
        }
        ItemCfg shard = GameDataManager.getItemCfg(shardItemId);
        if (shard == null) {
            return 0;
        }
        for (int itemId = FIRST_UNIVERSAL_SHARD_ITEM_ID; itemId <= LAST_UNIVERSAL_SHARD_ITEM_ID; itemId++) {
            ItemCfg universal = GameDataManager.getItemCfg(itemId);
            if (universal != null && universal.getQuality() == shard.getQuality()) {
                return itemId;
            }
        }
        return 0;
    }

    /**
     * 上报各职业各星级雇员持有量, 推进 12212 "拥有 N 个 X 星雇员" (X 星按 ≥ 阈值判定)。
     * 雇员为玩家级全量驻留内存, 按职业分别上报, 供 12212 的职业过滤维度匹配。
     * 直接用 ctx 投递: 登录期补报时 ctx 尚未入 registry, 走 playerId 查找会被丢弃。
     */
    @Override
    public void reportTaskState(SimPlayerContext ctx, Consumer<ActionConditionEvent> sink) {
        reportEmployeeCounts(ctx, sink);
    }

    private void reportEmployeeCounts(SimPlayerContext ctx) {
        reportEmployeeCounts(ctx, e -> simTaskService.onConditionEvent(ctx, e));
    }

    private void reportEmployeeCounts(SimPlayerContext ctx, Consumer<ActionConditionEvent> sink) {
        Map<Integer, SimEmployeeData> employees = ctx.getEmployeeMap();
        if (employees == null || employees.isEmpty()) {
            return;
        }
        // 职业 -> (星级 -> 恰好该星级的数量)
        Map<Integer, SortedMap<Integer, Long>> byProfession = new HashMap<>();
        long maxLevel = 0;
        for (SimEmployeeData employee : employees.values()) {
            maxLevel = Math.max(maxLevel, employee.getLevel());
            EmployeeProfileCfg cfg = GameDataManager.getEmployeeProfileCfg(employee.getEmployeeId());
            if (cfg == null) {
                continue;
            }
            byProfession.computeIfAbsent(cfg.getProfessionID(), k -> new TreeMap<>())
                    .merge(employee.getStar(), 1L, Long::sum);
        }
        byProfession.forEach((profession, starCounts) ->
                SimConditionEventFactory.emitOwnershipCounts(sink,
                        ActionConditionEvent.Type.EMPLOYEE_COUNT, profession, starCounts));
        sink.accept(new ActionConditionEvent(ActionConditionEvent.Type.EMPLOYEE_LEVEL,
                0, 0, maxLevel, 0, 0, false));
    }

    /**
     * 任命主管
     */
    public void onAssignSupervisor(SimPlayerContext ctx, int employeeId) {
        ResAssignSupervisor res = new ResAssignSupervisor(Code.SUCCESS);
        res.employeeId = employeeId;
        try {
            //获取雇员配置
            EmployeeProfileCfg cfg = GameDataManager.getEmployeeProfileCfg(employeeId);
            if (cfg == null) {
                log.warn("任命主管失败, 未找到雇员配置信息 playerId={},employeeId={}", ctx.playerId(), employeeId);
                res.code = Code.NOT_FOUND;
                ctx.send(res);
                return;
            }
            SimEmployeeData data = ctx.getEmployee(employeeId);
            if (data == null) {
                log.warn("任命主管失败, 雇员未解锁 playerId={},employeeId={}", ctx.playerId(), employeeId);
                res.code = Code.NOT_FOUND;
                ctx.send(res);
                return;
            }
            ctx.getCurrentCasino().addManagerEmploy(cfg.getProfessionID(), employeeId);

            SimEmployeeAttributeService.Snapshot snapshot = attributeService.snapshot(ctx);
            res.employee = toEmployeeDetail(ctx, data, snapshot);
            res.totalCombatPower = snapshot.totalCombatPower();

            log.info("任命主管 playerId={},casinoId={},professionId={},employeeId={}", ctx.playerId(), ctx.getCurrentCasino().getCasinoId(), cfg.getProfessionID(), employeeId);
        } catch (Exception e) {
            log.error("", e);
            res.code = Code.EXCEPTION;
        }
        ctx.send(res);
    }

    /**
     * 获取所有雇员
     *
     * @param ctx
     */
    public void onAllEmployee(SimPlayerContext ctx) {
        ResAllEmployee res = new ResAllEmployee(Code.SUCCESS);
        try {
            if (ctx.getEmployeeMap() != null && !ctx.getEmployeeMap().isEmpty()) {
                SimEmployeeAttributeService.Snapshot snapshot = attributeService.snapshot(ctx);
                res.employees = new ArrayList<>();
                for (SimEmployeeData data : ctx.getEmployeeMap().values()) {
                    res.employees.add(toEmployeeDetail(ctx, data, snapshot));
                }
            }
        } catch (Exception e) {
            log.error("", e);
            res.code = Code.EXCEPTION;
        }
        ctx.send(res);
    }

    /**
     * 加载雇员数据
     *
     * @param ctx
     */
    public void loadEmployeeData(SimPlayerContext ctx) {
        List<SimEmployeeData> list = simEmployeeDao.findByPlayerId(ctx.playerId());
        if (list != null && !list.isEmpty()) {
            for (SimEmployeeData e : list) {
                ctx.getEmployeeMap().put(e.getEmployeeId(), e);
            }
        }
    }

    /**
     * 添加雇员道具：首次解锁，其余数量按招募配置转为碎片。
     */
    public boolean addEmployeeItem(SimPlayerContext ctx, int itemId, long count, AddType addType) {
        EmployeeProfileCfg cfg = configCache.getEmployeeProfileCfgByItemId(itemId);
        if (cfg == null) {
            log.warn("添加雇员道具失败, 配置不存在 playerId={},itemId={}", ctx.playerId(), itemId);
            return false;
        }
        boolean unlocked = ctx.getEmployee(cfg.getId()) == null;
        long duplicateCount = count - (unlocked ? 1 : 0);
        if (duplicateCount > 0) {
            List<Integer> shards = cfg.getDuplicatetoShard();
            CommonResult<ItemOperationResult> result = playerPackService.addItems(ctx.playerId(),
                    Map.of(shards.get(1), duplicateCount * shards.get(2)), addType, "雇员重复转碎片", true);
            if (!result.success()) {
                return false;
            }
        }
        if (unlocked) {
            unlockEmployee(ctx, cfg);
            employeeRedDotService.recordNewContent(ctx, SimEmployeeRedDotService.NEW_EMPLOYEE, cfg.getId());
            employeeRedDotService.updateRedDots(ctx.playerId(), SimConstant.Employee.RED_DOT_EMPLOYEE_GROWTH);
            reportEmployeeCounts(ctx);
        }
        return true;
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    private void unlockEmployee(SimPlayerContext ctx, EmployeeProfileCfg profile) {
        SimEmployeeData data = new SimEmployeeData();
        data.setPlayerId(ctx.playerId());
        data.setEmployeeId(profile.getId());
        data.setLevel(INITIAL_LEVEL);
        data.setStar(INITIAL_STAR);
        if (profile.getSkillIdList() != null) {
            for (int skillId : profile.getSkillIdList()) {
                EmployeeSkillConfigCfg skill = GameDataManager.getEmployeeSkillConfigCfg(skillId);
                if (skill != null) {
                    data.getSkillLevels().put(skill.getGroupId(), skill.getSkillLevel());
                }
            }
        }
        ctx.putEmployee(data);
    }

    public List<EmployeeSkillInfo> skillInfos(SimEmployeeData data) {
        List<EmployeeSkillInfo> infos = new ArrayList<>(data.getSkillLevels().size());
        data.getSkillLevels().forEach((groupId, level) -> {
            EmployeeSkillConfigCfg cfg = configCache.getEmployeeSkillCfg(groupId, level);
            if (cfg != null) {
                EmployeeSkillInfo info = new EmployeeSkillInfo();
                info.groupId = groupId;
                info.level = level;
                info.configId = cfg.getId();
                info.maxLevel = configCache.employSkillMaxLevel(groupId);
                infos.add(info);
            }
        });
        infos.sort(Comparator.comparingInt(info -> info.groupId));
        return infos;
    }

    public EmployDetailInfo toEmployeeDetail(SimPlayerContext ctx, SimEmployeeData data,
                                             SimEmployeeAttributeService.Snapshot snapshot) {
        EmployDetailInfo info = new EmployDetailInfo();
        info.id = data.getEmployeeId();
        info.level = data.getLevel();
        info.star = data.getStar();
        info.maxLevel = configCache.getEmployeeMaxLevel(info.id);
        info.maxStar = configCache.getEmployeeMaxStar(info.id);
        info.manager = ctx.getCurrentCasino().employIsManager(data.getEmployeeId());
        info.combatPower = snapshot.combatPower(data.getEmployeeId());
        info.attributes = new ArrayList<>();
        snapshot.attributes(data.getEmployeeId()).forEach((type, value) ->
                info.attributes.add(new KVLongInfo(type.getCode(), value)));
        info.attributes.sort(Comparator.comparingInt(attribute -> attribute.key));
        info.skills = skillInfos(data);

        EmployeeProfileCfg employeeProfileCfg = GameDataManager.getEmployeeProfileCfg(data.getEmployeeId());
        if (employeeProfileCfg != null) {
            info.professionID = employeeProfileCfg.getProfessionID();
        }
        return info;
    }

    private EmployeeStarCfg getStarCfg(int employeeId, int star) {
        Map<Integer, Map<Integer, EmployeeStarCfg>> starMap = configCache.getEmployeeStarCfgMap();
        if (starMap == null) {
            return null;
        }
        Map<Integer, EmployeeStarCfg> m = starMap.get(employeeId);
        return m == null ? null : m.get(star);
    }

    /**
     * 获取卡池
     *
     * @param ctx
     * @param poolId 卡池id
     */
    public void onPool(SimPlayerContext ctx, int poolId) {
        ResEmployeePool res = new ResEmployeePool(Code.SUCCESS);
        try {
            PoolListCfg poolCfg = configCache.getOpenPoolCfg(poolId, SimConstant.PoolList.TYPE_EMPLOYEE);
            if (poolCfg == null) {
                log.warn("获取雇员卡池失败,卡池不存在、未开启、未到开放时间或类型错误 playerId={},poolId={}", ctx.playerId(), poolId);
                res.code = Code.PARAM_ERROR;
                ctx.send(res);
                return;
            }

            EmployeePoolCfg employeePoolCfg = GameDataManager.getEmployeePoolCfg(poolCfg.getDropItem());
            if (employeePoolCfg == null || employeePoolCfg.getDetailedDropItem() == null) {
                log.warn("获取雇员卡池失败,掉落配置不存在 playerId={},poolId={},dropItem={}", ctx.playerId(), poolId, poolCfg.getDropItem());
                res.code = Code.PARAM_ERROR;
                ctx.send(res);
                return;
            }

            res.employeeIds = new ArrayList<>();
            for (List<Integer> list : employeePoolCfg.getDetailedDropItem()) {
                res.employeeIds.add(list.get(1));
            }
        } catch (Exception e) {
            log.error("", e);
            res.code = Code.EXCEPTION;
        }
        ctx.send(res);
    }

    /**
     * 计算所有雇员的战力之和
     *
     * @param ctx
     * @return
     */
    public long calAllCombatPower(SimPlayerContext ctx) {
        return attributeService.snapshot(ctx).totalCombatPower();
    }
}
