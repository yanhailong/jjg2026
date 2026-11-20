package com.jjg.game.minigame.data;

import com.jjg.game.core.constant.Code;
import com.jjg.game.minigame.constant.MiniGameConstant.RoundState;

/** 会话内限时对局，暂停时冻结倒计时。 */
public abstract class TimedMiniGameRound {
    private long endTime;
    private long pausedAt;
    private int state = RoundState.PLAYING;

    public int setPaused(boolean paused, long now) {
        if (isPaused()) {
            if (!paused) {
                endTime = now + getRemainingMillis(now);
                setState(RoundState.PLAYING);
            }
            return Code.SUCCESS;
        }
        if (state != RoundState.PLAYING) {
            return Code.REPEAT_OP;
        }
        if (now >= endTime) {
            return Code.EXPIRE;
        }
        if (paused) {
            pausedAt = now;
            state = RoundState.PAUSED;
        }
        return Code.SUCCESS;
    }

    public boolean isPaused() {
        return state == RoundState.PAUSED;
    }

    public long getRemainingMillis(long now) {
        return Math.max(0L, endTime - (isPaused() ? pausedAt : now));
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
        if (state != RoundState.PAUSED) {
            pausedAt = 0;
        }
    }
}
