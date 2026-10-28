package com.jjg.game.minigame.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.pb.ItemInfo;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.minigame.constant.MiniGameConstant;

import java.util.List;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_MINI_GAME, cmd = MiniGameConstant.MsgBean.RES_ENTER_SPEEDY_DELIVERY_GAME, resp = true)
@ProtoDesc("进入飞驰快送结果")
public class ResEnterSpeedyDeliveryGame extends AbstractResponse {
    @ProtoDesc("每局消耗的配置道具")
    public List<ItemInfo> costItems;
    @ProtoDesc("各消耗道具当前持有总量")
    public List<ItemInfo> ownedItems;
    @ProtoDesc("是否已开放一键通关")
    public boolean openOneClickPass;

    public ResEnterSpeedyDeliveryGame(int code) {
        super(code);
    }
}
