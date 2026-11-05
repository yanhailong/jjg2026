package com.jjg.game.core.data;

import com.jjg.game.core.pb.RechargeCfgType;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 于下订单的信息
 */
public class PreGenerateOrderData {
    //付费类型 1.付费  2.道具兑换  3.广告  4.免费
    private RechargeCfgType type;
    //价格
    private BigDecimal price;
    //ReqGenerateOrder.payType  支付方式  1.google   2.ios
    private int payType;
    //ReqGenerateOrder.productId  商品id
    private String productId;
    //ReqGenerateOrder.desc  json格式备注
    private String desc;
    //其他
    private String orderProductId;
    // 道具兑换的实际购买份数及已扣消耗，仅用于本次购买交付和失败返还。
    private int buyCount = 1;
    private Map<Integer, Long> exchangeCost;

    public int getBuyCount() {
        return type == RechargeCfgType.ITEM_EXCHANGE ? Math.max(1, buyCount) : 1;
    }

    public void setBuyCount(int buyCount) {
        this.buyCount = buyCount;
    }

    public Map<Integer, Long> getExchangeCost() {
        return exchangeCost;
    }

    public void setExchangeCost(Map<Integer, Long> exchangeCost) {
        this.exchangeCost = exchangeCost;
    }

    public RechargeCfgType getType() {
        return type;
    }

    public void setType(RechargeCfgType type) {
        this.type = type;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public int getPayType() {
        return payType;
    }

    public void setPayType(int payType) {
        this.payType = payType;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public String getOrderProductId() {
        return orderProductId;
    }

    public void setOrderProductId(String orderProductId) {
        this.orderProductId = orderProductId;
    }
}
