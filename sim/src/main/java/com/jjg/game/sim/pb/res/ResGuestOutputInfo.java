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
    @ProtoDesc("当日累计在线时长（秒，与游客产出时长限制口径一致）")
    public int allTime;
    @ProtoDesc("当前场景游客产出上限时长（秒）")
    public int timeLimit;
    @ProtoDesc("当日累计获得奖励数量")
    public List<ItemInfo> allRewards;
    @ProtoDesc("当日普通游客生成人数")
    public int normalGuestCount;
    @ProtoDesc("当日特殊游客生成人数")
    public int specialGuestCount;

    public ResGuestOutputInfo(int code) {
        super(code);
    }
}
