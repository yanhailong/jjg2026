package com.jjg.game.minigame.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.minigame.constant.MiniGameConstant;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_MINI_GAME, cmd = MiniGameConstant.MsgBean.RES_STOCK_SPEEDY_DELIVERY_SHELF, resp = true)
@ProtoDesc("温馨酒馆货物上架结果")
public class ResStockSpeedyDeliveryShelf extends AbstractResponse {
    public ResStockSpeedyDeliveryShelf(int code) {
        super(code);
    }
}
