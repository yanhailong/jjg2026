package com.jjg.game.minigame.data;

import com.jjg.game.minigame.constant.MiniGameConstant.RoundState;

import java.util.HashMap;
import java.util.Map;

/** 会话内单局；初始结束时间作为局号，加时只更新endTime。 */
public class HappyHappyFishRound {
    private long roundId;
    private long endTime;
    private int score;
    private int state = RoundState.PLAYING;
    private boolean quickClear;
    private final Map<Integer, Integer> caughtCounts = new HashMap<>();
    private Map<Integer, Long> rewards;

    public long getRoundId() {
        return roundId;
    }

    public void setRoundId(long roundId) {
        this.roundId = roundId;
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

    public Map<Integer, Integer> getCaughtCounts() {
        return caughtCounts;
    }

    public Map<Integer, Long> getRewards() {
        return rewards;
    }

    public void setRewards(Map<Integer, Long> rewards) {
        this.rewards = rewards;
    }
}
