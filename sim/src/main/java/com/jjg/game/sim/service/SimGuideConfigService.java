package com.jjg.game.sim.service;

import com.jjg.game.core.listener.ConfigExcelChangeListener;
import com.jjg.game.sampledata.GameDataManager;
import com.jjg.game.sampledata.bean.GuideCfg;
import com.jjg.game.sim.constant.SimConstant;
import com.jjg.game.sim.data.SimBaseData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Guide.xlsx 的服务端触发索引；步骤链与表现字段由客户端使用。
 */
@Component
public class SimGuideConfigService implements ConfigExcelChangeListener {
    private static final Logger log = LoggerFactory.getLogger(SimGuideConfigService.class);

    private volatile Map<TriggerKey, List<Integer>> triggerGroups = Collections.emptyMap();
    private volatile Map<Integer, Map<Integer, Integer>> groupConditions = Collections.emptyMap();
    private volatile Map<Integer, String> groupPathNames = Collections.emptyMap();
    private volatile Map<Integer, Integer> guideGroups = Collections.emptyMap();
    private volatile Map<Integer, List<Integer>> groupGuideIds = Collections.emptyMap();
    private volatile Map<Integer, Set<Integer>> groupSkipGuideIds = Collections.emptyMap();

    @Override
    public void initSampleCallbackCollector() {
        addInitSampleFileObserveWithCallBack(GuideCfg.EXCEL_NAME, this::load);
    }

    @Override
    public void changeSampleCallbackCollector() {
        addChangeSampleFileObserveWithCallBack(GuideCfg.EXCEL_NAME, this::load);
    }

    public void load() {
        List<GuideCfg> all = GameDataManager.getGuideCfgList();
        if (all == null || all.isEmpty()) {
            triggerGroups = Collections.emptyMap();
            groupConditions = Collections.emptyMap();
            groupPathNames = Collections.emptyMap();
            guideGroups = Collections.emptyMap();
            groupGuideIds = Collections.emptyMap();
            groupSkipGuideIds = Collections.emptyMap();
            log.warn("加载新手引导触发索引失败: Guide 配置为空");
            return;
        }
        Map<TriggerKey, Set<Integer>> groups = new HashMap<>();
        Map<Integer, Map<Integer, Integer>> conditions = new HashMap<>();
        Map<Integer, String> pathNames = new HashMap<>();
        Map<Integer, Integer> guides = new HashMap<>();
        Map<Integer, List<Integer>> guidesByGroup = new HashMap<>();
        Map<Integer, Set<Integer>> skipGuidesByGroup = new HashMap<>();
        for (GuideCfg cfg : all) {
            if (cfg == null) {
                continue;
            }
            // 关闭的引导组不建立任何触发、步骤或跳过索引，后续不会参与条件判断。
            if (!cfg.getIsOpen()) {
                continue;
            }
            Map<Integer, Integer> configuredConditions = normalizeConditions(cfg);
            if (cfg.getGuideGroupId() <= 0 || configuredConditions.isEmpty()) {
                continue;
            }
            Map<Integer, Integer> oldConditions = conditions.putIfAbsent(cfg.getGuideGroupId(), configuredConditions);
            if (oldConditions != null && !oldConditions.equals(configuredConditions)) {
                log.warn("同一引导组配置了不同触发条件 groupId={},old={},new={},guideId={}",
                        cfg.getGuideGroupId(), oldConditions, configuredConditions, cfg.getId());
                continue;
            }
            String pathName = normalizePathName(cfg.getPathName());
            String oldPathName = pathNames.putIfAbsent(cfg.getGuideGroupId(), pathName);
            if (oldPathName != null && !oldPathName.equals(pathName)) {
                log.warn("同一引导组配置了不同触发场景 groupId={},old={},new={},guideId={}",
                        cfg.getGuideGroupId(), oldPathName, pathName, cfg.getId());
                continue;
            }
            configuredConditions.forEach((condition, param) ->
                    groups.computeIfAbsent(new TriggerKey(condition, param), ignored -> new LinkedHashSet<>())
                            .add(cfg.getGuideGroupId()));

            List<Integer> configuredGuideIds = cfg.getGuideIdList();
            List<Integer> normalizedGuideIds = new ArrayList<>();
            if (configuredGuideIds != null) {
                for (Integer guideId : configuredGuideIds) {
                    if (guideId != null && guideId > 0 && !normalizedGuideIds.contains(guideId)) {
                        normalizedGuideIds.add(guideId);
                    }
                }
            }
            // 兼容尚未配置 GuideIdList 的单步骤引导组，以配置行 ID 作为步骤 ID。
            if (normalizedGuideIds.isEmpty() && cfg.getId() > 0) {
                normalizedGuideIds.add(cfg.getId());
            }
            for (int guideId : normalizedGuideIds) {
                Integer oldGroupId = guides.putIfAbsent(guideId, cfg.getGuideGroupId());
                if (oldGroupId != null && oldGroupId != cfg.getGuideGroupId()) {
                    log.warn("同一引导步骤配置到了不同引导组 guideId={},oldGroupId={},newGroupId={}",
                            guideId, oldGroupId, cfg.getGuideGroupId());
                }
            }
            guidesByGroup.put(cfg.getGuideGroupId(),
                    Collections.unmodifiableList(new ArrayList<>(normalizedGuideIds)));
            Set<Integer> normalizedSkipGuideIds = new LinkedHashSet<>();
            List<Integer> configuredSkipGuideIds = cfg.getSkipGuideId();
            if (configuredSkipGuideIds != null) {
                for (Integer guideId : configuredSkipGuideIds) {
                    if (guideId == null || guideId <= 0) continue;
                    if (!normalizedGuideIds.contains(guideId)) {
                        log.warn("跳过引导ID不属于当前引导组，已忽略 groupId={},guideId={}",
                                cfg.getGuideGroupId(), guideId);
                        continue;
                    }
                    normalizedSkipGuideIds.add(guideId);
                }
            }
            skipGuidesByGroup.put(cfg.getGuideGroupId(),
                    Collections.unmodifiableSet(normalizedSkipGuideIds));
        }
        Map<TriggerKey, List<Integer>> immutable = new HashMap<>();
        groups.forEach((key, value) -> immutable.put(key,
                Collections.unmodifiableList(new ArrayList<>(value))));
        triggerGroups = Collections.unmodifiableMap(immutable);
        groupConditions = Collections.unmodifiableMap(conditions);
        groupPathNames = Collections.unmodifiableMap(pathNames);
        guideGroups = Collections.unmodifiableMap(guides);
        groupGuideIds = Collections.unmodifiableMap(guidesByGroup);
        groupSkipGuideIds = Collections.unmodifiableMap(skipGuidesByGroup);
        int skipGuideCount = groupSkipGuideIds.values().stream().mapToInt(Set::size).sum();
        long pathRestrictedGroupCount = groupPathNames.values().stream().filter(value -> !value.isEmpty()).count();
        log.info("加载新手引导触发索引完成 triggerCount={},groupCount={},guideCount={},skipGuideCount={},pathRestrictedGroupCount={}",
                triggerGroups.size(), groupConditions.size(), guideGroups.size(), skipGuideCount,
                pathRestrictedGroupCount);
    }

