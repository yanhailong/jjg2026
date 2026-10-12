package com.jjg.game.season.config;

import java.util.*;

/** 小额抽奖的配置接入模型；本次不新增 Excel，后续配置源负责组装该模型。 */
public record LuckyDrawDefinition(int activityId, int stageIndex, String name, String iconPath,
                                  int priceType, long price, int shopId, int maxCarryDays,
                                  List<Reward> rewards, List<Grand> grands, Map<Integer, Long> dailyRewards) {
    public static final int ITEM_DRAW = 1;
    public static final int SLOT_COUNT = 14;

    public LuckyDrawDefinition {
        if (activityId <= 0 || stageIndex <= 0 || priceType < 1 || priceType > 3 || price <= 0
                || (priceType == 1 && shopId <= 0) || maxCarryDays != 3)
            throw new IllegalArgumentException("invalid lucky draw activity/price/carry limit");
        rewards = List.copyOf(rewards);
        grands = List.copyOf(grands);
        dailyRewards = validItems(dailyRewards);
        if (rewards.size() != SLOT_COUNT || rewards.stream().filter(Reward::grand).count() != 1
                || rewards.stream().map(Reward::id).distinct().count() != SLOT_COUNT
                || rewards.stream().map(Reward::slot).distinct().count() != SLOT_COUNT
                || grands.isEmpty() || grands.stream().map(Grand::id).distinct().count() != grands.size())
            throw new IllegalArgumentException("expected 13 ordinary slots and one selectable grand slot");
    }

    public record Reward(int id, int slot, boolean grand, Map<Integer, Long> items) {
        public Reward {
            if (id <= 0 || (grand ? slot != 0 : slot < 1 || slot >= SLOT_COUNT))
                throw new IllegalArgumentException("invalid reward slot");
            items = grand ? Map.of() : validItems(items);
        }
    }
    public record Grand(int id, Map<Integer, Long> items) {
        public Grand {
            if (id <= 0) throw new IllegalArgumentException("invalid grand id");
            items = validItems(items);
        }
    }
    public Grand grand(int id) { return grands.stream().filter(g -> g.id() == id).findFirst().orElse(null); }
    public static Map<Integer, Long> validItems(Map<Integer, Long> items) {
        if (items == null || items.isEmpty() || items.entrySet().stream().anyMatch(e ->
                e.getKey() == null || e.getKey() <= 0 || e.getValue() == null || e.getValue() <= 0))
            throw new IllegalArgumentException("invalid rewards");
        return Map.copyOf(items);
    }
}
