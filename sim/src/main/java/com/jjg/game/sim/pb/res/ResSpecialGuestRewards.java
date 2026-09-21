package com.jjg.game.sim.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.pb.ItemInfo;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.sim.constant.SimConstant;

import java.util.List;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_GAME, cmd = SimConstant.MsgBean.RES_SPECIAL_GUEST_REWARDS, resp = true)
@ProtoDesc("领取特殊游客奖励返回")
public class ResSpecialGuestRewards extends AbstractResponse {
    @ProtoDesc("获得的奖励")
    public List<ItemInfo> items;

    public ResSpecialGuestRewards(int code) {
        super(code);
    }
}
