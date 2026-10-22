package com.jjg.game.sim.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.sim.constant.SimConstant;
import com.jjg.game.sim.pb.struct.EmployDetailInfo;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_GAME, cmd = SimConstant.MsgBean.RES_UPGRADE_EMPLOYEE_SKILL, resp = true)
@ProtoDesc("升级雇员技能返回")
public class ResUpgradeEmployeeSkill extends AbstractResponse {
    @ProtoDesc("雇员id")
    public int employeeId;
    @ProtoDesc("技能组id")
    public int groupId;
    @ProtoDesc("升级后的雇员详情")
    public EmployDetailInfo employee;
    @ProtoDesc("当前场景雇员总战力")
    public long totalCombatPower;

    public ResUpgradeEmployeeSkill(int code) {
        super(code);
    }
}
