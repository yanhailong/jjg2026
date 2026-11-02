package com.jjg.game.sim.pb.req;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractMessage;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.sim.constant.SimConstant;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_GAME, cmd = SimConstant.MsgBean.REQ_TOKEN_SHOP_EXCHANGE)
@ProtoDesc("请求代币商店兑换")
public class ReqTokenShopExchange extends AbstractMessage {
    @ProtoDesc("TokenShop商品ID")
    public int goodsId;
    @ProtoDesc("兑换份数，必须大于0")
    public int count;
}
