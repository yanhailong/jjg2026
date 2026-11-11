package com.jjg.game.minigame.pb.req;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractMessage;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.minigame.constant.MiniGameConstant;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_MINI_GAME, cmd = MiniGameConstant.MsgBean.REQ_SPEEDY_DELIVERY_GOODS_INFO)
@ProtoDesc("查询飞驰快送仓库和温馨酒馆货架库存")
public class ReqSpeedyDeliveryGoodsInfo extends AbstractMessage {
    @ProtoDesc("类型 对应Minigamegoods.type")
    public int type;
}
