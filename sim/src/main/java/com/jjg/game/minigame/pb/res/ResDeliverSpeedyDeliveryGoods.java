package com.jjg.game.minigame.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.minigame.constant.MiniGameConstant;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_MINI_GAME, cmd = MiniGameConstant.MsgBean.RES_DELIVER_SPEEDY_DELIVERY_GOODS, resp = true)
@ProtoDesc("飞驰快送交货结果")
public class ResDeliverSpeedyDeliveryGoods extends AbstractResponse {
    @ProtoDesc("本次交货积分，合计后向上取整")
    public int deliveryScore;
    @ProtoDesc("服务器当前累计积分")
    public int score;

    public ResDeliverSpeedyDeliveryGoods(int code) {
        super(code);
    }
}
