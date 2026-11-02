package com.jjg.game.sim.data;

/** 玩家单个商品的限购记录，周期以开服当天的 0 点为起点。 */
public class TokenShopPurchaseData {
    //本周期起始日（epoch day）；不重置商品固定为0
    private long periodStartDay;
    private int purchased;

    public long getPeriodStartDay() {
        return periodStartDay;
    }

    public void setPeriodStartDay(long periodStartDay) {
        this.periodStartDay = periodStartDay;
    }

    public int getPurchased() {
        return purchased;
    }

    public void setPurchased(int purchased) {
        this.purchased = purchased;
    }
}
