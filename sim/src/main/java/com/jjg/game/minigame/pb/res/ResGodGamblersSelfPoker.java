package com.jjg.game.minigame.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.pb.ItemInfo;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.minigame.constant.MiniGameConstant;
import com.jjg.game.minigame.pb.struct.GodGamblersInfo;

import java.util.List;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_MINI_GAME, cmd = MiniGameConstant.MsgBean.RES_GOD_GAMBLERS_SELF_POKER, resp = true)
@ProtoDesc("获取自己牌结果")
public class ResGodGamblersSelfPoker extends AbstractResponse {
    @ProtoDesc("本局状态及本次获取的自己牌")
    public GodGamblersInfo info;
    @ProtoDesc("各消耗道具当前背包持有数量")
    public List<ItemInfo> ownedItems;

    public ResGodGamblersSelfPoker(int code) {
        super(code);
    }
}
