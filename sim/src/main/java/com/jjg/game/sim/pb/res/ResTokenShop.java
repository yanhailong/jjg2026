package com.jjg.game.sim.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.core.pb.KVInfo;
import com.jjg.game.sim.constant.SimConstant;
import com.jjg.game.sim.pb.struct.TokenShopGoodsInfo;

import java.util.List;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_GAME, cmd = SimConstant.MsgBean.RES_TOKEN_SHOP, resp = true)
@ProtoDesc("代币商店商品列表")
public class ResTokenShop extends AbstractResponse {
    @ProtoDesc("商店类型：1=当前赛季商店，其他按TokenShop.type返回")
    public int type;
    @ProtoDesc("已按Order升序排列的开放商品，新手赛季返回空列表")
    public List<TokenShopGoodsInfo> goods;
    @ProtoDesc("该商店消耗的货币类型的道具id")
    public int costItemId;
    @ProtoDesc("拥有该类型货币的数量")
    public long hasItemCount;
    @ProtoDesc("商店类型id -> 多语言id，赛季商店统一为1且仅返回一项，不保证顺序")
    public List<KVInfo> typeList;

    public ResTokenShop(int code) {
        super(code);
    }
}
