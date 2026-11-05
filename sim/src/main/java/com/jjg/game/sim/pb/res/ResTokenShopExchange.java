package com.jjg.game.sim.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.pb.ItemInfo;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.sim.constant.SimConstant;
import com.jjg.game.sim.pb.struct.TokenShopGoodsInfo;

import java.util.List;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_GAME, cmd = SimConstant.MsgBean.RES_TOKEN_SHOP_EXCHANGE, resp = true)
@ProtoDesc("代币商店购买成功推送")
public class ResTokenShopExchange extends AbstractResponse {
    @ProtoDesc("TokenShop商品ID")
    public int goodsId;
    @ProtoDesc("本次实际获得的道具")
    public List<ItemInfo> goods;
    @ProtoDesc("成功后的商品及限购状态")
    public TokenShopGoodsInfo info;

    public ResTokenShopExchange(int code) {
        super(code);
    }
}
