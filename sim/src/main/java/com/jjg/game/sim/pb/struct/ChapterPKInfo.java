package com.jjg.game.sim.pb.struct;

import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;

@ProtobufMessage
@ProtoDesc("pk信息")
public class ChapterPKInfo {
    @ProtoDesc("自己的战斗力")
    public long selfPower;
    @ProtoDesc("对方战斗力")
    public long targetPower;
}
