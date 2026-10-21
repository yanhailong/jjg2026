package com.jjg.game.minigame.pb.req;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractMessage;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.minigame.constant.MiniGameConstant;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_MINI_GAME, cmd = MiniGameConstant.MsgBean.REQ_FIND_CHEATER_HUNT_SPOT)
@ProtoDesc("寻找作弊点位或触发超时失败结算；回合内不再消耗道具，待发奖时仅重试结算")
public class ReqFindCheaterHuntSpot extends AbstractMessage {
    @ProtoDesc("本次单局标识")
    public long roundId;
    @ProtoDesc("点击点位，范围[0,TotalSpotCount)；服务器判定已超时时仅结算，不校验点位")
    public int spotId;
}
