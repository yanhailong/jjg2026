package com.jjg.game.minigame.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.minigame.constant.MiniGameConstant;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_MINI_GAME, cmd = MiniGameConstant.MsgBean.RES_START_HAPPY_HAPPY_FISH_GAME, resp = true)
@ProtoDesc("开始欢乐捉鱼结果")
public class ResStartHappyHappyFishGame extends AbstractResponse {
    @ProtoDesc("游戏结束时间，毫秒时间戳；后续请求以该初始值作为roundId，加时不改变roundId")
    public long endTime;

    public ResStartHappyHappyFishGame(int code) {
        super(code);
    }
}
