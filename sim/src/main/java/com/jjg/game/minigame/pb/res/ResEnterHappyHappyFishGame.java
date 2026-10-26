package com.jjg.game.minigame.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.pb.ItemInfo;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.minigame.constant.MiniGameConstant;

import java.util.List;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_MINI_GAME, cmd = MiniGameConstant.MsgBean.RES_ENTER_HAPPY_HAPPY_FISH_GAME, resp = true)
@ProtoDesc("进入欢乐捉鱼结果")
public class ResEnterHappyHappyFishGame extends AbstractResponse {
    @ProtoDesc("每局消耗的配置道具")
    public List<ItemInfo> costItems;
    @ProtoDesc("各消耗道具当前持有总量")
    public List<ItemInfo> ownedItems;
    @ProtoDesc("是否已开放一键通关")
    public boolean openOneClickPass;

    public ResEnterHappyHappyFishGame(int code) {
        super(code);
    }
}
