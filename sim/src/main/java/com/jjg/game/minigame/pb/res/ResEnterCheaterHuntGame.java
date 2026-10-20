package com.jjg.game.minigame.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.minigame.constant.MiniGameConstant;
import com.jjg.game.minigame.pb.struct.CheaterHuntInfo;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_MINI_GAME, cmd = MiniGameConstant.MsgBean.RES_ENTER_CHEAT_HUNT_GAME, resp = true)
@ProtoDesc("进入找老千结果")
public class ResEnterCheaterHuntGame extends AbstractResponse {
    @ProtoDesc("当前进度及单局状态")
    public CheaterHuntInfo info;

    public ResEnterCheaterHuntGame(int code) {
        super(code);
    }
}
