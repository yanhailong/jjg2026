package com.jjg.game.minigame.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.minigame.constant.MiniGameConstant;
import com.jjg.game.minigame.pb.struct.CheaterHuntInfo;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_MINI_GAME, cmd = MiniGameConstant.MsgBean.RES_EXIT_CHEATER_HUNT_GAME, resp = true)
@ProtoDesc("退出找老千结果")
public class ResExitCheaterHuntGame extends AbstractResponse {
    @ProtoDesc("退出后的进度及单局状态")
    public CheaterHuntInfo info;

    public ResExitCheaterHuntGame(int code) {
        super(code);
    }
}
