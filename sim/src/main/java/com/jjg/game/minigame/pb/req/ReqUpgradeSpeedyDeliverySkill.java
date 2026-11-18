package com.jjg.game.minigame.pb.req;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractMessage;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.minigame.constant.MiniGameConstant;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_MINI_GAME, cmd = MiniGameConstant.MsgBean.REQ_UPGRADE_SPEEDY_DELIVERY_SKILL)
@ProtoDesc("升级飞驰快送技能")
public class ReqUpgradeSpeedyDeliverySkill extends AbstractMessage {
    @ProtoDesc("技能组ID，对应MinigameSpeedySkil.groupId")
    public int groupId;
}
