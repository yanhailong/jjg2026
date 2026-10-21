package com.jjg.game.minigame.pb.req;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractMessage;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.minigame.constant.MiniGameConstant;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_MINI_GAME, cmd = MiniGameConstant.MsgBean.REQ_GOD_GAMBLERS_QUICK_BATTLE)
@ProtoDesc("快速比拼，扣除配置道具后直接确定双方牌并发奖")
public class ReqGodGamblersQuickBattle extends AbstractMessage {
}
