package com.jjg.game.sim.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.pb.ItemInfo;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.sim.constant.SimConstant;

import java.util.List;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_GAME, cmd = SimConstant.MsgBean.RES_GUEST_OUTPUT_INFO, resp = true)
@ProtoDesc("游客产出信息")
public class ResGuestOutputInfo extends AbstractResponse {
    @ProtoDesc("当日总交互时长")
    public int allTime;
    @ProtoDesc("上限时长")
    public int timeLimit;
    @ProtoDesc("当日累计获得奖励数量")
    public List<ItemInfo> allRewards;
    @ProtoDesc("普通游客人数")
    public int normalGuestCount;
    @ProtoDesc("特殊游客人数")
    public int specialGuestCount;

    public ResGuestOutputInfo(int code) {
        super(code);
    }
}
