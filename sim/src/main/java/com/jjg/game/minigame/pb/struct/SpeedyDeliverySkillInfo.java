package com.jjg.game.minigame.pb.struct;

import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;

@ProtobufMessage
@ProtoDesc("飞驰快送技能")
public class SpeedyDeliverySkillInfo {
    @ProtoDesc("技能组ID，对应MinigameSpeedySkil.groupId")
    public int groupId;
    @ProtoDesc("当前技能等级")
    public int level;

    public SpeedyDeliverySkillInfo(int groupId, int level) {
        this.groupId = groupId;
        this.level = level;
    }
}
