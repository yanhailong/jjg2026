package com.jjg.game.minigame.data;

import com.jjg.game.minigame.constant.MiniGameConstant;
import com.jjg.game.sim.data.AbstractData;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** 找老千小游戏的玩家进度、每日赠送标记和对局序号。 */
@Document
public class CheaterHuntData extends AbstractData {
    //玩家ID，同时作为文档主键
    @Id
    private long playerId;
    //当前关卡配置ID，首次从1001开始
    private int levelId = MiniGameConstant.FIRST_CHEATER_HUNT_LEVEL_ID;
    //当前关卡已通关次数
    private int clearCount;
    //上次每日道具赠送时间，毫秒时间戳；0表示尚未赠送
    private long lastDailyGrantTime;
    //玩家对局递增序号，用于区分新旧对局
    private long roundSequence;

    public long getPlayerId() {
        return playerId;
    }

    public void setPlayerId(long playerId) {
        this.playerId = playerId;
    }

    public int getLevelId() {
        return levelId;
    }

    public void setLevelId(int levelId) {
        this.levelId = levelId;
    }

    public int getClearCount() {
        return clearCount;
    }

    public void setClearCount(int clearCount) {
        this.clearCount = clearCount;
    }

    public long getLastDailyGrantTime() {
        return lastDailyGrantTime;
    }

    public void setLastDailyGrantTime(long lastDailyGrantTime) {
        this.lastDailyGrantTime = lastDailyGrantTime;
    }

    public long getRoundSequence() {
        return roundSequence;
    }

    public void setRoundSequence(long roundSequence) {
        this.roundSequence = roundSequence;
    }
}
