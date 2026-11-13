package com.jjg.game.minigame.pb.struct;

import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;

@ProtobufMessage
@ProtoDesc("欢乐捉鱼技能")
public class HappyHappyFishSkillInfo {
    @ProtoDesc("技能组ID，对应MinigameFishSkil.groupId")
    public int groupId;
    @ProtoDesc("当前技能等级")
    public int level;

    public HappyHappyFishSkillInfo(int groupId, int level) {
        this.groupId = groupId;
        this.level = level;
    }
}
