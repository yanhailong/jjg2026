package com.jjg.game.minigame.data;

import java.util.ArrayList;
import java.util.List;

/** 找老千单局内存状态，退出后丢弃，不保存进度。 */
public class CheaterHuntRound {
    public int randomEventConfigId;
    //本局ID，由玩家自己的对局序号生成
    private long roundId;
    //本局关卡配置ID，通关后仍保留用于返回结算信息
    private int levelId;
    //本局随机场景配置ID
    private int mapId;
    //本局作弊点位，下标区间为[0,场景总点位数)
    private List<Integer> cheaterSpots = new ArrayList<>();
    //本局已找到的作弊点位，不重复记录
    private List<Integer> foundSpots = new ArrayList<>();
    //截止时间，毫秒时间戳；-1表示不限时
    private long endTime;
    //对局状态：1进行中，2通关，3失败，4通关待发奖
    private int state;
    //失败奖励成功发放后置为true，避免同局重复结算
    private boolean failureRewardGranted;

    public long getRoundId() {
        return roundId;
    }

    public void setRoundId(long roundId) {
        this.roundId = roundId;
    }

    public int getLevelId() {
        return levelId;
    }

    public void setLevelId(int levelId) {
        this.levelId = levelId;
    }

    public int getMapId() {
        return mapId;
    }

    public void setMapId(int mapId) {
        this.mapId = mapId;
    }

    public List<Integer> getCheaterSpots() {
        return cheaterSpots;
    }

    public void setCheaterSpots(List<Integer> cheaterSpots) {
        this.cheaterSpots = cheaterSpots;
    }

    public List<Integer> getFoundSpots() {
        return foundSpots;
    }

    public void setFoundSpots(List<Integer> foundSpots) {
        this.foundSpots = foundSpots;
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
    }

    public boolean isFailureRewardGranted() {
        return failureRewardGranted;
    }

    public void setFailureRewardGranted(boolean failureRewardGranted) {
        this.failureRewardGranted = failureRewardGranted;
    }
}
