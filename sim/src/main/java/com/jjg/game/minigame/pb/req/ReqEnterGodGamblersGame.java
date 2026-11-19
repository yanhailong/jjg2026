package com.jjg.game.minigame.pb.req;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractMessage;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.minigame.constant.MiniGameConstant;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_MINI_GAME, cmd = MiniGameConstant.MsgBean.REQ_ENTER_GOD_GAMBLERS_GAME)
@ProtoDesc("进入赌神搓牌小游戏，放弃当前局并获取消耗及背包数量")
public class ReqEnterGodGamblersGame extends AbstractMessage {
    @ProtoDesc("0表示普通进入，2004表示从对应随机事件进入，值为RandomEventDetails.id")
    public int randomEventConfigId;
}
