package com.jjg.game.sim.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.pb.ItemInfo;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.sim.constant.SimConstant;

import java.util.List;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_GAME, cmd = SimConstant.MsgBean.RES_BUY_MINI_SHOP, resp = true)
@ProtoDesc("购买迷你商店商品返回")
public class ResBuyMiniShop extends AbstractResponse {
    @ProtoDesc("迷你商店商品ID")
    public String productId;
    @ProtoDesc("获得的道具")
    public List<ItemInfo> items;

    public ResBuyMiniShop(int code) {
        super(code);
    }
}
