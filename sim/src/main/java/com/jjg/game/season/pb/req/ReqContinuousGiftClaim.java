package com.jjg.game.season.pb.req;
import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractMessage;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.season.constant.SeasonConstant;
@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SEASON, cmd = SeasonConstant.MsgBean.REQ_CONTINUOUS_GIFT_CLAIM)
@ProtoDesc("连续礼包Claim")
public class ReqContinuousGiftClaim extends AbstractMessage {
    @ProtoDesc("活动ID") public int activityId;
}
