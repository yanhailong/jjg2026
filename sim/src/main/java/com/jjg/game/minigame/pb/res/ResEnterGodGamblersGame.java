package com.jjg.game.minigame.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.pb.ItemInfo;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.minigame.constant.MiniGameConstant;

import java.util.List;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_MINI_GAME, cmd = MiniGameConstant.MsgBean.RES_ENTER_GOD_GAMBLERS_GAME, resp = true)
@ProtoDesc("进入赌神搓牌小游戏结果")
public class ResEnterGodGamblersGame extends AbstractResponse {
    @ProtoDesc("每次开始或再次搓牌消耗的配置道具")
    public List<ItemInfo> costItems;
    @ProtoDesc("各消耗道具当前背包持有数量")
    public List<ItemInfo> ownedItems;
    @ProtoDesc("每局最多获取自己牌次数，含首次")
    public int maxRubCount;
    @ProtoDesc("是否已开放一键比拼")
    public boolean openOneClickPass;

    public ResEnterGodGamblersGame(int code) {
        super(code);
    }
}
