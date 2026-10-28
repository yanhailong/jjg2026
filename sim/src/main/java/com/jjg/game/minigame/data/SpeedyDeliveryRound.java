package com.jjg.game.minigame.data;

import com.jjg.game.minigame.constant.MiniGameConstant.RoundState;
import com.jjg.game.sampledata.bean.MinigameSpeedyDeliveryCfg;

import java.util.Map;

/** 会话内单局；引用开局配置，避免热更新改变本局时长、容量和奖励规则。 */
public class SpeedyDeliveryRound {
    private final MinigameSpeedyDeliveryCfg config;
    private long endTime;
    private int score;
    private int state = RoundState.PLAYING;
    private boolean quickClear;
    private Map<Integer, Long> rewards;

    public SpeedyDeliveryRound(MinigameSpeedyDeliveryCfg config, long endTime) {
        this.config = config;
        this.endTime = endTime;
    }

    public MinigameSpeedyDeliveryCfg getConfig() {
        return config;
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
