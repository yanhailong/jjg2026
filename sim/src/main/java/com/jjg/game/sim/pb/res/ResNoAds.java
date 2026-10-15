package com.jjg.game.sim.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.pb.ItemInfo;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.core.pb.RechargeInfo;
import com.jjg.game.sim.constant.SimConstant;

import java.util.List;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_GAME, cmd = SimConstant.MsgBean.RES_NO_ADS_INFO, resp = true)
@ProtoDesc("免广告信息：查询返回及购买成功推送；入口开放状态统一通过gameFunction获取")
public class ResNoAds extends AbstractResponse {
    @ProtoDesc("购买赠送的道具")
    public List<ItemInfo> items;
    @ProtoDesc("折扣结束时间戳，单位毫秒")
    public long discountEndTime;
    @ProtoDesc("折扣百分比")
    public String discountPercent;
    @ProtoDesc("折扣价")
    public String discountPrice;
    @ProtoDesc("原价")
    public String originalPrice;
    @ProtoDesc("当前展示的付费商品信息，含价格和玩家渠道商品ID；实际下单价格由服务器按下单时的折扣时间选择，无需传productId或detailId")
    public RechargeInfo rechargeInfo;
    @ProtoDesc("支付成功推送时的服务器订单ID，与NotifyPayInfo.orderId一致，可据此识别及去重支付事件；普通查询为空")
    public String orderId;

    public ResNoAds(int code) {
        super(code);
    }
}
