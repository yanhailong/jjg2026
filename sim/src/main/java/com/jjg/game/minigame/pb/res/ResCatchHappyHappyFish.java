package com.jjg.game.minigame.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.minigame.constant.MiniGameConstant;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_MINI_GAME, cmd = MiniGameConstant.MsgBean.RES_CATCH_HAPPY_HAPPY_FISH, resp = true)
@ProtoDesc("捕获欢乐捉鱼目标结果")
public class ResCatchHappyHappyFish extends AbstractResponse {
    @ProtoDesc("服务器当前累计积分")
    public int score;
    @ProtoDesc("本局结束时间，毫秒时间戳")
    public long endTime;

    public ResCatchHappyHappyFish(int code) {
        super(code);
    }
}
