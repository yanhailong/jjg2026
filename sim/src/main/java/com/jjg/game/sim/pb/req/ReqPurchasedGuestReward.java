package com.jjg.game.sim.pb.req;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractMessage;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.sim.constant.SimConstant;

/**
 * @author 11
 * @date 2026/6/15
 */
@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_GAME, cmd = SimConstant.MsgBean.REQ_PURCHASED_GUEST_REWARD)
@ProtoDesc("确认购买游客到达目的地，将该点奖励存入建筑")
public class ReqPurchasedGuestReward extends AbstractMessage {
    @ProtoDesc("购买游客唯一id")
    public String uid;
    @ProtoDesc("目的地序号 (GuestInfo.destinations 下标)")
    public int index;
}
