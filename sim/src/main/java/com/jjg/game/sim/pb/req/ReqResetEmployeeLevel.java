package com.jjg.game.sim.pb.req;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractMessage;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.sim.constant.SimConstant;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_GAME, cmd = SimConstant.MsgBean.REQ_RESET_EMPLOYEE_LEVEL)
@ProtoDesc("重置雇员等级")
public class ReqResetEmployeeLevel extends AbstractMessage {
    @ProtoDesc("雇员id")
    public int employeeId;
    @ProtoDesc("是否仅预览重置结果")
    public boolean preview;
}
