package com.jjg.game.sim.pb.struct;

import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;

@ProtobufMessage
@ProtoDesc("产出属性")
public class OutputAttributsInfo {
    @ProtoDesc("为BuildingOutputType")
    public int key;
    @ProtoDesc("当前值")
    public long currentValue;
    @ProtoDesc("下一星级的值，不可升星时为当前值")
    public long nextStarValue;
    @ProtoDesc("下一等级的值，达到等级上限时为当前值")
    public long nextLevelValue;
    @ProtoDesc("升十级后的值，遇到突破门槛或等级上限时按该等级计算")
    public long nextTenLevelValue;
}