    public List<Integer> groupsFor(int condition, int param) {
        int normalized = condition == SimConstant.GuideCondition.NEW_PLAYER ? 0 : param;
        return triggerGroups.getOrDefault(new TriggerKey(condition, normalized), Collections.emptyList());
    }

    /**
     * 获取指定条件下不高于当前值的全部配置参数，供“达到等级”补扫使用。
     */
    public List<Integer> paramsAtOrBelow(int condition, int currentValue) {
        if (currentValue <= 0) return Collections.emptyList();
        List<Integer> params = triggerGroups.keySet().stream()
                .filter(key -> key.condition() == condition && key.param() > 0 && key.param() <= currentValue)
                .map(TriggerKey::param)
                .distinct()
                .sorted()
                .toList();
        return params.isEmpty() ? Collections.emptyList() : params;
    }

    /** 同组配置的全部条件必须同时满足。 */
    public Map<Integer, Integer> conditionsOfGroup(int guideGroupId) {
        return groupConditions.getOrDefault(guideGroupId, Collections.emptyMap());
    }

    /**
     * 获取引导组要求的触发场景；空字符串表示不限制场景。
     */
    public String pathNameOfGroup(int guideGroupId) {
        return groupPathNames.getOrDefault(guideGroupId, "");
    }

    public boolean containsGroup(int guideGroupId) {
        return groupConditions.containsKey(guideGroupId);
    }

    /**
     * 是否存在指定引导步骤。
     */
    public boolean containsGuide(int guideId) {
        return guideId > 0 && guideGroups.containsKey(guideId);
    }

    /**
     * 根据步骤ID获取所属引导组。
     */
    public int groupOfGuide(int guideId) {
        return guideGroups.getOrDefault(guideId, 0);
    }

