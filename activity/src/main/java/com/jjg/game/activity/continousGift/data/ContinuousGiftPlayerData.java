package com.jjg.game.activity.continousGift.data;

import com.jjg.game.activity.common.data.PlayerActivityData;

public class ContinuousGiftPlayerData extends PlayerActivityData {
    private long purchaseTime;
    private String orderId;

    public ContinuousGiftPlayerData() {
    }

    public ContinuousGiftPlayerData(long activityId, long round) {
        super(activityId, round);
    }

    public long getPurchaseTime() {
        return purchaseTime;
    }

    public void setPurchaseTime(long purchaseTime) {
        this.purchaseTime = purchaseTime;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }
}
