package com.jjg.game.minigame.pb.req;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractMessage;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.minigame.constant.MiniGameConstant;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_MINI_GAME, cmd = MiniGameConstant.MsgBean.REQ_ENTER_HAPPY_HAPPY_FISH_GAME)
@ProtoDesc("进入欢乐捉鱼")
public class ReqEnterHappyHappyFishGame extends AbstractMessage {
    @ProtoDesc("0表示普通进入，2002表示从对应随机事件进入，值为RandomEventDetails.id")
    public int randomEventConfigId;
}
