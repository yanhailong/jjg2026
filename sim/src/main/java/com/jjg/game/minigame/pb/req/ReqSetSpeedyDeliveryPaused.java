package com.jjg.game.minigame.pb.req;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractMessage;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.minigame.constant.MiniGameConstant;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_MINI_GAME, cmd = MiniGameConstant.MsgBean.REQ_SET_SPEEDY_DELIVERY_PAUSED)
@ProtoDesc("飞驰快送暂停或恢复")
public class ReqSetSpeedyDeliveryPaused extends AbstractMessage {
    @ProtoDesc("true暂停当前局，false恢复当前局")
    public boolean paused;
}
