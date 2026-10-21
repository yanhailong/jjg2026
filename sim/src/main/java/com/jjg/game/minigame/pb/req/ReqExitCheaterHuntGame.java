package com.jjg.game.minigame.pb.req;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractMessage;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.minigame.constant.MiniGameConstant;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_MINI_GAME, cmd = MiniGameConstant.MsgBean.REQ_EXIT_CHEATER_HUNT_GAME)
@ProtoDesc("退出找老千当前单局；已通关待发奖或已超时的单局先结算奖励，发放失败不退出")
public class ReqExitCheaterHuntGame extends AbstractMessage {
    @ProtoDesc("要退出的单局标识")
    public long roundId;
}
