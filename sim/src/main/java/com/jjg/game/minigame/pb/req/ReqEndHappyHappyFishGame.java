package com.jjg.game.minigame.pb.req;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractMessage;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.minigame.constant.MiniGameConstant;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_MINI_GAME, cmd = MiniGameConstant.MsgBean.REQ_END_HAPPY_HAPPY_FISH_GAME)
@ProtoDesc("结束欢乐捉鱼，支持中途退出并按当前积分结算")
public class ReqEndHappyHappyFishGame extends AbstractMessage {
    @ProtoDesc("开局返回的endTime")
    public long roundId;
}
