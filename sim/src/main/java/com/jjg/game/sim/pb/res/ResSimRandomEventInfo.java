package com.jjg.game.sim.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.sim.constant.SimConstant;
import com.jjg.game.sim.pb.struct.RandomEventInfo;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_GAME, cmd = SimConstant.MsgBean.RES_RANDOM_EVENT_INFO, resp = true)
@ProtoDesc("当前场景的单个随机事件详情")
public class ResSimRandomEventInfo extends AbstractResponse {
    @ProtoDesc("查询成功时返回的事件状态")
    public RandomEventInfo event;

    public ResSimRandomEventInfo(int code) {
        super(code);
    }
}
