package com.jjg.game.minigame.pb.req;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractMessage;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.minigame.constant.MiniGameConstant;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_MINI_GAME, cmd = MiniGameConstant.MsgBean.REQ_EXIT_CHEATER_HUNT_GAME)
@ProtoDesc("退出找老千当前单局，未通关不发奖励")
public class ReqExitCheaterHuntGame extends AbstractMessage {
    @ProtoDesc("要退出的单局标识")
    public long roundId;
}
