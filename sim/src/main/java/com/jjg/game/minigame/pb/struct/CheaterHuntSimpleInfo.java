package com.jjg.game.minigame.pb.struct;

import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;

@ProtobufMessage
@ProtoDesc("找老千关卡简要信息")
public class CheaterHuntSimpleInfo {
    @ProtoDesc("当前进度关卡ID，对应CheaterHuntLevel.id")
    public int levelId;
    @ProtoDesc("关卡名称多语言id")
    public int levelLangId;
    @ProtoDesc("是否通关")
    public boolean pass;
}
