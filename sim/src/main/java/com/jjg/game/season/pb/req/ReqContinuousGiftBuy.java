package com.jjg.game.season.pb.req;
import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractMessage;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.season.constant.SeasonConstant;
@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SEASON, cmd = SeasonConstant.MsgBean.REQ_CONTINUOUS_GIFT_BUY)
@ProtoDesc("连续礼包Buy")
public class ReqContinuousGiftBuy extends AbstractMessage {
    @ProtoDesc("活动ID") public int activityId;
    @ProtoDesc("宝箱天数，从1开始") public int dayIndex;
}
