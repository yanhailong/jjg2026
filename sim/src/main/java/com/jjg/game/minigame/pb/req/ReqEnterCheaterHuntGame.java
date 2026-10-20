package com.jjg.game.minigame.pb.req;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractMessage;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.minigame.constant.MiniGameConstant;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_MINI_GAME, cmd = MiniGameConstant.MsgBean.REQ_ENTER_CHEAT_HUNT_GAME)
@ProtoDesc("开始找老千新局；已有进行中单局时拒绝重复进入，离开游戏需发送退出请求")
public class ReqEnterCheaterHuntGame extends AbstractMessage {
}
