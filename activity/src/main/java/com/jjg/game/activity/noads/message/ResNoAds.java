package com.jjg.game.activity.noads.message;

import com.jjg.game.activity.constant.ActivityConstant;
import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;

import java.util.List;

/**
 * 使用ReqActivityInfoByType(activityType=26)查询。
 * 购买使用ReqGenerateOrder(rechargeType=22, payType=1 Google/2 iOS)，无需productId；服务器按下单时的折扣时间选择价格。
 * 支付处理成功主动推送本协议，随后沿用通用NotifyPayInfo通知；不需要单独请求领奖。
 * 活动入口统一由gameFunction控制，购买成功通过NotifyOpenFunction关闭功能5019。
 */
@ProtobufMessage(messageType = MessageConst.MessageTypeDef.ACTIVITY, cmd = ActivityConstant.MsgBean.RES_NO_ADS, resp = true)
@ProtoDesc("免广告礼包信息及购买成功推送；查询activityType=26，下单rechargeType=22、payType=1 Google/2 iOS，无需productId，服务器按折扣时间选择价格，无单独领奖接口")
public class ResNoAds extends AbstractResponse {
    @ProtoDesc("免广告礼包详情；查询时已购买、活动未开放或参与条件未满足则为空，购买成功推送时包含礼包配置")
    public List<NoAdsDetailInfo> activityData = List.of();

    public ResNoAds(int code) {
        super(code);
    }
}
