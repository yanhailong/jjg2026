package com.jjg.game.season.data;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** 小额抽奖玩家状态，嵌入赛季聚合并随赛季重置。 */
public class SeasonSmallDrawData {
    private int drawId;
    private int purchaseChances;
    private int lastLoginDay;
    private int dailyRewardDay;
    private Map<Integer, Integer> selectedOptions = new HashMap<>();
    private Set<Integer> obtainedRewards = new HashSet<>();
    private Set<String> receivedOrders = new HashSet<>();
    private int nonMoneyPurchaseCount;

    /** 已购买未抽奖次数；非充值购买只累计次数，不保存订单记录。 */
    public int drawChances() { return Math.max(0, receivedOrders.size() + nonMoneyPurchaseCount - obtainedRewards.size()); }

    public int getDrawId() { return drawId; }
    public void setDrawId(int drawId) { this.drawId = drawId; }
    public int getPurchaseChances() { return purchaseChances; }
    public void setPurchaseChances(int purchaseChances) { this.purchaseChances = purchaseChances; }
    public int getLastLoginDay() { return lastLoginDay; }
    public void setLastLoginDay(int lastLoginDay) { this.lastLoginDay = lastLoginDay; }
    public int getDailyRewardDay() { return dailyRewardDay; }
    public void setDailyRewardDay(int dailyRewardDay) { this.dailyRewardDay = dailyRewardDay; }
    public Map<Integer, Integer> getSelectedOptions() { return selectedOptions; }
    public void setSelectedOptions(Map<Integer, Integer> selectedOptions) { this.selectedOptions = selectedOptions; }
    public Set<Integer> getObtainedRewards() { return obtainedRewards; }
    public void setObtainedRewards(Set<Integer> obtainedRewards) { this.obtainedRewards = obtainedRewards; }
    public Set<String> getReceivedOrders() { return receivedOrders; }
    public void setReceivedOrders(Set<String> receivedOrders) { this.receivedOrders = receivedOrders; }
    public int getNonMoneyPurchaseCount() { return nonMoneyPurchaseCount; }
    public void setNonMoneyPurchaseCount(int nonMoneyPurchaseCount) { this.nonMoneyPurchaseCount = nonMoneyPurchaseCount; }
}