    /**
     * 获取新手引导期间应固定抽取的道具 ID，未命中返回 0。
     * 配置格式为 [固定道具 ID, 关联引导步骤 ID...]；任一关联步骤所属引导组
     * 已触发且未完成时启用固定抽取，引导组完成后恢复随机抽取。
     */
    public int newbieFixedDrawItemId(SimBaseData baseData, List<Integer> newbieGuideDraw) {
        if (baseData == null || newbieGuideDraw == null || newbieGuideDraw.size() < 2) {
            log.warn("baseData={},newbieGuideDraw={},newbieGuideDraw.size()={}", baseData, newbieGuideDraw, newbieGuideDraw == null ? 0 : newbieGuideDraw.size());
            return 0;
        }

//        log.warn("newbieGuideDraw={}", newbieGuideDraw);

        Integer fixedItemId = newbieGuideDraw.getFirst();
//        log.warn("fixedItemId={}", fixedItemId);
        if (fixedItemId == null || fixedItemId <= 0) {
            return 0;
        }

        //首个引导id
        Integer firstGuideId = newbieGuideDraw.get(1);
//        log.warn("firstGuideId={}", firstGuideId);
        if (firstGuideId == null || firstGuideId <= 0) {
            return 0;
        }

        //获取该引导所在的引导组
        int groupId = groupOfGuide(firstGuideId);
//        log.warn("firstGuideId={},groupId={}", firstGuideId, groupId);
        if (groupId < 1) {
            return 0;
        }

        //未触发或者已经完成该引导组则跳过
        log.warn("trigger={},complete={},groupId={}", baseData.hasTriggeredGuideGroup(groupId), baseData.hasCompletedGuideGroup(groupId), groupId);
        if (!baseData.hasTriggeredGuideGroup(groupId) || baseData.hasCompletedGuideGroup(groupId)) {
            return 0;
        }

        //获取指定引导组包含的全部步骤ID
        List<Integer> allGuideList = guideIdsOfGroup(groupId);
        if (allGuideList == null || allGuideList.isEmpty()) {
            return 0;
        }

        int maxGuideId = 0;
        for (int id : allGuideList) {
            if (baseData.hasCompletedGuideId(id) && id > maxGuideId) {
                maxGuideId = id;
            }
        }

        if (maxGuideId < 1) {
            return 0;
        }

        final int finalMaxGuideId = maxGuideId;
        boolean match = newbieGuideDraw.stream().anyMatch(gid -> gid == finalMaxGuideId);
        return match ? fixedItemId : 0;
    }

    /**
     * 获取指定引导组包含的全部步骤ID。
     */
    public List<Integer> guideIdsOfGroup(int guideGroupId) {
        return groupGuideIds.getOrDefault(guideGroupId, Collections.emptyList());

    }

    /**
     * 获取指定引导组中允许在重新进入大厅时自动完成的步骤ID。
     */
    public Set<Integer> skipGuideIdsOfGroup(int guideGroupId) {
        return groupSkipGuideIds.getOrDefault(guideGroupId, Collections.emptySet());
    }

    /**
     * 获取配置中的全部引导步骤ID。
     */
    public List<Integer> allGuideIds() {
        List<Integer> result = new ArrayList<>(guideGroups.keySet());

        result.sort(Integer::compareTo);
        return Collections.unmodifiableList(result);
    }

    private Map<Integer, Integer> normalizeConditions(GuideCfg cfg) {
        Map<Integer, Integer> configuredConditions = cfg.getCondition();
        if (configuredConditions == null || configuredConditions.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Integer, Integer> normalized = new HashMap<>();
        for (Map.Entry<Integer, Integer> entry : configuredConditions.entrySet()) {
            Integer condition = entry.getKey();
            Integer param = entry.getValue();
            if (condition == null || param == null || !validCondition(condition, param)) {
                log.warn("无效的新手引导条件，整组不建立触发索引 groupId={},guideId={},condition={},param={}",
                        cfg.getGuideGroupId(), cfg.getId(), condition, param);
                return Collections.emptyMap();
            }
            normalized.put(condition, condition == SimConstant.GuideCondition.NEW_PLAYER ? 0 : param);
        }
        return Collections.unmodifiableMap(normalized);
    }

    private boolean validCondition(int condition, int param) {
        return switch (condition) {
            case SimConstant.GuideCondition.NEW_PLAYER -> true;
            case SimConstant.GuideCondition.CLIENT_PAGE -> param >= 0 && param != SimConstant.ClientPage.UNKNOWN;
            case SimConstant.GuideCondition.TASK_REWARD_CLAIMED,
                 SimConstant.GuideCondition.SCENE_TOTAL_LEVEL,
                 SimConstant.GuideCondition.PLAYER_LEVEL,
                 SimConstant.GuideCondition.ITEM_NOT_ENOUGH,
                 SimConstant.GuideCondition.ITEM_GAINED,
                 SimConstant.GuideCondition.FUNCTION_UNLOCKED,
                 SimConstant.GuideCondition.GUIDE_GROUP_FINISHED,
                 SimConstant.GuideCondition.ENTER_MINI_GAME,
                 SimConstant.GuideCondition.CLIENT_EVENT -> param > 0;
            default -> false;
        };
    }

    private String normalizePathName(String pathName) {
        if (pathName == null) {
            return "";
        }
        String normalized = pathName.trim();
        // PathName 列定义为 string，但策划填写数字时 Excel 解析器会得到 "1.0"。
        if (normalized.endsWith(".0")) {
            String integerPart = normalized.substring(0, normalized.length() - 2);
            if (!integerPart.isEmpty() && integerPart.chars().allMatch(Character::isDigit)) {
                return integerPart;
            }
        }
        return normalized;
    }

    private record TriggerKey(int condition, int param) {
    }
}
