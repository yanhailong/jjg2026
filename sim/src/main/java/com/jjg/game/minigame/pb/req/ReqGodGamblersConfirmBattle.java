package com.jjg.game.minigame.pb.req;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractMessage;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.minigame.constant.MiniGameConstant;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_MINI_GAME, cmd = MiniGameConstant.MsgBean.REQ_GOD_GAMBLERS_CONFIRM_BATTLE)
@ProtoDesc("确认比拼并领取当前牌对应奖励；最后一次搓牌后仍需请求")
public class ReqGodGamblersConfirmBattle extends AbstractMessage {
    @ProtoDesc("本次单局标识；待发奖时可原样重试")
    public long roundId;
}
