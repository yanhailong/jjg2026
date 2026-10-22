package com.jjg.game.sim.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.sim.constant.SimConstant;
import com.jjg.game.sim.pb.struct.EmployDetailInfo;

/**
 * @author 11
 * @date 2026/5/28
 */
@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_GAME, cmd = SimConstant.MsgBean.RES_ASSIGN_SUPERVISOR, resp = true)
@ProtoDesc("任命主管返回")
public class ResAssignSupervisor extends AbstractResponse {
    @ProtoDesc("雇员id")
    public int employeeId;
    @ProtoDesc("当前场景的雇员详情")
    public EmployDetailInfo employee;
    @ProtoDesc("当前场景雇员总战力")
    public long totalCombatPower;

    public ResAssignSupervisor(int code) {
        super(code);
    }
}
