package com.jjg.game.minigame.data;

import com.jjg.game.minigame.constant.MiniGameConstant.RoundState;

import java.util.HashMap;
import java.util.Map;

/** 会话内单局，初始结束时间作为局号，捕获积分加成在开局时确定。 */
public class HappyHappyFishRound {
    private static final int FIRST_FISH_ID = 101;
    private static final int ALL_FISH_TARGET = 6;
    private static final int FISH_TYPE = 1;

    private long roundId;
    private long endTime;
    private int score;
    private int state = RoundState.PLAYING;
    private boolean quickClear;
    private boolean scoreCredited;
    private final Map<Integer, Integer> caughtCounts = new HashMap<>();
    private Map<Integer, Long> rewards;
    private long[] scoreFlat;
    private long[] scorePermille;

    public void addScoreBonus(int target, int flat, int permille) {
        if (target < 1 || target > ALL_FISH_TARGET) {
            return;
        }
        if (scoreFlat == null) {
            scoreFlat = new long[ALL_FISH_TARGET];
            scorePermille = new long[ALL_FISH_TARGET];
        }
        scoreFlat[target - 1] += flat;
        scorePermille[target - 1] += permille;
    }

    public long scoreFor(int fishId, int type, int baseScore) {
        if (scoreFlat == null) {
            return baseScore;
        }
        long flat = 0;
        long permille = 0;
        if (type == FISH_TYPE) {
            flat = scoreFlat[ALL_FISH_TARGET - 1];
            permille = scorePermille[ALL_FISH_TARGET - 1];
        }
        int index = fishId - FIRST_FISH_ID;
        if (index >= 0 && index < ALL_FISH_TARGET - 1) {
            flat += scoreFlat[index];
            permille += scorePermille[index];
        }
        return baseScore + flat + MiniGameSkillEffect.scaleByPermille(baseScore, permille);
    }

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

    public boolean isScoreCredited() {
        return scoreCredited;
    }

    public void markScoreCredited() {
        scoreCredited = true;
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
