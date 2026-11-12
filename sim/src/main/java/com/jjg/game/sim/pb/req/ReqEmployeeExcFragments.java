package com.jjg.game.sim.pb.req;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractMessage;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.sim.constant.SimConstant;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_GAME, cmd = SimConstant.MsgBean.REQ_EMPLOYEE_EXCHANGE_FRAGMENTS)
@ProtoDesc("请求兑换雇员碎片")
public class ReqEmployeeExcFragments extends AbstractMessage {
    @ProtoDesc("雇员id")
    public int employeeId;
    @ProtoDesc("兑换的道具个数")
    public long count;
}
