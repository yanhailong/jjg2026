package com.jjg.game.sim.pb.req;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractMessage;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.sim.constant.SimConstant;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_GAME, cmd = SimConstant.MsgBean.REQ_BUY_MINI_SHOP)
@ProtoDesc("购买迷你商店商品（道具兑换时才使用）")
public class ReqBuyMiniShop extends AbstractMessage {
    @ProtoDesc("商品id")
    public String productId;
}
