package com.jjg.game.minigame.data;

import com.jjg.game.minigame.constant.MiniGameConstant.RoundState;
import com.jjg.game.sampledata.bean.MinigameSpeedyDeliveryCfg;

import java.util.HashMap;
import java.util.Map;

/** 会话内单局；引用开局配置，避免热更新改变本局时长、容量和奖励规则。 */
public class SpeedyDeliveryRound {
    public int randomEventConfigId;
    // Type 4 的参数 1 至 3 依次对应 MinigameTruck.id 1001 至 1003。
    private static final int FIRST_SKILL_TRUCK_ID = 1001;
    private static final int SKILL_TRUCK_COUNT = 3;

    private final MinigameSpeedyDeliveryCfg config;
    private long endTime;
    private int backpackCapacity;
    private int score;
    private int state = RoundState.PLAYING;
    private boolean quickClear;
    private Map<Integer, Long> rewards;
    private long[] scoreFlat;
    private long[] scorePermille;
    private long goodsRewardFlat;
    private long goodsRewardPermille;
    // 按货物ID统计本局实际入仓数量，首次结算时计入技能增加的部分。
    private Map<Integer, Long> deliveredGoods = Map.of();

    public SpeedyDeliveryRound(MinigameSpeedyDeliveryCfg config, long endTime) {
        this.config = config;
        this.endTime = endTime;
        this.backpackCapacity = config.getBackpackCapacity();
    }

    public boolean addScoreBonus(int truckIndex, int flat, int permille) {
        if (truckIndex < 1 || truckIndex > SKILL_TRUCK_COUNT) {
            return false;
        }
        if (scoreFlat == null) {
            scoreFlat = new long[SKILL_TRUCK_COUNT];
            scorePermille = new long[SKILL_TRUCK_COUNT];
        }
        scoreFlat[truckIndex - 1] += flat;
        scorePermille[truckIndex - 1] += permille;
        return true;
    }

    public long scoreForTruck(int truckId, long baseScore) {
        int index = truckId - FIRST_SKILL_TRUCK_ID;
        if (scoreFlat == null || index < 0 || index >= SKILL_TRUCK_COUNT) {
            return baseScore;
        }
        return baseScore + scoreFlat[index] + MiniGameSkillEffect.scaleByPermille(baseScore, scorePermille[index]);
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

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
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
