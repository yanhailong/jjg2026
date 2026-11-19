package com.jjg.game.minigame.pb.req;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractMessage;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.minigame.constant.MiniGameConstant;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_MINI_GAME, cmd = MiniGameConstant.MsgBean.REQ_ENTER_CHEAT_HUNT_GAME)
@ProtoDesc("进入找老千小游戏，获取关卡列表及一键通关开放状态")
public class ReqEnterCheaterHuntGame extends AbstractMessage {
    @ProtoDesc("0表示普通进入，2003表示从对应随机事件进入，值为RandomEventDetails.id")
    public int randomEventConfigId;
}
