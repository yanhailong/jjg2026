package com.jjg.game.season.service;

import com.jjg.game.sampledata.GameDataManager;
import com.jjg.game.sampledata.bean.SmallDrawCfg;
import com.jjg.game.sampledata.bean.SmallDrawRewardCfg;
import com.jjg.game.sampledata.container.BaseCfgContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 赛季小额抽奖配置索引，配置容器热更新后才重新整理奖池。 */
@Service
public class SeasonSmallDrawConfigService {
    private static final Logger log = LoggerFactory.getLogger(SeasonSmallDrawConfigService.class);
    private volatile ConfigIndex index;

    public DrawDefinition activeDraw(int seasonId) {
        GameDataManager manager = GameDataManager.getInstance();
        BaseCfgContainer<SmallDrawCfg> draws = manager.getCfgContainer(SmallDrawCfg.class);
        BaseCfgContainer<SmallDrawRewardCfg> rewards = manager.getCfgContainer(SmallDrawRewardCfg.class);
        ConfigIndex cached = index;
        if (cached == null || cached.drawSource() != draws || cached.rewardSource() != rewards) {
            cached = new ConfigIndex(draws, rewards, buildIndex(draws.getCfgBeanMap().values(),
                    rewards.getCfgBeanMap().values()));
            index = cached;
        }
        return cached.bySeason().get(seasonId);
    }

    static Map<Integer, DrawDefinition> buildIndex(Iterable<SmallDrawCfg> draws,
                                                  Iterable<SmallDrawRewardCfg> rewards) {
        Map<Integer, List<RewardDefinition>> pools = new HashMap<>();
        for (SmallDrawRewardCfg reward : rewards) {
            pools.computeIfAbsent(reward.getSmallDrawId(), ignored -> new ArrayList<>())
                    .add(new RewardDefinition(reward.getId(), reward.getOpen(), reward.getQuality(),
                            reward.getWeight(), reward.getItemid()));
        }
        Map<Integer, DrawDefinition> result = new HashMap<>();
        for (SmallDrawCfg draw : draws) {
            if (!draw.getOpen()) {
                continue;
            }
            try {
                List<RewardDefinition> pool = pools.getOrDefault(draw.getId(), List.of());
                boolean validPool = !pool.isEmpty() && pool.stream().allMatch(SeasonSmallDrawConfigService::validReward);
                if (!validPool) {
                    log.error("小额抽奖奖池为空或无效，停用付费抽奖 drawId={}", draw.getId());
                }
                //每日福利仅取决于SmallDraw.open，奖池无效不影响每日福利。
                List<RewardDefinition> sorted = validPool ? pool.stream()
                        .sorted(Comparator.comparingInt(RewardDefinition::id))
                        .map(SeasonSmallDrawConfigService::prepareReward).toList() : List.of();
                DrawDefinition definition = new DrawDefinition(draw, sorted);
                if (result.putIfAbsent(draw.getSeasonid(), definition) != null) {
                    throw new IllegalArgumentException("duplicate seasonId " + draw.getSeasonid());
                }
            } catch (RuntimeException e) {
                log.error("赛季小额抽奖配置无效 drawId={},seasonId={}", draw.getId(), draw.getSeasonid(), e);
            }
        }
        return Map.copyOf(result);
    }

    private static boolean validReward(RewardDefinition reward) {
        return reward.id() > 0 && reward.options() != null && !reward.options().isEmpty()
                && reward.options().stream().allMatch(items -> items != null && !items.isEmpty()
                && items.entrySet().stream().allMatch(entry -> entry.getKey() != null && entry.getKey() > 0
                && entry.getValue() != null && entry.getValue() > 0));
    }

    private static RewardDefinition prepareReward(RewardDefinition reward) {
        if (reward.core()) {
            return new RewardDefinition(reward.id(), true, reward.quality(), reward.weight(),
                    reward.options().stream().map(Map::copyOf).toList());
        }
        Map<Integer, Long> items = new HashMap<>();
        reward.options().forEach(option -> option.forEach((id, count) -> items.merge(id, count, Math::addExact)));
        return new RewardDefinition(reward.id(), false, reward.quality(), reward.weight(), List.of(Map.copyOf(items)));
    }

    public record DrawDefinition(SmallDrawCfg config, List<RewardDefinition> rewards) {
        public int id() { return config.getId(); }

        public RewardDefinition reward(int rewardId) {
            return rewards.stream().filter(reward -> reward.id() == rewardId).findFirst().orElse(null);
        }

        public int remainingRewardCount(Set<Integer> obtainedRewards) {
            int count = 0;
            for (RewardDefinition reward : rewards) {
                if (reward.weight() > 0 && !obtainedRewards.contains(reward.id())) {
                    count++;
                }
            }
            return count;
        }
    }

    public record RewardDefinition(int id, boolean core, int quality, int weight, List<Map<Integer, Long>> options) {
        public Map<Integer, Long> items(int optionIndex) {
            return options.get(core ? optionIndex : 0);
        }
    }

    private record ConfigIndex(Object drawSource, Object rewardSource,
                               Map<Integer, DrawDefinition> bySeason) {
    }
}
