package com.jjg.game.minigame.pb.req;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractMessage;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.minigame.constant.MiniGameConstant;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_MINI_GAME, cmd = MiniGameConstant.MsgBean.REQ_END_HAPPY_HAPPY_FISH_GAME)
@ProtoDesc("结束欢乐捉鱼")
public class ReqEndHappyHappyFishGame extends AbstractMessage {
    @ProtoDesc("开局返回的初始endTime，沙漏加时后仍使用初始值")
    public long roundId;
}
