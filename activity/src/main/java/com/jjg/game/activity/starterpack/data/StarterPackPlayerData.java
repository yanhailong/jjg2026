package com.jjg.game.activity.starterpack.data;

import com.jjg.game.activity.common.data.PlayerActivityData;

public class StarterPackPlayerData extends PlayerActivityData {
    private String orderId;
    // 按顺序一次领取全部已解锁奖励，只需保存已领取的档数。
    private int claimedDays;

    public StarterPackPlayerData() {
    }

    public StarterPackPlayerData(long activityId, long round, String orderId) {
        super(activityId, round);
        this.orderId = orderId;
    }

    public String getOrderId() {
        return orderId;
    }

    public int getClaimedDays() {
        return claimedDays;
    }

    public void setClaimedDays(int claimedDays) {
        this.claimedDays = claimedDays;
    }
}
