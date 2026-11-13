package com.jjg.game.minigame.pb.req;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractMessage;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.minigame.constant.MiniGameConstant;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_MINI_GAME, cmd = MiniGameConstant.MsgBean.REQ_UPGRADE_HAPPY_HAPPY_FISH_SKILL)
@ProtoDesc("升级欢乐捉鱼技能")
public class ReqUpgradeHappyHappyFishSkill extends AbstractMessage {
    @ProtoDesc("技能组ID，对应MinigameFishSkil.groupId")
    public int groupId;
}
