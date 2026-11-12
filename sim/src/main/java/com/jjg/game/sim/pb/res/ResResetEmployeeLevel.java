package com.jjg.game.sim.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.pb.ItemInfo;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.sim.constant.SimConstant;

import java.util.List;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_GAME, cmd = SimConstant.MsgBean.RES_RESET_EMPLOYEE_LEVEL, resp = true)
@ProtoDesc("重置雇员等级返回")
public class ResResetEmployeeLevel extends AbstractResponse {
    @ProtoDesc("雇员id")
    public int employeeId;
    @ProtoDesc("重置后的等级")
    public int level;
    @ProtoDesc("返还的升级道具")
    public List<ItemInfo> refundItems;

    public ResResetEmployeeLevel(int code) {
        super(code);
    }
}
