package com.jjg.game.sim.pb.struct;

import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;

@ProtobufMessage
@ProtoDesc("雇员技能信息")
public class EmployeeSkillInfo {
    @ProtoDesc("技能组id")
    public int groupId;
    @ProtoDesc("当前技能等级")
    public int level;
    @ProtoDesc("当前EmployeeSkillConfig配置id")
    public int configId;
    @ProtoDesc("最大等级")
    public int maxLevel;
}
