package com.jjg.game.sim.pb.req;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractMessage;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.sim.constant.SimConstant;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_GAME, cmd = SimConstant.MsgBean.REQ_MINI_SHOP)
@ProtoDesc("请求迷你商店")
public class ReqMiniShop extends AbstractMessage {
    @ProtoDesc("缺少的道具ID，对应ShopMini.needItemId")
    public int itemId;
}
