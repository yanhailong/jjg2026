package com.jjg.game.minigame.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.pb.ItemInfo;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.minigame.constant.MiniGameConstant;
import com.jjg.game.minigame.pb.struct.GodGamblersInfo;

import java.util.List;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_MINI_GAME, cmd = MiniGameConstant.MsgBean.RES_START_GOD_GAMBLERS_GAME, resp = true)
@ProtoDesc("开始赌神搓牌单局结果")
public class ResStartGodGamblersGame extends AbstractResponse {
    @ProtoDesc("本局状态及对手牌")
    public GodGamblersInfo info;
    @ProtoDesc("各消耗道具当前背包持有数量")
    public List<ItemInfo> ownedItems;

    public ResStartGodGamblersGame(int code) {
        super(code);
    }
}
