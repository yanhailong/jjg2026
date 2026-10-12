package com.jjg.game.core.data;

import com.jjg.game.core.pb.RechargeCfgType;

import java.math.BigDecimal;

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
}
