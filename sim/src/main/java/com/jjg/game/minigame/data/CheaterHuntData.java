package com.jjg.game.minigame.data;

import com.jjg.game.minigame.constant.MiniGameConstant;
import com.jjg.game.sim.data.AbstractData;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.HashSet;
import java.util.Set;

/** 找老千小游戏的玩家进度、每日赠送标记和对局序号。 */
@Document
public class CheaterHuntData extends AbstractData {
    //玩家ID，同时作为文档主键
    @Id
    private long playerId;
    //当前已解锁关卡配置ID，首次从1001开始
    private int levelId = MiniGameConstant.FIRST_CHEATER_HUNT_LEVEL_ID;
    //实际通关过的关卡，解锁不会记为通关
    private Set<Integer> passedLevels = new HashSet<>();
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

    public Set<Integer> getPassedLevels() {
        return passedLevels;
    }

    public void setPassedLevels(Set<Integer> passedLevels) {
        this.passedLevels = passedLevels;
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
