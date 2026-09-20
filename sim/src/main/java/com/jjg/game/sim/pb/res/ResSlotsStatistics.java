package com.jjg.game.sim.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.sim.constant.SimConstant;
import com.jjg.game.sim.pb.struct.SlotsStatisticsInfo;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_GAME, cmd = SimConstant.MsgBean.RES_SLOTS_STATISTICS, resp = true)
@ProtoDesc("本轮slots统计信息返回")
public class ResSlotsStatistics extends AbstractResponse {
    @ProtoDesc("统计信息，无本轮统计时为空")
    public SlotsStatisticsInfo statistics;

    public ResSlotsStatistics(int code) {
        super(code);
    }
}
