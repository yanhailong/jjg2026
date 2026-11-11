package com.jjg.game.minigame.pb.req;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractMessage;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.minigame.constant.MiniGameConstant;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_MINI_GAME, cmd = MiniGameConstant.MsgBean.REQ_STOCK_SPEEDY_DELIVERY_SHELF)
@ProtoDesc("温馨酒馆货物上架")
public class ReqStockSpeedyDeliveryShelf extends AbstractMessage {
    @ProtoDesc("类型 对应Minigamegoods.type")
    public int type;
    @ProtoDesc("货架格子ID，0至14")
    public int slotId;
    @ProtoDesc("Minigamegoods配置ID")
    public int goodsId;
}
