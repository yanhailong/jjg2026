package com.jjg.game.sim.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.sim.constant.SimConstant;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_GAME, cmd = SimConstant.MsgBean.RES_EMPLOYEE_EXCHANGE_FRAGMENTS, resp = true)
@ProtoDesc("兑换雇员碎片返回")
public class ResEmployeeExcFragments extends AbstractResponse {
    public ResEmployeeExcFragments(int code) {
        super(code);
    }
}
