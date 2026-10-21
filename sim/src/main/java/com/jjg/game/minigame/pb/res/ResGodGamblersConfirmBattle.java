package com.jjg.game.minigame.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.pb.ItemInfo;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.minigame.constant.MiniGameConstant;
import com.jjg.game.minigame.pb.struct.GodGamblersInfo;

import java.util.List;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_MINI_GAME, cmd = MiniGameConstant.MsgBean.RES_GOD_GAMBLERS_CONFIRM_BATTLE, resp = true)
@ProtoDesc("确认比拼结果")
public class ResGodGamblersConfirmBattle extends AbstractResponse {
    @ProtoDesc("本局双方牌及结算状态")
    public GodGamblersInfo info;
    @ProtoDesc("本次成功发放的奖励")
    public List<ItemInfo> rewards;

    public ResGodGamblersConfirmBattle(int code) {
        super(code);
    }
}
