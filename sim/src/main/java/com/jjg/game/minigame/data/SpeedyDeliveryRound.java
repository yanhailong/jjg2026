package com.jjg.game.minigame.data;

import com.jjg.game.sampledata.bean.MinigameSpeedyDeliveryCfg;

import java.util.HashMap;
import java.util.Map;

/** 会话内单局；引用开局配置，避免热更新改变本局时长、容量和奖励规则。 */
public class SpeedyDeliveryRound extends TimedMiniGameRound {
    public int randomEventConfigId;
    // Type 4 的参数 1/2/3 分别表示糕点、酒水、生鲜。
    private static final int SCORE_BONUS_CATEGORY_COUNT = 3;

    private final MinigameSpeedyDeliveryCfg config;
    private int backpackCapacity;
    private int score;
    private boolean quickClear;
    private Map<Integer, Long> rewards;
    private long[] scoreFlat;
    private long[] scorePermille;
    private final Map<Integer, Long> truckMultiplierBonuses = new HashMap<>();
    private long goodsRewardFlat;
    private long goodsRewardPermille;
    // 按货物ID统计本局实际入仓数量，首次结算时计入技能增加的部分。
    private Map<Integer, Long> deliveredGoods = Map.of();

    public SpeedyDeliveryRound(MinigameSpeedyDeliveryCfg config, long endTime) {
        this.config = config;
        setEndTime(endTime);
        this.backpackCapacity = config.getBackpackCapacity();
    }

    public boolean addScoreBonus(int category, int flat, int permille) {
        if (category < 1 || category > SCORE_BONUS_CATEGORY_COUNT) {
            return false;
        }
        if (scoreFlat == null) {
            scoreFlat = new long[SCORE_BONUS_CATEGORY_COUNT];
            scorePermille = new long[SCORE_BONUS_CATEGORY_COUNT];
        }
        scoreFlat[category - 1] += flat;
        scorePermille[category - 1] += permille;
        return true;
    }

    public long scoreForGoods(int category, int baseScore) {
        if (scoreFlat == null) {
            return baseScore;
        }
        int index = category - 1;
        return baseScore + scoreFlat[index] + MiniGameSkillEffect.scaleByPermille(baseScore, scorePermille[index]);
    }

    public void addTruckMultiplierBonus(int truckId, int flat, int permille) {
        truckMultiplierBonuses.merge(truckId, flat * 100L + permille, Long::sum);
    }

    /** 配置表倍率以百分比填写，换算到万分比以保留技能倍率的小数。 */
    public long truckMultiplier(int truckId, int goodsCategory, int baseMultiplier) {
        int matchingCategory = switch (truckId) {
            case 1002 -> 1;
            case 1003 -> 2;
            case 1001 -> 3;
            default -> 0;
        };
        long bonus = goodsCategory == matchingCategory ? truckMultiplierBonuses.getOrDefault(truckId, 0L) : 0;
        return baseMultiplier * 100L + bonus;
    }

    public void recordDeliveredGood(int goodsId) {
        if (deliveredGoods.isEmpty()) {
            deliveredGoods = new HashMap<>();
        }
        deliveredGoods.merge(goodsId, 1L, Long::sum);
    }

    public Map<Integer, Long> getDeliveredGoods() {
        return deliveredGoods;
    }

    public void addGoodsRewardBonus(int flat, int permille) {
        goodsRewardFlat += flat;
        goodsRewardPermille += permille;
    }

    public long goodsRewardBonus(long count) {
        return goodsRewardFlat + MiniGameSkillEffect.scaleByPermille(count, goodsRewardPermille);
    }

    public MinigameSpeedyDeliveryCfg getConfig() {
        return config;
    }

    public int getBackpackCapacity() {
        return backpackCapacity;
    }

    public void setBackpackCapacity(int backpackCapacity) {
        this.backpackCapacity = backpackCapacity;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public boolean isQuickClear() {
        return quickClear;
    }

    public void setQuickClear(boolean quickClear) {
        this.quickClear = quickClear;
    }

    public Map<Integer, Long> getRewards() {
        return rewards;
    }

    public void setRewards(Map<Integer, Long> rewards) {
        this.rewards = rewards;
    }
}
