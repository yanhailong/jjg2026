package com.jjg.game.season.pb.req;
import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractMessage;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.season.constant.SeasonConstant;
@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SEASON, cmd = SeasonConstant.MsgBean.REQ_CONTINUOUS_GIFT_LIST)
@ProtoDesc("连续礼包List")
public class ReqContinuousGiftList extends AbstractMessage {
    @ProtoDesc("0查询所有入口；非0进入该活动并清除红点") public int activityId;
}
