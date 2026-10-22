package com.jjg.game.sim.pb.req;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractMessage;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.sim.constant.SimConstant;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_GAME, cmd = SimConstant.MsgBean.REQ_UPGRADE_EMPLOYEE_SKILL)
@ProtoDesc("升级雇员技能")
public class ReqUpgradeEmployeeSkill extends AbstractMessage {
    @ProtoDesc("雇员id")
    public int employeeId;
    @ProtoDesc("技能组id")
    public int groupId;
}
