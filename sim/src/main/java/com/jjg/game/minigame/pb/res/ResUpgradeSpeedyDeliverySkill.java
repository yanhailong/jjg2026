package com.jjg.game.minigame.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.minigame.constant.MiniGameConstant;
import com.jjg.game.minigame.pb.struct.SpeedyDeliverySkillInfo;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_MINI_GAME, cmd = MiniGameConstant.MsgBean.RES_UPGRADE_SPEEDY_DELIVERY_SKILL, resp = true)
@ProtoDesc("升级飞驰快送技能结果")
public class ResUpgradeSpeedyDeliverySkill extends AbstractResponse {
    @ProtoDesc("本次请求的技能和服务端当前等级")
    public SpeedyDeliverySkillInfo skill;
    @ProtoDesc("可用技能积分")
    public long skillPoints;

    public ResUpgradeSpeedyDeliverySkill(int code) {
        super(code);
    }
}
