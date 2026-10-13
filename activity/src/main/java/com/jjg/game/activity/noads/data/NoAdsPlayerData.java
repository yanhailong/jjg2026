package com.jjg.game.activity.noads.data;

import com.jjg.game.activity.common.data.PlayerActivityData;
import com.jjg.game.activity.constant.ActivityConstant;

/** 赠品交付记录；权益落库失败后重试同一订单时不重复发放赠品。 */
public class NoAdsPlayerData extends PlayerActivityData {
    private String orderId;

    public NoAdsPlayerData() {
    }

    public NoAdsPlayerData(long activityId, long round, String orderId) {
        super(activityId, round);
        this.orderId = orderId;
        setClaimStatus(ActivityConstant.ClaimStatus.PURCHASED);
    }

    public String getOrderId() {
        return orderId;
    }
}
