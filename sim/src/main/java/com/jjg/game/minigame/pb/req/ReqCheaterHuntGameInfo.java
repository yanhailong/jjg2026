package com.jjg.game.minigame.pb.req;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractMessage;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.minigame.constant.MiniGameConstant;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_MINI_GAME, cmd = MiniGameConstant.MsgBean.REQ_CHEAT_HUNT_GAME_INFO)
@ProtoDesc("获取找老千当前关卡并开始新局，每回合开局扣一次配置道具；已有进行中单局时拒绝重复开局")
public class ReqCheaterHuntGameInfo extends AbstractMessage {
}
