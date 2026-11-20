package com.jjg.game.minigame.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.minigame.constant.MiniGameConstant;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_MINI_GAME, cmd = MiniGameConstant.MsgBean.RES_SET_SPEEDY_DELIVERY_PAUSED, resp = true)
@ProtoDesc("飞驰快送暂停或恢复结果")
public class ResSetSpeedyDeliveryPaused extends AbstractResponse {
    @ProtoDesc("是否已暂停")
    public boolean paused;
    @ProtoDesc("剩余游戏时间，毫秒")
    public long remainingMillis;
    @ProtoDesc("游戏截止时间，毫秒时间戳；暂停时为0")
    public long endTime;

    public ResSetSpeedyDeliveryPaused(int code) {
        super(code);
    }
}
