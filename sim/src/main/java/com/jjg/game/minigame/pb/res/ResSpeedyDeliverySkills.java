package com.jjg.game.minigame.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.minigame.constant.MiniGameConstant;
import com.jjg.game.minigame.pb.struct.SpeedyDeliverySkillInfo;

import java.util.List;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_MINI_GAME, cmd = MiniGameConstant.MsgBean.RES_SPEEDY_DELIVERY_SKILLS, resp = true)
@ProtoDesc("飞驰快送技能和可用积分")
public class ResSpeedyDeliverySkills extends AbstractResponse {
    @ProtoDesc("所有配置技能组的当前等级，未升级为0")
    public List<SpeedyDeliverySkillInfo> skills;
    @ProtoDesc("可用技能积分")
    public long skillPoints;

    public ResSpeedyDeliverySkills(int code) {
        super(code);
    }
}
