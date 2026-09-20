package com.jjg.game.sim.data;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.HashMap;
import java.util.Map;

@Document
public class SlotsStatisticsData {
    @Id
    private long playerId;
    //spin的次数
    private int spinCount;
    //大奖统计次数
    private Map<Integer, Integer> bigWinCountMap;
    //触发免费的次数
    private int freeCount;
    //特殊模式的次数
    private int specialCount;
    //奖池次数
    private int jackpoolCount;
    //累计获得的金币
    private long allWinCoin;
    //累计下注金币
    private long allBetCoin;
    //累计消耗的体力值
    private int allSpendPower;
    //累计因体力额外获得的金币量
    private long allSpendPowerGetCoin;
    //该字段表示客户端是否请求过该统计信息
    private boolean clientReq;

    public long getPlayerId() {
        return playerId;
    }

    public void setPlayerId(long playerId) {
        this.playerId = playerId;
    }

    public int getSpinCount() {
        return spinCount;
    }

    public void setSpinCount(int spinCount) {
        this.spinCount = spinCount;
    }

    public Map<Integer, Integer> getBigWinCountMap() {
        return bigWinCountMap;
    }

    public void setBigWinCountMap(Map<Integer, Integer> bigWinCountMap) {
        this.bigWinCountMap = bigWinCountMap;
    }

    public int getFreeCount() {
        return freeCount;
    }

    public void setFreeCount(int freeCount) {
        this.freeCount = freeCount;
    }

    public int getSpecialCount() {
        return specialCount;
    }

    public void setSpecialCount(int specialCount) {
        this.specialCount = specialCount;
    }

    public int getJackpoolCount() {
        return jackpoolCount;
    }

    public void setJackpoolCount(int jackpoolCount) {
        this.jackpoolCount = jackpoolCount;
    }

    public long getAllWinCoin() {
        return allWinCoin;
    }

    public void setAllWinCoin(long allWinCoin) {
        this.allWinCoin = allWinCoin;
    }

    public long getAllBetCoin() {
        return allBetCoin;
    }

    public void setAllBetCoin(long allBetCoin) {
        this.allBetCoin = allBetCoin;
    }

    public int getAllSpendPower() {
        return allSpendPower;
    }

    public void setAllSpendPower(int allSpendPower) {
        this.allSpendPower = allSpendPower;
    }

    public long getAllSpendPowerGetCoin() {
        return allSpendPowerGetCoin;
    }

    public void setAllSpendPowerGetCoin(long allSpendPowerGetCoin) {
        this.allSpendPowerGetCoin = allSpendPowerGetCoin;
    }

    public void addBigWinCount(int bigWinShowType) {
        if(bigWinShowType < 1){
            return;
        }
        if (this.bigWinCountMap == null) {
            this.bigWinCountMap = new HashMap<>();
        }
        this.bigWinCountMap.merge(bigWinShowType, 1, Integer::sum);
    }

    public boolean isClientReq() {
        return clientReq;
    }

    public void setClientReq(boolean clientReq) {
        this.clientReq = clientReq;
    }
}
