package com.jjg.game.minigame.pb.req;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractMessage;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.minigame.constant.MiniGameConstant;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_MINI_GAME, cmd = MiniGameConstant.MsgBean.REQ_QUICK_CLEAR_CHEATER_HUNT_GAME)
@ProtoDesc("从开始页按配置消耗道具一键通关；待发奖状态下仅重试发奖，不重复扣费")
public class ReqQuickClearCheaterHuntGame extends AbstractMessage {
}
