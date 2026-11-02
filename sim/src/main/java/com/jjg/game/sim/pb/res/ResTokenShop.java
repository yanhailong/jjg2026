package com.jjg.game.sim.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.sim.constant.SimConstant;
import com.jjg.game.sim.pb.struct.TokenShopGoodsInfo;

import java.util.List;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_GAME, cmd = SimConstant.MsgBean.RES_TOKEN_SHOP, resp = true)
@ProtoDesc("代币商店商品列表")
public class ResTokenShop extends AbstractResponse {
    @ProtoDesc("请求的商店类型：1=赛季，3=联盟，4=钻石")
    public int type;
    @ProtoDesc("已按Order升序排列的开放商品，新手赛季返回空列表")
    public List<TokenShopGoodsInfo> goods;

    public ResTokenShop(int code) {
        super(code);
    }
}
