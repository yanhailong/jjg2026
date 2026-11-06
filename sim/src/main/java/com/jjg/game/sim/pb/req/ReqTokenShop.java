package com.jjg.game.sim.pb.req;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractMessage;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.sim.constant.SimConstant;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_GAME, cmd = SimConstant.MsgBean.REQ_TOKEN_SHOP)
@ProtoDesc("请求代币商店商品列表")
public class ReqTokenShop extends AbstractMessage {
    @ProtoDesc("商店类型：小于1选择第一个开启的商城，1/2=当前赛季商店，其他按TokenShop.type请求")
    public int type;
}
