package com.jjg.game.minigame.data;

import com.jjg.game.minigame.constant.MiniGameConstant.RoundState;

import java.util.Map;

/** 赌神搓牌单局，只保存在玩家会话；重新进入或退出会话即放弃。 */
public class GodGamblersRound {
    public int randomEventConfigId;
    //服务器局号，拒绝上一局迟到的取牌、结算请求
    private long roundId;
    //对手的牌开局固定，ID按黑桃、红桃、梅花、方块各13张，花色内2到A递增
    private int opponentPoker;
    //开局已决定自己的牌，首次取牌前只保存在服务端
    private int selfPoker;
    //已取自己牌的次数，首次计1，达到配置上限后仍需主动确认比拼
    private int rubCount;
    //进行中、待发奖或已结算；待发奖时禁止重新搓牌
    private int state = RoundState.PLAYING;
    //当前点差对应的奖励快照，避免开局后配置刷新改变本次已抽中的奖励
    private Map<Integer, Long> rewards;

    public long getRoundId() {
        return roundId;
    }

    public void setRoundId(long roundId) {
        this.roundId = roundId;
    }

    public int getOpponentPoker() {
        return opponentPoker;
    }

    public void setOpponentPoker(int opponentPoker) {
        this.opponentPoker = opponentPoker;
    }

    public int getSelfPoker() {
        return selfPoker;
    }

    public void setSelfPoker(int selfPoker) {
        this.selfPoker = selfPoker;
    }

    public int getRubCount() {
        return rubCount;
    }

    public void setRubCount(int rubCount) {
        this.rubCount = rubCount;
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }

    public Map<Integer, Long> getRewards() {
        return rewards;
    }

    public void setRewards(Map<Integer, Long> rewards) {
        this.rewards = rewards;
    }
}
