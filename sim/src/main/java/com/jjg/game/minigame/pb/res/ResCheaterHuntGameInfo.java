package com.jjg.game.minigame.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.minigame.constant.MiniGameConstant;
import com.jjg.game.minigame.pb.struct.CheaterHuntInfo;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_MINI_GAME, cmd = MiniGameConstant.MsgBean.RES_CHEAT_HUNT_GAME_INFO, resp = true)
@ProtoDesc("获取找老千关卡信息结果")
public class ResCheaterHuntGameInfo extends AbstractResponse {
    @ProtoDesc("当前关卡进度及单局状态")
    public CheaterHuntInfo info;

    public ResCheaterHuntGameInfo(int code) {
        super(code);
    }
}
