package com.jjg.game.minigame.pb.req;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractMessage;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.minigame.constant.MiniGameConstant;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_MINI_GAME, cmd = MiniGameConstant.MsgBean.REQ_CATCH_HAPPY_HAPPY_FISH)
@ProtoDesc("捕获欢乐捉鱼目标")
public class ReqCatchHappyHappyFish extends AbstractMessage {
    @ProtoDesc("开局返回的endTime")
    public long roundId;
    @ProtoDesc("MinigameFish配置ID")
    public int fishId;
}
