package com.jjg.game.minigame.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.pb.ItemInfo;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.minigame.constant.MiniGameConstant;
import com.jjg.game.minigame.pb.struct.SpeedyDeliveryShelfGoods;

import java.util.List;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_MINI_GAME, cmd = MiniGameConstant.MsgBean.RES_SPEEDY_DELIVERY_GOODS_INFO, resp = true)
@ProtoDesc("飞驰快送仓库和温馨酒馆货架库存")
public class ResSpeedyDeliveryGoodsInfo extends AbstractResponse {
    @ProtoDesc("请求类型的仓库货物，itemId为Minigamegoods配置ID")
    public List<ItemInfo> houseGoods;
    @ProtoDesc("请求类型的已上架货物，空格子省略")
    public List<SpeedyDeliveryShelfGoods> goods;

    public ResSpeedyDeliveryGoodsInfo(int code) {
        super(code);
    }
}
