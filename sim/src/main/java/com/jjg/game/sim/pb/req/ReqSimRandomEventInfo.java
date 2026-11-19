package com.jjg.game.sim.pb.req;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractMessage;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.sim.constant.SimConstant;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_GAME, cmd = SimConstant.MsgBean.REQ_RANDOM_EVENT_INFO)
@ProtoDesc("按配置ID查询当前场景的随机事件")
public class ReqSimRandomEventInfo extends AbstractMessage {
    @ProtoDesc("当前场景中的事件配置ID，对应RandomEventDetails.id")
    public int configId;
}
