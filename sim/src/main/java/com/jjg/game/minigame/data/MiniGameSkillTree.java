package com.jjg.game.minigame.data;

import com.jjg.game.core.constant.Code;
import com.jjg.game.sampledata.bean.BaseCfgBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.ToIntFunction;

/** 每次配置加载构建索引，查询及升级复用同一份配置。 */
public final class MiniGameSkillTree<T extends BaseCfgBean> {
    private static final Logger log = LoggerFactory.getLogger(MiniGameSkillTree.class);

    private final String gameName;
    private final ToIntFunction<T> groupIdOf;
    private final ToIntFunction<T> levelOf;
    private final ToIntFunction<T> costOf;
    private final Map<Integer, Map<Integer, T>> levels = new HashMap<>();
    private final Map<Integer, List<T>> prerequisites = new HashMap<>();
    private final List<Integer> groupIds;

    public MiniGameSkillTree(String gameName, List<T> rows, ToIntFunction<T> groupIdOf,
                             ToIntFunction<T> levelOf, ToIntFunction<T> costOf, Function<T, List<Integer>> linksOf) {
        this.gameName = gameName;
        this.groupIdOf = groupIdOf;
        this.levelOf = levelOf;
        this.costOf = costOf;
        for (T row : rows) {
            levels.computeIfAbsent(groupIdOf.applyAsInt(row), key -> new HashMap<>()).put(levelOf.applyAsInt(row), row);
            List<Integer> links = linksOf.apply(row);
            if (links != null) {
                for (int targetGroupId : links) {
                    prerequisites.computeIfAbsent(targetGroupId, key -> new ArrayList<>()).add(row);
                }
            }
        }
        groupIds = levels.keySet().stream().sorted().toList();
    }

    public T levelConfig(int groupId, int level) {
        Map<Integer, T> group = levels.get(groupId);
        return group == null ? null : group.get(level);
    }

    public List<Integer> groupIds() {
        return groupIds;
    }

    public boolean isRoot(int groupId) {
        return levels.containsKey(groupId) && !prerequisites.containsKey(groupId);
    }

    public Upgrade checkUpgrade(long playerId, int groupId, Map<Integer, Integer> skillLevels,
                                long points, IntPredicate initiallyUnlocked) {
        int level = skillLevels.getOrDefault(groupId, 0);
        T current = levelConfig(groupId, level);
        if (current == null) {
            log.warn("{}升级失败，缺少当前等级配置 playerId={},groupId={},level={}", gameName, playerId, groupId, level);
            return new Upgrade(level == 0 ? Code.PARAM_ERROR : Code.SAMPLE_ERROR, 0);
        }
        if (levelConfig(groupId, level + 1) == null) {
            log.warn("{}升级失败，已达配置等级上限 playerId={},groupId={},level={}", gameName, playerId, groupId, level);
            return new Upgrade(Code.LEVEL_MAX, 0);
        }
        int cost = costOf.applyAsInt(current);
        if (cost < 0) {
            log.warn("{}升级失败，费用配置为负数 playerId={},skillId={},cost={}", gameName, playerId, current.getId(), cost);
            return new Upgrade(Code.SAMPLE_ERROR, 0);
        }
        if (level == 0 && !initiallyUnlocked.test(groupId)) {
            List<T> sources = prerequisites.getOrDefault(groupId, List.of());
            boolean unlocked = sources.stream().anyMatch(source -> {
                int sourceGroupId = groupIdOf.applyAsInt(source);
                int sourceLevel = levelOf.applyAsInt(source);
                return skillLevels.getOrDefault(sourceGroupId, 0) == sourceLevel
                        && (sourceLevel > 0 || initiallyUnlocked.test(sourceGroupId));
            });
            if (!unlocked) {
                log.warn("{}升级失败，技能未解锁 playerId={},groupId={},level={},levels={},prerequisiteIds={}",
                        gameName, playerId, groupId, level, skillLevels, sources.stream().map(BaseCfgBean::getId).toList());
                return new Upgrade(Code.NOT_UNLOCKED, 0);
            }
        }
        if (points < cost) {
            log.warn("{}升级失败，积分不足 playerId={},skillId={},skillPoints={},cost={}",
                    gameName, playerId, current.getId(), points, cost);
            return new Upgrade(Code.NOT_ENOUGH, 0);
        }
        return new Upgrade(Code.SUCCESS, cost);
    }

    public record Upgrade(int code, int cost) {
    }
}
