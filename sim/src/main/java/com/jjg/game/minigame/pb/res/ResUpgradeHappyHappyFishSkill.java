package com.jjg.game.minigame.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.minigame.constant.MiniGameConstant;
import com.jjg.game.minigame.pb.struct.HappyHappyFishSkillInfo;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_MINI_GAME, cmd = MiniGameConstant.MsgBean.RES_UPGRADE_HAPPY_HAPPY_FISH_SKILL, resp = true)
@ProtoDesc("升级欢乐捉鱼技能结果")
public class ResUpgradeHappyHappyFishSkill extends AbstractResponse {
    @ProtoDesc("本次请求的技能和服务端当前等级")
    public HappyHappyFishSkillInfo skill;
    @ProtoDesc("可用技能积分")
    public long skillPoints;

    public ResUpgradeHappyHappyFishSkill(int code) {
        super(code);
    }
}
