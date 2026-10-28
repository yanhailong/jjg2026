package com.jjg.game.minigame.pb.req;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractMessage;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.minigame.constant.MiniGameConstant;

import java.util.List;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_MINI_GAME, cmd = MiniGameConstant.MsgBean.REQ_DELIVER_SPEEDY_DELIVERY_GOODS)
@ProtoDesc("飞驰快送交货")
public class ReqDeliverSpeedyDeliveryGoods extends AbstractMessage {
    @ProtoDesc("本次交货的Minigamegoods ID列表；同种货物可重复，数量不超过背包容量")
    public List<Integer> goodsIds;
    @ProtoDesc("MinigameTruck配置ID")
    public int truckId;
}
