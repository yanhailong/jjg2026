package com.jjg.game.sim.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.pb.ItemInfo;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.sim.constant.SimConstant;

import java.util.List;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_GAME, cmd = SimConstant.MsgBean.RES_MINI_SHOP, resp = true)
@ProtoDesc("迷你商店返回")
public class ResMiniShop extends AbstractResponse {
    @ProtoDesc("获得的道具")
    public List<ItemInfo> items;
    @ProtoDesc("售价")
    public String price;
    @ProtoDesc("渠道商品ID")
    public String productId;
    @ProtoDesc("兑换使用的道具")
    public List<ItemInfo> costItems;

    public ResMiniShop(int code) {
        super(code);
    }
}
