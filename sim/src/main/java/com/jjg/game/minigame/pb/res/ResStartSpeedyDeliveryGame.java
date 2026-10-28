package com.jjg.game.minigame.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.minigame.constant.MiniGameConstant;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_MINI_GAME, cmd = MiniGameConstant.MsgBean.RES_START_SPEEDY_DELIVERY_GAME, resp = true)
@ProtoDesc("开始飞驰快送结果")
public class ResStartSpeedyDeliveryGame extends AbstractResponse {
    @ProtoDesc("游戏截止时间，毫秒时间戳；结束请求允许延迟2秒")
    public long endTime;

    public ResStartSpeedyDeliveryGame(int code) {
        super(code);
    }
}
