package com.jjg.game.sim.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.sim.constant.SimConstant;
import com.jjg.game.sim.pb.struct.RandomEventInfo;

import java.util.List;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_GAME, cmd = SimConstant.MsgBean.NOTIFY_RANDOM_EVENTS, resp = true)
@ProtoDesc("通知自己场景新增随机事件")
public class NotifySimRandomEvents extends AbstractResponse {
    @ProtoDesc("本次新增的系统事件和好友投放事件")
    public List<RandomEventInfo> events;

    public NotifySimRandomEvents(int code) {
        super(code);
    }
}
