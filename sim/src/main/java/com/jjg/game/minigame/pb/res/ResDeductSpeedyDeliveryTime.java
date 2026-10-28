package com.jjg.game.minigame.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.minigame.constant.MiniGameConstant;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_MINI_GAME, cmd = MiniGameConstant.MsgBean.RES_DEDUCT_SPEEDY_DELIVERY_TIME, resp = true)
@ProtoDesc("飞驰快送扣除时间结果")
public class ResDeductSpeedyDeliveryTime extends AbstractResponse {
    @ProtoDesc("扣时后的截止时间，毫秒时间戳")
    public long endTime;

    public ResDeductSpeedyDeliveryTime(int code) {
        super(code);
    }
}
