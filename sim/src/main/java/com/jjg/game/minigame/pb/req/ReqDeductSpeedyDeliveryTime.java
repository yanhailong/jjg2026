package com.jjg.game.minigame.pb.req;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractMessage;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.minigame.constant.MiniGameConstant;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_MINI_GAME, cmd = MiniGameConstant.MsgBean.REQ_DEDUCT_SPEEDY_DELIVERY_TIME)
@ProtoDesc("飞驰快送扣除时间")
public class ReqDeductSpeedyDeliveryTime extends AbstractMessage {
    @ProtoDesc("扣除的秒数，必须大于0")
    public int seconds;
}
