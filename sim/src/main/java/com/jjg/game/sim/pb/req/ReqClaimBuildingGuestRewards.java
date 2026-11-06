package com.jjg.game.sim.pb.req;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractMessage;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.sim.constant.SimConstant;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_GAME, cmd = SimConstant.MsgBean.REQ_CLAIM_BUILDING_GUEST_REWARDS)
@ProtoDesc("领取当前场景建筑累计的游客奖励")
public class ReqClaimBuildingGuestRewards extends AbstractMessage {
    @ProtoDesc("建筑id，0表示领取当前场景所有建筑的游客奖励")
    public int buildingId;
}
